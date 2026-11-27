package com.nuvio.app.features.livetv.player

import com.nuvio.app.features.livetv.model.descriptionText
import com.nuvio.app.features.livetv.model.subTitle
import com.nuvio.app.features.livetv.data.CatchupUrlBuilder
import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.data.LiveTvRepository
import com.nuvio.app.features.livetv.data.ServerZones
import com.nuvio.app.features.livetv.data.XtreamClient
import com.nuvio.app.features.livetv.model.EpgProgram
import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.livetv.model.LiveTvSettings
import com.nuvio.app.features.livetv.model.LiveUserState
import com.nuvio.app.features.livetv.model.BackupOrder
import com.nuvio.app.features.livetv.parental.ParentalControls
import com.nuvio.app.features.livetv.model.LiveTvClock
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.player.PlayerLaunch
import com.nuvio.app.features.player.PlayerLaunchStore
import com.nuvio.app.features.livetv.stalker.StalkerGateway
import com.nuvio.stalker.StalkerContentKind
import com.nuvio.stalker.StalkerStreamRef
import com.nuvio.stalker.StalkerException
import com.nuvio.stalker.StalkerError
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/** One stream the player is told to open: the link, its headers and what kind of stream it is. */
internal data class LivePlayRequest(
    val url: String,
    val headers: Map<String, String>,
    /** "hls" for .m3u8 links, null for anything else (the player sniffs it). */
    val streamType: String?,
    val mediaPolicy: com.nuvio.app.features.livetv.stalker.StalkerMediaPolicy? = null,
) {
    override fun toString(): String = "LivePlayRequest(streamType=$streamType, <redacted>)"
}

/** What to play instead when a live stream fails: another link for the same channel, or another channel. */
internal data class LiveFallback(
    val channel: LiveChannel,
    val request: LivePlayRequest,
    /** Set when it's a different channel ("ESPN Backup"), for a notice to the viewer. */
    val switchedFrom: LiveChannel? = null,
)

/**
 * Builds what the app's own player needs to play a channel, and decides what to try when a
 * channel won't play, the TV app's way:
 *  1. an Xtream HLS link that can't be read is played as TS once (and remembered for this run);
 *  2. then another channel with the same name from another group or playlist, when
 *     "Switch to a backup channel" is on, each tried once.
 * The audio track picked on a channel is kept by the player under the channel's key (the
 * launch's content id), so it's used again the next time that channel plays.
 */
internal object LiveTvPlayback {
    const val PROVIDER_ID = "livetv"
    const val CONTENT_TYPE = "livetv"

    private val lock = SynchronizedObject()

    /** Live channels whose HLS link couldn't be read: played as TS (until the app restarts). */
    private val tsInstead = HashSet<String>()

    /** Channels already tried in a row of backup switches (cleared when you pick a channel). */
    private val backupTried = HashSet<String>()

    /** The channel behind each source URL handed to the player (fallbacks included). */
    private val playing = HashMap<String, LiveChannel>()
    private val portalRetried = HashSet<String>()

    /** Channels by their "backup name", so a failed channel's stand-ins are found quickly. */
    private var backupIndex: Map<String, List<LiveChannel>> = emptyMap()
    private var backupIndexFor: List<LiveChannel> = emptyList()

    /** A catch-up replay the player was handed: the show, and the other links still to try. */
    private class CatchupContext(val channel: LiveChannel, val program: EpgProgram, val remaining: ArrayDeque<String>, val primary: String, val portal: StalkerStreamRef? = null)

    /** Playlist id -> the kind of catch-up link that last worked there (this run only; see [CatchupUrlBuilder.linkForm]). */
    private val catchupForms = HashMap<String, String>()

    /** The catch-up replay behind each source URL handed to the player (fallback links included). */
    private val catchups = HashMap<String, CatchupContext>()

    /** TS catch-up links being tried because the HLS one failed: URL -> playlist id. */
    private val hlsFailedOn = HashMap<String, String>()

    /** Playlists already told this run that their server's time zone is unknown (one notice each). */
    private val zoneWarned = HashSet<String>()

    /** How long a replay waits for the server's time zone before playing without it. */
    private const val ZONE_WAIT_MS = 5_000L

    /**
     * The Sports hub's answer to "this channel showing a live game won't play": the next channel
     * showing the same game (the failed channel's key, and the keys already tried).
     */
    var gameFailover: ((failedKey: String, tried: Set<String>) -> LiveChannel?)? = null

    /** The key of the channel last handed to the player (for the Sports hub's score on the info bar). */
    @kotlin.concurrent.Volatile
    var currentKey: String? = null
        private set

    /** The request for a channel, with its HLS→TS memory applied. */
    suspend fun requestFor(channel: LiveChannel): LivePlayRequest {
        channel.stalker?.let { ref ->
            val resolved = StalkerGateway.resolve(channel.sourceId, ref)
            return request(channel, resolved.url, resolved.headers, resolved.mediaPolicy, resolved.streamType)
        }
        if (StalkerGateway.isLocator(channel.url)) throw StalkerException(StalkerError.LINK_UNAVAILABLE)
        val url = synchronized(lock) { if (channel.key in tsInstead) tsLinkFor(channel.url) ?: channel.url else channel.url }
        return request(channel, url)
    }

    private fun request(channel: LiveChannel, url: String, portalHeaders: Map<String, String> = emptyMap(), policy: com.nuvio.app.features.livetv.stalker.StalkerMediaPolicy? = null, portalStreamType: String? = null): LivePlayRequest {
        val headers = LinkedHashMap<String, String>()
        (if (channel.stalker == null) channel.headers else portalHeaders).forEach { (k, v) -> if (k.isNotBlank() && v.isNotBlank()) headers[k] = v }
        if (headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) headers["User-Agent"] = XtreamClient.DEFAULT_UA
        return LivePlayRequest(url = url, headers = headers, streamType = portalStreamType ?: streamTypeOf(url, channel), mediaPolicy = policy)
    }

    /** "hls" for .m3u8 links; null otherwise, so the player works the format out itself. */
    private fun streamTypeOf(url: String, channel: LiveChannel): String? {
        val base = url.substringBefore('?').lowercase()
        return when {
            base.endsWith(".m3u8") -> "hls"
            base.endsWith(".mpd") || channel.drm?.manifestType == "mpd" -> "dash"
            else -> null
        }
    }

    /**
     * The player launch for a channel, as the rest of the app opens streams. [profileId] is the
     * active profile. [nowTitle] is what's on, shown under the channel's name.
     */
    suspend fun launchFor(channel: LiveChannel, profileId: Int, nowTitle: String? = null): PlayerLaunch {
        val req = requestFor(channel)
        synchronized(lock) {
            // A channel you picked yourself: the backup list starts afresh.
            backupTried.clear()
            portalRetried.clear()
            playing.clear(); catchups.clear()
            playing[req.url] = channel
        }
        currentKey = channel.key
        return PlayerLaunch(
            profileId = profileId,
            title = channel.name,
            sourceUrl = req.url,
            // A YouTube highlight from Sports: its sound is a file of its own.
            sourceAudioUrl = if (channel.sourceId == com.nuvio.app.features.livetv.sports.SportsVideos.SOURCE_ID)
                com.nuvio.app.features.livetv.sports.SportsVideos.soundFor(req.url) else null,
            sourceHeaders = req.headers,
            stalkerMediaPolicy = req.mediaPolicy,
            streamType = req.streamType,
            logo = channel.logo,
            poster = channel.logo,
            streamTitle = channel.name,
            streamSubtitle = nowTitle ?: channel.group,
            pauseDescription = nowTitle,
            providerName = channel.sourceName,
            providerAddonId = PROVIDER_ID,
            contentType = CONTENT_TYPE,
            videoId = channel.key,
            parentMetaId = channel.key,
            parentMetaType = CONTENT_TYPE,
        )
    }

    /** Puts the launch in the store and returns its id, for a PlayerRoute. */
    suspend fun prepare(channel: LiveChannel, profileId: Int, nowTitle: String? = null): Long {
        val launch = launchFor(channel, profileId, nowTitle)
        // Adult channels never go in Recently watched.
        LiveTvPreferences.recordWatched(channel.key, addToRecent = !com.nuvio.app.features.livetv.parental.ParentalControls.isAdultChannel(channel))
        return PlayerLaunchStore.put(launch)
    }

    /**
     * Changing channel inside the player (its channel panel or a swipe on the video): the same
     * bookkeeping as picking the channel from a list, minus the new player screen.
     */
    suspend fun switchTo(channel: LiveChannel): LivePlayRequest {
        val req = requestFor(channel)
        // A sports add-on's stream (from the Backups list) isn't one of your channels: not kept as Recent or last channel.
        if (channel.sourceId != com.nuvio.app.features.livetv.sports.SportsAddonSource.SOURCE_ID)
            LiveTvPreferences.recordWatched(channel.key, addToRecent = !com.nuvio.app.features.livetv.parental.ParentalControls.isAdultChannel(channel))
        synchronized(lock) {
            backupTried.clear()
            portalRetried.clear()
            playing.clear(); catchups.clear()
            playing[req.url] = channel
        }
        currentKey = channel.key
        return req
    }

    /** Set while a Live TV channel is on screen: a swipe up (true) or down (false) on the video changes channel. */
    @kotlin.concurrent.Volatile
    var channelSwipe: ((next: Boolean) -> Unit)? = null

    /** Catch-up links already reported as playing (the player reports it again on every pause and play). */
    private val reportedPlaying = HashSet<String>()

    /** What kind of catch-up or stream link this is, for the Start-Up Report (never the link itself: it holds the login). */
    private fun linkKind(url: String): String = when {
        LiveTvCatchup.isHlsTimeshift(url) -> "HLS timeshift"
        LiveTvCatchup.isTsTimeshift(url) -> "TS timeshift"
        url.contains(".m3u8", ignoreCase = true) -> "HLS"
        url.substringBefore('?').endsWith(".ts", ignoreCase = true) -> "TS"
        else -> "provider"
    }

    /** Whether this provider id is Live TV's (the player asks before trying a fallback). */
    fun owns(providerAddonId: String?): Boolean = providerAddonId == PROVIDER_ID

    /** Recordings handed to the player (their captions are inside the video too). */
    private val recordings = HashSet<String>()

    fun noteRecording(url: String) { synchronized(lock) { recordings += url } }

    /** A recording handed to the player (read from the same storage as the video). */
    fun isRecording(url: String): Boolean = synchronized(lock) { url in recordings }

    /**
     * Whether the player should look for closed captions hidden inside the video: live channels,
     * catch-up and recordings (not Sports highlight clips).
     */
    fun looksForCaptions(url: String): Boolean = synchronized(lock) {
        url in recordings || (playing[url]?.let { it.sourceId != com.nuvio.app.features.livetv.sports.SportsVideos.SOURCE_ID } ?: false)
    }

    fun isPortalReplay(url: String): Boolean = synchronized(lock) { catchups[url]?.portal != null }

    /** Whether [url] is a catch-up replay (not a live channel), for seek thumbnails. */
    fun isCatchup(url: String): Boolean = synchronized(lock) { catchups.containsKey(url) }

    /** An official highlight or condensed game from Sports (a clip: when it ends, the player closes). */
    fun isSportsClip(url: String): Boolean =
        synchronized(lock) { playing[url] }?.sourceId == com.nuvio.app.features.livetv.sports.SportsVideos.SOURCE_ID

    /** Only actual continuous channels: replays, saved recordings and highlight clips end normally. */
    fun isContinuousLive(providerAddonId: String?, url: String): Boolean = owns(providerAddonId) && synchronized(lock) {
        val channel = playing[url]
        channel != null && url !in catchups && url !in recordings && isLiveChannelSource(channel)
    }

    // Add-on channels also represent completed-game replays without catch-up metadata.
    private fun isLiveChannelSource(channel: LiveChannel): Boolean =
        channel.sourceId != com.nuvio.app.features.livetv.sports.SportsVideos.SOURCE_ID &&
            channel.sourceId != com.nuvio.app.features.livetv.sports.SportsAddonSource.SOURCE_ID

    // ---------------------------------------------------------------- catch-up

    /**
     * Puts a catch-up replay of [program] on [channel] in the launch store (from [offsetMs] into
     * the show) and returns its id for a PlayerRoute, or null when the show can't be replayed.
     * The time zone picked for this playlist, else the server's. Not known yet (M3U from an Xtream
     * server, or a login whose server didn't say): looked up first (a few seconds at most), so the
     * replay starts at the right time. Once found it's saved with the playlist; until then it's
     * asked again on later replays.
     */
    suspend fun prepareCatchup(channel: LiveChannel, program: EpgProgram, profileId: Int, offsetMs: Long = 0L): Long? {
        channel.stalker?.let { channelRef ->
            if (!LiveTvCatchup.isReplayable(channel, program, LiveTvClock.nowMs())) return null
            val ref = channelRef.copy(kind = StalkerContentKind.ARCHIVE, command = "", programId = program.stalkerProgramId,
                sourceIdentity = program.stalkerSourceIdentity)
            val stream = StalkerGateway.resolve(channel.sourceId, ref)
            val req = request(channel, stream.url, stream.headers, stream.mediaPolicy, stream.streamType)
            synchronized(lock) {
                backupTried.clear(); portalRetried.clear(); playing.clear(); catchups.clear(); playing[req.url] = channel
                catchups[req.url] = CatchupContext(channel, program, ArrayDeque(), req.url, ref)
            }
            prefsRecordWatched(channel)
            return PlayerLaunchStore.put(PlayerLaunch(
                profileId = profileId, title = com.nuvio.app.features.livetv.record.RecordingRules.shownTitle(program.title), sourceUrl = req.url, sourceHeaders = req.headers, stalkerMediaPolicy = req.mediaPolicy,
                streamType = req.streamType, logo = channel.logo, poster = program.icon ?: channel.logo,
                streamTitle = com.nuvio.app.features.livetv.model.guideTitleWithSub(com.nuvio.app.features.livetv.record.RecordingRules.shownTitle(program.title), program.subTitle), streamSubtitle = channel.name, pauseDescription = program.descriptionText,
                providerName = channel.sourceName, providerAddonId = PROVIDER_ID, contentType = CONTENT_TYPE,
                videoId = channel.key, parentMetaId = channel.key, parentMetaType = CONTENT_TYPE,
                initialPositionMs = offsetMs.coerceAtLeast(0),
            ))
        }
        val prefs = LiveTvPreferences
        val stored = prefs.currentPlaylists().firstOrNull { it.id == channel.sourceId }
        var playlist = stored
        if (stored != null && stored.catchupZone.isNotBlank()) {
            // A zone worked out from the server's clock or guide is checked again now and then
            // in the background (clock changes); never one picked in settings.
            if (stored.catchupTimezone.isBlank()) ServerZones.recheck(channel)
        } else if (stored != null && ServerZones.canLookUp(channel, stored)) {
            val tz = runCatching { withTimeoutOrNull(ZONE_WAIT_MS) { ServerZones.learn(channel) } }.getOrNull()
            if (tz != null) playlist = stored.copy(serverTimezone = tz)
        }
        val now = LiveTvClock.nowMs()
        val plan = LiveTvCatchup.plan(channel, program, prefs.currentSettings(), playlist, now, offsetMs) ?: return null
        if (playlist?.catchupZone.isNullOrBlank()) warnZoneUnknown(playlist?.id, playlist?.name, plan.url)
        com.nuvio.app.features.livetv.model.LiveTvLoadReport.add("Catch-up \"${program.title}\" on ${channel.name}: ${linkKind(plan.url)} link, ${plan.fallbacks.size} other link(s) to try" +
            (if (offsetMs > 0) ", from ${offsetMs / 60_000} min in" else "") +
            (playlist?.catchupZone?.takeIf { it.isNotBlank() }?.let { ", time zone $it" } ?: ", time zone unknown"))
        // The kind of link that worked last time on this provider goes first, so a skip or a
        // second replay doesn't wait on links the provider never answers.
        val links = CatchupUrlBuilder.preferForm(plan.url, plan.fallbacks, synchronized(lock) { catchupForms[channel.sourceId] })
        val req = request(channel, links.first())
        synchronized(lock) {
            backupTried.clear()
            portalRetried.clear(); playing.clear(); catchups.clear()
            playing[req.url] = channel
            catchups[req.url] = CatchupContext(channel, program, ArrayDeque(links.drop(1)), plan.url)
        }
        prefs.recordWatched(channel.key)
        val launch = PlayerLaunch(
            profileId = profileId,
            title = com.nuvio.app.features.livetv.record.RecordingRules.shownTitle(program.title),
            sourceUrl = req.url,
            sourceHeaders = req.headers,
            streamType = req.streamType,
            logo = channel.logo,
            poster = program.icon ?: channel.logo,
            streamTitle = com.nuvio.app.features.livetv.model.guideTitleWithSub(com.nuvio.app.features.livetv.record.RecordingRules.shownTitle(program.title), program.subTitle),
            streamSubtitle = channel.name,
            pauseDescription = program.descriptionText,
            providerName = channel.sourceName,
            providerAddonId = PROVIDER_ID,
            contentType = CONTENT_TYPE,
            videoId = channel.key,
            parentMetaId = channel.key,
            parentMetaType = CONTENT_TYPE,
        )
        return PlayerLaunchStore.put(launch)
    }

    /**
     * The server's time zone couldn't be found, so catch-up times are written in this device's own
     * zone and can land on a show hours away: says once per playlist (per app run) where to set it.
     */
    private fun warnZoneUnknown(sourceId: String?, name: String?, url: String) {
        if (sourceId == null || name == null || CatchupUrlBuilder.askedStamp(url) == null) return
        if (!synchronized(lock) { zoneWarned.add(sourceId) }) return
        NuvioToastController.show(
            "\"$name\" didn't say its time zone, so replays may start at the wrong time. Pick it under Catch-up time zone in the playlist's menu in Live TV settings",
            durationMillis = 6_000L,
        )
    }

    /**
     * The player reports what it's playing: a catch-up that plays as TS after its HLS link failed
     * means this provider has no HLS replays (TS is asked for straight away for a week); one
     * playing as HLS means it does, so a note from before is cleared.
     */
    fun onPlaying(url: String?) {
        if (url == null) return
        val ctx = synchronized(lock) { catchups[url] } ?: return
        if (synchronized(lock) { reportedPlaying.add(url) }) {
            com.nuvio.app.features.livetv.model.LiveTvLoadReport.add("Catch-up \"${ctx.program.title}\" playing (${linkKind(url)} link)")
        }
        val sourceId = ctx.channel.sourceId
        // Remember the kind of link this provider answers (the "other clock" link can play an
        // earlier show even when it's wrong, so it is never put first).
        if (CatchupUrlBuilder.wholeMinutes(ctx.primary)) {
            val form = CatchupUrlBuilder.linkForm(url, ctx.primary)
            if ("+clock" !in form) synchronized(lock) { catchupForms[sourceId] = form }
        }
        if (LiveTvCatchup.isTsTimeshift(url)) {
            val failedHls = synchronized(lock) { hlsFailedOn.remove(url) } != null
            if (failedHls) {
                val now = LiveTvClock.nowMs()
                LiveTvPreferences.updatePlaylists { l -> l.map { if (it.id == sourceId) it.copy(catchupNoHlsSince = now) else it } }
            }
        } else if (LiveTvCatchup.isHlsTimeshift(url)) {
            if (LiveTvPreferences.currentPlaylists().any { it.id == sourceId && it.catchupNoHlsSince > 0L }) {
                LiveTvPreferences.updatePlaylists { l -> l.map { if (it.id == sourceId) it.copy(catchupNoHlsSince = 0L) else it } }
            }
        }
    }

    /** The next form of a catch-up request to try after [failedUrl], or null when none is left. */
    private fun catchupFallback(failedUrl: String, ctx: CatchupContext): LiveFallback? {
        val next = synchronized(lock) { ctx.remaining.removeFirstOrNull() }
        if (next == null) {
            com.nuvio.app.features.livetv.model.LiveTvLoadReport.add("Catch-up failed on ${ctx.channel.name}; no other links left to try")
            return null
        }
        com.nuvio.app.features.livetv.model.LiveTvLoadReport.add("Catch-up failed on ${ctx.channel.name} (${linkKind(failedUrl)} link); trying a ${linkKind(next)} link")
        val req = request(ctx.channel, next)
        synchronized(lock) {
            playing[req.url] = ctx.channel
            catchups[req.url] = ctx
            if (LiveTvCatchup.isHlsTimeshift(failedUrl) && LiveTvCatchup.isTsTimeshift(req.url)) hlsFailedOn[req.url] = ctx.channel.sourceId
        }
        return LiveFallback(ctx.channel, req)
    }

    /**
     * What to play after [failedUrl] failed: for a catch-up replay, the next form of the same
     * request; for a live channel, the TS link for an HLS one, then a backup channel; or null
     * when nothing is left (the player shows the error).
     */
    suspend fun fallbackFor(failedUrl: String): LiveFallback? {
        synchronized(lock) { catchups[failedUrl] }?.let { ctx ->
            if (ctx.portal == null) return catchupFallback(failedUrl, ctx)
            if (!synchronized(lock) { portalRetried.add("archive:${ctx.channel.key}:${ctx.program.startMs}") }) {
                com.nuvio.app.features.livetv.model.LiveTvLoadReport.add("Catch-up failed on ${ctx.channel.name} (portal), also after asking the portal again")
                return null
            }
            com.nuvio.app.features.livetv.model.LiveTvLoadReport.add("Catch-up failed on ${ctx.channel.name} (portal); asking the portal for a new link")
            val stream = StalkerGateway.resolve(ctx.channel.sourceId, ctx.portal)
            val req = request(ctx.channel, stream.url, stream.headers, stream.mediaPolicy, stream.streamType)
            synchronized(lock) { playing[req.url] = ctx.channel; catchups[req.url] = ctx }
            return LiveFallback(ctx.channel, req)
        }
        val channel = synchronized(lock) { playing[failedUrl] } ?: return null
        if (channel.stalker != null && synchronized(lock) { portalRetried.add(channel.key) }) {
            com.nuvio.app.features.livetv.model.LiveTvLoadReport.add("Channel ${channel.name} failed (portal); asking the portal for a new link")
            val req = requestFor(channel)
            synchronized(lock) { playing[req.url] = channel }
            return LiveFallback(channel, req)
        }
        // 1. Xtream HLS that won't play: the same channel as TS, once.
        val ts = synchronized(lock) {
            if (channel.stalker != null || channel.key in tsInstead) null
            else tsLinkFor(failedUrl)?.takeIf { it != failedUrl }?.also { tsInstead += channel.key }
        }
        if (ts != null) {
            com.nuvio.app.features.livetv.model.LiveTvLoadReport.add("Channel ${channel.name}: its HLS stream couldn't be played; playing it as TS instead")
            val req = request(channel, ts)
            synchronized(lock) { playing[req.url] = channel }
            return LiveFallback(channel, req)
        }
        // 2. Another channel by the same name; 3. another channel showing the same game (Sports).
        val sameName = backupFor(channel)
        val next = sameName ?: gameFailoverFor(channel)
        if (next == null) {
            com.nuvio.app.features.livetv.model.LiveTvLoadReport.add("Channel ${channel.name} won't play; " +
                if (LiveTvPreferences.currentSettings().autoBackupChannel) "no backup channel left to try" else "Backup channel is off")
            return null
        }
        com.nuvio.app.features.livetv.model.LiveTvLoadReport.add("Channel ${channel.name} won't play: switched to ${next.name} (${next.group})" + if (sameName == null) ", same game" else "")
        val req = requestFor(next)
        synchronized(lock) { playing[req.url] = next; backupTried += next.key }
        currentKey = next.key
        LiveTvPreferences.recordWatched(next.key, addToRecent = false)
        return LiveFallback(next, req, switchedFrom = channel)
    }

    private fun prefsRecordWatched(channel: LiveChannel) = LiveTvPreferences.recordWatched(channel.key,
        addToRecent = !com.nuvio.app.features.livetv.parental.ParentalControls.isAdultChannel(channel))

    /** The current feed still works. Resolve a distinct same-channel backup before handing it over. */
    suspend fun backupForStalledChannel(sourceUrl: String, sourceKey: String, stillCurrent: () -> Boolean): LiveFallback? {
        val settings = LiveTvPreferences.currentSettings()
        fun eligible(): Boolean = stillCurrent() && LiveTvPreferences.currentSettings().let {
            it.autoBackupChannel && it.adaptiveLiveBufferEnabled
        } && currentKey == sourceKey && isContinuousLive(PROVIDER_ID, sourceUrl)
        if (!eligible()) return null
        val from = synchronized(lock) { playing[sourceUrl]?.takeIf { it.key == sourceKey } } ?: return null
        val tried = synchronized(lock) { backupTried.toSet() }
        val copies = copiesAmong(from, tried).filter(::isLiveChannelSource).toList()
        val playlists = LiveTvPreferences.currentPlaylists().associate { it.id to it.name }
        val ordered = BackupOrder.organize(copies, settings, playlists) { it }.flatMap { it.channels }
        val attempted = HashSet<String>()
        for (candidate in ordered) {
            if (!eligible()) return null
            // Include failed resolutions and aliases when continuing through the existing backup list.
            if (!attempted.add(candidate.key) || synchronized(lock) { candidate.key in backupTried }) continue
            val req = try { requestFor(candidate) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { continue }
            if (!eligible()) return null
            // A portal can take time to resolve. Respect visibility or PIN changes made meanwhile.
            if (!backupVisible(candidate)) continue
            val accepted = synchronized(lock) {
                if (currentKey != sourceKey || playing[sourceUrl]?.key != sourceKey || req.url in playing) false
                else {
                    backupTried += from.key
                    backupTried += attempted
                    // Some playlists give several channel IDs the exact same underlying stream.
                    // Subsequent hard-error retries should not circle back through those aliases.
                    backupTried += copies.filter { it.stalker == null && (it.url in playing || it.url == req.url) }.map { it.key }
                    playing[req.url] = candidate
                    currentKey = candidate.key
                    true
                }
            }
            if (accepted) {
                LiveTvPreferences.recordWatched(candidate.key, addToRecent = false)
                return LiveFallback(candidate, req, switchedFrom = from)
            }
        }
        if (eligible()) synchronized(lock) { backupTried += attempted }
        return null
    }

    private fun backupFor(failed: LiveChannel): LiveChannel? {
        if (!LiveTvPreferences.currentSettings().autoBackupChannel) return null
        synchronized(lock) { backupTried += failed.key }
        val tried = synchronized(lock) { backupTried.toSet() }
        return backupAmong(failed, tried)
    }

    /**
     * A copy of [failed] (same channel, another stream: other playlists first) that isn't in
     * [tried], for a Multiview pane whose channel won't play. Follows the "Backup channel"
     * setting; doesn't touch the full-screen player's own list of channels tried.
     */
    fun backupChannel(failed: LiveChannel, tried: Set<String>): LiveChannel? {
        if (!LiveTvPreferences.currentSettings().autoBackupChannel) return null
        return backupAmong(failed, tried)
    }

    private fun backupAmong(failed: LiveChannel, tried: Set<String>): LiveChannel? = copiesAmong(failed, tried).firstOrNull()

    /**
     * Every other stream of [channel] (same channel name, other playlists first), for the
     * player's Backups list. Not tied to the "Backup channel" setting: picked by hand.
     */
    fun copiesOf(channel: LiveChannel): List<LiveChannel> = copiesAmong(channel, emptySet()).take(30).toList()

    private fun copiesAmong(failed: LiveChannel, tried: Set<String>): Sequence<LiveChannel> {
        val index = indexNow()
        val name = backupName(failed.name)
        if (name.length < 2) return emptySequence()
        val hidden = LiveTvPreferences.currentUserState()
        val settings = LiveTvPreferences.currentSettings()
        return index[name].orEmpty().asSequence()
            .filter { it.key !in tried && it.key != failed.key }
            .filter { backupVisible(it, hidden, settings) }
            // Local stations: "FOX (WNYW)" isn't a backup for "FOX (KTTV)".
            .filter { c -> val a = stationNote(failed.name); val b = stationNote(c.name); a == null || b == null || a == b }
            // Other playlists first.
            .sortedBy { if (it.sourceId != failed.sourceId) 0 else 1 }
    }

    private fun backupVisible(
        channel: LiveChannel,
        user: LiveUserState = LiveTvPreferences.currentUserState(),
        settings: LiveTvSettings = LiveTvPreferences.currentSettings(),
    ) = channel.key !in user.hiddenChannels && channel.groupId !in user.hiddenGroups && !user.hidesPlaylistOf(channel) &&
        !ParentalControls.isLocked(channel.groupId, "${channel.group} ${channel.name}", settings, user.lockedGroups)

    private fun gameFailoverFor(failed: LiveChannel): LiveChannel? {
        if (!LiveTvPreferences.currentSettings().autoBackupChannel) return null
        val tried = synchronized(lock) { backupTried += failed.key; backupTried.toSet() }
        return runCatching { gameFailover?.invoke(failed.key, tried) }.getOrNull()?.takeIf { it.key != failed.key && it.key !in tried }
    }

    private fun indexNow(): Map<String, List<LiveChannel>> {
        val list = LiveTvRepository.displayChannels.value
        synchronized(lock) {
            if (backupIndexFor !== list) {
                backupIndex = list.groupBy { backupName(it.name) }
                backupIndexFor = list
            }
            return backupIndex
        }
    }

    /**
     * A plain reason for the player's error text, the TV app's wording where it fits. On screen in the phone's language; the Start-Up Report keeps English so it reads the same for everyone.
     */
    suspend fun describeError(message: String?, url: String? = null): String {
        val m = message.orEmpty()
        val code = Regex("""\b(4\d\d|5\d\d|884)\b""").find(m)?.value?.toIntOrNull()
        val catchup = url != null && synchronized(lock) { catchups.containsKey(url) }
        val name = url?.let { synchronized(lock) { playing[it] } }?.name
        val reason = describe(m, code, catchup)
        // The player's own text, without links (they hold the login).
        val raw = m.replace(Regex("""\w+://\S+"""), "<link>").take(160)
        com.nuvio.app.features.livetv.model.LiveTvLoadReport.add("Player error on ${name ?: "a Live TV stream"}: ${reason.english}" + if (raw.isNotBlank()) " ($raw)" else "")
        return runCatching { if (reason.code != null) getString(reason.text, reason.code) else getString(reason.text) }
            .getOrDefault(reason.english)
    }

    private class ErrorReason(val english: String, val text: StringResource, val code: Int? = null)

    private fun describe(m: String, code: Int?, catchup: Boolean): ErrorReason {
        return when {
            catchup -> if (code != null) ErrorReason("This show couldn't be replayed. The provider may not have it in its catch-up (HTTP $code)", Res.string.livetv_error_catchup_code, code)
                else ErrorReason("This show couldn't be replayed. The provider may not have it in its catch-up", Res.string.livetv_error_catchup)
            code == 401 || code == 403 -> ErrorReason("Provider refused the stream (HTTP $code): account limit, expired, or blocked", Res.string.livetv_error_refused, code)
            code == 404 -> ErrorReason("Stream not found (HTTP 404). Try another stream format in the playlist's settings", Res.string.livetv_error_not_found)
            code == 429 || code == 456 || code == 458 || code == 509 -> ErrorReason("Too many connections on this account (HTTP $code)", Res.string.livetv_error_too_many, code)
            code != null && code >= 500 -> ErrorReason("Provider's server error (HTTP $code)", Res.string.livetv_error_server, code)
            m.contains("malformed", true) || m.contains("parsing", true) || m.contains("manifest", true) ->
                ErrorReason("The provider isn't sending this channel properly right now", Res.string.livetv_error_bad_stream)
            m.contains("timeout", true) || m.contains("Unable to connect", true) || m.contains("network", true) ->
                ErrorReason("Can't reach the provider right now", Res.string.livetv_error_unreachable)
            m.contains("decoder", true) || m.contains("codec", true) -> ErrorReason("This phone couldn't play this channel's picture or sound", Res.string.livetv_error_decoder)
            m.contains("drm", true) || m.contains("license", true) -> ErrorReason("This channel is protected and couldn't be unlocked", Res.string.livetv_error_drm)
            else -> ErrorReason("This channel isn't playing right now", Res.string.livetv_error_not_playing)
        }
    }

    // ---------------------------------------------------------------- link and name helpers

    /** Xtream live links: ".../live/user/pass/123.m3u8" or "host/user/pass/123.m3u8". */
    private val XTREAM_HLS = Regex("""/(?:live/)?[^/]+/[^/]+/\d+\.m3u8$""", RegexOption.IGNORE_CASE)
    /** An Xtream live link with no ending: ".../live/user/pass/123" or ".../user/pass/123". */
    private val XTREAM_BARE = Regex("""/(?:live/)?[^/]+/[^/]+/\d+$""")

    /**
     * The TS version of an Xtream live link: ".../123.m3u8" -> ".../123.ts", and a link with no
     * ending at all (which the server answers as HLS) -> ".../123.ts". Null if it isn't one.
     */
    fun tsLinkFor(url: String): String? {
        val base = url.substringBefore('?')
        if (base.endsWith(".m3u8", ignoreCase = true) && XTREAM_HLS.containsMatchIn(base))
            return base.dropLast(5) + ".ts" + url.substring(base.length)
        if (XTREAM_BARE.containsMatchIn(base)) return base + ".ts" + url.substring(base.length)
        return null
    }

    private val BACKUP_PREFIX = Regex("""^\s*[a-z]{2,4}\s*[:|]\s*""")
    private val BACKUP_PREFIX_DASH = Regex("""^\s*[a-z]{2,3}\s+-\s+""")
    private val BACKUP_BRACKETS = Regex("""[\[(][^\])]*[\])]""")
    // Sound layout written in the name ("5.1", "7.1", "2.0"): left in, it would read as part of the name.
    private val BACKUP_AUDIO = Regex("""\b[257]\.[01]\b""")
    private val BACKUP_NOISE = Regex("""\b(hd|fhd|uhd|sd|4k|8k|hevc|h\.?26[45]|1080[pi]?|720p?|2160p?|\d{2}\s?fps|backup|bkp|bk|alt|alternate|raw|ᴴᴰ)\b""")

    /**
     * A channel's name without what differs between copies of the same channel: a country or
     * provider prefix ("US: ", "UK | "), bracketed notes and quality or backup words. "US: ESPN
     * HD" and "ESPN (Backup)" both give "espn".
     */
    internal fun backupName(name: String): String {
        var s = name.lowercase()
        s = s.replace(BACKUP_PREFIX, "")
        s = s.replace(BACKUP_PREFIX_DASH, "")
        s = s.replace(BACKUP_BRACKETS, " ")
        s = s.replace(BACKUP_AUDIO, " ")
        s = s.replace(BACKUP_NOISE, " ")
        return s.filter { it.isLetterOrDigit() || it == ' ' }.split(' ').filter { it.isNotBlank() }.joinToString(" ")
    }

    private val STATION_NOTE = Regex("""[\[(]([^\])]*)[\])]""")

    /** The call sign or region in brackets ("FOX (WNYW)" -> "wnyw"), not quality notes like "(HD)". */
    internal fun stationNote(name: String): String? =
        STATION_NOTE.findAll(name.lowercase()).map { it.groupValues[1].trim() }
            .firstOrNull { it.isNotBlank() && backupName(it).isNotBlank() }
}

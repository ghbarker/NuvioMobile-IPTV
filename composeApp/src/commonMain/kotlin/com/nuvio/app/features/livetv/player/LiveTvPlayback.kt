package com.nuvio.app.features.livetv.player

import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.data.LiveTvRepository
import com.nuvio.app.features.livetv.data.XtreamClient
import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.player.PlayerLaunch
import com.nuvio.app.features.player.PlayerLaunchStore
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/** One stream the player is told to open: the link, its headers and what kind of stream it is. */
internal data class LivePlayRequest(
    val url: String,
    val headers: Map<String, String>,
    /** "hls" for .m3u8 links, null for anything else (the player sniffs it). */
    val streamType: String?,
)

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

    /** Channels by their "backup name", so a failed channel's stand-ins are found quickly. */
    private var backupIndex: Map<String, List<LiveChannel>> = emptyMap()
    private var backupIndexFor: List<LiveChannel> = emptyList()

    /** The request for a channel, with its HLS→TS memory applied. */
    fun requestFor(channel: LiveChannel): LivePlayRequest {
        val url = synchronized(lock) { if (channel.key in tsInstead) tsLinkFor(channel.url) ?: channel.url else channel.url }
        return request(channel, url)
    }

    private fun request(channel: LiveChannel, url: String): LivePlayRequest {
        val headers = LinkedHashMap<String, String>()
        channel.headers.forEach { (k, v) -> if (k.isNotBlank() && v.isNotBlank()) headers[k] = v }
        if (headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) headers["User-Agent"] = XtreamClient.DEFAULT_UA
        return LivePlayRequest(url = url, headers = headers, streamType = streamTypeOf(url, channel))
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
    fun launchFor(channel: LiveChannel, profileId: Int, nowTitle: String? = null): PlayerLaunch {
        val req = requestFor(channel)
        synchronized(lock) {
            // A channel you picked yourself: the backup list starts afresh.
            backupTried.clear()
            playing[req.url] = channel
        }
        return PlayerLaunch(
            profileId = profileId,
            title = channel.name,
            sourceUrl = req.url,
            sourceHeaders = req.headers,
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
    fun prepare(channel: LiveChannel, profileId: Int, nowTitle: String? = null): Long {
        LiveTvPreferences.recordWatched(channel.key)
        return PlayerLaunchStore.put(launchFor(channel, profileId, nowTitle))
    }

    /** Whether this provider id is Live TV's (the player asks before trying a fallback). */
    fun owns(providerAddonId: String?): Boolean = providerAddonId == PROVIDER_ID

    /**
     * What to play after [failedUrl] failed: the TS link for an HLS one, then a backup channel,
     * or null when nothing is left (the player shows the error).
     */
    fun fallbackFor(failedUrl: String): LiveFallback? {
        val channel = synchronized(lock) { playing[failedUrl] } ?: return null
        // 1. Xtream HLS that won't play: the same channel as TS, once.
        val ts = synchronized(lock) {
            if (channel.key in tsInstead) null
            else tsLinkFor(failedUrl)?.takeIf { it != failedUrl }?.also { tsInstead += channel.key }
        }
        if (ts != null) {
            val req = request(channel, ts)
            synchronized(lock) { playing[req.url] = channel }
            return LiveFallback(channel, req)
        }
        // 2. Another channel by the same name.
        val next = backupFor(channel) ?: return null
        val req = requestFor(next)
        synchronized(lock) { playing[req.url] = next }
        LiveTvPreferences.recordWatched(next.key, addToRecent = false)
        return LiveFallback(next, req, switchedFrom = channel)
    }

    private fun backupFor(failed: LiveChannel): LiveChannel? {
        if (!LiveTvPreferences.currentSettings().autoBackupChannel) return null
        val index = indexNow()
        synchronized(lock) { backupTried += failed.key }
        val name = backupName(failed.name)
        if (name.length < 2) return null
        val tried = synchronized(lock) { backupTried.toSet() }
        val hidden = LiveTvPreferences.currentUserState()
        return index[name].orEmpty().asSequence()
            .filter { it.key !in tried && it.key != failed.key }
            .filter { it.key !in hidden.hiddenChannels && it.groupId !in hidden.hiddenGroups }
            // Local stations: "FOX (WNYW)" isn't a backup for "FOX (KTTV)".
            .filter { c -> val a = stationNote(failed.name); val b = stationNote(c.name); a == null || b == null || a == b }
            // Other playlists first.
            .sortedBy { if (it.sourceId != failed.sourceId) 0 else 1 }
            .firstOrNull()
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

    /** A plain-English reason for the player's error text, the TV app's wording where it fits. */
    fun describeError(message: String?): String {
        val m = message.orEmpty()
        val code = Regex("""\b(4\d\d|5\d\d|884)\b""").find(m)?.value?.toIntOrNull()
        return when {
            code == 401 || code == 403 -> "Provider refused the stream (HTTP $code): account limit, expired, or blocked"
            code == 404 -> "Stream not found (HTTP 404). Try another stream format in the playlist's settings"
            code == 429 || code == 456 || code == 458 || code == 509 -> "Too many connections on this account (HTTP $code)"
            code != null && code >= 500 -> "Provider's server error (HTTP $code)"
            m.contains("malformed", true) || m.contains("parsing", true) || m.contains("manifest", true) ->
                "The provider isn't sending this channel properly right now"
            m.contains("timeout", true) || m.contains("Unable to connect", true) || m.contains("network", true) ->
                "Can't reach the provider right now"
            m.contains("decoder", true) || m.contains("codec", true) -> "This phone couldn't play this channel's picture or sound"
            m.contains("drm", true) || m.contains("license", true) -> "This channel is protected and couldn't be unlocked"
            else -> "This channel isn't playing right now"
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
        s = s.replace(BACKUP_NOISE, " ")
        return s.filter { it.isLetterOrDigit() || it == ' ' }.split(' ').filter { it.isNotBlank() }.joinToString(" ")
    }

    private val STATION_NOTE = Regex("""[\[(]([^\])]*)[\])]""")

    /** The call sign or region in brackets ("FOX (WNYW)" -> "wnyw"), not quality notes like "(HD)". */
    internal fun stationNote(name: String): String? =
        STATION_NOTE.findAll(name.lowercase()).map { it.groupValues[1].trim() }
            .firstOrNull { it.isNotBlank() && backupName(it).isNotBlank() }
}

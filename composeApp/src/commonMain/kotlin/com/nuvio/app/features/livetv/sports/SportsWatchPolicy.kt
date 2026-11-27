package com.nuvio.app.features.livetv.sports

import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.livetv.model.LiveTvSettings
import com.nuvio.app.features.livetv.model.LiveUserState
import com.nuvio.app.features.livetv.parental.ParentalControls


/** Events come from SportsRepository.events, including its existing score delay. */
internal data class SportsWatchGame(val event: SportsEvent, val channels: List<ChannelMatch>)

/** The Sports page's row/section order, with only games that can be watched now or shortly. */
internal data class SportsWatchRow(val key: String, val title: String, val games: List<SportsWatchGame>)

/** A filter chip over Multiview's Sports list: My Sports, All Events, Live Now, or one sport. */
internal data class SportsWatchFilter(val key: String, val icon: String, val label: String)

/** Multiview's Sports filter chips and how they map to the Sports page's selection. */
internal object SportsWatchFilters {
    const val MINE = "mine"
    const val ALL = "all"
    const val LIVE = "live"
    private const val SPORT = "sport:"

    /** My Sports (when something is followed), All Events, Live Now, then each sport with a game on now or soon. */
    fun filters(games: List<SportsWatchGame>, follows: SportsFollows, settings: SportsSettings): List<SportsWatchFilter> {
        val order = SportsCatalog.sportsOrder(settings)
        val sports = games.mapNotNull { SportsCatalog.league(it.event.leagueId)?.sport }.distinct()
            .sortedBy { order.indexOf(it).let { i -> if (i < 0) 99 else i } }
        return buildList {
            if (!follows.isEmpty) add(SportsWatchFilter(MINE, "★", "My Sports"))
            add(SportsWatchFilter(ALL, "▦", "All Events"))
            add(SportsWatchFilter(LIVE, "●", "Live Now"))
            sports.forEach { add(SportsWatchFilter(SPORT + it.name, it.icon, it.label)) }
        }
    }

    /** Which chip the current selection is (null: one the chips don't offer, like another day). */
    fun key(mine: Boolean, live: Boolean, day: Int, sport: String?): String? = when {
        mine -> MINE
        live -> LIVE
        day != 0 -> null
        sport != null -> SPORT + sport
        else -> ALL
    }

    /** The sport a chip stands for (null for the other chips). */
    fun sport(key: String): String? = key.takeIf { it.startsWith(SPORT) }?.removePrefix(SPORT)
}

internal data class SportsWatchRules(
    val user: LiveUserState,
    val live: LiveTvSettings,
    val sports: SportsSettings,
    val enabledAddonPrefixes: Set<String>,
    val unlockedGroups: Set<String>,
) {
    private val explicitChannels = SportsPickedChannels.keys(sports)
    // The same picks by playlist + tvg-id, for channels the provider renamed since.
    private val explicitStable = explicitChannels.mapNotNullTo(HashSet()) { SportsPickedChannels.stableId(it) }

    private fun picked(channel: LiveChannel): Boolean =
        channel.key in explicitChannels ||
            (explicitStable.isNotEmpty() && SportsPickedChannels.stableId(channel.key)?.let { it in explicitStable } == true)

    fun permits(channel: LiveChannel): Boolean {
        if (!sports.showInSidebar) return false
        if (user.hidesPlaylistOf(channel)) return false
        if (!picked(channel) &&
            (channel.key in user.hiddenChannels || channel.groupId in user.hiddenGroups)) return false
        if (ParentalControls.enabled(live)) {
            if (channel.groupId !in unlockedGroups &&
                (channel.groupId in user.lockedGroups ||
                    (live.lockAdultContent && ParentalControls.isAdult(channel.group)))) return false
            // Match Sports' existing adult-channel rule, including manually chosen channels.
            if (live.lockAdultContent && ParentalControls.isAdult(channel.name)) return false
        }
        return true
    }

    fun permitsAddon(channel: LiveChannel): Boolean =
        channel.sourceId == SportsAddonSource.SOURCE_ID &&
            enabledAddonPrefixes.any { channel.key.startsWith(it) } && permits(channel)

    fun permitsOvertime(event: SportsEvent, channel: LiveChannel, matched: Boolean): Boolean {
        if (event.state != GameState.POST || event.leagueId in sports.hiddenLeagues) return false
        if (matched && "${event.id}|${channel.key}" in sports.blocked) return false
        return if (channel.sourceId == SportsAddonSource.SOURCE_ID) matched && permitsAddon(channel) else permits(channel)
    }
}

/** Uses already matched Sports sources; it never searches the guide or opens a player. */
internal object SportsWatchPolicy {
    private const val MINUTE = 60_000L
    internal const val STARTING_SOON_MS = 30 * MINUTE
    internal const val STARTED_GRACE_MS = 15 * MINUTE

    fun liveEvent(event: SportsEvent, now: Long): Boolean = when (event.state) {
        GameState.LIVE -> true
        GameState.PRE -> event.hasTime && event.startMs >= now - STARTED_GRACE_MS &&
            event.startMs <= now + STARTING_SOON_MS
        GameState.POST, GameState.OFF -> false
    }

    fun sameProfile(opening: Int, current: Int): Boolean = opening == current

    fun allowedChannels(
        playlistChannels: List<LiveChannel>,
        matches: Map<String, List<ChannelMatch>>,
        rules: SportsWatchRules,
    ): Map<String, LiveChannel> = LinkedHashMap<String, LiveChannel>().apply {
        // The current source record wins over an older match with the same stable key.
        playlistChannels.forEach { if (rules.permits(it)) put(it.key, it) }
        matches.values.forEach { list ->
            list.forEach { match ->
                val channel = match.channel
                if (rules.permitsAddon(channel)) putIfAbsent(channel.key, channel)
            }
        }
    }

    fun games(
        events: List<SportsEvent>,
        matches: Map<String, List<ChannelMatch>>,
        allowed: Map<String, LiveChannel>,
        settings: SportsSettings,
        now: Long,
    ): List<SportsWatchGame> {
        if (!settings.showInSidebar) return emptyList()
        return events.asSequence()
            .filter { liveEvent(it, now) && it.leagueId !in settings.hiddenLeagues }
            .distinctBy { it.id }
            .sortedWith(compareBy<SportsEvent> { if (it.state == GameState.LIVE) 0 else 1 }
                .thenBy { it.startMs }.thenBy { it.id })
            .map { event ->
                SportsWatchGame(event, currentMatches(matches[event.id].orEmpty(), allowed))
            }.toList()
    }

    fun currentMatches(matches: List<ChannelMatch>, allowed: Map<String, LiveChannel>): List<ChannelMatch> =
        matches.mapNotNull { match ->
            val channel = allowed[match.channel.key] ?: return@mapNotNull null
            if (channel === match.channel) match else match.copy(channel = channel)
        }.distinctBy { it.channel.key }

    fun projectRows(
        ordered: List<Triple<String, String, List<SportsEvent>>>,
        current: Map<String, SportsWatchGame>,
    ): List<SportsWatchRow> = ordered.mapNotNull { (key, title, events) ->
        val games = events.mapNotNull { current[it.id] }.distinctBy { it.event.id }
        games.takeIf { it.isNotEmpty() }?.let { SportsWatchRow(key, title, it) }
    }

    /**
     * An explicit broadcast is a choice, not a preference: losing it must never tune a different
     * source. Without a key, retain Sports' existing best-allowed order. Resolve source records
     * only after checking this event's current membership, preserving fresh URLs/headers/DRM.
     */
    fun resolveMatch(
        matches: List<ChannelMatch>,
        channelKey: String? = null,
        freshSource: (LiveChannel) -> LiveChannel?,
    ): ChannelMatch? {
        for (match in matches) {
            if (channelKey != null && match.channel.key != channelKey) continue
            val current = freshSource(match.channel) ?: continue
            if (current.key != match.channel.key) continue
            return if (current === match.channel) match else match.copy(channel = current)
        }
        return null
    }
}

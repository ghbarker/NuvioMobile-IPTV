package com.nuvio.app.features.livetv.home

import com.nuvio.app.features.livetv.ondemand.OnDemandPlayback
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.features.livetv.ondemand.vodHidden
import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.data.LiveTvRepository
import com.nuvio.app.features.livetv.model.ChannelGroup
import com.nuvio.app.features.livetv.model.EpgProgram
import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.livetv.model.LiveTvClock
import com.nuvio.app.features.livetv.ondemand.OnDemandGroups
import com.nuvio.app.features.livetv.ondemand.OnDemandRepository
import com.nuvio.app.features.livetv.ondemand.VodItem
import com.nuvio.app.features.livetv.ondemand.VodKind
import com.nuvio.app.features.livetv.ondemand.VodSort
import com.nuvio.app.features.livetv.parental.ParentalControls
import com.nuvio.app.features.livetv.sports.GameState
import com.nuvio.app.features.livetv.sports.SportsCatalog
import com.nuvio.app.features.livetv.sports.SportsEvent
import com.nuvio.app.features.livetv.sports.SportsRepository
import com.nuvio.app.features.livetv.sports.SportsSettings
import com.nuvio.app.features.livetv.sports.SportsWatchGame
import com.nuvio.app.features.livetv.sports.SportsWatchPolicy
import com.nuvio.app.features.livetv.ui.LiveTvViewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A channel card: the channel, what's on now and what's on next (guide offset applied). */
internal data class HomeChannelEntry(val channel: LiveChannel, val now: EpgProgram?, val next: EpgProgram?)

/**
 * What each Home row shows: its channels with what's on, its sport's games when nothing with a
 * channel is on, and its On Demand titles. Works out which rows have nothing to show, so Home
 * leaves them out and Settings › Homescreen says so.
 */
internal object HomeLiveRowsData {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val clock = flow { while (true) { emit(LiveTvClock.nowMs()); delay(30_000) } }
    val now: StateFlow<Long> = clock.stateIn(scope, SharingStarted.Eagerly, LiveTvClock.nowMs())

    private val channelRows = HomeLiveRows.shownRows.map { l -> l.filter { it.kind == HomeLiveRow.KIND_CHANNELS } }.distinctUntilChanged()
    private val sportsRows = HomeLiveRows.shownRows.map { l -> l.filter { it.kind == HomeLiveRow.KIND_SPORTS } }.distinctUntilChanged()
    private val vodRows = HomeLiveRows.shownRows.map { l -> l.filter { it.kind == HomeLiveRow.KIND_VOD } }.distinctUntilChanged()

    /** Each channels row's channels before what's on is filled in, by row id. */
    private val selections: StateFlow<Map<String, List<LiveChannel>>> = combine(
        channelRows, LiveTvViewState.list, LiveTvPreferences.userState,
    ) { rows, list, user ->
        if (rows.isEmpty()) return@combine emptyMap()
        val byKey = list.visible.associateBy { it.key }
        rows.associate { row ->
            val section = list.sections.firstOrNull { it.group.id == row.source }?.channels
            row.id to (section ?: when (row.source) {
                // The Favorites / Recently watched groups can be hidden from the guide; the row still has them.
                ChannelGroup.FAVORITES -> user.favorites.mapNotNull { byKey[it] }
                ChannelGroup.RECENT -> user.recent.mapNotNull { byKey[it] }.filterNot { ParentalControls.isAdultChannel(it) }
                else -> emptyList()
            })
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** Each channels row's cards, by row id. */
    val channelEntries: StateFlow<Map<String, List<HomeChannelEntry>>> = combine(
        selections, channelRows, LiveTvRepository.programs, now,
        LiveTvPreferences.settings.map { it.epgOffsetMinutes }.distinctUntilChanged(),
    ) { picked, rows, progs, nowMs, offsetMinutes ->
        if (picked.isEmpty()) return@combine emptyMap()
        val shift = offsetMinutes * 60_000L
        fun moved(p: EpgProgram?) = if (p == null || shift == 0L) p else p.copy(startMs = p.startMs + shift, stopMs = p.stopMs + shift)
        val at = nowMs - shift
        rows.associate { row ->
            var entries = picked[row.id].orEmpty().map { ch ->
                val l = progs[ch.key].orEmpty()
                val cur = l.firstOrNull { at >= it.startMs && at < it.stopMs }
                val nxt = l.firstOrNull { it.startMs >= (cur?.stopMs ?: at) }
                HomeChannelEntry(ch, moved(cur), moved(nxt))
            }
            if (row.hidePlaceholders) {
                entries = entries.filter { e -> e.now != null && !LiveTvRepository.isPlaceholderTitle(e.now.title) }
            }
            entries = when (row.sort) {
                "number" -> entries.sortedBy { it.channel.number }
                "ending" -> entries.sortedBy { it.now?.stopMs ?: Long.MAX_VALUE }
                else -> entries
            }
            row.id to entries
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /**
     * Each Sports row's games for when none with a channel is on now or about to start: its games
     * on now (channel or not) and those coming up in the next week, soonest first. Empty = nothing
     * at all to show (the row is left off Home).
     */
    val sportsFallback: StateFlow<Map<String, List<SportsEvent>>> = combine(
        sportsRows, SportsRepository.events, SportsRepository.settings, now,
    ) { rows, events, settings, nowMs ->
        if (rows.isEmpty()) return@combine emptyMap()
        rows.associate { row -> row.id to fallbackEvents(events, settings, row, nowMs) }
    }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private val _vodItems = MutableStateFlow<Map<String, List<VodItem>>>(emptyMap())

    /** Each On Demand row's titles, by row id. */
    val vodItems: StateFlow<Map<String, List<VodItem>>> = _vodItems

    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(
                vodRows, OnDemandRepository.version, LiveTvPreferences.userState,
                // Group labels are presentation only: changing cleanup must not reload titles.
                LiveTvPreferences.settings.map { it.copy(nameRemovals = "", cleanVodGroupNames = false) }.distinctUntilChanged(),
                ParentalControls.unlocked,
            ) { rows, _, user, settings, _ -> Triple(rows, user, settings) }
                // Continue Watching On Demand also follows what you watch.
                .let { flow ->
                    combine(flow, continueChanges) { t, _ -> t }
                }
                .collectLatest { (rows, user, settings) ->
                    if (rows.isEmpty()) { _vodItems.value = emptyMap(); return@collectLatest }
                    val cats = OnDemandRepository.categories(VodKind.MOVIE) + OnDemandRepository.categories(VodKind.SERIES)
                    val locked = cats.filter { c -> ParentalControls.isLocked("vod:${c.uid}", c.name, settings, user.lockedGroups) }
                        .mapTo(HashSet()) { it.uid }
                    val hidden = user.vodHidden(cats) + locked
                    _vodItems.value = rows.associate { row ->
                        row.id to runCatching {
                            when (row.source) {
                                HomeLiveRow.VOD_CONTINUE -> continueWatching(hidden)
                                HomeLiveRow.VOD_RECENT_MOVIES, HomeLiveRow.VOD_RECENT_SERIES -> {
                                    val kind = if (row.source == HomeLiveRow.VOD_RECENT_SERIES) VodKind.SERIES else VodKind.MOVIE
                                    OnDemandRepository.items(kind, null, hidden, VOD_TITLES, 0, VodSort.RECENTLY_ADDED, settings.vodMergeDuplicates)
                                }
                                else -> {
                                    val uid = row.source.removePrefix(HomeLiveRow.VOD_GROUP_PREFIX)
                                    val cat = cats.firstOrNull { it.uid == uid }
                                    if (cat == null || uid in hidden) emptyList()
                                    else OnDemandRepository.items(cat.kind, cat, hidden, VOD_TITLES, 0, OnDemandGroups.sortFor(uid, user, settings), settings.vodMergeDuplicates)
                                }
                            }
                        }.getOrDefault(emptyList())
                    }
                }
        }
        // Rows with nothing to show are left off Home (no gap where they'd be).
        scope.launch {
            combine(HomeLiveRows.shownRows, channelEntries, sportsFallback, vodItems) { rows, channels, sports, vod ->
                rows.filter { r ->
                    when (r.kind) {
                        HomeLiveRow.KIND_CHANNELS -> channels[r.id].isNullOrEmpty()
                        HomeLiveRow.KIND_SPORTS -> sports[r.id].isNullOrEmpty()
                        else -> vod[r.id].isNullOrEmpty()
                    }
                }.mapTo(HashSet()) { it.id } as Set<String>
            }.distinctUntilChanged().collect { HomeLiveRows.setEmptyRows(it) }
        }
    }

    /** The games a Sports row shows with a channel: its sport or league, live first, then starting soon (if on). */
    fun rowGames(all: List<SportsWatchGame>, row: HomeLiveRow): List<SportsWatchGame> = all
        .filter { it.channels.isNotEmpty() && matches(row.source, it.event.leagueId) }
        .filter { row.startingSoon || it.event.state == GameState.LIVE }

    private fun matches(source: String, leagueId: String): Boolean = when {
        source.startsWith("league:") -> leagueId == source.removePrefix("league:")
        source.startsWith("sport:") -> SportsCatalog.league(leagueId)?.sport?.name == source.removePrefix("sport:")
        else -> true
    }

    private fun fallbackEvents(events: List<SportsEvent>, settings: SportsSettings, row: HomeLiveRow, now: Long): List<SportsEvent> {
        if (!settings.showInSidebar) return emptyList()
        val until = now + UPCOMING_DAYS * 24 * 3_600_000L
        return events.asSequence()
            .filter { it.leagueId !in settings.hiddenLeagues && matches(row.source, it.leagueId) }
            .filter { ev ->
                SportsWatchPolicy.liveEvent(ev, now) ||
                    (ev.state == GameState.PRE && ev.hasTime && ev.startMs > now && ev.startMs <= until)
            }
            .distinctBy { it.id }
            .sortedWith(compareBy<SportsEvent> { if (it.state == GameState.LIVE) 0 else 1 }.thenBy { it.startMs }.thenBy { it.id })
            .toList()
    }

    /** How far ahead a Sports row looks for its next games. */
    private const val UPCOMING_DAYS = 7

    /** Changes when an On Demand title's saved place changes (only while a Continue row is on Home). */
    private val continueChanges = combine(vodRows, WatchProgressRepository.uiState) { rows, state ->
        if (rows.none { it.source == HomeLiveRow.VOD_CONTINUE }) 0
        else state.entries.filter { isOnDemand(it) }.map { it.videoId to it.lastUpdatedEpochMs }.hashCode()
    }.distinctUntilChanged()

    private fun isOnDemand(e: WatchProgressEntry): Boolean =
        e.providerAddonId == OnDemandPlayback.PROVIDER_ID || e.videoId.startsWith(OnDemandPlayback.PROVIDER_ID + ":")

    /**
     * The provider titles played from On Demand that you started and haven't finished, last watched
     * first: one card per movie or series (a series by the episode watched last). Hidden and locked
     * groups, and playlists this profile doesn't show, stay out.
     */
    private suspend fun continueWatching(hidden: Set<String>): List<VodItem> {
        val prefix = OnDemandPlayback.PROVIDER_ID + ":"
        val started = WatchProgressRepository.uiState.value.entries.asSequence()
            .filter { isOnDemand(it) && it.videoId.startsWith(prefix) && !it.isCompleted }
            .filter { e -> e.durationMs <= 0L || e.lastPositionMs < e.durationMs * 0.92 }
            .filter { e -> e.lastPositionMs > 0L || (e.progressPercent ?: 0f) > 0f }
            .sortedByDescending { it.lastUpdatedEpochMs }
            .toList()
        val out = ArrayList<VodItem>()
        val seen = HashSet<String>()
        for (e in started) {
            if (out.size >= VOD_TITLES) break
            // "<prefix><kind>:<playlist>:<id>" for a movie, plus ":<season>:<episode>" for an episode.
            val parts = e.videoId.removePrefix(prefix).split(':')
            if (parts.size < 3) continue
            val uid = if (parts.size >= 5 && parts[parts.size - 1].toIntOrNull() != null && parts[parts.size - 2].toIntOrNull() != null)
                parts.dropLast(2).joinToString(":") else parts.joinToString(":")
            if (!seen.add(uid)) continue
            val item = OnDemandRepository.itemByUid(uid) ?: continue
            if ("${item.kind.key}:${item.playlistId}:${item.categoryId}" in hidden) continue
            out += item
        }
        return out
    }

    /** Titles in an On Demand row (See all opens the whole category). */
    const val VOD_TITLES = 40
}

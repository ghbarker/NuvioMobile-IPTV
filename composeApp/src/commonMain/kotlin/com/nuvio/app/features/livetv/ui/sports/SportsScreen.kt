package com.nuvio.app.features.livetv.ui.sports

import com.nuvio.app.features.livetv.ui.liveTvSideInsets
import com.nuvio.app.core.ui.LocalScreenActive
import com.nuvio.app.core.ui.ScreenActivityEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.rememberCoroutineScope
import com.nuvio.app.features.livetv.sports.SportsCatalog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.features.livetv.data.LiveTvRepository
import com.nuvio.app.features.livetv.model.EpgProgram
import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.livetv.multiview.isMultiviewSupported
import com.nuvio.app.features.livetv.sports.SportsWatchSelection
import com.nuvio.app.features.livetv.model.LiveTvClock
import com.nuvio.app.features.livetv.sports.ChannelMatch
import com.nuvio.app.features.livetv.sports.GameState
import com.nuvio.app.features.livetv.sports.League
import com.nuvio.app.features.livetv.sports.Sport
import com.nuvio.app.features.livetv.sports.SportsDates
import com.nuvio.app.features.livetv.sports.SportsEvent
import com.nuvio.app.features.livetv.sports.SportsFollows
import com.nuvio.app.features.livetv.sports.SportsMatcher
import com.nuvio.app.features.livetv.sports.SportsReminders
import com.nuvio.app.features.livetv.sports.SportsRepository
import com.nuvio.app.features.livetv.sports.MyTeamsPage
import com.nuvio.app.features.livetv.sports.MySportsInputs
import com.nuvio.app.features.livetv.sports.PreparedSportsPage
import com.nuvio.app.features.livetv.sports.SportsDayInputs
import com.nuvio.app.features.livetv.sports.SportsDayPage
import com.nuvio.app.features.livetv.sports.SportsDayQuery
import com.nuvio.app.features.livetv.sports.SportsDaySections
import com.nuvio.app.features.livetv.sports.buildMySportsPage
import com.nuvio.app.features.livetv.sports.buildSportsDayPage
import com.nuvio.app.features.livetv.sports.prepareSportsPages
import com.nuvio.app.features.livetv.sports.SportsSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.livetv_sports
import nuvio.composeapp.generated.resources.livetv_sports_all_events
import nuvio.composeapp.generated.resources.livetv_sports_finished
import nuvio.composeapp.generated.resources.livetv_sports_following
import nuvio.composeapp.generated.resources.livetv_sports_live_now
import nuvio.composeapp.generated.resources.livetv_sports_my_sports
import nuvio.composeapp.generated.resources.livetv_sports_nothing_followed
import nuvio.composeapp.generated.resources.livetv_sports_nothing_today
import nuvio.composeapp.generated.resources.livetv_sports_pick_teams
import nuvio.composeapp.generated.resources.livetv_sports_search
import nuvio.composeapp.generated.resources.livetv_sports_this_week
import nuvio.composeapp.generated.resources.livetv_sports_upcoming
import org.jetbrains.compose.resources.stringResource

/** What the Sports hub does outside itself: plays channels, opens pages. */
data class SportsActions(
    val onPlay: (LiveChannel, nowTitle: String?) -> Unit,
    val onPlayCatchup: (LiveChannel, EpgProgram) -> Unit,
    val onOpenGame: (SportsEvent) -> Unit,
    val onOpenLeague: (League) -> Unit,
    val onOpenMultiview: () -> Unit = {},
)

private enum class SportsTab { MINE, ALL, FOLLOWING, NETWORKS }

/**
 * The Sports hub: My Sports (your teams' big card, then a row per team, league and name you
 * follow), All Events (every game by day: the big card, live on your channels, then each league
 * folded), Following (manage what you follow) and Networks. The search bar at the top of My
 * Sports and All Events finds teams, leagues and names. Tap a game for its page.
 */
@Composable
fun SportsScreen(
    onBack: () -> Unit,
    actions: SportsActions,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val repo = SportsRepository
    val screenActive = LocalScreenActive.current
    ScreenActivityEffect { active ->
        if (active) { SportsReminders.start(); LiveTvRepository.ensureLoaded() }
    }
    SportsViewingEffect(repo::setViewing)

    val settings by repo.settings.collectAsStateWithLifecycle()
    val follows by repo.follows.collectAsStateWithLifecycle()
    val events by repo.events.collectAsStateWithLifecycle()
    val known by repo.listingKnown.collectAsStateWithLifecycle()
    val matches by repo.matches.collectAsStateWithLifecycle()
    val loading by repo.loading.collectAsStateWithLifecycle()
    val matching by repo.matching.collectAsStateWithLifecycle()
    val problem by repo.problem.collectAsStateWithLifecycle()
    val use24h = use24h()
    val now by produceState(LiveTvClock.nowMs(), screenActive) {
        if (screenActive) while (true) { value = LiveTvClock.nowMs(); delay(15_000) }
    }
    var tabIndex by rememberSaveable { mutableIntStateOf(if (follows.isEmpty) SportsTab.ALL.ordinal else SportsTab.MINE.ordinal) }
    val tab = SportsTab.entries[tabIndex.coerceIn(0, SportsTab.entries.lastIndex)]
    androidx.compose.runtime.SideEffect {
        if (tab == SportsTab.MINE) SportsWatchSelection.select(mine = true)
    }
    var showSettings by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val onHighlights: (SportsEvent) -> Unit = { ev -> playGameHighlights(ev, actions, scope) }

    Column(modifier = modifier.fillMaxSize().background(tokens.colors.background).statusBarsPadding().liveTvSideInsets()) {
        NuvioScreenHeader(
            title = stringResource(Res.string.livetv_sports),
            includeStatusBarPadding = false,
            onBack = onBack,
            modifier = Modifier.padding(horizontal = NuvioTokens.Space.s8),
            actions = {
                if (loading || matching) NuvioLoadingIndicator(size = NuvioTokens.Space.s16)
                if (isMultiviewSupported) {
                    IconButton(onClick = actions.onOpenMultiview) {
                        Icon(Icons.Filled.GridView, contentDescription = "Open Multiview", tint = tokens.colors.textPrimary)
                    }
                }
                IconButton(onClick = { repo.refreshNow() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = tokens.colors.textPrimary)
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Filled.Tune, contentDescription = "Sports settings", tint = tokens.colors.textPrimary)
                }
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s6),
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
        ) {
            Chip(stringResource(Res.string.livetv_sports_my_sports), selected = tab == SportsTab.MINE, onClick = { tabIndex = SportsTab.MINE.ordinal })
            Chip(stringResource(Res.string.livetv_sports_all_events), selected = tab == SportsTab.ALL, onClick = { tabIndex = SportsTab.ALL.ordinal })
            Chip(stringResource(Res.string.livetv_sports_following), selected = tab == SportsTab.FOLLOWING, onClick = { tabIndex = SportsTab.FOLLOWING.ordinal })
            Chip("Networks", selected = tab == SportsTab.NETWORKS, onClick = { tabIndex = SportsTab.NETWORKS.ordinal })
        }
        problem?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.colors.danger, modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        val withSearch = tab == SportsTab.MINE || tab == SportsTab.ALL
        if (withSearch) {
            SportsSearchBar(searchQuery, { searchQuery = it }, Modifier.padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s4))
        }
        if (withSearch && searchQuery.isNotBlank()) {
            SportsSearchTab(known, follows, actions, searchQuery)
        } else when (tab) {
            SportsTab.MINE -> MySportsTab(known, follows, settings, matches, now, use24h, actions, onHighlights, onPick = { tabIndex = SportsTab.FOLLOWING.ordinal })
            SportsTab.ALL -> AllEventsTab(events, known, follows, settings, matches, now, use24h, loading, actions, onHighlights)
            SportsTab.FOLLOWING -> SportsFollowingTab(follows, settings, actions)
            SportsTab.NETWORKS -> SportsNetworksTab(actions)
        }
    }

    if (screenActive && showSettings) SportsSettingsSheet(settings, onDismiss = { showSettings = false })
    if (screenActive) GameAlertsOffer()
}

/** Cached root tabs remain composed; acquire a viewer only while this pane is active. */
@Composable
internal fun SportsViewingEffect(onViewingChanged: (Boolean) -> Unit) {
    val active = LocalScreenActive.current
    DisposableEffect(active, onViewingChanged) {
        if (active) onViewingChanged(true)
        onDispose { if (active) onViewingChanged(false) }
    }
}

/** How many of your channels show [ev] on a card (null: channels not looked through yet). */
private fun channelCount(ev: SportsEvent, matches: Map<String, List<ChannelMatch>>): Int? =
    if (matches.isEmpty() || ev.state == GameState.POST) null else matches[ev.id]?.size ?: 0

// ------------------------------------------------------------------ My Sports

private val emptyMySportsPage = MyTeamsPage(emptyList(), emptyList())
private val emptySportsDayPage = SportsDayPage(emptyList(), SportsDaySections())

@Composable
private fun MySportsTab(
    known: List<SportsEvent>,
    follows: SportsFollows,
    settings: SportsSettings,
    matches: Map<String, List<ChannelMatch>>,
    now: Long,
    use24h: Boolean,
    actions: SportsActions,
    onHighlights: (SportsEvent) -> Unit,
    onPick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val repo = SportsRepository
    val tables by repo.standings.collectAsStateWithLifecycle()
    LaunchedEffect(follows.teams) { runCatching { repo.loadTeamTables() } }
    val inputs = remember(known, follows, now / 60_000L) { MySportsInputs(known, follows, now) }
    val currentInputs by rememberUpdatedState(inputs)
    var prepared by remember { mutableStateOf<PreparedSportsPage<SportsFollows, MyTeamsPage>?>(null) }
    LaunchedEffect(Unit) {
        snapshotFlow { currentInputs }.prepareSportsPages({ it.follows }) { input ->
            buildMySportsPage(input, ::isMine)
        }.collect { prepared = it }
    }
    val ready = prepared?.forQuery(follows)
    val page = ready ?: emptyMySportsPage
    if (follows.isEmpty) {
        EmptyNote(stringResource(Res.string.livetv_sports_nothing_followed), stringResource(Res.string.livetv_sports_pick_teams), onPick)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = NuvioTokens.Space.s8, bottom = nuvioSafeBottomPadding(NuvioTokens.Space.s16)),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s18),
    ) {
        if (page.heroes.isNotEmpty()) {
            item(key = "hero") {
                FeaturedGames(
                    page.heroes, matches, follows, now, use24h, settings.hideScores,
                    onOpen = actions.onOpenGame, label = "Your teams",
                    modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16),
                )
            }
        }
        if (page.rows.isEmpty()) {
            item { Text(if (ready == null) "Getting today's games…" else stringResource(Res.string.livetv_sports_nothing_today), style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted, modifier = Modifier.padding(NuvioTokens.Space.s16)) }
        }
        items(page.rows, key = { it.key }) { row ->
            Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10)) {
                MySportsRowHeader(row, tables, now, actions)
                val state = rememberLazyListState(initialFirstVisibleItemIndex = row.startIndex.coerceIn(0, (row.events.size - 1).coerceAtLeast(0)))
                LazyRow(
                    state = state,
                    contentPadding = PaddingValues(horizontal = NuvioTokens.Space.s16),
                    horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10),
                ) {
                    items(row.events, key = { it.id }) { ev ->
                        RowCard(
                            ev, follows, now, use24h, settings.hideScores,
                            channels = channelCount(ev, matches),
                            onHighlights = { onHighlights(ev) },
                        ) { actions.onOpenGame(ev) }
                    }
                }
            }
        }
    }
}

/**
 * A My Sports row's heading: a team's in its colours (logo, name, place in the table and recent
 * form, Table), a league's on a plain card (logo, name, games this week, Table), or a plain title
 * (Live & Upcoming, a name you follow).
 */
@Composable
private fun MySportsRowHeader(
    row: com.nuvio.app.features.livetv.sports.SportsRow,
    tables: Map<String, com.nuvio.app.features.livetv.sports.Standings>,
    now: Long,
    actions: SportsActions,
) {
    val tokens = MaterialTheme.nuvio
    val repo = SportsRepository
    val team = row.team
    val league = row.league ?: team?.let { SportsCatalog.league(it.leagueId) }
    val tableLeague = league?.takeIf { repo.mayHaveTable(it) && (team != null || row.league != null) }
    when {
        team != null -> {
            val info = team.toTeamInfo()
            val found = tables[team.leagueId]?.find(team.teamId)?.second
            val place = tables[team.leagueId]?.placeText(team.teamId)
            val form = found?.form?.takeLast(5)?.uppercase()?.toList()?.joinToString(" ")?.takeIf { it.isNotBlank() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NuvioTokens.Space.s16)
                    .clip(tokens.shapes.card)
                    .background(tokens.colors.surface)
                    .background(teamBandBrush(info))
                    .heightIn(min = 56.dp)
                    .padding(horizontal = NuvioTokens.Space.s10, vertical = NuvioTokens.Space.s8),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10),
            ) {
                TeamLogo(info, 32.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.title, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(place ?: league?.name, form).joinToString(" · ")
                    if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                tableLeague?.let { l -> TablePill(onDark = true) { actions.onOpenLeague(l) } }
            }
        }
        row.league != null -> {
            val l = row.league
            val week = row.events.count { it.startMs in now..(now + 7 * 24 * 3_600_000L) || it.state == GameState.LIVE }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NuvioTokens.Space.s16)
                    .clip(tokens.shapes.card)
                    .background(tokens.colors.surface)
                    .heightIn(min = 56.dp)
                    .padding(horizontal = NuvioTokens.Space.s10, vertical = NuvioTokens.Space.s8),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10),
            ) {
                LeagueMark(l, 32.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.title, style = MaterialTheme.typography.titleSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "League you follow" + if (week > 0) " · $week game${if (week == 1) "" else "s"} this week" else "",
                        style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, maxLines = 1,
                    )
                }
                tableLeague?.let { tl -> TablePill(onDark = false) { actions.onOpenLeague(tl) } }
            }
        }
        else -> Text(
            row.title,
            style = MaterialTheme.typography.titleSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16),
        )
    }
}

@Composable
private fun TablePill(onDark: Boolean, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(if (onDark) Color.Black.copy(alpha = 0.28f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = NuvioTokens.Space.s10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Table", style = MaterialTheme.typography.labelLarge, color = if (onDark) Color.White else tokens.colors.accent, fontWeight = FontWeight.SemiBold)
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = if (onDark) Color.White else tokens.colors.accent, modifier = Modifier.size(16.dp))
    }
}

private fun com.nuvio.app.features.livetv.sports.FollowedTeam.toTeamInfo() =
    com.nuvio.app.features.livetv.sports.TeamInfo(leagueId, teamId, name, name, abbr, null, color, null, logo)

// ------------------------------------------------------------------ All Events

/** A league's games on the day, for its folding group. */
private class LeagueGroup(val leagueId: String, val league: League?, val games: List<SportsEvent>)

@Composable
private fun AllEventsTab(
    events: List<SportsEvent>,
    known: List<SportsEvent>,
    follows: SportsFollows,
    settings: SportsSettings,
    matches: Map<String, List<ChannelMatch>>,
    now: Long,
    use24h: Boolean,
    loading: Boolean,
    actions: SportsActions,
    onHighlights: (SportsEvent) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val repo = SportsRepository
    var day by rememberSaveable { mutableIntStateOf(0) }
    var sportFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var leaguePick by rememberSaveable { mutableStateOf<String?>(null) }
    var choosingLeagues by remember { mutableStateOf(false) }
    LaunchedEffect(day) { if (day < -1) repo.wantPast() }
    val dayStart = repo.dayStart(day)
    val dayEnd = repo.dayStart(day + 1)
    val activeLeagues = remember(settings, follows) { repo.activeLeagues(settings, follows) }
    val active = remember(activeLeagues) { activeLeagues.map { it.id }.toSet() + SportsMatcher.OTHER_ID }
    // The league row: the picked sport's leagues (not the guide-only ones), when it has more than one.
    val sportLeagues = remember(activeLeagues, sportFilter) {
        if (sportFilter == null) emptyList() else activeLeagues.filter { it.sport.name == sportFilter && !it.guideOnly }
    }
    val pick = leaguePick?.takeIf { id -> sportLeagues.size > 1 && sportLeagues.any { it.id == id } }
    val hasChannels = LiveTvRepository.channels.collectAsStateWithLifecycle().value.isNotEmpty()
    val filterToChannels = settings.onlyOnMyChannels && hasChannels && matches.isNotEmpty()
    val query = SportsDayQuery(day, sportFilter, dayStart, dayEnd, active, filterToChannels, follows, pick)
    androidx.compose.runtime.SideEffect { SportsWatchSelection.select(mine = false, day = day, sport = sportFilter) }
    val inputs = remember(events, known, follows, settings, matches, active, filterToChannels, dayStart, dayEnd, query, now / 60_000L) {
        SportsDayInputs(query, events, settings, matches, now, known)
    }
    val currentInputs by rememberUpdatedState(inputs)
    var prepared by remember { mutableStateOf<PreparedSportsPage<SportsDayQuery, SportsDayPage>?>(null) }
    LaunchedEffect(Unit) {
        snapshotFlow { currentInputs }.prepareSportsPages({ it.query }) { buildSportsDayPage(it, ::isMine) }
            .collect { prepared = it }
    }
    val ready = prepared?.forQuery(query)
    val page = ready ?: emptySportsDayPage
    // Keep the existing filter choices available while the newly selected page is prepared.
    val sports = ready?.sports ?: prepared?.page?.sports.orEmpty()
    val sections = page.sections
    val all = remember(sections) { sections.live + sections.weekLong + sections.upcoming + sections.finished }
    // The leagues in the order their games come (live ones first), each a folding group.
    val groups = remember(all) {
        val byLeague = LinkedHashMap<String, MutableList<SportsEvent>>()
        all.forEach { byLeague.getOrPut(it.leagueId) { ArrayList() } += it }
        byLeague.map { (id, games) -> LeagueGroup(id, SportsCatalog.league(id), games) }
    }
    var openAll by rememberSaveable { mutableStateOf(false) }
    var toggled by remember { mutableStateOf(emptySet<String>()) }
    val defaultOpen = openAll || groups.size <= 2
    fun isOpen(id: String) = if (id in toggled) !defaultOpen else defaultOpen
    // The big card: live games (yours and the ones on your channels first), else the day's next ones.
    val heroes = remember(sections, follows, pick, day) {
        if (pick != null || day < 0) emptyList()
        else sections.live.filter { it.hasTime }.take(6).ifEmpty {
            sections.upcoming.filter { it.hasTime && it.state == GameState.PRE }
                .sortedByDescending { isMine(it, follows) }.take(5)
        }
    }
    val liveItems = remember(sections, matches, now / 60_000L, pick, day) {
        if (pick != null || day != 0) emptyList() else liveOnChannels(sections.live, matches, now)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (sports.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s4),
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
            ) {
                item { Chip("All", selected = sportFilter == null, onClick = { sportFilter = null; leaguePick = null }) }
                items(sports, key = { it.name }) { s ->
                    Chip(s.icon + " " + s.label, selected = sportFilter == s.name, onClick = {
                        if (sportFilter != s.name) leaguePick = null
                        sportFilter = s.name
                    })
                }
            }
        }
        LazyRow(
            state = rememberLazyListState(initialFirstVisibleItemIndex = SportsRepository.DAYS_BACK - 1),
            contentPadding = PaddingValues(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s4),
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
        ) {
            items((-SportsRepository.DAYS_BACK..SportsRepository.DAYS_AHEAD).toList(), key = { it }) { offset ->
                val label = when (offset) {
                    -1 -> "Yesterday"
                    0 -> "Today"
                    1 -> "Tomorrow"
                    else -> SportsDates.shortDayLabel(repo.dayStart(offset) + 12 * 3_600_000L)
                }
                Chip(label, selected = day == offset, onClick = { day = offset })
            }
        }
        if (sportLeagues.size > 1) {
            Spacer(Modifier.height(NuvioTokens.Space.s4))
            LeaguePickRow(sportLeagues, pick, onPick = { leaguePick = it })
            Spacer(Modifier.height(NuvioTokens.Space.s4))
        }
        val empty = all.isEmpty()
        val pickedLeague = pick?.let { id -> sportLeagues.firstOrNull { it.id == id } }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = NuvioTokens.Space.s16, end = NuvioTokens.Space.s16, top = NuvioTokens.Space.s8, bottom = nuvioSafeBottomPadding(NuvioTokens.Space.s16)),
            verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
        ) {
            if (pickedLeague != null) {
                // ---- one league picked: its games (or its next match day), with its table
                val dayName = if (day == 0) "today" else if (day == 1) "tomorrow" else if (day == -1) "yesterday" else "on " + SportsDates.dayLabel(dayStart + 12 * 3_600_000L)
                if (ready != null && page.nextDay != null) {
                    item(key = "next-day") {
                        NextMatchDayNote(
                            "No ${pickedLeague.name} games $dayName",
                            "Showing the next match day: " + SportsDates.dayLabel(repo.dayStart(page.nextDay) + 12 * 3_600_000L),
                        )
                    }
                } else if (ready != null && page.noneAhead) {
                    item(key = "none-ahead") { NextMatchDayNote("No ${pickedLeague.name} games $dayName or in the week ahead", null) }
                }
                item(key = "table") { LeagueTablePreview(pickedLeague, follows, settings.hideScores) { actions.onOpenLeague(pickedLeague) } }
                if (all.isNotEmpty()) {
                    item(key = "day-h") {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = NuvioTokens.Space.s4)) {
                            val shownDay = page.nextDay ?: day
                            Text(
                                when (shownDay) { 0 -> "Today"; 1 -> "Tomorrow"; -1 -> "Yesterday"; else -> SportsDates.dayLabel(repo.dayStart(shownDay) + 12 * 3_600_000L) },
                                style = MaterialTheme.typography.titleSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                            )
                            Text("${all.size} game${if (all.size == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium, color = tokens.colors.textMuted)
                        }
                    }
                }
                items(all, key = { "p/" + it.id }) { ev ->
                    GameCard(
                        ev, follows, now, use24h, settings.hideScores, channels = channelCount(ev, matches),
                        showLeague = false, onHighlights = { onHighlights(ev) },
                    ) { actions.onOpenGame(ev) }
                }
                if (ready == null || loading && all.isEmpty() && page.nextDay == null && !page.noneAhead) {
                    item { Text("Getting the games…", style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted, modifier = Modifier.padding(vertical = NuvioTokens.Space.s24)) }
                }
            } else {
                // ---- the big card and live on your channels
                if (heroes.isNotEmpty()) {
                    item(key = "hero") { FeaturedGames(heroes, matches, follows, now, use24h, settings.hideScores, onOpen = actions.onOpenGame) }
                }
                if (liveItems.isNotEmpty()) {
                    item(key = "live-row") { LiveOnChannelsRow(liveItems, now, use24h, settings.hideScores, actions) }
                }
                // ---- the leagues, folded
                item(key = "leagues-h") {
                    LeaguesHeading(
                        settings, follows, showToggle = groups.size > 2, allOpen = defaultOpen && toggled.isEmpty(),
                        onChoose = { choosingLeagues = true },
                        onToggleAll = { wantOpen -> openAll = wantOpen; toggled = emptySet() },
                    )
                }
                if (empty) {
                    item {
                        Text(
                            when {
                                ready == null || loading -> "Getting today's games…"
                                else -> stringResource(Res.string.livetv_sports_nothing_today)
                            },
                            style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted, modifier = Modifier.padding(vertical = NuvioTokens.Space.s24),
                        )
                    }
                }
                items(groups, key = { "g/" + it.leagueId }) { g ->
                    val isOpenNow = isOpen(g.leagueId)
                    Column(modifier = Modifier.fillMaxWidth().clip(tokens.shapes.card).background(tokens.colors.surface.copy(alpha = 0.6f))) {
                        LeagueGroupHeader(
                            league = g.league,
                            name = g.league?.name ?: g.games.first().let { SportsCatalog.league(it.leagueId)?.name } ?: "More sports",
                            live = g.games.count { it.state == GameState.LIVE && it.hasTime },
                            onChannels = if (matches.isEmpty()) null else g.games.count { it.state != GameState.POST && matches[it.id]?.isNotEmpty() == true },
                            total = g.games.size,
                            open = isOpenNow,
                            onToggle = { toggled = if (g.leagueId in toggled) toggled - g.leagueId else toggled + g.leagueId },
                            onOpenLeague = g.league?.takeIf { g.leagueId != SportsMatcher.OTHER_ID }?.let { l -> { actions.onOpenLeague(l) } },
                        )
                        if (isOpenNow) {
                            Column(
                                modifier = Modifier.padding(start = NuvioTokens.Space.s8, end = NuvioTokens.Space.s8, bottom = NuvioTokens.Space.s8),
                                verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
                            ) {
                                g.games.forEach { ev ->
                                    androidx.compose.runtime.key(ev.id) {
                                        GameCard(
                                            ev, follows, now, use24h, settings.hideScores, channels = channelCount(ev, matches),
                                            showLeague = false, onHighlights = { onHighlights(ev) },
                                        ) { actions.onOpenGame(ev) }
                                    }
                                }
                            }
                        }
                    }
                }
                if (groups.isNotEmpty() && matches.isNotEmpty()) {
                    item(key = "legend") {
                        Text("Blue number: games on your channels · grey: all games that day", style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, modifier = Modifier.padding(horizontal = NuvioTokens.Space.s4))
                    }
                }
            }
        }
    }
    if (LocalScreenActive.current && choosingLeagues) LeaguesSheet(onDismiss = { choosingLeagues = false })
}

/** "Leagues", how many are showing, Edit League List, and Open all / Close all. */
@Composable
private fun LeaguesHeading(
    settings: SportsSettings,
    follows: SportsFollows,
    showToggle: Boolean,
    allOpen: Boolean,
    onChoose: () -> Unit,
    onToggleAll: (Boolean) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val repo = SportsRepository
    val count = remember(settings, follows) { repo.activeLeagues(settings, follows).size }
    val hidden = remember(settings, follows) { repo.hiddenLeagueCount(settings, follows) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = NuvioTokens.Space.s4)) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Leagues", style = MaterialTheme.typography.titleSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold)
            Text(
                "Showing $count league${if (count == 1) "" else "s"}" + if (hidden > 0) " · $hidden hidden" else "",
                style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, maxLines = 1,
            )
        }
        LinkButton("Edit League List", onChoose)
        if (showToggle) LinkButton(if (allOpen) "Close all" else "Open all") { onToggleAll(!allOpen) }
    }
}

@Composable
private fun LinkButton(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.nuvio.colors.accent,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(NuvioTokens.Radius.sm)).clickable(onClick = onClick)
            .padding(horizontal = NuvioTokens.Space.s8, vertical = NuvioTokens.Space.s10),
    )
}

@Composable
internal fun EmptyNote(text: String, button: String? = null, onClick: () -> Unit = {}) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = Modifier.fillMaxWidth().padding(NuvioTokens.Space.s24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted)
        if (button != null) NuvioPrimaryButton(text = button, onClick = onClick)
    }
}

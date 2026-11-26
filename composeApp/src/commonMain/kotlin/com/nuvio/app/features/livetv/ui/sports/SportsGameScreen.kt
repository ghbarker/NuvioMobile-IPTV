package com.nuvio.app.features.livetv.ui.sports

import com.nuvio.app.features.livetv.ui.liveTvSideInsets
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.ui.draw.rotate
import com.nuvio.app.features.livetv.multiview.isMultiviewSupported
import com.nuvio.app.features.livetv.sports.SportsDates
import com.nuvio.app.features.livetv.sports.SportsFollows
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogOption
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioBottomSheetActionRow
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioProgressBar
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.features.livetv.data.LiveTvRepository
import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.model.EpgProgram
import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.livetv.model.LiveTvClock
import com.nuvio.app.features.livetv.sports.ChannelMatch
import com.nuvio.app.features.livetv.sports.SportsAddonSource
import com.nuvio.app.features.livetv.sports.ChannelRegions
import com.nuvio.app.features.livetv.sports.GameExtras
import com.nuvio.app.features.livetv.sports.GameState
import com.nuvio.app.features.livetv.sports.LeagueKind
import com.nuvio.app.features.livetv.sports.ScoringPlay
import com.nuvio.app.features.livetv.sports.Side
import com.nuvio.app.features.livetv.sports.Sport
import com.nuvio.app.features.livetv.sports.ChannelPickerSearchResult
import com.nuvio.app.features.livetv.sports.channelPickerSearchResults
import com.nuvio.app.features.livetv.sports.SportsCatalog
import com.nuvio.app.features.livetv.sports.SportsEvent
import com.nuvio.app.features.livetv.sports.SportsChannelRowInfo
import com.nuvio.app.features.livetv.sports.SportsMatcher
import com.nuvio.app.features.livetv.sports.SportsQualitySource
import com.nuvio.app.features.livetv.sports.sportsGameChannelRows
import com.nuvio.app.features.livetv.sports.SportsRecordings
import com.nuvio.app.features.livetv.sports.SportsRepository
import com.nuvio.app.features.livetv.sports.SportsVideos
import com.nuvio.app.features.livetv.sports.Standings
import com.nuvio.app.features.livetv.sports.TeamInfo
import com.nuvio.app.features.livetv.ui.ChannelLogo
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.livetv_sports_fix_channel
import nuvio.composeapp.generated.resources.livetv_sports_game_gone
import nuvio.composeapp.generated.resources.livetv_sports_latest_scoring
import nuvio.composeapp.generated.resources.livetv_sports_maybe
import nuvio.composeapp.generated.resources.livetv_sports_no_channels
import nuvio.composeapp.generated.resources.livetv_sports_not_this_channel
import nuvio.composeapp.generated.resources.livetv_sports_pick_channel
import nuvio.composeapp.generated.resources.livetv_sports_record
import nuvio.composeapp.generated.resources.livetv_sports_remind
import nuvio.composeapp.generated.resources.livetv_sports_reminded
import nuvio.composeapp.generated.resources.livetv_sports_showing_it
import nuvio.composeapp.generated.resources.livetv_sports_watch_on
import nuvio.composeapp.generated.resources.livetv_sports_your_pick
import nuvio.composeapp.generated.resources.livetv_sports_quality_measured
import nuvio.composeapp.generated.resources.livetv_sports_quality_reported
import nuvio.composeapp.generated.resources.livetv_sports_quality_reported_format
import nuvio.composeapp.generated.resources.livetv_sports_quality_saved
import nuvio.composeapp.generated.resources.livetv_watch_from_start
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * A game's page: who's playing and the score, the channels showing it (tap to play, long-press
 * to fix), record and remind, then the latest scoring and what else the feed has (stats,
 * leaders, form, the table, the venue, chances).
 */
@Composable
fun SportsGameScreen(
    eventId: String,
    onBack: () -> Unit,
    actions: SportsActions,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val repo = SportsRepository
    LaunchedEffect(Unit) { repo.start(); repo.setViewing(true) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { repo.setViewing(false) } }
    val known by repo.known.collectAsStateWithLifecycle()
    val events by repo.events.collectAsStateWithLifecycle()
    // The row of the day's games at the top switches the page to another game (Back still leaves).
    var shownId by remember(eventId) { mutableStateOf(eventId) }
    val ev = remember(events, known, shownId) { events.firstOrNull { it.id == shownId } ?: known.firstOrNull { it.id == shownId } }
    Column(modifier = modifier.fillMaxSize().background(tokens.colors.background).statusBarsPadding().liveTvSideInsets()) {
        NuvioScreenHeader(
            title = ev?.let { SportsCatalog.league(it.leagueId)?.name } ?: "Sports",
            includeStatusBarPadding = false,
            onBack = onBack,
            modifier = Modifier.padding(horizontal = NuvioTokens.Space.s8),
        )
        if (ev == null) {
            EmptyNote(stringResource(Res.string.livetv_sports_game_gone))
        } else {
            androidx.compose.runtime.key(ev.id) { GameBody(ev, actions, onPickGame = { shownId = it.id }) }
        }
    }
    GameAlertsOffer()
}

// ------------------------------------------------------------------ what the guide says

/** How sure we are, from the guide, that tuning in now shows the game (the TV app's guide lines). */
internal enum class GuideKind { NOW, LATER, OTHER, UNKNOWN, ENDED }

/** One channel's guide line: a short [text] in its colour ([kind]) and, sometimes, what's on now ([detail]). */
internal class GuideLine(val kind: GuideKind, val text: String, val detail: String?)

/**
 * What [m]'s channel guide says about the game: on now, on later (and what's on until then),
 * something else on while the game is live, or no listing. [onNow]: the channel's programme on now.
 */
private fun guideLine(ev: SportsEvent, m: ChannelMatch, onNow: EpgProgram?, now: Long, use24h: Boolean): GuideLine {
    val p = m.program
    val nowTitle = onNow?.title?.takeIf { it.isNotBlank() }
    fun differs(a: EpgProgram?, b: EpgProgram?) = a == null || b == null || a.startMs != b.startMs || a.title != b.title
    return when {
        p != null && now >= p.startMs && now < p.stopMs -> GuideLine(GuideKind.NOW, "Showing it now", p.title.takeIf { it.isNotBlank() })
        p != null && p.startMs > now -> {
            val at = com.nuvio.app.features.livetv.sports.clockText(p.startMs, use24h)
            val onNowTitle = nowTitle?.takeIf { differs(onNow, p) }
            if (ev.state == GameState.LIVE && onNowTitle != null) GuideLine(GuideKind.OTHER, "Something else on now", "On now: $onNowTitle · the game is listed at $at")
            else GuideLine(GuideKind.LATER, "Shows it at $at", onNowTitle?.let { "On now: $it" })
        }
        p != null -> GuideLine(GuideKind.ENDED, "Its listing has ended", nowTitle?.let { "On now: $it" })
        nowTitle != null -> GuideLine(if (ev.state == GameState.LIVE) GuideKind.OTHER else GuideKind.UNKNOWN, "Not in this channel's guide", "On now: $nowTitle")
        else -> GuideLine(GuideKind.UNKNOWN, "No guide info", null)
    }
}

@Composable
private fun guideColor(kind: GuideKind): Color = when (kind) {
    GuideKind.NOW -> Color(0xFF5FD38D)
    GuideKind.LATER -> MaterialTheme.nuvio.colors.accent
    GuideKind.OTHER -> Color(0xFFF0C36A)
    GuideKind.UNKNOWN, GuideKind.ENDED -> MaterialTheme.nuvio.colors.textMuted
}

// ------------------------------------------------------------------ the day's other games

/** The day's games in the same league (or the same sport when the league has only this one), for the row at the top. */
private fun stripGames(ev: SportsEvent, known: List<SportsEvent>): List<SportsEvent> {
    val dayStart = SportsDates.dayOf(ev.startMs)
    val dayEnd = dayStart + 24 * 3_600_000L
    fun onDay(e: SportsEvent) = (e.startMs in dayStart until dayEnd) || e.id == ev.id
    val league = known.filter { it.leagueId == ev.leagueId && onDay(it) }
    val list = if (league.size >= 2) league else {
        val sport = SportsCatalog.league(ev.leagueId)?.sport
        known.filter { sport != null && SportsCatalog.league(it.leagueId)?.sport == sport && onDay(it) }
    }
    return list.distinctBy { it.id }.sortedWith(compareBy<SportsEvent> { it.startMs }.thenBy { it.id }).take(30)
}

/** The row of the day's games across the top of a game page: the open one outlined, tap another to switch to it. */
@Composable
private fun GamesStrip(games: List<SportsEvent>, currentId: String, use24h: Boolean, hideScores: Boolean, onPick: (SportsEvent) -> Unit) {
    val tokens = MaterialTheme.nuvio
    val start = games.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    androidx.compose.foundation.lazy.LazyRow(
        state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (start - 1).coerceAtLeast(0)),
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
    ) {
        items(games, key = { "s:" + it.id }) { g ->
            val current = g.id == currentId
            val league = SportsCatalog.league(g.leagueId)
            val sides = g.ordered(league?.sport ?: Sport.OTHER)
            val showScore = g.hasScore && !hideScores
            val top = if (sides.size == 2) {
                val a = sides[0].team.abbr.ifBlank { sides[0].team.name.take(3).uppercase() }
                val b = sides[1].team.abbr.ifBlank { sides[1].team.name.take(3).uppercase() }
                if (showScore) "$a ${sides[0].score ?: "–"}–${sides[1].score ?: "–"} $b" else "$a v $b"
            } else eventHeadline(g, league)
            val bottom = when (g.state) {
                GameState.LIVE -> if (showScore) "LIVE " + cornerStatus(g, use24h, hideScores) else "LIVE"
                else -> cornerStatus(g, use24h, hideScores)
            }
            Column(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .widthIn(max = 180.dp)
                    .clip(tokens.shapes.compactCard)
                    .background(if (current) tokens.colors.accent.copy(alpha = 0.2f) else tokens.colors.surface)
                    .border(if (current) 1.5.dp else 0.dp, if (current) tokens.colors.accent else Color.Transparent, tokens.shapes.compactCard)
                    .clickable(enabled = !current) { onPick(g) }
                    .padding(horizontal = NuvioTokens.Space.s10, vertical = NuvioTokens.Space.s6),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(top, style = MaterialTheme.typography.labelLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(bottom, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = when (g.state) { GameState.LIVE -> SportsLiveRed; GameState.PRE -> tokens.colors.accent; else -> tokens.colors.textMuted })
            }
        }
    }
}

// ------------------------------------------------------------------ the page

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameBody(ev: SportsEvent, actions: SportsActions, onPickGame: (SportsEvent) -> Unit) {
    val tokens = MaterialTheme.nuvio
    val repo = SportsRepository
    val settings by repo.settings.collectAsStateWithLifecycle()
    val follows by repo.follows.collectAsStateWithLifecycle()
    val allMatches by repo.matches.collectAsStateWithLifecycle()
    val matching by repo.matching.collectAsStateWithLifecycle()
    val known by repo.known.collectAsStateWithLifecycle()
    val schedules by SportsRecordings.scheduled.collectAsStateWithLifecycle()
    val hasChannels = LiveTvRepository.channels.collectAsStateWithLifecycle().value.isNotEmpty()
    val use24h = use24h()
    val now by produceState(LiveTvClock.nowMs()) { while (true) { value = LiveTvClock.nowMs(); delay(15_000) } }
    val haptics = LocalHapticFeedback.current
    val league = SportsCatalog.league(ev.leagueId)
    val sport = league?.sport ?: Sport.OTHER
    val sides = ev.ordered(sport)
    val hide = settings.hideScores
    val showScore = ev.hasScore && !hide
    val matches = allMatches[ev.id].orEmpty()
    // Recording and "fix channel" work on your own playlists' channels, not on an addon's streams.
    val ownChannels = remember(matches) { matches.filter { it.channel.sourceId != SportsAddonSource.SOURCE_ID } }
    // Your sports addons (HighFly, StremVerse…): streams for this game, or replays once it's over.
    androidx.compose.runtime.DisposableEffect(ev.id) {
        repo.openGame(ev)
        onDispose { repo.closeGame(ev.id) }
    }
    val vodLooked = repo.vodMatches.collectAsStateWithLifecycle().value[ev.id]
    // Official highlights and condensed games (ESPN, NHL, MLB, YouTube) go in the same list.
    val official = repo.videoMatches.collectAsStateWithLifecycle().value[ev.id]
    val replays = remember(vodLooked, official, settings.showHighlights, settings.showCondensed) {
        SportsVideos.merge(vodLooked.orEmpty(), official.orEmpty(), settings.showHighlights, settings.showCondensed)
    }
    val videoScope = androidx.compose.runtime.rememberCoroutineScope()
    val vodAddons = repo.vodAddons.collectAsStateWithLifecycle().value[ev.id]
    val suggestNuvioSports by repo.suggestNuvioSports.collectAsStateWithLifecycle()
    val nuvioSportsTip = if (suggestNuvioSports)
        " Tip: ${SportsAddonSource.NUVIO_SPORTS_NAME} (${SportsAddonSource.NUVIO_SPORTS_SITE}) has replays of most games: add it in Add-ons." else ""
    val reminded = follows.reminders.any { it.eventId == ev.id }
    val recording = remember(schedules, ev.id) { SportsRecordings.forEvent(ev, schedules) }
    var askRecord by remember(ev.id) { mutableStateOf(false) }
    var fixing by remember(ev.id) { mutableStateOf(false) }
    var regionPick by remember { mutableStateOf(false) }
    var channelMenu by remember { mutableStateOf<ChannelMatch?>(null) }
    var teamSheet by remember { mutableStateOf<TeamInfo?>(null) }
    var moreReplays by remember(ev.id) { mutableStateOf(false) }
    var channelsOpen by remember(ev.id) { mutableStateOf(false) }
    val plays = rememberScoringPlays(ev, hide)
    val extras = rememberGameExtras(ev)
    val tables by repo.standings.collectAsStateWithLifecycle()
    LaunchedEffect(ev.leagueId, ev.state) {
        if (!hide && league != null && repo.mayHaveTable(league)) runCatching { repo.standings(ev.leagueId) }
    }
    val qualityUpdates = remember { LiveTvPreferences.userState.map { it.channelQuality }.distinctUntilChanged() }
    val channelQuality by qualityUpdates.collectAsStateWithLifecycle(LiveTvPreferences.userState.value.channelQuality)
    val channelRows = remember(matches, channelQuality, settings.bestChannelFirst) {
        sportsGameChannelRows(matches, channelQuality, settings.bestChannelFirst)
    }
    val strip = remember(known, ev.id, ev.leagueId, ev.startMs) { stripGames(ev, known) }
    // What each channel's guide has on now (for its guide line), checked again every minute.
    val guideKeys = remember(channelRows) { channelRows.map { it.match.channel }.filter { it.sourceId != SportsAddonSource.SOURCE_ID }.map { it.key } }
    val guideNow by produceState<Map<String, EpgProgram?>>(emptyMap(), guideKeys) {
        while (true) {
            value = runCatching { repo.nowAndNext(guideKeys).mapValues { it.value.now } }.getOrDefault(value)
            delay(60_000)
        }
    }
    fun playVideo(c: ChannelMatch) {
        if (!SportsVideos.needsResolving(c.channel)) actions.onPlay(c.channel, ev.title)
        else videoScope.launch {
            // NHL's and YouTube's videos are worked out when picked (a moment).
            NuvioToastController.show("Opening the video…")
            val ch = runCatching { repo.playableVideo(c.channel) }.getOrNull()
            if (ch != null) actions.onPlay(ch, ev.title)
            else NuvioToastController.show("That video can't be played right now. Try another one.")
        }
    }
    val overview: (@Composable () -> Unit)? = run {
        val hasPeriods = sides.size == 2 && showScore && sides.any { it.periods.isNotEmpty() } && (sport != Sport.RUGBY || periodsAddUp(sides))
        val hasResults = ev.standings.isNotEmpty() && !hide
        if (plays.isEmpty() && !hasPeriods && !hasResults) null else {
            @Composable {
                Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10)) {
                    if (plays.isNotEmpty()) {
                        Text(stringResource(Res.string.livetv_sports_latest_scoring), style = MaterialTheme.typography.titleSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold)
                        plays.asReversed().take(12).forEach { p -> PlayRow(p) }
                    }
                    if (hasPeriods) PeriodTable(sides, sport)
                    if (hasResults) {
                        Text(
                            when (sport) { Sport.TENNIS -> "Matches"; Sport.FIGHTING -> "Fight card"; Sport.GOLF -> "Leaderboard"; else -> "Results" },
                            style = MaterialTheme.typography.titleSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold,
                        )
                        ev.standings.forEach { st ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = NuvioTokens.Space.s2), verticalAlignment = Alignment.CenterVertically) {
                                Text(st.pos, style = MaterialTheme.typography.labelMedium, color = if (st.pos == "LIVE") SportsLiveRed else tokens.colors.textMuted, modifier = Modifier.width(44.dp))
                                Text(st.name, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                st.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textSecondary) }
                            }
                        }
                    }
                }
            }
        }
    }
    val tabs = listOfNotNull(overview?.let { GameTab("Overview", it) }) + (extras?.let { gameExtrasTabs(ev, it, sides, hide) } ?: emptyList())
    var tabName by remember(ev.id) { mutableStateOf<String?>(null) }
    val tab = tabs.firstOrNull { it.title == tabName } ?: tabs.firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = NuvioTokens.Space.s16, end = NuvioTokens.Space.s16, bottom = nuvioSafeBottomPadding(NuvioTokens.Space.s24)),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10),
    ) {
        // ---- the day's games in this league
        if (strip.size >= 2) {
            item(key = "strip") { GamesStrip(strip, ev.id, use24h, hide, onPickGame) }
        }
        // ---- the scoreboard, in the teams' colours
        item(key = "head") {
            Scoreboard(ev, sides, follows, tables, extras, matches, now, use24h, hide, onTeam = { teamSheet = it })
        }
        // ---- Watch (or play the replay), Multiview, and the game's other actions
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
                val firstChannel = matches.firstOrNull()
                val firstReplay = replays.firstOrNull()
                val main: Pair<String, () -> Unit>? = when {
                    ev.state == GameState.POST && firstReplay != null -> (if (firstReplay.how.startsWith("Full game") || firstReplay.how.startsWith(SportsVideos.Kind.REPLAY.label)) "Play full replay" else "Play " + firstReplay.how.substringBefore(" · ").lowercase()) to { playVideo(firstReplay) }
                    ev.state != GameState.POST && firstChannel != null -> "Watch" to { actions.onPlay(firstChannel.channel, ev.title) }
                    else -> null
                }
                if (main != null || (isMultiviewSupported && ev.state == GameState.LIVE)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8), verticalAlignment = Alignment.CenterVertically) {
                        if (main != null) {
                            Row(
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).clip(tokens.shapes.compactCard).background(tokens.colors.accent).clickable(onClick = main.second),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = tokens.colors.onAccent, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(NuvioTokens.Space.s6))
                                Text(main.first, style = MaterialTheme.typography.titleSmall, color = tokens.colors.onAccent, fontWeight = FontWeight.Bold)
                            }
                        } else Spacer(Modifier.weight(1f))
                        if (isMultiviewSupported && ev.state == GameState.LIVE) {
                            Box(
                                modifier = Modifier.size(48.dp).clip(tokens.shapes.compactCard).background(tokens.colors.surface).clickable(onClickLabel = "Open Multiview", onClick = actions.onOpenMultiview),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.GridView, contentDescription = "Open Multiview", tint = tokens.colors.textPrimary) }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
                    if (ev.state != GameState.POST && ev.hasTime) {
                        Chip(label = stringResource(if (reminded) Res.string.livetv_sports_reminded else Res.string.livetv_sports_remind), selected = reminded, onClick = { repo.toggleReminder(ev) })
                    }
                    // Score alerts for just this game (its scores and the final result), when its teams aren't followed.
                    if ((ev.state == GameState.PRE || ev.state == GameState.LIVE) && ev.sides.size == 2 && !hide && !repo.alertsFromFollows(ev, follows)) {
                        val alertsOn = follows.alertGames.any { it.eventId == ev.id }
                        Chip(label = if (alertsOn) "📣 Score alerts on" else "📣 Score alerts", selected = alertsOn, onClick = {
                            repo.toggleGameAlerts(ev)
                            NuvioToastController.show(if (alertsOn) "Score alerts off for this game" else "You'll get this game's scores and the final result")
                        })
                    }
                    if (ev.state != GameState.POST && ev.hasTime && hasChannels && ownChannels.isNotEmpty()) {
                        Chip(
                            label = if (recording != null) "● Recording set" else stringResource(Res.string.livetv_sports_record),
                            selected = recording != null,
                            onClick = { if (recording != null) SportsRecordings.cancel(ev) else askRecord = true },
                        )
                    }
                    if (hasChannels) Chip(label = stringResource(Res.string.livetv_sports_fix_channel), selected = false, onClick = { fixing = true })
                    if (league != null && repo.mayHaveTable(league)) Chip(label = "Table", selected = false, onClick = { actions.onOpenLeague(league) })
                    if (matches.size > 1) {
                        Chip(label = "🌐 " + ChannelRegions.shortName(settings.channelRegion), selected = settings.channelRegion.isNotBlank(), onClick = { regionPick = true })
                    }
                }
            }
        }
        // ---- a finished game: replays and highlights first (your channels aren't showing it any more)
        if (ev.state == GameState.POST) {
            item(key = "replays-h") { SectionHeading("Replays & highlights", if (replays.isNotEmpty()) replays.size.toString() else null) }
            if (replays.isNotEmpty()) {
                val shown = if (moreReplays) replays else replays.take(4)
                items(shown, key = { "r:" + it.channel.key }) { m -> ReplayRow(m, sides, onPlay = { playVideo(m) }) }
                if (replays.size > 4 && !moreReplays) {
                    item(key = "replays-more") {
                        Text(
                            "Show ${replays.size - 4} more",
                            style = MaterialTheme.typography.labelLarge, color = tokens.colors.accent, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(NuvioTokens.Radius.sm)).clickable { moreReplays = true }.padding(horizontal = NuvioTokens.Space.s4, vertical = NuvioTokens.Space.s10),
                        )
                    }
                }
            } else {
                item(key = "replays-none") {
                    Text(
                        when {
                            vodLooked == null || official == null -> "Looking for replays and highlights…"
                            vodAddons != null && vodAddons.isEmpty() ->
                                "Replays and highlights come from sports add-ons that offer them (HighFly Premium, StremVerse…). " +
                                    "None of your add-ons do yet: install one in Add-ons and they'll show here." + nuvioSportsTip
                            else -> (vodAddons?.takeIf { it.isNotEmpty() }?.let { "No replay of this game in ${it.joinToString(", ")} yet. " }
                                ?: "No replay of this game in your sports add-ons yet. ") +
                                "They're often added a few hours after the game ends." + nuvioSportsTip
                        },
                        style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted,
                    )
                }
            }
            // Your channels, folded into one line (open it for Watch from start).
            if (channelRows.isNotEmpty()) {
                item(key = "channels-fold") {
                    val fromStart = channelRows.count { it.hasCatchup }
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(tokens.shapes.card).background(tokens.colors.surface)
                            .clickable(onClickLabel = if (channelsOpen) "Close" else "Open") { channelsOpen = !channelsOpen }
                            .padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s8),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
                    ) {
                        Icon(Icons.Filled.LiveTv, contentDescription = null, tint = tokens.colors.textMuted, modifier = Modifier.size(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Shown on ${channelRows.size} of your channel${if (channelRows.size == 1) "" else "s"}", style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (fromStart > 0) "$fromStart can play it from the start · hold a channel for Watch from start" else "Hold a channel for more",
                                style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted,
                            )
                        }
                        Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = tokens.colors.textMuted, modifier = Modifier.rotate(if (channelsOpen) 180f else 0f))
                    }
                }
            }
        } else {
            // ---- live or coming up: your channels, each with what its guide says
            item(key = "channels-h") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeading(stringResource(Res.string.livetv_sports_watch_on).uppercase(), null, modifier = Modifier.weight(1f))
                    if (matching) NuvioLoadingIndicator(size = NuvioTokens.Space.s16)
                }
            }
            if (channelRows.isEmpty()) {
                item(key = "channels-none") {
                    Text(
                        if (!hasChannels) "Add a playlist in Live TV settings to watch games." else if (matching) "Looking through your channels…" else stringResource(Res.string.livetv_sports_no_channels),
                        style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted,
                    )
                }
            }
        }
        if (ev.state != GameState.POST || channelsOpen) {
            items(channelRows, key = { "c:" + it.match.channel.key }) { row ->
                val m = row.match
                val guide = if (m.channel.sourceId == SportsAddonSource.SOURCE_ID || ev.state == GameState.POST) null else guideLine(ev, m, guideNow[m.channel.key], now, use24h)
                SportsChannelRow(
                    row = row, guide = guide,
                    onPlay = { c -> actions.onPlay(c.channel, ev.title) },
                    onLongPress = { c ->
                        if (c.channel.sourceId != SportsAddonSource.SOURCE_ID) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress); channelMenu = c
                        }
                    },
                )
            }
        }
        // ---- tabs: Overview (scoring, periods, results) and the feed's extras
        if (tabs.isNotEmpty() && tab != null) {
            item(key = "tabs") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = NuvioTokens.Space.s8).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4),
                ) {
                    tabs.forEach { t ->
                        val selected = t.title == tab.title
                        Column(
                            modifier = Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(NuvioTokens.Radius.sm)).clickable { tabName = t.title }
                                .padding(horizontal = NuvioTokens.Space.s10),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(t.title, style = MaterialTheme.typography.titleSmall, color = if (selected) tokens.colors.textPrimary else tokens.colors.textMuted, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                            Spacer(Modifier.height(NuvioTokens.Space.s6))
                            Box(Modifier.width(28.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) tokens.colors.accent else Color.Transparent))
                        }
                    }
                }
            }
            item(key = "tab:" + tab.title) { tab.content() }
        }
    }

    if (askRecord) {
        RecordDialog(ev, ownChannels, use24h, onRecord = { m, minutes -> askRecord = false; NuvioToastController.show(SportsRecordings.record(ev, m, minutes)) }, onDismiss = { askRecord = false })
    }
    if (fixing) FixChannelDialog(ev, ownChannels, onDismiss = { fixing = false })
    if (regionPick) {
        RegionDialog(matches.map { SportsMatcher.channelRegionCode(it.channel) }, settings.channelRegion, onPick = { repo.updateSettings { s -> s.copy(channelRegion = it) }; regionPick = false }, onDismiss = { regionPick = false })
    }
    channelMenu?.let { m -> ChannelMenuSheet(ev, m, actions, onDismiss = { channelMenu = null }) }
    teamSheet?.let { t -> TeamSheet(t, actions, onDismiss = { teamSheet = null }) }
}

@Composable
private fun SectionHeading(text: String, count: String?, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    Row(modifier = modifier.padding(top = NuvioTokens.Space.s8), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = tokens.colors.textSecondary, fontWeight = FontWeight.Bold)
        count?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = tokens.colors.textMuted) }
    }
}

/**
 * The scoreboard: the two teams either side in their colours (tap one for its card), the score or
 * the start time in the middle with the state under it, then when it is and the TV networks.
 */
@Composable
private fun Scoreboard(
    ev: SportsEvent,
    sides: List<Side>,
    follows: SportsFollows,
    tables: Map<String, Standings>,
    extras: GameExtras?,
    matches: List<ChannelMatch>,
    now: Long,
    use24h: Boolean,
    hide: Boolean,
    onTeam: (TeamInfo) -> Unit,
) {
    val league = SportsCatalog.league(ev.leagueId)
    val showScore = ev.hasScore && !hide
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(NuvioTokens.Radius.xl)).background(featuredBrush(sides, league))
            .background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f)))
            .padding(horizontal = NuvioTokens.Space.s10, vertical = NuvioTokens.Space.s14),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (sides.size == 2) {
            val table = tables[ev.leagueId]
            Row(verticalAlignment = Alignment.CenterVertically) {
                sides.forEachIndexed { i, s ->
                    if (i == 1) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = NuvioTokens.Space.s4)) {
                            if (showScore) {
                                Text("${sides[0].score ?: "–"} – ${sides[1].score ?: "–"}", style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = FontWeight.Black, maxLines = 1)
                            } else {
                                Text(if (ev.state == GameState.PRE && ev.hasTime) startLabel(ev, use24h) else if (sport(ev).homeFirst) "v" else "at", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Black, maxLines = 1)
                            }
                            Spacer(Modifier.height(NuvioTokens.Space.s4))
                            when (ev.state) {
                                GameState.LIVE -> SportsBadge(if (showScore) "LIVE " + cornerStatus(ev, use24h, hide) else "LIVE", SportsLiveRed)
                                GameState.PRE -> SportsBadge(if (ev.hasTime) countdownLabel(ev.startMs, now) else ev.note ?: startLabel(ev, use24h), Color.Black.copy(alpha = 0.35f))
                                else -> SportsBadge(cornerStatus(ev, use24h, hide), Color.Black.copy(alpha = 0.35f))
                            }
                        }
                    }
                    val followed = follows.followsTeam(ev.leagueId, s.team.id)
                    val sub = when {
                        ev.state == GameState.PRE && !hide -> preGameLine(s, table, extras)
                        !hide -> table?.placeText(s.team.id)?.substringBefore(" in ")?.let { listOfNotNull(it, s.record).joinToString(" · ") } ?: s.record
                        else -> null
                    }
                    Column(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(NuvioTokens.Radius.md)).clickable(onClickLabel = s.team.name) { onTeam(s.team) }.padding(vertical = NuvioTokens.Space.s4),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
                    ) {
                        TeamLogo(s.team, 56.dp)
                        Text(
                            s.team.name + (if (followed) " ★" else ""),
                            style = MaterialTheme.typography.titleSmall, color = Color.White,
                            fontWeight = if (ev.state == GameState.POST && s.winner) FontWeight.Black else FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        )
                        sub?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
                    }
                }
            }
        } else {
            Text(eventHeadline(ev, league), style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            ev.about?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f), maxLines = 4, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
            when (ev.state) {
                GameState.LIVE -> SportsBadge("LIVE", SportsLiveRed)
                else -> SportsBadge(cornerStatus(ev, use24h, hide), Color.Black.copy(alpha = 0.35f))
            }
        }
        if (ev.state == GameState.LIVE) NuvioProgressBar(progress = progressOf(ev, now), height = NuvioTokens.Space.s3, modifier = Modifier.padding(horizontal = NuvioTokens.Space.s8))
        val whenText = when (ev.state) {
            GameState.LIVE -> null
            GameState.PRE -> if (ev.hasTime) dayWord(ev.startMs, now) + " " + startLabel(ev, use24h) else ev.note
            GameState.POST -> dayWord(ev.startMs, now)
            GameState.OFF -> ev.status ?: "Postponed"
        }
        // The channel of yours the game is on first (before it starts), then the TV networks.
        val shownNetworks = (listOfNotNull(matches.firstOrNull()?.channel?.name?.takeIf { it.isNotBlank() && ev.state == GameState.PRE }) + ev.broadcasts).distinct().take(3)
        val line = listOfNotNull(whenText, ev.subtitle?.takeIf { sides.size == 2 }, shownNetworks.joinToString(", ").ifBlank { null }).joinToString(" · ")
        if (line.isNotBlank()) Text(line, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        ev.situation?.takeIf { !hide }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center) }
    }
}

private fun sport(ev: SportsEvent): Sport = SportsCatalog.league(ev.leagueId)?.sport ?: Sport.OTHER

/** A replay or highlight: a tile in the teams' colours, its name, where it's from and how long, and a tag. */
@Composable
private fun ReplayRow(m: ChannelMatch, sides: List<Side>, onPlay: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val how = m.how
    val tag = when {
        how.startsWith("Full game") || how.startsWith(SportsVideos.Kind.REPLAY.label) -> "Full game"
        how.startsWith("Condensed") -> "Condensed"
        m.channel.sourceId == SportsVideos.SOURCE_ID -> "Official"
        else -> null
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(tokens.shapes.card).background(tokens.colors.surface).clickable(onClickLabel = "Play", onClick = onPlay)
            .padding(NuvioTokens.Space.s10),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        Box(
            modifier = Modifier.width(88.dp).height(50.dp).clip(RoundedCornerShape(NuvioTokens.Radius.md)).background(featuredBrush(sides, null)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2)) {
            Text(m.channel.name, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(how, m.quality).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            tag?.let {
                SportsBadge(it, if (it == "Full game") tokens.colors.accent.copy(alpha = 0.22f) else tokens.colors.surfaceElevated, if (it == "Full game") tokens.colors.accent else tokens.colors.textSecondary)
            }
        }
    }
}

/** Before a game: "3rd · WWDLW · 62% win" for one side, from the table and the game's extras. */
private fun preGameLine(s: Side, table: Standings?, x: GameExtras?): String? {
    val row = table?.find(s.team.id)?.second
    val form = x?.form?.firstOrNull { it.teamId == s.team.id || it.team.equals(s.team.name, true) }?.results?.joinToString("") { it.take(1) } ?: row?.form
    val win = if (s.home) x?.homeWin else x?.awayWin
    return listOfNotNull(row?.pos?.let { Standings.ordinal(it) }, form?.takeLast(5)?.uppercase()?.takeIf { it.isNotEmpty() }, win?.let { "${it.roundToInt()}% win" })
        .joinToString(" · ").ifBlank { null }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SportsChannelRow(row: SportsChannelRowInfo, guide: GuideLine? = null, onPlay: (ChannelMatch) -> Unit, onLongPress: (ChannelMatch) -> Unit) {
    val tokens = MaterialTheme.nuvio
    val m = row.match
    Row(
        modifier = Modifier.fillMaxWidth().clip(tokens.shapes.card).background(tokens.colors.surface)
            .combinedClickable(onClick = { onPlay(m) }, onLongClick = { onLongPress(m) })
            .heightIn(min = 60.dp)
            .padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        ChannelLogo(m.channel, size = 40.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6)) {
                Text(m.channel.name, style = MaterialTheme.typography.bodyLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (row.copies > 1) Text("+${row.copies - 1}", style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted)
            }
            // What the channel's guide says (green: showing it now; amber: something else on), then where it's from.
            if (guide != null) {
                val c = guideColor(guide.kind)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6)) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(c))
                    Text(guide.text, style = MaterialTheme.typography.labelMedium, color = c, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(
                text = listOfNotNull(
                    m.channel.sourceName.takeIf { it.isNotBlank() },
                    guide?.takeIf { it.kind != GuideKind.NOW }?.detail
                        ?: m.program?.let { p -> "${sportsClock(p.startMs)} ${com.nuvio.app.features.livetv.record.RecordingRules.shownTitle(p.title)}" } ?: m.how,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2)) {
            row.quality?.let { quality ->
                SportsBadge(quality.label, tokens.colors.surfaceElevated, tokens.colors.textSecondary)
                val source = when (quality.source) {
                    SportsQualitySource.PLAYBACK -> Res.string.livetv_sports_quality_measured
                    SportsQualitySource.PROVIDER -> Res.string.livetv_sports_quality_reported
                    SportsQualitySource.SAVED -> Res.string.livetv_sports_quality_saved
                }
                Text(stringResource(source), style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted)
                quality.reportedRateLabel?.let { label ->
                    Text(stringResource(Res.string.livetv_sports_quality_reported_format, label),
                        style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted)
                }
            }
            when {
                m.score >= 150 -> SportsBadge(stringResource(Res.string.livetv_sports_your_pick), tokens.colors.accent, tokens.colors.onAccent)
                m.sure && guide == null -> SportsBadge(stringResource(Res.string.livetv_sports_showing_it), Color(0xFF2E9E5B))
                !m.sure -> SportsBadge(stringResource(Res.string.livetv_sports_maybe), tokens.colors.surfaceElevated, tokens.colors.textSecondary)
            }
        }
        Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = tokens.colors.textPrimary)
    }
}

@Composable
private fun PlayRow(p: ScoringPlay) {
    val tokens = MaterialTheme.nuvio
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = NuvioTokens.Space.s2), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
        Text(p.clock, style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, modifier = Modifier.width(64.dp))
        p.teamAbbr?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = tokens.colors.textSecondary, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp)) }
        Text(p.text, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (p.awayScore != null && p.homeScore != null) Text("${p.awayScore}–${p.homeScore}", style = MaterialTheme.typography.labelMedium, color = tokens.colors.textSecondary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PeriodTable(sides: List<Side>, sport: Sport) {
    val tokens = MaterialTheme.nuvio
    val n = sides.maxOf { it.periods.size }
    val heading = when (sport) { Sport.BASEBALL -> "Innings"; Sport.HOCKEY -> "Periods"; Sport.FOOTBALL, Sport.RUGBY, Sport.HANDBALL -> "Halves"; else -> "Quarters" }
    Column(modifier = Modifier.fillMaxWidth().clip(tokens.shapes.card).background(tokens.colors.surface).padding(NuvioTokens.Space.s10), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4)) {
        Row {
            Text(heading, style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, modifier = Modifier.weight(1f))
            (1..n).forEach { Text(it.toString(), style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center) }
            Text("T", style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
        }
        sides.forEach { s ->
            Row {
                Text(s.team.abbr.ifBlank { s.team.name }, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                (0 until n).forEach { i -> Text(s.periods.getOrNull(i) ?: "-", style = MaterialTheme.typography.bodySmall, color = tokens.colors.textSecondary, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center) }
                Text(s.score ?: "-", style = MaterialTheme.typography.bodySmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.Black, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
            }
        }
    }
}

/** A game's scoring plays, kept up to date while it's on. */
@Composable
private fun rememberScoringPlays(ev: SportsEvent, hideScores: Boolean): List<ScoringPlay> {
    var plays by remember(ev.id) { mutableStateOf<List<ScoringPlay>>(emptyList()) }
    val latest = rememberUpdatedState(ev)
    LaunchedEffect(ev.id, ev.state, ev.away?.score, ev.home?.score, hideScores) {
        if (hideScores) return@LaunchedEffect
        while (true) {
            val e = latest.value
            if (e.state != GameState.PRE) plays = runCatching { SportsRepository.scoringPlays(e) }.getOrDefault(emptyList())
            if (e.state != GameState.LIVE) break
            delay(30_000)
        }
    }
    return plays
}

/** The game's extras, asked again every minute while it's on (once otherwise). */
@Composable
private fun rememberGameExtras(ev: SportsEvent): GameExtras? {
    var extras by remember(ev.id) { mutableStateOf<GameExtras?>(null) }
    val latest = rememberUpdatedState(ev)
    LaunchedEffect(ev.id, ev.state) {
        while (true) {
            runCatching { SportsRepository.gameExtras(latest.value) }.getOrNull()?.let { extras = it }
            if (latest.value.state != GameState.LIVE) break
            delay(60_000)
        }
    }
    return extras
}

// ------------------------------------------------------------------ dialogs and sheets

@Composable
private fun RecordDialog(ev: SportsEvent, matches: List<ChannelMatch>, use24h: Boolean, onRecord: (ChannelMatch, Int) -> Unit, onDismiss: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val channels = remember(matches) { matches.take(6) }
    var pick by remember(ev.id) { mutableIntStateOf(0) }
    var minutes by remember(ev.id) { mutableIntStateOf(SportsRecordings.defaultLength(ev)) }
    DialogSurface(onDismissRequest = onDismiss, title = stringResource(Res.string.livetv_sports_record), message = ev.title) {
        if (channels.isEmpty()) Text("No channel found for this game yet. Use Fix channel to pick one.", style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted)
        Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4)) {
            channels.forEachIndexed { i, m ->
                DialogOption(text = m.channel.name, description = listOfNotNull(m.channel.sourceName.takeIf { it.isNotBlank() }, if (m.sure) "Showing it" else "Maybe").joinToString(" · "), selected = i == pick, onClick = { pick = i })
            }
        }
        Text("Record for", style = MaterialTheme.typography.labelMedium, color = tokens.colors.textMuted)
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6)) {
            SportsRecordings.LENGTHS.forEach { len -> Chip(label = SportsRecordings.label(len), selected = len == minutes, onClick = { minutes = len }) }
        }
        DialogButtons {
            DialogButton(text = stringResource(Res.string.action_cancel), onClick = onDismiss)
            DialogButton(text = stringResource(Res.string.livetv_sports_record), style = DialogButtonStyle.Primary, enabled = channels.isNotEmpty(), onClick = { channels.getOrNull(pick)?.let { onRecord(it, minutes) } })
        }
    }
}

@Composable
private fun FixChannelDialog(ev: SportsEvent, matches: List<ChannelMatch>, onDismiss: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val repo = SportsRepository
    var picking by remember { mutableStateOf(matches.isEmpty()) }
    var chosen by remember { mutableStateOf<LiveChannel?>(null) }
    val pick = chosen
    if (pick != null) {
        val networks = ev.broadcasts
        val league = SportsCatalog.league(ev.leagueId)
        val teams = ev.ordered(league?.sport ?: Sport.OTHER).map { it.team }
        if (networks.isEmpty() && teams.isEmpty() && league?.kind != LeagueKind.EVENT) {
            LaunchedEffect(pick.key) {
                repo.pickChannel(ev, pick.key, forNetworks = false)
                NuvioToastController.show("Saved: ${ev.title} on ${pick.name}")
                onDismiss()
            }
            return
        }
        DialogSurface(onDismissRequest = onDismiss, title = "Use ${pick.name} for more games?", message = "Pick a network or a team, and their games from now on use ${pick.name} too.") {
            Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4)) {
                networks.firstOrNull()?.let { n ->
                    DialogOption(text = "Yes, every $n game", selected = false, onClick = { repo.pickChannel(ev, pick.key, forNetworks = true); NuvioToastController.show("Saved: $n games use ${pick.name}"); onDismiss() })
                }
                if (league?.kind == LeagueKind.EVENT) {
                    DialogOption(text = "Yes, every ${league.name} event", selected = false, onClick = { repo.pickChannel(ev, pick.key, forNetworks = false, forLeague = true); NuvioToastController.show("Saved: ${league.name} events use ${pick.name}"); onDismiss() })
                }
                teams.forEach { t ->
                    DialogOption(text = "Yes, every ${t.name} game", selected = false, onClick = { repo.pickChannel(ev, pick.key, forNetworks = false, forTeam = t); NuvioToastController.show("Saved: ${t.name} games use ${pick.name}"); onDismiss() })
                }
                DialogOption(text = "Just this game", selected = false, onClick = { repo.pickChannel(ev, pick.key, forNetworks = false); NuvioToastController.show("Saved: ${ev.title} on ${pick.name}"); onDismiss() })
            }
            DialogButtons { DialogButton(text = stringResource(Res.string.action_cancel), onClick = onDismiss) }
        }
        return
    }
    if (picking) {
        ChannelPickerDialog(title = "Pick the channel for ${ev.title}", onPick = { chosen = it }, onDismiss = onDismiss)
        return
    }
    DialogSurface(onDismissRequest = onDismiss, title = stringResource(Res.string.livetv_sports_fix_channel), message = "Take wrong channels off the list, or pick the right one.") {
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4)) {
            items(matches, key = { it.channel.key }) { m ->
                DialogOption(text = m.channel.name, description = stringResource(Res.string.livetv_sports_not_this_channel), selected = false, onClick = {
                    repo.blockChannel(ev, m.channel.key)
                    NuvioToastController.show("${m.channel.name} won't be used for this game")
                })
            }
        }
        DialogButtons {
            DialogButton(text = stringResource(Res.string.action_cancel), onClick = onDismiss)
            DialogButton(text = stringResource(Res.string.livetv_sports_pick_channel), style = DialogButtonStyle.Primary, onClick = { picking = true })
        }
    }
}

/** Any channel, by name. */
@Composable
internal fun ChannelPickerDialog(title: String, onPick: (LiveChannel) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val all = remember { SportsRepository.channelsForPicker() }
    var result by remember(all) { mutableStateOf<ChannelPickerSearchResult<LiveChannel>?>(null) }
    LaunchedEffect(all) {
        channelPickerSearchResults(all, snapshotFlow { query }) { it.name }.collect { result = it }
    }
    val shown = result?.forQuery(query).orEmpty()
    DialogSurface(onDismissRequest = onDismiss, title = title) {
        NuvioInputField(value = query, onValueChange = { query = it }, placeholder = "Channel name")
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4)) {
            items(shown, key = { it.key }) { ch ->
                DialogOption(text = ch.name, description = ch.group + " · " + ch.sourceName, selected = false, onClick = { onPick(ch) })
            }
        }
        DialogButtons { DialogButton(text = stringResource(Res.string.action_cancel), onClick = onDismiss) }
    }
}

@Composable
private fun RegionDialog(codes: Collection<String?>, current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val choices = remember(codes, current) { ChannelRegions.choicesFor(codes, current) }
    fun count(n: Int) = if (n == 1) "1 channel" else "$n channels"
    DialogSurface(onDismissRequest = onDismiss, title = "Channels from", message = "Shows that region's channels first for every game (handy for commentary in your language).") {
        Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4)) {
            DialogOption(text = "All regions", description = count(codes.size), selected = current.isBlank(), onClick = { onPick("") })
            choices.forEach { (code, n) ->
                DialogOption(text = ChannelRegions.name(code), description = if (n == 0) "None for this game" else count(n), selected = code == current, onClick = { onPick(code) })
            }
        }
        DialogButtons { DialogButton(text = stringResource(Res.string.action_cancel), onClick = onDismiss) }
    }
}

/** Long-press on a channel: play, from the start, not this channel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelMenuSheet(ev: SportsEvent, m: ChannelMatch, actions: SportsActions, onDismiss: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    NuvioModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s8), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12)) {
            ChannelLogo(m.channel, size = 40.dp)
            Column {
                Text(m.channel.name, style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold)
                Text(m.how, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted)
            }
        }
        NuvioBottomSheetActionRow(title = "Play", icon = Icons.Filled.PlayArrow, onClick = { onDismiss(); actions.onPlay(m.channel, ev.title) })
        if (m.catchup) {
            NuvioBottomSheetActionRow(title = stringResource(Res.string.livetv_watch_from_start), icon = Icons.Filled.Replay, onClick = {
                onDismiss()
                val league = SportsCatalog.league(ev.leagueId)
                val program = m.program?.takeIf { it.startMs <= ev.startMs + 30 * 60_000L }
                    ?: EpgProgram(startMs = ev.startMs, stopMs = ev.endMs ?: (ev.startMs + (league?.minutes ?: 180) * 60_000L), title = ev.title)
                actions.onPlayCatchup(m.channel, program)
            })
        }
        NuvioBottomSheetActionRow(title = "This is the right channel", icon = Icons.Filled.NotificationsActive, onClick = {
            onDismiss(); SportsRepository.pickChannel(ev, m.channel.key, forNetworks = false); NuvioToastController.show("Saved: ${ev.title} on ${m.channel.name}")
        })
        NuvioBottomSheetActionRow(title = stringResource(Res.string.livetv_sports_not_this_channel), icon = Icons.Filled.FiberManualRecord, onClick = {
            onDismiss(); SportsRepository.blockChannel(ev, m.channel.key); NuvioToastController.show("${m.channel.name} won't be used for this game")
        })
        Spacer(Modifier.height(NuvioTokens.Space.s12))
    }
}

/** A team's card: follow, and its games (results and what's next). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TeamSheet(team: TeamInfo, actions: SportsActions, onDismiss: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val repo = SportsRepository
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val follows by repo.follows.collectAsStateWithLifecycle()
    val settings by repo.settings.collectAsStateWithLifecycle()
    val use24h = use24h()
    val now = LiveTvClock.nowMs()
    var games by remember(team.key) { mutableStateOf<List<SportsEvent>?>(null) }
    LaunchedEffect(team.key) { games = runCatching { repo.teamGames(team) }.getOrDefault(emptyList()) }
    val followed = follows.followedTeam(team) != null
    NuvioModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, fullHeight = true) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s8)
                .clip(tokens.shapes.compactCard)
                .background(teamBandBrush(team))
                .padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s10),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
        ) {
            TeamLogo(team, 44.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(team.fullName, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(listOfNotNull(SportsCatalog.league(team.leagueId)?.name, team.record).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
            }
            Chip(label = if (followed) "Following" else "Follow", selected = followed, onClick = { repo.toggleTeam(team) })
        }
        val list = games
        if (list == null) {
            NuvioLoadingIndicator(modifier = Modifier.padding(NuvioTokens.Space.s24).align(Alignment.CenterHorizontally))
        } else {
            val startIndex = list.indexOfFirst { it.state != GameState.POST }.let { if (it < 0) (list.size - 3).coerceAtLeast(0) else (it - 1).coerceAtLeast(0) }
            LazyColumn(
                state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = startIndex),
                modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
                contentPadding = PaddingValues(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s8),
                verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
            ) {
                if (list.isEmpty()) item { Text("No games known yet.", style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted) }
                items(list, key = { it.id }) { ev ->
                    GameCard(ev, follows, now, use24h, settings.hideScores, channels = null) { onDismiss(); actions.onOpenGame(ev) }
                }
            }
        }
    }
}

/**
 * Whether each side's scores by period add up to its score. ESPN's live rugby leaves them at 0
 * all game, which showed 0-0 halves beside a 32-12 score: for rugby such a table is left out.
 */
private fun periodsAddUp(sides: List<Side>): Boolean = sides.all { s ->
    val total = s.score?.toDoubleOrNull() ?: return@all true
    val parts = s.periods.map { it.toDoubleOrNull() ?: return@all true }
    parts.isEmpty() || kotlin.math.abs(parts.sum() - total) < 0.01
}

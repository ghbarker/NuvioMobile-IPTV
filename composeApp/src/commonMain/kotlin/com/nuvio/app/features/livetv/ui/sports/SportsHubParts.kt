package com.nuvio.app.features.livetv.ui.sports

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioProgressBar
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.livetv.sports.ChannelMatch
import com.nuvio.app.features.livetv.sports.GameState
import com.nuvio.app.features.livetv.sports.League
import com.nuvio.app.features.livetv.sports.Side
import com.nuvio.app.features.livetv.sports.Sport
import com.nuvio.app.features.livetv.sports.SportsCatalog
import com.nuvio.app.features.livetv.sports.SportsEvent
import com.nuvio.app.features.livetv.sports.SportsFollows
import com.nuvio.app.features.livetv.sports.SportsMatcher
import com.nuvio.app.features.livetv.sports.SportsRepository
import com.nuvio.app.features.livetv.sports.SportsVideos
import com.nuvio.app.features.livetv.sports.Standings
import com.nuvio.app.features.livetv.sports.StandingsRow
import com.nuvio.app.features.livetv.sports.StandingsView
import com.nuvio.app.features.livetv.ui.ChannelLogo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/*
 * The pieces of the Sports pages' new look (the user-suggested layout): the search bar, the big
 * swipe card, "Live on your channels", folding league groups, the league row with its next match
 * day, and a small table.
 */

// ------------------------------------------------------------------ search

/** The search bar at the top of My Sports and All Events: typing shows teams, leagues and names in place of the page. */
@Composable
internal fun SportsSearchBar(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    NuvioInputField(
        value = query,
        onValueChange = onQuery,
        placeholder = "Search games, teams, leagues…",
        shape = CircleShape,
        modifier = modifier,
        trailingContent = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = tokens.colors.textSecondary) }
            } else {
                Icon(Icons.Filled.Search, contentDescription = null, tint = tokens.colors.textMuted)
            }
        },
    )
}

// ------------------------------------------------------------------ the big card

/** The big card's colours: the first team's on the left, the second's on the right (a league's colour for an event). */
internal fun featuredBrush(sides: List<Side>, league: League?): Brush {
    if (sides.size == 2) {
        val a = bandColor(bandBase(sides[0].team))
        val b = bandColor(bandBase(sides[1].team))
        return Brush.linearGradient(listOf(a, lerpDark(a), lerpDark(b), b))
    }
    val c = bandColor(leagueColor(league))
    return Brush.linearGradient(listOf(c, lerpDark(c)))
}

private fun lerpDark(c: Color): Color = androidx.compose.ui.graphics.lerp(c, Color(0xFF0B0E14), 0.55f)

/**
 * The big swipe card: a game at a time (live first), in the teams' colours, with the score or the
 * start time and the channel it's on. Tap opens the game. [label]: a small heading on the card
 * ("YOUR TEAMS" on My Sports).
 */
@Composable
internal fun FeaturedGames(
    games: List<SportsEvent>,
    matches: Map<String, List<ChannelMatch>>,
    follows: SportsFollows,
    now: Long,
    use24h: Boolean,
    hideScores: Boolean,
    onOpen: (SportsEvent) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    if (games.isEmpty()) return
    val pager = rememberPagerState { games.size }
    Box(modifier = modifier.fillMaxWidth().height(188.dp)) {
        HorizontalPager(state = pager, pageSpacing = NuvioTokens.Space.s8, key = { games.getOrNull(it)?.id ?: it }) { page ->
            val ev = games.getOrNull(page) ?: return@HorizontalPager
            FeaturedCard(ev, matches[ev.id], follows, now, use24h, hideScores, label, onClick = { onOpen(ev) })
        }
        if (games.size > 1) {
            Row(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = NuvioTokens.Space.s16, bottom = NuvioTokens.Space.s16),
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4),
            ) {
                repeat(games.size) { i ->
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White.copy(alpha = if (i == pager.currentPage) 1f else 0.4f)))
                }
            }
        }
    }
}

@Composable
private fun FeaturedCard(
    ev: SportsEvent,
    found: List<ChannelMatch>?,
    follows: SportsFollows,
    now: Long,
    use24h: Boolean,
    hideScores: Boolean,
    label: String?,
    onClick: () -> Unit,
) {
    val league = SportsCatalog.league(ev.leagueId)
    val sides = ev.ordered(league?.sport ?: Sport.OTHER)
    val showScore = ev.hasScore && !hideScores
    Box(
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(NuvioTokens.Radius.xl)).background(featuredBrush(sides, league)).clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s14, vertical = NuvioTokens.Space.s12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
        ) {
            LeagueMark(league, 22.dp)
            Text(
                listOfNotNull(label, league?.name).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            when (ev.state) {
                GameState.LIVE -> {
                    SportsBadge("● LIVE", SportsLiveRed)
                    if (showScore) SportsBadge(cornerStatus(ev, use24h, hideScores), Color(0xFFFFC24A), Color(0xFF1A1200))
                }
                GameState.POST -> SportsBadge(cornerStatus(ev, use24h, hideScores), Color.Black.copy(alpha = 0.35f))
                else -> SportsBadge(dayWord(ev.startMs, now) + (if (ev.hasTime) " " + startLabel(ev, use24h) else ""), Color.Black.copy(alpha = 0.35f))
            }
        }
        if (sides.size == 2) {
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.Center).padding(horizontal = NuvioTokens.Space.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FeaturedTeam(sides[0], ev, hideScores, follows.followsTeam(ev.leagueId, sides[0].team.id), Modifier.weight(1f))
                Text(
                    text = if (showScore) "${sides[0].score ?: "–"} – ${sides[1].score ?: "–"}" else if (sport(ev).homeFirst) "v" else "at",
                    style = if (showScore) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge,
                    color = Color.White, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                )
                FeaturedTeam(sides[1], ev, hideScores, follows.followsTeam(ev.leagueId, sides[1].team.id), Modifier.weight(1f))
            }
        } else {
            Column(modifier = Modifier.align(Alignment.Center).padding(horizontal = NuvioTokens.Space.s16), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(eventHeadline(ev, league), style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                ev.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        val channelLine = when {
            ev.state == GameState.POST -> null
            !found.isNullOrEmpty() -> {
                val groups = SportsMatcher.groupCopies(found)
                groups.first().first().channel.name + (if (groups.size > 1) " · +${groups.size - 1} channel${if (groups.size == 2) "" else "s"}" else "")
            }
            found != null -> "Not on your channels"
            else -> ev.broadcasts.take(2).joinToString(", ").ifBlank { null }
        }
        if (channelLine != null) {
            Row(
                modifier = Modifier.align(Alignment.BottomStart).padding(start = NuvioTokens.Space.s14, end = 72.dp, bottom = NuvioTokens.Space.s12),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
            ) {
                Icon(Icons.Filled.LiveTv, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(14.dp))
                Text(channelLine, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun sport(ev: SportsEvent): Sport = SportsCatalog.league(ev.leagueId)?.sport ?: Sport.OTHER

@Composable
private fun FeaturedTeam(side: Side, ev: SportsEvent, hideScores: Boolean, followed: Boolean, modifier: Modifier) {
    val sport = sport(ev)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6)) {
        MatchupLogo(side, 56.dp, sport, !hideScores && ev.state == GameState.LIVE)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4)) {
            Text(side.rankedName, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
            if (followed) Icon(Icons.Filled.Star, contentDescription = "You follow this team", tint = Color(0xFFF5C400), modifier = Modifier.size(NuvioTokens.Icon.xs))
        }
        if (sport == Sport.AMERICAN_FOOTBALL && !hideScores) side.record?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
        }
    }
}

// ------------------------------------------------------------------ live on your channels

/** A live game and the channel of yours to watch it on (the one airing it now, else the surest). */
internal class LiveOnChannel(val event: SportsEvent, val match: ChannelMatch, val channels: Int)

/** Live games on your channels, best channel first, in the page's order. */
internal fun liveOnChannels(live: List<SportsEvent>, matches: Map<String, List<ChannelMatch>>, now: Long): List<LiveOnChannel> =
    live.mapNotNull { ev ->
        val found = matches[ev.id].orEmpty()
        if (found.isEmpty()) return@mapNotNull null
        val airing = found.firstOrNull { m -> m.program?.let { now >= it.startMs && now < it.stopMs } == true }
        val pick = airing ?: found.firstOrNull { it.sure } ?: return@mapNotNull null
        LiveOnChannel(ev, pick, SportsMatcher.groupCopies(found).size)
    }

/** "Live on your channels": a card per live game, tap plays it on its channel (hold opens the game). */
@Composable
internal fun LiveOnChannelsRow(
    games: List<LiveOnChannel>,
    now: Long,
    use24h: Boolean,
    hideScores: Boolean,
    actions: SportsActions,
) {
    if (games.isEmpty()) return
    val tokens = MaterialTheme.nuvio
    Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
            Icon(Icons.Filled.LiveTv, contentDescription = null, tint = tokens.colors.accent, modifier = Modifier.size(18.dp))
            Text("Live on your channels", style = MaterialTheme.typography.titleSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold)
            Text(games.size.toString(), style = MaterialTheme.typography.labelMedium, color = tokens.colors.textMuted)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10)) {
            items(games, key = { it.event.id }) { game -> LiveChannelCard(game, now, use24h, hideScores, actions) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiveChannelCard(item: LiveOnChannel, now: Long, use24h: Boolean, hideScores: Boolean, actions: SportsActions) {
    val tokens = MaterialTheme.nuvio
    val ev = item.event
    val m = item.match
    val league = SportsCatalog.league(ev.leagueId)
    val sides = ev.ordered(league?.sport ?: Sport.OTHER)
    val showScore = ev.hasScore && !hideScores
    Column(
        modifier = Modifier
            .width(252.dp)
            .clip(tokens.shapes.card)
            .background(tokens.colors.surface)
            .combinedClickable(onClick = { actions.onPlay(m.channel, ev.title) }, onLongClick = { actions.onOpenGame(ev) }, onClickLabel = "Watch")
            .padding(NuvioTokens.Space.s12),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10)) {
            ChannelLogo(m.channel, size = 36.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(m.channel.name, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val p = m.program
                Text(
                    if (p != null) sportsClock(p.startMs) + " – " + sportsClock(p.stopMs) else if (item.channels > 1) "+${item.channels - 1} more channel${if (item.channels == 2) "" else "s"}" else league?.name ?: "",
                    style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            SportsBadge("LIVE", SportsLiveRed)
        }
        if (sides.size == 2) {
            sides.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
                    MatchupLogo(s, 20.dp, sport(ev), !hideScores && ev.state == GameState.LIVE)
                    Text(s.rankedName, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (!hideScores && sport(ev) == Sport.AMERICAN_FOOTBALL) s.record?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, maxLines = 1)
                    }
                    if (showScore) Text(s.score ?: "–", style = MaterialTheme.typography.bodyLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.Black)
                }
            }
        } else {
            Text(eventHeadline(ev, league), style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        NuvioProgressBar(progress = progressOf(ev, now), height = NuvioTokens.Space.s3)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4)) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = tokens.colors.accent, modifier = Modifier.size(14.dp))
            Text("Watch", style = MaterialTheme.typography.labelMedium, color = tokens.colors.accent, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(if (showScore) cornerStatus(ev, use24h, hideScores) else "On now", style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, maxLines = 1)
        }
    }
}

// ------------------------------------------------------------------ league groups

/**
 * A league's folding header on All Events: its logo and name, how many are live, how many of the
 * day's games are on your channels (the blue number) and how many there are in all. Tap opens or
 * closes it; the arrow on the right opens the league's own page (table, all its games).
 */
@Composable
internal fun LeagueGroupHeader(
    league: League?,
    name: String,
    live: Int,
    onChannels: Int?,
    total: Int,
    open: Boolean,
    onToggle: () -> Unit,
    onOpenLeague: (() -> Unit)?,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClickLabel = if (open) "Close" else "Open", onClick = onToggle)
            .padding(start = NuvioTokens.Space.s12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10),
    ) {
        Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = if (open) tokens.colors.textPrimary else tokens.colors.textMuted, modifier = Modifier.size(20.dp).rotate(if (open) 0f else -90f))
        LeagueMark(league, 26.dp)
        Text(name, style = MaterialTheme.typography.titleSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (live > 0) SportsBadge("$live live", SportsLiveRed)
        if (onChannels != null && onChannels > 0) ChannelsBadge(onChannels)
        Text(total.toString(), style = MaterialTheme.typography.labelMedium, color = tokens.colors.textMuted, textAlign = TextAlign.End, modifier = Modifier.width(22.dp))
        if (onOpenLeague != null) {
            IconButton(onClick = onOpenLeague) { Icon(Icons.Filled.ChevronRight, contentDescription = "Open $name", tint = tokens.colors.textMuted) }
        } else Spacer(Modifier.width(NuvioTokens.Space.s12))
    }
}

// ------------------------------------------------------------------ league row

/** The league row under the sport and day rows (a sport with more than one league picked): All leagues, then each league. */
@Composable
internal fun LeaguePickRow(leagues: List<League>, picked: String?, onPick: (String?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = NuvioTokens.Space.s12),
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
    ) {
        item(key = "all") { LeaguePill(null, "All leagues", picked == null) { onPick(null) } }
        items(leagues, key = { it.id }) { l -> LeaguePill(l, l.name, picked == l.id) { onPick(l.id) } }
    }
}

@Composable
private fun LeaguePill(league: League?, text: String, selected: Boolean, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(if (selected) tokens.colors.accent.copy(alpha = 0.2f) else Color.Transparent)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) tokens.colors.accent else tokens.colors.borderDefault, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = NuvioTokens.Space.s12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
    ) {
        if (league != null) LeagueMark(league, 18.dp)
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) tokens.colors.textPrimary else tokens.colors.textSecondary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
    }
}

/** "No Serie A games today" and what's shown instead (its next match day), or that there's none this week. */
@Composable
internal fun NextMatchDayNote(title: String, detail: String?) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier.fillMaxWidth().clip(tokens.shapes.card).background(tokens.colors.surface).padding(NuvioTokens.Space.s12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = tokens.colors.accent, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted) }
        }
    }
}

// ------------------------------------------------------------------ small table

/**
 * A picked league's table in short: the top four and the teams you follow (in place order), the
 * last few columns (points tables: P, GD, PTS). "Full table" opens the league's page. Nothing
 * when the league has no table, while it loads, or with Hide Scores on.
 */
@Composable
internal fun LeagueTablePreview(league: League, follows: SportsFollows, hideScores: Boolean, onFullTable: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val repo = SportsRepository
    val mayHave = remember(league.id) { repo.mayHaveTable(league) }
    if (!mayHave || hideScores) return
    val tables by repo.standings.collectAsStateWithLifecycle()
    LaunchedEffect(league.id) { runCatching { repo.standings(league.id) } }
    val table = tables[league.id] ?: return
    val rows = remember(table, follows) { previewRows(table, follows) }
    if (rows.isEmpty()) return
    val columns = remember(table) { previewColumns(table) }
    Column(
        modifier = Modifier.fillMaxWidth().clip(tokens.shapes.card).background(tokens.colors.surface).padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s10),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${league.name} table", style = MaterialTheme.typography.titleSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(
                modifier = Modifier.heightIn(min = 40.dp).clip(CircleShape).clickable(onClick = onFullTable).padding(horizontal = NuvioTokens.Space.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Full table", style = MaterialTheme.typography.labelLarge, color = tokens.colors.accent, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = tokens.colors.accent, modifier = Modifier.size(16.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 22.dp)) {
            Text("#", style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, modifier = Modifier.width(26.dp))
            Text("Team", style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, modifier = Modifier.weight(1f))
            columns.forEach { c -> Text(c.label, style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, textAlign = TextAlign.End, modifier = Modifier.width(38.dp)) }
        }
        var last = 0
        rows.forEach { r ->
            if (last > 0 && r.pos > last + 1) Text("···", style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, modifier = Modifier.padding(start = 26.dp))
            last = r.pos
            val mine = follows.followsTeam(league.id, r.id)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp).clip(RoundedCornerShape(NuvioTokens.Radius.sm))
                    .background(if (mine) tokens.colors.background.copy(alpha = 0.5f) else Color.Transparent),
            ) {
                Text(r.pos.toString(), style = MaterialTheme.typography.labelMedium, color = tokens.colors.textMuted, modifier = Modifier.width(26.dp).padding(start = NuvioTokens.Space.s4))
                Box(Modifier.size(20.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
                    r.logo?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxSize().padding(2.dp), contentScale = ContentScale.Fit) }
                }
                Spacer(Modifier.width(NuvioTokens.Space.s8))
                Text(r.shortName.ifBlank { r.name }, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textPrimary, fontWeight = if (mine) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (mine) Icon(Icons.Filled.Star, contentDescription = "You follow this team", tint = Color(0xFFF5C400), modifier = Modifier.padding(start = NuvioTokens.Space.s4).size(12.dp))
                Spacer(Modifier.weight(1f))
                columns.forEachIndexed { i, c ->
                    Text(r.values[c.key] ?: "–", style = MaterialTheme.typography.bodySmall, color = tokens.colors.textPrimary, fontWeight = if (i == columns.lastIndex) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.End, modifier = Modifier.width(38.dp))
                }
            }
        }
        Text("Top 4 plus the teams you follow", style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, modifier = Modifier.padding(top = NuvioTokens.Space.s4))
    }
}

/** The whole league in one list (a merged view when it's split into divisions); none for one-table-per-conference leagues. */
private fun previewRows(table: Standings, follows: SportsFollows): List<StandingsRow> {
    if (table.groupTabs) return emptyList()
    val all = if (table.groups.size == 1) table.groups.first().rows
        else table.view(StandingsView.LEAGUE, null).firstOrNull()?.rows.orEmpty()
    val top = all.sortedBy { it.pos }.take(4)
    val mine = all.filter { follows.followsTeam(table.leagueId, it.id) }
    return (top + mine).distinctBy { it.id }.sortedBy { it.pos }
}

/** The last three columns, which hold games played, the difference and the points (or W, L, PCT). */
private fun previewColumns(table: Standings) = table.columns.takeLast(3)

// ------------------------------------------------------------------ highlights

/**
 * The Highlights button on a finished game: looks for its videos (your sports add-ons and the
 * league's own), plays the best highlight (a recap or highlights, else whatever is first: a full
 * replay or a condensed game), and opens the game's page when there's nothing yet.
 */
internal fun playGameHighlights(ev: SportsEvent, actions: SportsActions, scope: CoroutineScope) {
    val repo = SportsRepository
    scope.launch {
        NuvioToastController.show("Finding highlights…")
        repo.openGame(ev)
        val found = try {
            withTimeoutOrNull(15_000L) {
                combine(repo.vodMatches, repo.videoMatches) { addon, official -> addon[ev.id] to official[ev.id] }
                    .first { (addon, official) -> addon != null && official != null }
            }
        } finally {
            repo.closeGame(ev.id)
        }
        val addon = found?.first ?: repo.vodMatches.value[ev.id].orEmpty()
        val official = found?.second ?: repo.videoMatches.value[ev.id].orEmpty()
        val settings = repo.settings.value
        val list = SportsVideos.merge(addon, official, settings.showHighlights, settings.showCondensed)
        val pick = list.firstOrNull { it.how.contains("Highlights", true) || it.how.startsWith("Recap", true) } ?: list.firstOrNull()
        if (pick == null) {
            NuvioToastController.show("No highlights for this game yet")
            actions.onOpenGame(ev)
            return@launch
        }
        val channel = runCatching { repo.playableVideo(pick.channel) }.getOrNull()
        if (channel != null) actions.onPlay(channel, ev.title)
        else {
            NuvioToastController.show("That video can't be played right now")
            actions.onOpenGame(ev)
        }
    }
}

package com.nuvio.app.features.livetv.ui.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SportsBaseball
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material.icons.filled.SportsFootball
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioProgressBar
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.sports.EventSource
import com.nuvio.app.features.livetv.sports.GameState
import com.nuvio.app.features.livetv.sports.League
import com.nuvio.app.features.livetv.sports.LeagueKind
import com.nuvio.app.features.livetv.sports.Side
import com.nuvio.app.features.livetv.sports.Sport
import com.nuvio.app.features.livetv.sports.SportsCatalog
import com.nuvio.app.features.livetv.sports.SportsDates
import com.nuvio.app.features.livetv.sports.SportsEvent
import com.nuvio.app.features.livetv.sports.SportsFeeds
import com.nuvio.app.features.livetv.sports.SportsFollows
import com.nuvio.app.features.livetv.sports.SportsRecordings
import com.nuvio.app.features.livetv.sports.TeamInfo
import com.nuvio.app.features.livetv.sports.clockText
import com.nuvio.app.features.livetv.sports.wrestlingHeadline
import com.nuvio.app.features.livetv.ui.formatClock

// ------------------------------------------------------------------ colours

/** A team's color that reads on a dark screen: the alternate color when the main one is near white. */
internal fun teamColor(t: TeamInfo?): Color {
    // The same colours for the same team wherever it's shown (see TeamColors).
    val known = t?.let { com.nuvio.app.features.livetv.sports.TeamColors.paint(it) }
    val main = known?.color?.let { Color(it) }
    val alt = known?.altColor?.let { Color(it) }
    val pick = when {
        main == null -> alt
        main.luminance() > 0.62f && alt != null && alt.luminance() < 0.62f -> alt
        else -> main
    }
    return pick ?: Color(0xFF3A4150)
}

/** A team colour made dark enough for white text on top of it (the TV app's rule). */
internal fun bandColor(c: Color): Color =
    lerp(c, Color(0xFF0B0E14), if (c.luminance() > 0.5f) 0.5f else if (c.luminance() > 0.25f) 0.25f else 0.08f)

/**
 * The colour a team's band is made from: [teamColor], except a team whose main colour is nearly
 * black (Bruins, Golden Knights) takes its second colour when that one is clearly lighter, so the
 * band shows against the dark card.
 */
internal fun bandBase(t: TeamInfo?): Color {
    val main = teamColor(t)
    val alt = t?.altColor?.let { Color(it) }
    return if (main.luminance() < 0.04f && alt != null && alt.luminance() > main.luminance() + 0.05f) alt else main
}

/** The TV app's team-colour fade: the team's colour, darkened, strong on the left and gone by the right. */
internal fun teamBandBrush(team: TeamInfo?, strength: Float = 1f): Brush {
    val c = bandColor(bandBase(team))
    return Brush.horizontalGradient(
        0f to c.copy(alpha = 0.92f * strength),
        0.7f to c.copy(alpha = 0.28f * strength),
        1f to c.copy(alpha = 0.05f * strength),
    )
}

internal fun leagueColor(l: League?): Color = Color(l?.color ?: 0xFF2A2F3A)

internal val SportsLiveRed = Color(0xFFE53935)

// ------------------------------------------------------------------ words for times and states

internal fun startLabel(ev: SportsEvent, use24h: Boolean): String = when {
    ev.hasTime -> clockText(ev.startMs, use24h)
    ev.endMs != null && ev.endMs - ev.startMs > 20 * 3_600_000L -> "Starts"
    else -> "All day"
}

/** What the corner of a card says: the game clock, "Started 1:00", "Final", or the start time. */
internal fun cornerStatus(ev: SportsEvent, use24h: Boolean, hideScores: Boolean): String = when (ev.state) {
    GameState.LIVE -> if (!hideScores && ev.status != null && ev.source != EventSource.GUIDE && ev.hasScore) ev.status
    else "Started " + clockText(ev.startMs, use24h)
    GameState.POST -> if (ev.source == EventSource.GUIDE) "Ended" else (ev.status ?: "Final")
    GameState.OFF -> ev.status ?: "Postponed"
    GameState.PRE -> startLabel(ev, use24h)
}

/** Time bar: how far through, from the game clock when known, otherwise from the start time and usual length. */
internal fun progressOf(ev: SportsEvent, now: Long): Float {
    if (ev.state == GameState.POST) return 1f
    if (ev.state != GameState.LIVE) return 0f
    ev.progress?.let { return it }
    val league = SportsCatalog.league(ev.leagueId)
    val end = ev.endMs ?: (ev.startMs + (league?.minutes ?: 120) * 60_000L)
    return ((now - ev.startMs).toFloat() / (end - ev.startMs).coerceAtLeast(1L)).coerceIn(0.02f, 0.98f)
}

/** A followed team plays, a league you follow, a name you follow, or a reminder you set. */
internal fun isMine(ev: SportsEvent, f: SportsFollows): Boolean =
    ev.leagueId in f.leagues || ev.word != null ||
        (ev.leagueId in f.pins && SportsCatalog.league(ev.leagueId)?.kind == LeagueKind.EVENT) ||
        ev.sides.any { f.followsTeam(ev.leagueId, it.team.id) } ||
        f.reminders.any { it.eventId == ev.id }

/** "Today", "Tomorrow", "Yesterday", or "Sun 2 Nov". */
internal fun dayWord(ms: Long, now: Long): String {
    val today = SportsDates.dayOf(now)
    val day = SportsDates.dayOf(ms)
    val diff = ((day - today) / 86_400_000L).toInt()
    return when (diff) {
        0 -> "Today"
        1 -> "Tomorrow"
        -1 -> "Yesterday"
        else -> SportsDates.dayLabel(ms)
    }
}

/** "in 1 day 16 h", "in 5 h 20", "in 12 min". */
internal fun countdownLabel(startMs: Long, now: Long): String {
    val m = ((startMs - now) / 60_000L).toInt()
    return when {
        m <= 0 -> "Starting"
        m < 60 -> "in $m min"
        m < 24 * 60 -> "in ${m / 60} h ${m % 60}"
        else -> { val d = m / (24 * 60); val h = (m % (24 * 60)) / 60; "in $d day${if (d == 1) "" else "s"}" + (if (h > 0) " $h h" else "") }
    }
}

/** When a game is, in a row card: "Today 7:30 PM", "Final", "2nd · 4:12". */
internal fun whenLabel(ev: SportsEvent, now: Long, use24h: Boolean, hideScores: Boolean): String = when (ev.state) {
    GameState.LIVE -> cornerStatus(ev, use24h, hideScores)
    GameState.POST -> (if (ev.source == EventSource.GUIDE) "Ended" else (ev.status ?: "Final")) + " · " + dayWord(ev.startMs, now)
    GameState.OFF -> ev.status ?: "Postponed"
    GameState.PRE -> dayWord(ev.startMs, now) + (if (ev.hasTime) " " + clockText(ev.startMs, use24h) else "")
}

/** The one-line headline of an event: a wrestling show without its promotion, otherwise the title. */
internal fun eventHeadline(ev: SportsEvent, league: League?): String =
    if (league?.sport == Sport.WRESTLING) wrestlingHeadline(ev.title) else ev.title

@Composable
internal fun use24h(): Boolean {
    val settings by LiveTvPreferences.settings.collectAsStateWithLifecycle()
    return settings.use24HourClock
}

// ------------------------------------------------------------------ small pieces

@Composable
internal fun TeamLogo(team: TeamInfo?, size: Dp) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        val logo = team?.logo?.takeIf { it.isNotBlank() }
        if (logo != null) {
            AsyncImage(model = logo, contentDescription = null, modifier = Modifier.fillMaxSize().padding(size / 8), contentScale = ContentScale.Fit)
        } else {
            Text(
                text = team?.abbr?.take(3)?.ifBlank { null } ?: team?.name?.take(2)?.uppercase() ?: "?",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colors.textInverse,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** A league's logo (from ESPN or the nuvio-data files), or its short name on its colour. */
@Composable
internal fun LeagueMark(league: League?, size: Dp) {
    val logo = league?.let { SportsFeeds.leagueLogos[it.id] }
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(NuvioTokens.Radius.sm)).background(leagueColor(league)),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            AsyncImage(model = logo, contentDescription = null, modifier = Modifier.fillMaxSize().padding(size / 8), contentScale = ContentScale.Fit)
        } else {
            Text(
                text = league?.short?.take(5) ?: "?",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun SportsBadge(text: String, background: Color, color: Color = Color.White, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.Black,
        maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(NuvioTokens.Radius.xs)).background(background).padding(horizontal = NuvioTokens.Space.s6, vertical = NuvioTokens.Space.s2),
    )
}

/** "LIVE", "FINAL", "7:30 PM" in the corner of a card. */
@Composable
internal fun StateBadge(ev: SportsEvent, use24h: Boolean) {
    val tokens = MaterialTheme.nuvio
    when (ev.state) {
        GameState.LIVE -> SportsBadge("LIVE", SportsLiveRed)
        GameState.POST -> SportsBadge(if (ev.source == EventSource.GUIDE) "ENDED" else "FINAL", tokens.colors.surfaceElevated, tokens.colors.textSecondary)
        GameState.OFF -> SportsBadge("OFF", tokens.colors.surfaceElevated, tokens.colors.textMuted)
        GameState.PRE -> SportsBadge(startLabel(ev, use24h), tokens.colors.surfaceElevated, tokens.colors.textPrimary)
    }
}

/**
 * A card's soft wash in the two teams' colours: the first team's on the left fading into the
 * second's, gone by the right edge (drawn over the card's own colour, so text stays readable).
 */
internal fun gameWash(sides: List<Side>, strength: Float = 1f): Brush? {
    if (sides.size != 2) return null
    val a = bandColor(bandBase(sides[0].team))
    val b = bandColor(bandBase(sides[1].team))
    return Brush.horizontalGradient(
        0f to a.copy(alpha = 0.50f * strength),
        0.55f to b.copy(alpha = 0.30f * strength),
        1f to Color.Transparent,
    )
}

/** "3" with a little TV: how many of your channels show the game (the blue number on league rows too). */
@Composable
internal fun ChannelsBadge(count: Int, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = modifier.clip(CircleShape).background(tokens.colors.accent.copy(alpha = 0.18f)).padding(horizontal = NuvioTokens.Space.s8, vertical = NuvioTokens.Space.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4),
    ) {
        Icon(Icons.Filled.Tv, contentDescription = null, tint = tokens.colors.accent, modifier = Modifier.size(12.dp))
        Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = tokens.colors.accent, fontWeight = FontWeight.Bold)
    }
}

/** The reminder bell on a card (games that haven't started): filled when a reminder is set. */
@Composable
internal fun ReminderBell(ev: SportsEvent, reminded: Boolean, size: Dp = 40.dp) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(NuvioTokens.Radius.md))
            .background(if (reminded) tokens.colors.accent.copy(alpha = 0.22f) else tokens.colors.background.copy(alpha = 0.6f))
            .clickable(onClickLabel = if (reminded) "Cancel reminder" else "Remind me") { com.nuvio.app.features.livetv.sports.SportsRepository.toggleReminder(ev) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (reminded) Icons.Filled.Notifications else Icons.Outlined.NotificationsNone,
            contentDescription = if (reminded) "Reminder set" else "Remind me",
            tint = if (reminded) tokens.colors.accent else tokens.colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** "▶ Highlights" on a finished game's card: plays its best video (see [playGameHighlights]). */
@Composable
internal fun HighlightsButton(onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier.heightIn(min = 32.dp).clip(CircleShape).background(tokens.colors.surfaceElevated).clickable(onClick = onClick)
            .padding(horizontal = NuvioTokens.Space.s10),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4),
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = tokens.colors.textPrimary, modifier = Modifier.size(14.dp))
        Text("Highlights", style = MaterialTheme.typography.labelMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold)
    }
}

/** The two scores stacked in a small dark box at a card's right; the loser's dimmed once it's over. */
@Composable
private fun ScoreBox(sides: List<Side>, loser: Side?, sport: Sport, showOffense: Boolean) {
    val tokens = MaterialTheme.nuvio
    val showMarker = showOffense && sides.any { it.onOffense }
    val iconSize = 14.dp
    val rowSpacing = Arrangement.spacedBy(NuvioTokens.Space.s4)
    Box {
        Column(
            modifier = Modifier.padding(start = if (showMarker) iconSize + NuvioTokens.Space.s4 else 0.dp)
                .testTag("sports_score_box").widthIn(min = 44.dp).clip(RoundedCornerShape(NuvioTokens.Radius.md))
                .background(tokens.colors.background.copy(alpha = 0.7f))
                .padding(horizontal = NuvioTokens.Space.s8, vertical = NuvioTokens.Space.s6),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = rowSpacing,
        ) {
            sides.forEach { s ->
                Text(
                    s.score ?: "–",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (s === loser) tokens.colors.textMuted else tokens.colors.textPrimary,
                    fontWeight = if (s === loser) FontWeight.Medium else FontWeight.Black,
                )
            }
        }
        if (showMarker) {
            // Match the score rows' height, padding and gap so ownership lines up after a turnover.
            Column(Modifier.matchParentSize().padding(vertical = NuvioTokens.Space.s6), verticalArrangement = rowSpacing) {
                sides.forEach { s ->
                    Box(Modifier.width(iconSize).weight(1f), contentAlignment = Alignment.Center) {
                        OffenseMarker(s, sport, showOffense, size = iconSize)
                    }
                }
            }
        }
    }
}

@Composable
internal fun OffenseMarker(side: Side, sport: Sport, showOffense: Boolean, size: Dp = 14.dp) {
    if (!showOffense || !side.onOffense) return
    val marker = when (sport) {
        Sport.AMERICAN_FOOTBALL -> Icons.Filled.SportsFootball
        Sport.BASEBALL -> Icons.Filled.SportsBaseball
        Sport.CRICKET -> Icons.Filled.SportsCricket
        else -> return
    }
    Icon(
        marker, contentDescription = if (sport == Sport.AMERICAN_FOOTBALL) "${side.team.name} has possession" else "${side.team.name} batting",
        modifier = Modifier.size(size).background(Color.Black, CircleShape).padding(1.dp), tint = Color.White,
    )
}

@Composable
private fun TeamLine(side: Side, sport: Sport, showOffense: Boolean, followed: Boolean, dim: Boolean, record: Boolean, logo: Dp = 24.dp, score: String? = null) {
    val tokens = MaterialTheme.nuvio
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
        modifier = Modifier.fillMaxWidth().heightIn(min = logo + 4.dp),
    ) {
        TeamLogo(side.team, logo)
        Text(
            text = side.rankedName,
            style = MaterialTheme.typography.bodyMedium,
            color = if (dim) tokens.colors.textMuted else tokens.colors.textPrimary,
            fontWeight = if (dim) FontWeight.Medium else if (side.winner) FontWeight.Black else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = score != null || (showOffense && side.onOffense)),
        )
        if (followed) Icon(Icons.Filled.Star, contentDescription = "You follow this team", tint = Color(0xFFF5C400), modifier = Modifier.size(NuvioTokens.Icon.xs))
        if (record) side.record?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, maxLines = 1)
        }
        OffenseMarker(side, sport, showOffense)
        if (score != null) {
            Text(score, style = MaterialTheme.typography.bodyLarge, color = if (dim) tokens.colors.textMuted else tokens.colors.textPrimary, fontWeight = if (dim) FontWeight.Medium else FontWeight.Black)
        }
    }
}

/** The line at the top of a card: LIVE and the clock, the start time and how long until, or the result. */
@Composable
private fun StatusLine(ev: SportsEvent, now: Long, use24h: Boolean, hideScores: Boolean, showScore: Boolean, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val tokens = MaterialTheme.nuvio
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6)) {
        when (ev.state) {
            GameState.LIVE -> {
                SportsBadge("LIVE", SportsLiveRed)
                Text(if (showScore) cornerStatus(ev, use24h, hideScores) else "On now", style = MaterialTheme.typography.labelMedium, color = SportsLiveRed, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
            GameState.PRE -> {
                Icon(Icons.Outlined.Schedule, contentDescription = null, tint = tokens.colors.accent, modifier = Modifier.size(13.dp))
                Text(startLabel(ev, use24h), style = MaterialTheme.typography.labelMedium, color = tokens.colors.accent, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    "· " + (if (ev.hasTime) countdownLabel(ev.startMs, now) else ev.note ?: "Coming up"),
                    style = MaterialTheme.typography.labelMedium, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
            }
            GameState.POST -> Text(cornerStatus(ev, use24h, hideScores), style = MaterialTheme.typography.labelMedium, color = tokens.colors.textMuted, fontWeight = FontWeight.SemiBold, maxLines = 1)
            GameState.OFF -> Text(cornerStatus(ev, use24h, hideScores), style = MaterialTheme.typography.labelMedium, color = tokens.colors.textMuted, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/**
 * A game (two teams) or an event (a race, a fight card) as a card in the teams' colours: the state
 * and time at the top, the teams with the score box on the right, how many of your channels show
 * it, and a reminder bell before it starts (or Highlights once it's over). Tap opens the game.
 * [showLeague]: off inside a league's own group.
 */
@Composable
internal fun GameCard(
    ev: SportsEvent,
    follows: SportsFollows,
    now: Long,
    use24h: Boolean,
    hideScores: Boolean,
    /** How many of your channels show it (null: not looked yet). */
    channels: Int?,
    modifier: Modifier = Modifier,
    showLeague: Boolean = true,
    onHighlights: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val league = SportsCatalog.league(ev.leagueId)
    val sport = league?.sport ?: Sport.OTHER
    val sides = ev.ordered(sport)
    val showScore = ev.hasScore && !hideScores
    val recording = ev.state != GameState.POST && ev.hasTime && SportsRecordings.forEvent(ev) != null
    val reminded = follows.reminders.any { it.eventId == ev.id }
    val wash = gameWash(sides)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(tokens.shapes.card)
            .background(tokens.colors.surface)
            .let { if (wash != null) it.background(wash) else it }
            .clickable(onClick = onClick)
            .padding(start = NuvioTokens.Space.s12, end = NuvioTokens.Space.s10, top = NuvioTokens.Space.s10, bottom = NuvioTokens.Space.s10),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
    ) {
        StatusLine(ev, now, use24h, hideScores, showScore) {
            if (recording) Icon(Icons.Filled.FiberManualRecord, contentDescription = "Recording set", tint = SportsLiveRed, modifier = Modifier.size(NuvioTokens.Icon.xs))
            if (showLeague) {
                LeagueMark(league, 16.dp)
                Text(league?.short ?: "", style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, maxLines = 1)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2)) {
                if (sides.size == 2) {
                    val loser = if (ev.state == GameState.POST && sides.any { it.winner }) sides.firstOrNull { !it.winner } else null
                    sides.forEach { s ->
                        TeamLine(s, sport, !showScore && !hideScores && ev.state == GameState.LIVE, follows.followsTeam(ev.leagueId, s.team.id),
                            dim = showScore && loser === s, record = if (sport == Sport.AMERICAN_FOOTBALL) !hideScores else ev.state == GameState.PRE && !showScore)
                    }
                } else {
                    Text(eventHeadline(ev, league), style = MaterialTheme.typography.bodyLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    ev.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    if (ev.standings.isNotEmpty() && !hideScores) {
                        Text(
                            text = ev.standings.take(3).joinToString("   ") { listOf(it.pos, it.name).filter { p -> p.isNotBlank() }.joinToString(" ") },
                            style = MaterialTheme.typography.labelSmall, color = tokens.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6)) {
                when {
                    ev.state == GameState.POST || channels == null -> Unit
                    channels == 0 -> Text("Not on your\nchannels", style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, textAlign = TextAlign.End)
                    else -> ChannelsBadge(channels)
                }
                if (ev.state == GameState.PRE && ev.hasTime) ReminderBell(ev, reminded)
                if (ev.state == GameState.POST && onHighlights != null) HighlightsButton(onHighlights)
            }
            if (sides.size == 2 && showScore) {
                val loser = if (ev.state == GameState.POST && sides.any { it.winner }) sides.firstOrNull { !it.winner } else null
                ScoreBox(sides, loser, sport, ev.state == GameState.LIVE)
            }
        }
        ev.situation?.takeIf { !hideScores && ev.state == GameState.LIVE }?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = tokens.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (ev.state == GameState.LIVE) NuvioProgressBar(progress = progressOf(ev, now), height = NuvioTokens.Space.s3)
    }
}

/**
 * A card for a horizontal row (My Sports), in the same style as [GameCard]: league and when at the
 * top, the two teams with their scores, then your channels, the bell or Highlights.
 */
@Composable
internal fun RowCard(
    ev: SportsEvent,
    follows: SportsFollows,
    now: Long,
    use24h: Boolean,
    hideScores: Boolean,
    modifier: Modifier = Modifier,
    /** How many of your channels show it (null: not looked yet). */
    channels: Int? = null,
    onHighlights: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val league = SportsCatalog.league(ev.leagueId)
    val sport = league?.sport ?: Sport.OTHER
    val sides = ev.ordered(sport)
    val showScore = ev.hasScore && !hideScores
    val reminded = follows.reminders.any { it.eventId == ev.id }
    val wash = gameWash(sides, strength = 1.2f)
    Column(
        modifier = modifier
            .width(236.dp)
            .heightIn(min = 148.dp)
            .clip(tokens.shapes.card)
            .background(tokens.colors.surface)
            .let { if (wash != null) it.background(wash) else it }
            .clickable(onClick = onClick)
            .padding(NuvioTokens.Space.s10),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6)) {
            LeagueMark(league, 16.dp)
            Text(league?.short ?: "", style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (ev.state == GameState.LIVE) SportsBadge("LIVE", SportsLiveRed)
            Text(
                text = if (ev.state == GameState.LIVE) (if (showScore) cornerStatus(ev, use24h, hideScores) else "On now") else whenLabel(ev, now, use24h, hideScores),
                style = MaterialTheme.typography.labelSmall,
                color = when (ev.state) { GameState.LIVE -> SportsLiveRed; GameState.PRE -> tokens.colors.accent; else -> tokens.colors.textMuted },
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        if (sides.size == 2) {
            val loser = if (ev.state == GameState.POST && sides.any { it.winner }) sides.firstOrNull { !it.winner } else null
            sides.forEach { s ->
                TeamLine(s, sport, !hideScores && ev.state == GameState.LIVE, follows.followsTeam(ev.leagueId, s.team.id),
                    dim = showScore && loser === s, record = sport == Sport.AMERICAN_FOOTBALL && !hideScores,
                    logo = 22.dp, score = if (showScore) s.score ?: "–" else null)
            }
        } else {
            Text(eventHeadline(ev, league), style = MaterialTheme.typography.bodySmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(ev.subtitle ?: ev.note ?: "", style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.weight(1f, fill = false))
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8, Alignment.End)) {
            when {
                ev.state == GameState.POST || channels == null -> Unit
                channels == 0 -> Text("Not on your channels", style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, modifier = Modifier.weight(1f))
                else -> ChannelsBadge(channels)
            }
            if (ev.state == GameState.PRE && ev.hasTime) ReminderBell(ev, reminded, size = 36.dp)
            if (ev.state == GameState.POST && onHighlights != null) HighlightsButton(onHighlights)
        }
    }
}

/** "7:30 PM" following the Live TV 24-hour setting (composable; see [clockText] off the main thread). */
@Composable
internal fun sportsClock(ms: Long): String = formatClock(ms)

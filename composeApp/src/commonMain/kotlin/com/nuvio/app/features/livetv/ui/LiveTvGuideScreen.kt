package com.nuvio.app.features.livetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.features.livetv.data.LiveTvRepository
import com.nuvio.app.features.livetv.model.ChannelGroup
import com.nuvio.app.features.livetv.model.EpgProgram
import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.livetv.model.LiveTvClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_close
import nuvio.composeapp.generated.resources.action_play
import nuvio.composeapp.generated.resources.livetv_guide
import nuvio.composeapp.generated.resources.livetv_guide_all
import nuvio.composeapp.generated.resources.livetv_guide_no_info
import nuvio.composeapp.generated.resources.livetv_guide_now
import org.jetbrains.compose.resources.stringResource

private val ChannelColumnWidth = 92.dp
private val RowHeight = 64.dp
private val HourWidth = 240.dp
private const val HOUR_MS = 60L * 60L * 1000L
private const val SLOT_MS = 30L * 60L * 1000L

/**
 * The TV guide: a time grid, channels down the side, hours across. Tap a show for its details,
 * a channel's logo to play it; "Now" jumps the grid back to the current time.
 */
@Composable
fun LiveTvGuideScreen(
    onBack: () -> Unit,
    onPlayChannel: (LiveChannel, nowTitle: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val state by remember {
        LiveTvRepository.ensureLoaded()
        LiveTvViewState.list
    }.collectAsStateWithLifecycle()
    val generation by LiveTvRepository.guideGeneration.collectAsStateWithLifecycle()
    val now by produceState(LiveTvClock.nowMs()) {
        while (true) { value = LiveTvClock.nowMs(); delay(30_000) }
    }
    val sections = state.sections.filter { it.channels.isNotEmpty() }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    val selected = sections.firstOrNull { it.group.id == selectedGroupId } ?: sections.firstOrNull()
    val channels = if (selectedGroupId == ChannelGroup.ALL) state.visible else selected?.channels.orEmpty()

    // The grid shows from the start of the half hour before now, 24 hours ahead.
    val gridStart = remember(now / HOUR_MS) { (now / SLOT_MS) * SLOT_MS - SLOT_MS }
    val gridEnd = gridStart + 24 * HOUR_MS
    val listState = rememberLazyListState()
    val hScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var programs by remember { mutableStateOf<Map<String, List<EpgProgram>>>(emptyMap()) }
    LaunchedEffect(channels, generation, gridStart) {
        val keys = channels.map { it.key }
        // The visible group in chunks, so a big group fills in as it's read.
        val out = HashMap<String, List<EpgProgram>>()
        keys.chunked(150).forEach { part ->
            out += LiveTvRepository.programsIn(part, gridStart, gridEnd)
            programs = HashMap(out)
        }
    }
    var detail by remember { mutableStateOf<Pair<LiveChannel, EpgProgram>?>(null) }

    fun pxPerMs(): Float = with(density) { HourWidth.toPx() } / HOUR_MS
    fun jumpToNow() {
        scope.launch {
            val target = ((now - gridStart - 15 * 60_000L).coerceAtLeast(0) * pxPerMs()).toInt()
            hScroll.animateScrollTo(target)
        }
    }
    LaunchedEffect(Unit) { jumpToNow() }

    Column(modifier = modifier.fillMaxSize().background(tokens.colors.background).statusBarsPadding()) {
        NuvioScreenHeader(
            title = stringResource(Res.string.livetv_guide),
            includeStatusBarPadding = false,
            onBack = onBack,
            modifier = Modifier.padding(horizontal = NuvioTokens.Space.s8),
            actions = {
                IconButton(onClick = ::jumpToNow) {
                    Icon(Icons.Filled.Schedule, contentDescription = stringResource(Res.string.livetv_guide_now), tint = tokens.colors.accent)
                }
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s6),
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
        ) {
            sections.forEach { s ->
                Chip(label = s.group.title, selected = selected?.group?.id == s.group.id && selectedGroupId != ChannelGroup.ALL, onClick = { selectedGroupId = s.group.id })
            }
            Chip(label = stringResource(Res.string.livetv_guide_all), selected = selectedGroupId == ChannelGroup.ALL, onClick = { selectedGroupId = ChannelGroup.ALL })
        }
        // Time ruler.
        Row(modifier = Modifier.fillMaxWidth().height(28.dp)) {
            Spacer(Modifier.width(ChannelColumnWidth))
            Box(modifier = Modifier.weight(1f).fillMaxHeight().horizontalScroll(hScroll, enabled = false)) {
                Row {
                    var t = gridStart
                    while (t < gridEnd) {
                        Text(
                            text = formatClock(t),
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.colors.textMuted,
                            modifier = Modifier.width(HourWidth / 2).padding(start = NuvioTokens.Space.s6, top = NuvioTokens.Space.s6),
                        )
                        t += SLOT_MS
                    }
                }
                NowLine(gridStart, now, ::pxPerMs, height = 28.dp)
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = nuvioSafeBottomPadding(NuvioTokens.Space.s16)),
        ) {
            items(channels, key = { it.key }) { channel ->
                GuideRow(
                    channel = channel,
                    programs = programs[channel.key].orEmpty(),
                    gridStart = gridStart,
                    gridEnd = gridEnd,
                    now = now,
                    hScroll = hScroll,
                    pxPerMs = ::pxPerMs,
                    onPlay = { onPlayChannel(channel, programs[channel.key]?.firstOrNull { it.isLive(now) }?.title) },
                    onProgram = { detail = channel to it },
                )
            }
        }
    }

    detail?.let { (channel, program) ->
        ProgramDetailsDialog(channel, program, now, onDismiss = { detail = null }, onPlay = { detail = null; onPlayChannel(channel, program.title.takeIf { program.isLive(now) }) })
    }
}

@Composable
private fun NowLine(gridStart: Long, now: Long, pxPerMs: () -> Float, height: Dp) {
    val density = LocalDensity.current
    val x = with(density) { ((now - gridStart) * pxPerMs()).toDp() }
    if (x > 0.dp) {
        Box(modifier = Modifier.offset(x = x).width(2.dp).height(height).background(Color.LiveNowLine))
    }
}

@Composable
private fun GuideRow(
    channel: LiveChannel,
    programs: List<EpgProgram>,
    gridStart: Long,
    gridEnd: Long,
    now: Long,
    hScroll: androidx.compose.foundation.ScrollState,
    pxPerMs: () -> Float,
    onPlay: () -> Unit,
    onProgram: (EpgProgram) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val density = LocalDensity.current
    Row(modifier = Modifier.fillMaxWidth().height(RowHeight)) {
        Column(
            modifier = Modifier
                .width(ChannelColumnWidth)
                .fillMaxHeight()
                .background(tokens.colors.background)
                .clickable(onClick = onPlay)
                .padding(horizontal = NuvioTokens.Space.s8, vertical = NuvioTokens.Space.s4),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ChannelLogo(channel, size = 36.dp)
            Text(
                text = channel.name,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight().horizontalScroll(hScroll)) {
            val totalWidth = with(density) { ((gridEnd - gridStart) * pxPerMs()).toDp() }
            Box(modifier = Modifier.width(totalWidth).fillMaxHeight()) {
                if (programs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(NuvioTokens.Space.s2).clip(tokens.shapes.compactCard).background(tokens.colors.surface),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(stringResource(Res.string.livetv_guide_no_info), style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, modifier = Modifier.padding(start = NuvioTokens.Space.s12))
                    }
                }
                programs.forEach { p ->
                    val start = p.startMs.coerceAtLeast(gridStart)
                    val stop = p.stopMs.coerceAtMost(gridEnd)
                    if (stop <= start) return@forEach
                    val x = with(density) { ((start - gridStart) * pxPerMs()).toDp() }
                    val w = with(density) { ((stop - start) * pxPerMs()).toDp() }
                    val live = p.isLive(now)
                    Box(
                        modifier = Modifier
                            .offset(x = x)
                            .width(w)
                            .fillMaxHeight()
                            .padding(NuvioTokens.Space.s2)
                            .clip(tokens.shapes.compactCard)
                            .background(if (live) tokens.colors.accent.copy(alpha = tokens.opacity.selected) else tokens.colors.surface)
                            .clickable { onProgram(p) }
                            .padding(horizontal = NuvioTokens.Space.s8, vertical = NuvioTokens.Space.s6),
                    ) {
                        Column {
                            Text(
                                text = p.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.colors.textPrimary,
                                fontWeight = if (live) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (w > 90.dp) {
                                Text(
                                    text = formatClock(p.startMs) + " – " + formatClock(p.stopMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.colors.textMuted,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                NowLine(gridStart, now, pxPerMs, height = RowHeight)
            }
        }
    }
}

@Composable
private fun ProgramDetailsDialog(channel: LiveChannel, program: EpgProgram, now: Long, onDismiss: () -> Unit, onPlay: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    var full by remember { mutableStateOf(program) }
    LaunchedEffect(program) {
        LiveTvRepository.programDetails(channel.key, program.startMs)?.let { full = it }
    }
    DialogSurface(onDismissRequest = onDismiss, title = full.title) {
        Text(
            text = channel.name + "  ·  " + formatClock(full.startMs) + " – " + formatClock(full.stopMs) + (full.episode?.let { "  ·  $it" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textSecondary,
        )
        full.category?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = tokens.colors.textMuted) }
        full.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textPrimary, maxLines = 12, overflow = TextOverflow.Ellipsis)
        }
        if (full.people.isNotEmpty()) {
            Text(full.people.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        DialogButtons {
            DialogButton(text = stringResource(Res.string.action_close), onClick = onDismiss)
            if (program.isLive(now)) {
                DialogButton(text = stringResource(Res.string.action_play), style = DialogButtonStyle.Primary, onClick = onPlay)
            }
        }
    }
}

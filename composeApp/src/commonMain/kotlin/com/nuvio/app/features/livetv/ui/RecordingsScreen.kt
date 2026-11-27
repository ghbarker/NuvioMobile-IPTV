package com.nuvio.app.features.livetv.ui

import com.nuvio.app.features.livetv.record.NetworkLibrary
import com.nuvio.app.features.livetv.record.NetworkUploads
import com.nuvio.app.features.livetv.record.OfflineRecordingKey
import com.nuvio.app.features.livetv.record.OfflineRecordingState
import com.nuvio.app.features.livetv.record.OfflineRecordings
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import com.nuvio.app.features.tmdb.TmdbMetadataService
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.format.formatLocalDateTime
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioBottomSheetActionRow
import com.nuvio.app.core.ui.NuvioBottomSheetDivider
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.features.livetv.data.CivilTime
import com.nuvio.app.features.livetv.data.TitleMatchFixes
import com.nuvio.app.features.livetv.data.LiveTvTimeZones
import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.livetv.model.LiveTvClock
import com.nuvio.app.features.livetv.model.guideDescriptionText
import com.nuvio.app.features.livetv.record.LiveTvRecorder
import com.nuvio.app.features.livetv.record.LiveTvRecording
import com.nuvio.app.features.livetv.record.LiveTvRecordingScheduler
import com.nuvio.app.features.livetv.record.RecordingNotices
import com.nuvio.app.features.livetv.record.RecordingRules
import com.nuvio.app.features.livetv.record.RecordingSchedule
import com.nuvio.app.features.livetv.record.RecordingStatus
import com.nuvio.app.features.livetv.record.SeriesRule
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import com.nuvio.app.features.livetv.record.RepeatRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.livetv_fix_match
import nuvio.composeapp.generated.resources.livetv_fix_match_rec_scope
import nuvio.composeapp.generated.resources.livetv_rec_pad_after
import nuvio.composeapp.generated.resources.livetv_rec_pad_before
import nuvio.composeapp.generated.resources.livetv_rec_pad_default
import nuvio.composeapp.generated.resources.livetv_rec_pad_hint
import nuvio.composeapp.generated.resources.livetv_rec_pad_line_after
import nuvio.composeapp.generated.resources.livetv_rec_pad_line_before
import nuvio.composeapp.generated.resources.livetv_ui_minutes
import nuvio.composeapp.generated.resources.livetv_ui_none
import org.jetbrains.compose.resources.pluralStringResource
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.action_delete
import nuvio.composeapp.generated.resources.action_play
import nuvio.composeapp.generated.resources.livetv_rec_cancel
import nuvio.composeapp.generated.resources.livetv_rec_catchup_save
import nuvio.composeapp.generated.resources.livetv_rec_empty
import nuvio.composeapp.generated.resources.livetv_rec_keep_all
import nuvio.composeapp.generated.resources.livetv_rec_keep_last
import nuvio.composeapp.generated.resources.livetv_rec_manual_title
import nuvio.composeapp.generated.resources.livetv_rec_record_now
import nuvio.composeapp.generated.resources.livetv_rec_recorded
import nuvio.composeapp.generated.resources.livetv_rec_recording
import nuvio.composeapp.generated.resources.livetv_rec_schedule
import nuvio.composeapp.generated.resources.livetv_rec_scheduled
import nuvio.composeapp.generated.resources.livetv_rec_repeat_stop
import nuvio.composeapp.generated.resources.livetv_rec_repeat_pick
import nuvio.composeapp.generated.resources.livetv_rec_repeat_weekends
import nuvio.composeapp.generated.resources.livetv_rec_repeat_weekdays
import nuvio.composeapp.generated.resources.livetv_rec_repeat_every_day
import nuvio.composeapp.generated.resources.livetv_rec_repeat_once
import nuvio.composeapp.generated.resources.livetv_rec_repeat
import nuvio.composeapp.generated.resources.livetv_rec_series_all
import nuvio.composeapp.generated.resources.livetv_rec_series_new_only
import nuvio.composeapp.generated.resources.livetv_rec_series_stop
import nuvio.composeapp.generated.resources.livetv_rec_start_time_hint
import nuvio.composeapp.generated.resources.livetv_rec_stop
import nuvio.composeapp.generated.resources.livetv_rec_today
import nuvio.composeapp.generated.resources.livetv_rec_tomorrow
import nuvio.composeapp.generated.resources.livetv_recordings
import nuvio.composeapp.generated.resources.livetv_rec_rename
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.runtime.saveable.rememberSaveable
import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.record.RecordedShow
import com.nuvio.app.features.livetv.record.SingleRecording
import com.nuvio.app.features.livetv.record.groupRecorded
import com.nuvio.app.features.livetv.record.recordingEntryKey
import com.nuvio.app.features.livetv.ondemand.OnDemandRepository
import com.nuvio.app.features.livetv.ondemand.VodItem
import com.nuvio.app.features.livetv.ondemand.VodKind
import com.nuvio.app.features.livetv.sports.SportsDates
import nuvio.composeapp.generated.resources.livetv_rec_tab_series
import nuvio.composeapp.generated.resources.livetv_rec_failed_short
import nuvio.composeapp.generated.resources.livetv_rec_none_recorded
import nuvio.composeapp.generated.resources.livetv_rec_none_scheduled
import nuvio.composeapp.generated.resources.livetv_rec_none_series
import org.jetbrains.compose.resources.stringResource
import nuvio.composeapp.generated.resources.livetv_ui_rec_away
import nuvio.composeapp.generated.resources.livetv_ui_rec_away_has
import nuvio.composeapp.generated.resources.livetv_ui_rec_away_none
import nuvio.composeapp.generated.resources.livetv_ui_rec_copying
import nuvio.composeapp.generated.resources.livetv_ui_rec_net_unreachable
import nuvio.composeapp.generated.resources.livetv_ui_rec_network
import nuvio.composeapp.generated.resources.livetv_ui_rec_settings
import nuvio.composeapp.generated.resources.livetv_ui_rec_waiting_net
import nuvio.composeapp.generated.resources.download_failed
import nuvio.composeapp.generated.resources.livetv_rec_offline_away
import nuvio.composeapp.generated.resources.livetv_rec_offline_cancel
import nuvio.composeapp.generated.resources.livetv_rec_offline_delete_network
import nuvio.composeapp.generated.resources.updates_downloading_progress
import nuvio.composeapp.generated.resources.livetv_rec_offline_ready
import nuvio.composeapp.generated.resources.livetv_rec_offline_remove
import nuvio.composeapp.generated.resources.livetv_rec_offline_retry
import nuvio.composeapp.generated.resources.livetv_rec_offline_save
import nuvio.composeapp.generated.resources.livetv_rec_delete_changed
import nuvio.composeapp.generated.resources.livetv_rec_delete_incomplete

/**
 * Recordings: what's recording now above three tabs: Recorded (posters), Scheduled (by day) and
 * Series (posters). Tap a recording to play it; long-press (or
 * tap a scheduled one) for its options.
 */
@Composable
internal fun RecordingsScreen(
    onBack: () -> Unit,
    onPlay: (LiveTvRecording) -> Unit,
    modifier: Modifier = Modifier,
    /** The gear in the header: Recordings settings (the network folder, padding, keep rules). */
    onOpenSettings: () -> Unit = {},
) {
    val tokens = MaterialTheme.nuvio
    val recordings by remember { LiveTvRecordingScheduler.start(); LiveTvRecorder.recordings }.collectAsStateWithLifecycle()
    val schedules by LiveTvRecordingScheduler.schedules.collectAsStateWithLifecycle()
    val series by LiveTvRecordingScheduler.series.collectAsStateWithLifecycle()
    val repeats by LiveTvRecordingScheduler.repeats.collectAsStateWithLifecycle()
    val notice by RecordingNotices.current.collectAsStateWithLifecycle()
    // The TVs' recordings on the network folder (watch only), read now and every few minutes while open.
    val network by remember { NetworkLibrary.start(); NetworkLibrary.state }.collectAsStateWithLifecycle()
    val downloads by remember { OfflineRecordings.start(); OfflineRecordings.state }.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { while (true) { NetworkLibrary.refresh(force = true); delay(180_000) } }
    // Recordings made here for the network folder: copying across, or waiting until it can be reached.
    val uploads by NetworkUploads.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { NetworkUploads.kick(force = true) }
    val waitingForNetwork = remember(recordings, uploads) { recordings.filter { NetworkUploads.isPending(it) }.map { it.id }.toSet() }
    val netAway = network.folderLabel != null && network.reachable == false
    var sheet by remember { mutableStateOf<Any?>(null) }
    // Fix match: the show's name, and whether it's surely a show (null = don't know).
    var fixFor by remember { mutableStateOf<Pair<String, Boolean?>?>(null) }
    var renameFor by remember { mutableStateOf<LiveTvRecording?>(null) }
    var renameNetworkConfig by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { LiveTvRecordingScheduler.fireOverdue() }
    LaunchedEffect(notice) { if (notice != null) { NuvioToastController.show(notice.orEmpty()); RecordingNotices.clear() } }
    val now by produceState(LiveTvClock.nowMs()) { while (true) { value = LiveTvClock.nowMs(); delay(15_000) } }

    // This device's recordings, the network folder's, and network recordings saved on this phone
    // for offline watching (one card with the original while that folder is listed).
    val offline = remember(recordings, network.recordings, network.folderLabel, downloads.ready) {
        recordingEntries(recordings, network, downloads.ready)
    }
    val entryOf = remember(offline) { offline.associateBy { it.recording } }
    fun entryFor(r: LiveTvRecording) = entryOf[r] ?: RecordingEntry(r)
    val recordingNow = remember(offline) { offline.filter { it.recording.isRecording }.map { it.recording } }
    val recorded = remember(offline) { offline.filter { !it.recording.isRecording }.map { it.recording } }
    val entries = remember(recorded) { groupRecorded(recorded) }
    val recordedGridState = rememberLazyGridState()
    var openShowKey by rememberSaveable { mutableStateOf<String?>(null) }
    val openShow = entries.firstOrNull { it.key == openShowKey } as? RecordedShow
    // Initial disk/network reads may still be loading after recreation. Once the open folder
    // has appeared, losing it (for example after deleting an episode) returns to the grid.
    var openShowSeen by remember(openShowKey) { mutableStateOf(false) }
    LaunchedEffect(openShow) {
        if (openShow != null) openShowSeen = true
        else if (openShowSeen) openShowKey = null
    }
    var confirmDeleteShow by remember { mutableStateOf<RecordedShow?>(null) }
    var deleteNetworkConfig by remember { mutableStateOf<String?>(null) }
    var deletingShow by remember { mutableStateOf(false) }
    val deleteScope = rememberCoroutineScope()
    val deleteChangedText = stringResource(Res.string.livetv_rec_delete_changed)
    val deleteIncompleteText = stringResource(Res.string.livetv_rec_delete_incomplete)
    val sourceLines = recorded.associate { r -> recordingEntryKey(r) to (offlineStatusText(entryFor(r).source, downloads)
        ?: recordingTransferLine(r, netAway && entryFor(r).source !in downloads.ready, uploads.copyingId, uploads.progress, waitingForNetwork)) }
    val awayText = stringResource(Res.string.livetv_ui_rec_away)
    // A recording on the network folder: plays the copy saved on this phone, else from the
    // folder, only when it can be reached.
    fun play(r: LiveTvRecording) {
        val playable = entryFor(r).playable(downloads.ready, netAway)
        if (playable != null) onPlay(playable) else NuvioToastController.show(awayText)
    }
    val hasSeries = series.isNotEmpty() || repeats.isNotEmpty()
    // Recorded first. Until a tab is picked, it opens on the first one that has anything.
    var pickedTab by rememberSaveable { mutableStateOf(-1) }
    val tab = if (pickedTab >= 0) pickedTab else when {
        recorded.isNotEmpty() -> TAB_RECORDED
        schedules.isNotEmpty() -> TAB_SCHEDULED
        hasSeries -> TAB_SERIES
        else -> TAB_RECORDED
    }
    val sidePad = PaddingValues(start = NuvioTokens.Space.s16, end = NuvioTokens.Space.s16, bottom = nuvioSafeBottomPadding(NuvioTokens.Space.s16))

    if (openShow != null) {
        val newest = openShow.episodes.maxBy { it.programStartMs.takeIf { at -> at > 0 } ?: it.startedAtMs }
        RecordedShowPage(
            show = openShow,
            poster = rememberRecordingPoster(openShow.title, newest.channelName),
            statusLines = sourceLines.mapNotNull { (key, value) -> value?.let { key to it } }.toMap(),
            networkWarning = awayText.takeIf { netAway && openShow.episodes.any { NetworkLibrary.isNetwork(it) && entryFor(it).source !in downloads.ready } },
            deleteEnabled = !deletingShow,
            modifier = modifier,
            onPlay = ::play,
            onMenu = { sheet = it },
            onDeleteAll = {
                deleteNetworkConfig = LiveTvPreferences.currentNetworkFolderJson()
                confirmDeleteShow = openShow
            },
            onClose = { openShowKey = null },
        )
    } else Column(modifier = modifier.fillMaxSize().background(tokens.colors.background).statusBarsPadding().liveTvSideInsets()) {
        NuvioScreenHeader(
            title = stringResource(Res.string.livetv_recordings),
            includeStatusBarPadding = false,
            onBack = onBack,
            modifier = Modifier.padding(horizontal = NuvioTokens.Space.s8),
            actions = {
                androidx.compose.material3.IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(Res.string.livetv_ui_rec_settings), tint = tokens.colors.textPrimary)
                }
            },
        )
        // The network folder can't be reached: said even when its recordings were never listed
        // here (a blank list looked like the folder was empty).
        if (netAway) {
            Text(
                (when {
                    downloads.ready.isNotEmpty() -> stringResource(Res.string.livetv_rec_offline_away)
                    network.recordings.isNotEmpty() -> stringResource(Res.string.livetv_ui_rec_away_has)
                    else -> stringResource(Res.string.livetv_ui_rec_away_none, network.folderLabel.orEmpty())
                }) +
                    (network.problem?.let { " ${it.trimEnd('.')}." } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted,
                modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16).padding(bottom = NuvioTokens.Space.s8),
            )
        }
        if (offline.isEmpty() && schedules.isEmpty() && !hasSeries) {
            Text(stringResource(Res.string.livetv_rec_empty), style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted, modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s24))
            return@Column
        }
        // What's recording now: above the tabs, only while something records.
        if (recordingNow.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16).padding(bottom = NuvioTokens.Space.s8),
                verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
            ) {
                recordingNow.forEach { r -> RecordingRow(r, now, onClick = { play(r) }, onLongClick = { sheet = r }) }
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s8),
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
        ) {
            Chip(label = tabLabel(stringResource(Res.string.livetv_rec_recorded), recorded.size), selected = tab == TAB_RECORDED, onClick = { pickedTab = TAB_RECORDED })
            Chip(label = tabLabel(stringResource(Res.string.livetv_rec_scheduled), schedules.size), selected = tab == TAB_SCHEDULED, onClick = { pickedTab = TAB_SCHEDULED })
            Chip(label = tabLabel(stringResource(Res.string.livetv_rec_tab_series), series.size + repeats.size), selected = tab == TAB_SERIES, onClick = { pickedTab = TAB_SERIES })
        }
        when (tab) {
            TAB_RECORDED -> if (recorded.isEmpty()) EmptyTab(stringResource(Res.string.livetv_rec_none_recorded)) else LazyVerticalGrid(
                columns = GridCells.Adaptive(POSTER_MIN_WIDTH),
                state = recordedGridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = NuvioTokens.Space.s16, end = NuvioTokens.Space.s16, top = NuvioTokens.Space.s4, bottom = nuvioSafeBottomPadding(NuvioTokens.Space.s16)),
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
                verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s16),
            ) {
                gridItems(entries, key = { it.key }) { entry ->
                    if (entry is RecordedShow) {
                        val newest = entry.episodes.maxBy { it.programStartMs.takeIf { at -> at > 0 } ?: it.startedAtMs }
                        RecordedFolderCard(
                            show = entry,
                            poster = rememberRecordingPoster(entry.title, newest.channelName),
                            statusLine = entry.episodes.firstNotNullOfOrNull { sourceLines[recordingEntryKey(it)] },
                            onClick = { openShowKey = entry.key },
                            onLongClick = { openShowKey = entry.key },
                        )
                        return@gridItems
                    }
                    val r = (entry as SingleRecording).rec
                    val failed = r.status == RecordingStatus.FAILED
                    val fromTv = NetworkLibrary.isNetwork(r)
                    val source = entryFor(r).source
                    val offlineStatus = offlineStatusText(source, downloads)
                    RecordingPosterCard(
                        title = r.title + (r.episode?.let { " · $it" } ?: ""),
                        posterTitle = r.title,
                        channelName = r.channelName,
                        channelLogo = r.channelLogo,
                        line = offlineStatus ?: if (failed) (r.problem ?: stringResource(Res.string.livetv_rec_failed_short)) else if (fromTv && netAway) stringResource(Res.string.livetv_ui_rec_net_unreachable)
                        else if (uploads.copyingId == r.id) stringResource(Res.string.livetv_ui_rec_copying, (uploads.progress * 100).toInt())
                        else if (r.id in waitingForNetwork) stringResource(Res.string.livetv_ui_rec_waiting_net) else listOfNotNull(
                            stringResource(Res.string.livetv_ui_rec_network).takeIf { fromTv },
                            SportsDates.dayLabel(r.programStartMs.takeIf { it > 0 } ?: r.startedAtMs),
                            RecordingRules.durationLabel(r.durationSec).takeIf { r.durationSec >= 60 },
                        ).joinToString(" · "),
                        lineIsError = failed || source in downloads.failed,
                        onClick = { if (r.status == RecordingStatus.DONE || source in downloads.ready) play(r) else sheet = r },
                        onLongClick = { sheet = r },
                    )
                }
            }
            TAB_SCHEDULED -> if (schedules.isEmpty()) EmptyTab(stringResource(Res.string.livetv_rec_none_scheduled)) else {
                val days = remember(schedules) { schedules.sortedBy { it.startMs }.groupBy { dayStartOf(it.startMs) }.toList() }
                val today = dayStartOf(now)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = sidePad,
                    verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
                ) {
                    days.forEach { (day, list) ->
                        item(key = "day:$day") {
                            val label = when (SportsDates.dayLabel(day + 12 * 3_600_000L)) {
                                SportsDates.dayLabel(today + 12 * 3_600_000L) -> stringResource(Res.string.livetv_rec_today) + " · " + SportsDates.dayLabel(day + 12 * 3_600_000L)
                                SportsDates.dayLabel(today + 36 * 3_600_000L) -> stringResource(Res.string.livetv_rec_tomorrow) + " · " + SportsDates.dayLabel(day + 12 * 3_600_000L)
                                else -> SportsDates.dayLabel(day + 12 * 3_600_000L)
                            }
                            SectionTitle(label)
                        }
                        items(list.size, key = { "p:" + list[it].id }) { i -> ScheduleRow(list[i], onClick = { sheet = list[i] }) }
                    }
                }
            }
            else -> if (!hasSeries) EmptyTab(stringResource(Res.string.livetv_rec_none_series)) else LazyVerticalGrid(
                columns = GridCells.Adaptive(POSTER_MIN_WIDTH),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = NuvioTokens.Space.s16, end = NuvioTokens.Space.s16, top = NuvioTokens.Space.s4, bottom = nuvioSafeBottomPadding(NuvioTokens.Space.s16)),
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
                verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s16),
            ) {
                gridItems(series, key = { "s:" + it.id }) { r ->
                    val keep = if (r.keepLast > 0) stringResource(Res.string.livetv_rec_keep_last, r.keepLast) else stringResource(Res.string.livetv_rec_keep_all)
                    val padBefore = if (r.preMin >= 0) " · " + stringResource(Res.string.livetv_rec_pad_line_before, r.preMin) else ""
                    val padAfter = if (r.postMin >= 0) " · " + stringResource(Res.string.livetv_rec_pad_line_after, r.postMin) else ""
                    RecordingPosterCard(
                        title = r.title,
                        posterTitle = r.title,
                        channelName = r.channel.name,
                        channelLogo = r.channel.logo,
                        line = (if (r.newOnly) stringResource(Res.string.livetv_rec_series_new_only) + " · " else "") + keep + padBefore + padAfter,
                        lineIsError = false,
                        onClick = { sheet = r },
                        onLongClick = { sheet = r },
                    )
                }
                gridItems(repeats, key = { "m:" + it.id }) { r ->
                    RecordingPosterCard(
                        title = r.title,
                        posterTitle = r.title,
                        channelName = r.channel.name,
                        channelLogo = r.channel.logo,
                        line = RecordingRules.daysLabel(r.days) + " · " + minuteLabel(r.minuteOfDay),
                        lineIsError = false,
                        onClick = { sheet = r },
                        onLongClick = { sheet = r },
                    )
                }
            }
        }
    }

    confirmDeleteShow?.let { show ->
        DeleteRecordedShowDialog(show, onDismiss = { confirmDeleteShow = null }, onConfirm = {
            confirmDeleteShow = null
            val hasNetwork = show.episodes.any { NetworkLibrary.isNetwork(it) }
            val config = deleteNetworkConfig
            if (hasNetwork && config != LiveTvPreferences.currentNetworkFolderJson()) {
                NuvioToastController.show(deleteChangedText)
            } else {
                deletingShow = true
                deleteScope.launch {
                    var problem: String? = null
                    try {
                        for (snapshot in show.episodes) {
                            if (hasNetwork && config != LiveTvPreferences.currentNetworkFolderJson()) {
                                problem = deleteChangedText
                                break
                            }
                            val fresh = (LiveTvRecorder.recordings.value + NetworkLibrary.state.value.recordings)
                                .firstOrNull { recordingEntryKey(it) == recordingEntryKey(snapshot) }
                                ?.takeIf { it.status == RecordingStatus.DONE && it.dir == snapshot.dir && it.startedAtMs == snapshot.startedAtMs }
                                ?: continue
                            if (NetworkLibrary.isNetwork(fresh)) {
                                NetworkLibrary.delete(fresh, NetworkLibrary.state.value.folderLabel.orEmpty())?.let { if (problem == null) problem = it }
                            } else LiveTvRecorder.delete(fresh.id)
                        }
                        problem?.let { NuvioToastController.show("$deleteIncompleteText $it") }
                    } finally { deletingShow = false }
                }
            }
        })
    }

    when (val s = sheet) {
        is LiveTvRecording -> RecordingSheet(s, entryFor(s), downloads, network, onDismiss = { sheet = null }, onPlay = { play(s) }, onRename = {
            renameNetworkConfig = LiveTvPreferences.currentNetworkFolderJson()
            renameFor = s
        },
            onFixMatch = { fixFor = s.title to (if (s.episode.isNullOrBlank()) null else true) })
        is RecordingSchedule -> ScheduleSheet(s, onDismiss = { sheet = null }, onFixMatch = { fixFor = s.title to (if (s.episode.isNullOrBlank()) null else true) })
        is SeriesRule -> SeriesSheet(s, onDismiss = { sheet = null }, onFixMatch = { fixFor = s.title to true })
        is RepeatRule -> RepeatSheet(s, onDismiss = { sheet = null })
        else -> Unit
    }
    renameFor?.let { recording ->
        RenameRecordingDialog(recording, onDismiss = { renameFor = null }, onSave = { title ->
            val problem = if (NetworkLibrary.isNetwork(recording)) NetworkLibrary.rename(recording, title, renameNetworkConfig)
                else LiveTvRecorder.rename(recording, title)
            if (problem == null) aboutCache.remove(recording.id)
            problem
        })
    }
    fixFor?.let { (title, series) ->
        FixMatchSheet(
            title = title,
            scopeNote = stringResource(Res.string.livetv_fix_match_rec_scope),
            key = TitleMatchFixes.recordingKey(title),
            initialQuery = title,
            initialSeries = series,
            onDismiss = { fixFor = null },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.nuvio.colors.textPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = NuvioTokens.Space.s12, bottom = NuvioTokens.Space.s4))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun recordingTransferLine(
    rec: LiveTvRecording, networkAway: Boolean, copyingId: String?, progress: Float, waiting: Set<String>,
): String? = when {
    NetworkLibrary.isNetwork(rec) && networkAway -> stringResource(Res.string.livetv_ui_rec_net_unreachable)
    !NetworkLibrary.isNetwork(rec) && copyingId == rec.id -> stringResource(Res.string.livetv_ui_rec_copying, (progress * 100).toInt())
    !NetworkLibrary.isNetwork(rec) && rec.id in waiting -> stringResource(Res.string.livetv_ui_rec_waiting_net)
    NetworkLibrary.isNetwork(rec) -> stringResource(Res.string.livetv_ui_rec_network)
    else -> null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RecordingRow(
    rec: LiveTvRecording, now: Long, onClick: () -> Unit, onLongClick: () -> Unit,
    title: String = rec.title + (rec.episode?.let { "  ·  $it" } ?: ""), statusLine: String? = null,
    synopsis: String? = null,
) {
    val tokens = MaterialTheme.nuvio
    val liveSeconds = if (rec.isRecording && !rec.fromCatchup) ((now - rec.startedAtMs) / 1000.0).coerceAtLeast(rec.durationSec) else rec.durationSec
    val info = listOfNotNull(
        rec.channelName.takeIf { it.isNotBlank() },
        formatLocalDateTime(rec.programStartMs.takeIf { it > 0 } ?: rec.startedAtMs),
        RecordingRules.durationLabel(liveSeconds).takeIf { liveSeconds >= 60 },
        RecordingRules.sizeLabel(rec.sizeBytes).takeIf { rec.sizeBytes > 0 },
    ).joinToString("  ·  ")
    Row(
        modifier = Modifier.fillMaxWidth().clip(tokens.shapes.card).background(tokens.colors.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s10),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(NuvioTokens.Radius.md)).background(tokens.colors.surfaceCard), contentAlignment = Alignment.Center) {
            rec.channelLogo?.takeIf { it.isNotBlank() }?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxSize().padding(NuvioTokens.Space.s4), contentScale = ContentScale.Fit) }
                ?: Icon(Icons.Filled.FiberManualRecord, contentDescription = null, tint = tokens.colors.textMuted)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(info, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            synopsis?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textSecondary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            statusLine?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            rec.problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (rec.status == RecordingStatus.FAILED) tokens.colors.danger else tokens.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        when {
            rec.isRecording -> Icon(Icons.Filled.FiberManualRecord, contentDescription = null, tint = tokens.colors.danger, modifier = Modifier.size(NuvioTokens.Icon.sm))
            rec.status == RecordingStatus.DONE -> Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = tokens.colors.textMuted)
            else -> Unit
        }
    }
}

/** One show set to record: its poster, the title, the times and channel (the day is in the heading). */
@Composable
private fun ScheduleRow(s: RecordingSchedule, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val sub = listOfNotNull(
        formatClock(s.startMs) + " – " + formatClock(s.stopMs),
        s.channel.name,
        if (s.useCatchup) stringResource(Res.string.livetv_rec_catchup_save) else null,
    ).joinToString("  ·  ")
    val poster = rememberRecordingPoster(s.title, s.channel.name)
    Row(
        modifier = Modifier.fillMaxWidth().clip(tokens.shapes.card).background(tokens.colors.surface)
            .combinedClickableCompat(onClick)
            .padding(horizontal = NuvioTokens.Space.s10, vertical = NuvioTokens.Space.s8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        PosterBox(poster, s.channel.logo, Modifier.width(40.dp).aspectRatio(2f / 3f))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2)) {
            Text(s.title + (s.episode?.let { "  ·  $it" } ?: ""), style = MaterialTheme.typography.bodyLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (s.seriesId != null) Icon(Icons.Filled.FiberManualRecord, contentDescription = null, tint = tokens.colors.accent, modifier = Modifier.size(NuvioTokens.Icon.sm))
    }
}

private const val TAB_RECORDED = 0
private const val TAB_SCHEDULED = 1
private const val TAB_SERIES = 2
private val POSTER_MIN_WIDTH = 104.dp

private fun tabLabel(name: String, count: Int): String = if (count > 0) "$name  $count" else name

@Composable
private fun EmptyTab(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.nuvio.colors.textMuted, modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s24))
}

/** Midnight on the device of the day [ms] falls on. */
private fun dayStartOf(ms: Long): Long {
    val t = CivilTime.at(ms, LiveTvTimeZones.SYSTEM)
    return SportsDates.localDate(t.year, t.month, t.day)
}

/** Game titles found random films: sports keep the channel's logo. */
private val SPORTS_CHANNEL = Regex("""(?i)sport|espn|dazn|bein|\bnfl\b|\bnba\b|\bnhl\b|\bmlb\b|\bufc\b|\bf1\b|ppv""")
private val GAME_TITLE = Regex("""(?i)\s(vs?\.?|@)\s""")

private fun isSportsRecording(title: String, channelName: String?): Boolean =
    GAME_TITLE.containsMatchIn(title) || (channelName != null && SPORTS_CHANNEL.containsMatchIn(channelName))

/** A show's poster (TMDB, as On Demand finds them), or null. Off when "Show posters" is off. */
@Composable
internal fun rememberRecordingPoster(title: String, channelName: String?): String? {
    val settings by LiveTvPreferences.settings.collectAsStateWithLifecycle()
    val wanted = settings.showPosters && title.isNotBlank() && !isSportsRecording(title, channelName)
    // Fix match: your pick (here or on a TV) wins; then the channel's country picks between same-named shows.
    val fixes by TitleMatchFixes.fixes.collectAsStateWithLifecycle()
    val pick = fixes[TitleMatchFixes.recordingKey(title)]
    val poster by produceState<String?>(null, title, wanted, pick) {
        value = if (wanted) findRecordingPoster(title, channelName, pick?.poster) else null
    }
    return poster
}

/** Posters already found here, so the player shows the same one at once (used on the main thread). */
private val recordingPosters = HashMap<String, String>()

private suspend fun findRecordingPoster(title: String, channelName: String?, picked: String?): String? {
    picked?.let { return it }
    val key = title.trim().lowercase().replace(Regex("\\s+"), " ")
    recordingPosters[key + "|" + channelName.orEmpty()]?.let { return it }
    val found = TitleMatchFixes.posterByCountry(title, channelName) ?: run {
        val item = VodItem(
            playlistId = "rec", kind = VodKind.SERIES, id = key, name = title, icon = null, categoryId = "",
            ext = null, tmdbId = null, year = null, rating = null, addedSec = 0L,
        )
        runCatching { OnDemandRepository.match(item) }.getOrNull()?.poster?.takeIf { it.isNotBlank() }
    }
    if (found != null) recordingPosters[key + "|" + channelName.orEmpty()] = found
    return found
}

/**
 * The poster Recordings shows for [rec], for the player's picture: none for sports or with
 * "Show posters" off (the channel's logo is used then, as in Recordings).
 */
internal suspend fun recordingPosterFor(rec: LiveTvRecording): String? {
    val title = rec.title
    if (!LiveTvPreferences.settings.value.showPosters || title.isBlank() || isSportsRecording(title, rec.channelName)) return null
    val pick = TitleMatchFixes.fixes.value[TitleMatchFixes.recordingKey(title)]
    return findRecordingPoster(title, rec.channelName, pick?.poster)
}

private val EPISODE_NUMBERS = Regex("""(?i)s\s*0*(\d{1,3})\s*[.\- ]?\s*e\s*0*(\d{1,4})""")
private val aboutCache = HashMap<String, String>()

/**
 * What a recording is about: the guide's description, else TMDB's (the episode's own when the
 * guide gave its season and episode, a movie's otherwise). The show is matched the same way as
 * its poster (Fix match pick first). A show with no episode numbers gets nothing: its general
 * description would read as if it were this episode's.
 */
@Composable
private fun rememberRecordingAbout(rec: LiveTvRecording): String? {
    val guide = guideDescriptionText(rec.description)?.trim()?.takeIf { it.isNotBlank() }
    val fixes by TitleMatchFixes.fixes.collectAsStateWithLifecycle()
    val pick = fixes[TitleMatchFixes.recordingKey(rec.title)]
    val found by produceState(aboutCache[rec.id], rec.id, guide, pick) {
        if (guide != null || rec.title.isBlank() || isSportsRecording(rec.title, rec.channelName)) return@produceState
        if (value != null && pick == null) return@produceState
        val key = rec.title.trim().lowercase().replace(Regex("\\s+"), " ")
        fun item(kind: VodKind) = VodItem(
            playlistId = "rec", kind = kind, id = key, name = rec.title, icon = null, categoryId = "",
            ext = null, tmdbId = null, year = null, rating = null, addedSec = 0L,
        )
        val numbers = rec.episode?.let { EPISODE_NUMBERS.find(it) }?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
        val text = runCatching {
            if (numbers != null) {
                val id = pick?.takeIf { it.series }?.tmdbId ?: OnDemandRepository.match(item(VodKind.SERIES))?.tmdbId
                id?.let { TmdbMetadataService.episodeOverview(it, numbers.first, numbers.second) }
            } else if (pick != null) {
                pick.tmdbId?.takeIf { !pick.series }?.let { TmdbMetadataService.movieOverview(it) }
            } else {
                // Only when it's a movie and not a show of the same name.
                val show = OnDemandRepository.match(item(VodKind.SERIES))?.tmdbId
                if (show != null) null
                else OnDemandRepository.match(item(VodKind.MOVIE))?.tmdbId?.let { TmdbMetadataService.movieOverview(it) }
            }
        }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
        if (text != null) aboutCache[rec.id] = text else aboutCache.remove(rec.id)
        value = text
    }
    return guide ?: found
}

/** A portrait poster, or the channel's logo on the card color when there's none. */
@Composable
internal fun PosterBox(poster: String?, channelLogo: String?, modifier: Modifier) {
    val tokens = MaterialTheme.nuvio
    Box(modifier = modifier.clip(RoundedCornerShape(NuvioTokens.Radius.md)).background(tokens.colors.surfaceCard), contentAlignment = Alignment.Center) {
        when {
            poster != null -> AsyncImage(model = poster, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            !channelLogo.isNullOrBlank() -> AsyncImage(model = channelLogo, contentDescription = null, modifier = Modifier.fillMaxSize().padding(NuvioTokens.Space.s8), contentScale = ContentScale.Fit)
            else -> Icon(Icons.Filled.FiberManualRecord, contentDescription = null, tint = tokens.colors.textMuted)
        }
    }
}

/** A recording or a series in the Recorded and Series tabs: poster, title, a line under it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingPosterCard(
    title: String,
    posterTitle: String,
    channelName: String?,
    channelLogo: String?,
    line: String,
    lineIsError: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val poster = rememberRecordingPoster(posterTitle, channelName)
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(NuvioTokens.Radius.md)).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2),
    ) {
        PosterBox(poster, channelLogo, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
        Spacer(Modifier.height(NuvioTokens.Space.s4))
        Text(title, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(line, style = MaterialTheme.typography.bodySmall, color = if (lineIsError) tokens.colors.danger else tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "20:30" for a time of day in minutes after midnight. */
private fun minuteLabel(minuteOfDay: Int): String =
    (minuteOfDay / 60).toString().padStart(2, '0') + ":" + (minuteOfDay % 60).toString().padStart(2, '0')

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepeatSheet(r: RepeatRule, onDismiss: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close() { scope.launch { dismissNuvioBottomSheet(sheetState) { onDismiss() } } }
    NuvioModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s12), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
            Text(r.title, style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold)
            Text(r.channel.name + "  ·  " + RecordingRules.daysLabel(r.days) + "  ·  " + minuteLabel(r.minuteOfDay), style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted)
        }
        NuvioBottomSheetDivider()
        NuvioBottomSheetActionRow(title = stringResource(Res.string.livetv_rec_repeat_stop), icon = Icons.Filled.Delete, onClick = { LiveTvRecordingScheduler.removeRepeat(r.id); close() })
        Spacer(Modifier.height(NuvioTokens.Space.s12))
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier = combinedClickable(onClick = onClick)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordingSheet(
    rec: LiveTvRecording,
    entry: RecordingEntry,
    downloads: OfflineRecordingState,
    network: NetworkLibrary.State,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onFixMatch: () -> Unit,
) {
    val key = entry.source
    val tokens = MaterialTheme.nuvio
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(then: () -> Unit = {}) { scope.launch { dismissNuvioBottomSheet(sheetState) { onDismiss(); then() } } }
    NuvioModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s12)) {
            Text(rec.title, style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(rec.channelName, formatLocalDateTime(rec.startedAtMs), rec.endedAtMs?.let { formatClock(it) }, RecordingRules.sizeLabel(rec.sizeBytes).takeIf { rec.sizeBytes > 0 }).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted,
            )
            rememberRecordingAbout(rec)?.let { synopsis ->
                Text(synopsis, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textSecondary,
                    modifier = Modifier.padding(top = NuvioTokens.Space.s6).heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState()))
            }
            offlineStatusText(key, downloads)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textSecondary, modifier = Modifier.padding(top = NuvioTokens.Space.s6))
            }
        }
        NuvioBottomSheetDivider()
        if (rec.status != RecordingStatus.FAILED || key in downloads.ready) {
            NuvioBottomSheetActionRow(title = stringResource(Res.string.action_play), icon = Icons.Filled.PlayArrow, onClick = { close(onPlay) })
        }
        if (key != null && OfflineRecordings.available) {
            when (entry.downloadAction(downloads, network)) {
                OfflineAction.REMOVE -> NuvioBottomSheetActionRow(title = stringResource(Res.string.livetv_rec_offline_remove),
                    icon = Icons.Filled.Delete, onClick = { OfflineRecordings.remove(key); close() })
                OfflineAction.CANCEL -> NuvioBottomSheetActionRow(title = stringResource(Res.string.livetv_rec_offline_cancel),
                    icon = Icons.Filled.Stop, onClick = { OfflineRecordings.cancel(key) })
                OfflineAction.SAVE, OfflineAction.RETRY -> NuvioBottomSheetActionRow(
                    title = stringResource(if (key in downloads.failed) Res.string.livetv_rec_offline_retry else Res.string.livetv_rec_offline_save),
                    icon = Icons.Filled.FileDownload, onClick = { OfflineRecordings.save(key, rec) })
                null -> Unit
            }
        }
        // A network recording is renamed on the folder, so only while that folder is reachable;
        // a saved offline copy whose folder isn't listed has nothing to rename.
        if (!rec.isRecording && (key == null || entry.canDeleteFromNetwork(downloads, network))) {
            NuvioBottomSheetActionRow(title = stringResource(Res.string.livetv_rec_rename), icon = Icons.Filled.Edit, onClick = { close(onRename) })
        }
        // Wrong poster (a film instead of the show, the US version of a UK show): pick the right one.
        if (!isSportsRecording(rec.title, rec.channelName)) {
            NuvioBottomSheetActionRow(title = stringResource(Res.string.livetv_fix_match), icon = Icons.Filled.ImageSearch, onClick = { close(onFixMatch) })
        }
        if (key != null) {
            // A TV's recording on the network folder: deleting it takes it off every TV too. Not
            // while it records. An offline-only copy is removed from this phone above.
            if (entry.canDeleteFromNetwork(downloads, network)) {
                NuvioBottomSheetActionRow(title = stringResource(Res.string.livetv_rec_offline_delete_network), icon = Icons.Filled.Delete, onClick = {
                    NetworkLibrary.deleteAndTell(rec, key.folderLabel)
                    close()
                })
            }
        } else {
            if (rec.isRecording) {
                NuvioBottomSheetActionRow(title = stringResource(Res.string.livetv_rec_stop), icon = Icons.Filled.Stop, onClick = { LiveTvRecorder.stop(rec.id); close() })
            }
            NuvioBottomSheetActionRow(title = stringResource(Res.string.action_delete), icon = Icons.Filled.Delete, onClick = { LiveTvRecorder.delete(rec.id); close() })
        }
        Spacer(Modifier.height(NuvioTokens.Space.s12))
    }
}

@Composable
private fun offlineStatusText(key: OfflineRecordingKey?, downloads: OfflineRecordingState): String? {
    key ?: return null
    val progress = downloads.progress[key]
    return when {
        key in downloads.ready -> stringResource(Res.string.livetv_rec_offline_ready)
        progress != null -> stringResource(Res.string.updates_downloading_progress, (progress * 100).toInt().coerceIn(0, 99))
        key in downloads.failed -> stringResource(Res.string.download_failed)
        else -> null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleSheet(s: RecordingSchedule, onDismiss: () -> Unit, onFixMatch: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(then: () -> Unit = {}) { scope.launch { dismissNuvioBottomSheet(sheetState) { onDismiss(); then() } } }
    NuvioModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s12)) {
            Text(s.title, style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(s.channel.name + "  ·  " + formatLocalDateTime(s.startMs) + " – " + formatClock(s.stopMs), style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted)
            Text(
                (if (s.useCatchup) stringResource(Res.string.livetv_rec_catchup_save) + "  ·  " else "") + formatLocalDateTime(LiveTvRecordingScheduler.startsAt(s)),
                style = MaterialTheme.typography.bodySmall, color = tokens.colors.textSecondary,
            )
        }
        NuvioBottomSheetDivider()
        if (!isSportsRecording(s.title, s.channel.name)) {
            NuvioBottomSheetActionRow(title = stringResource(Res.string.livetv_fix_match), icon = Icons.Filled.ImageSearch, onClick = { close(onFixMatch) })
        }
        NuvioBottomSheetActionRow(title = stringResource(Res.string.livetv_rec_cancel), icon = Icons.Filled.Delete, onClick = { LiveTvRecordingScheduler.cancel(s.id); close() })
        Spacer(Modifier.height(NuvioTokens.Space.s12))
    }
}

private val KEEP_CHOICES = listOf(0, 1, 2, 3, 5, 10, 20)
private val PRE_PAD_CHOICES = listOf(0, 1, 2, 5, 10, 15, 30)
private val POST_PAD_CHOICES = listOf(0, 2, 5, 10, 15, 20, 30, 45, 60)

@Composable
private fun padMinutes(m: Int): String =
    if (m <= 0) stringResource(Res.string.livetv_ui_none) else pluralStringResource(Res.plurals.livetv_ui_minutes, m, m)

/** Default (the Recordings setting) or this series' own minutes; [selected] -1 = Default. */
@Composable
private fun PadChips(choices: List<Int>, selected: Int, default: Int, onPick: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)
    ) {
        Chip(label = stringResource(Res.string.livetv_rec_pad_default, padMinutes(default)), selected = selected < 0, onClick = { onPick(-1) })
        choices.forEach { m -> Chip(label = padMinutes(m), selected = selected == m, onClick = { onPick(m) }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeriesSheet(r: SeriesRule, onDismiss: () -> Unit, onFixMatch: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(then: () -> Unit = {}) { scope.launch { dismissNuvioBottomSheet(sheetState) { onDismiss(); then() } } }
    NuvioModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s12), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
            Text(r.title, style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold)
            Text(r.channel.name, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
                Chip(label = stringResource(Res.string.livetv_rec_series_all), selected = !r.newOnly, onClick = { if (r.newOnly) { LiveTvRecordingScheduler.setNewOnly(r.id, false); close() } })
                Chip(label = stringResource(Res.string.livetv_rec_series_new_only), selected = r.newOnly, onClick = { if (!r.newOnly) { LiveTvRecordingScheduler.setNewOnly(r.id, true); close() } })
            }
            Text(stringResource(Res.string.livetv_rec_keep_last, r.keepLast), style = MaterialTheme.typography.labelMedium, color = tokens.colors.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
                KEEP_CHOICES.forEach { n ->
                    Chip(label = if (n == 0) stringResource(Res.string.livetv_rec_keep_all) else n.toString(), selected = n == r.keepLast, onClick = { LiveTvRecordingScheduler.setKeep(r.id, n) })
                }
            }
            // This series' own extra time (Default = Settings › Live TV › Recordings).
            val settings by LiveTvPreferences.settings.collectAsStateWithLifecycle()
            var preMin by remember(r.id) { mutableStateOf(r.preMin) }
            var postMin by remember(r.id) { mutableStateOf(r.postMin) }
            Text(stringResource(Res.string.livetv_rec_pad_before), style = MaterialTheme.typography.labelMedium, color = tokens.colors.textSecondary)
            PadChips(PRE_PAD_CHOICES, preMin, settings.recordPreMinutes) { m -> preMin = m; LiveTvRecordingScheduler.setPadding(r.id, m, postMin) }
            Text(stringResource(Res.string.livetv_rec_pad_after), style = MaterialTheme.typography.labelMedium, color = tokens.colors.textSecondary)
            PadChips(POST_PAD_CHOICES, postMin, settings.recordPostMinutes) { m -> postMin = m; LiveTvRecordingScheduler.setPadding(r.id, preMin, m) }
            Text(stringResource(Res.string.livetv_rec_pad_hint), style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted)
        }
        NuvioBottomSheetDivider()
        if (!isSportsRecording(r.title, r.channel.name)) {
            NuvioBottomSheetActionRow(title = stringResource(Res.string.livetv_fix_match), icon = Icons.Filled.ImageSearch, onClick = { close(onFixMatch) })
        }
        NuvioBottomSheetActionRow(title = stringResource(Res.string.livetv_rec_series_stop), icon = Icons.Filled.Delete, onClick = { LiveTvRecordingScheduler.removeSeries(r.id); close() })
        Spacer(Modifier.height(NuvioTokens.Space.s12))
    }
}

private val DURATIONS_MIN = listOf(30, 60, 90, 120, 180, 240)

/**
 * Record by hand: a title, when to start ("Now", or a time today or tomorrow) and how long for.
 * Recorded like a show from the guide (a channel with catch-up is saved after it ends).
 */
@Composable
internal fun ManualRecordingDialog(channel: LiveChannel, scheduleOnly: Boolean, onDismiss: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    var title by remember { mutableStateOf(channel.name) }
    var startNow by remember { mutableStateOf(!scheduleOnly) }
    var tomorrow by remember { mutableStateOf(false) }
    var time by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf(60) }
    // Repeat: empty = once; otherwise the days (1 = Sunday … 7 = Saturday) it records every week.
    var repeatDays by remember { mutableStateOf(emptySet<Int>()) }
    var pickingDays by remember { mutableStateOf(false) }
    val now = LiveTvClock.nowMs()
    val repeating = repeatDays.isNotEmpty() || pickingDays
    val startMs: Long? = when {
        repeating -> parseMinuteOfDay(time)?.let { nextAiring(now, it, repeatDays) }
        startNow -> now
        else -> parseTime(time, tomorrow, now)
    }
    DialogSurface(onDismissRequest = onDismiss, title = stringResource(if (scheduleOnly) Res.string.livetv_rec_schedule else Res.string.livetv_rec_record_now)) {
        NuvioInputField(value = title, onValueChange = { title = it }, placeholder = stringResource(Res.string.livetv_rec_manual_title))
        if (!repeating) Row(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
            if (!scheduleOnly) Chip(label = stringResource(Res.string.livetv_rec_record_now), selected = startNow, onClick = { startNow = true })
            Chip(label = stringResource(Res.string.livetv_rec_today), selected = !startNow && !tomorrow, onClick = { startNow = false; tomorrow = false })
            Chip(label = stringResource(Res.string.livetv_rec_tomorrow), selected = !startNow && tomorrow, onClick = { startNow = false; tomorrow = true })
        }
        // Repeat: the same time every week on the days picked.
        Text(stringResource(Res.string.livetv_rec_repeat), style = MaterialTheme.typography.labelMedium, color = tokens.colors.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            val presets = listOf(
                Res.string.livetv_rec_repeat_once to emptySet<Int>(),
                Res.string.livetv_rec_repeat_every_day to (1..7).toSet(),
                Res.string.livetv_rec_repeat_weekdays to setOf(2, 3, 4, 5, 6),
                Res.string.livetv_rec_repeat_weekends to setOf(1, 7),
            )
            presets.forEach { (label, days) ->
                Chip(label = stringResource(label), selected = !pickingDays && repeatDays == days, onClick = {
                    pickingDays = false; repeatDays = days; if (days.isNotEmpty()) startNow = false
                })
            }
            Chip(label = stringResource(Res.string.livetv_rec_repeat_pick), selected = pickingDays, onClick = { pickingDays = true; startNow = false })
        }
        if (pickingDays) Row(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            // Monday first, Sunday last.
            listOf(2, 3, 4, 5, 6, 7, 1).forEach { d ->
                Chip(label = RecordingRules.DAY_NAMES[d - 1], selected = d in repeatDays, onClick = {
                    repeatDays = if (d in repeatDays) repeatDays - d else repeatDays + d
                })
            }
        }
        if (repeating || !startNow) {
            NuvioInputField(value = time, onValueChange = { time = it }, placeholder = stringResource(Res.string.livetv_rec_start_time_hint))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
            DURATIONS_MIN.forEach { m -> Chip(label = if (m % 60 == 0) "${m / 60} h" else "$m min", selected = m == minutes, onClick = { minutes = m }) }
        }
        startMs?.let { Text(formatLocalDateTime(it) + " – " + formatClock(it + minutes * 60_000L), style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted) }
        DialogButtons {
            DialogButton(text = stringResource(Res.string.action_cancel), onClick = onDismiss)
            DialogButton(
                text = stringResource(if (startNow) Res.string.livetv_rec_record_now else Res.string.livetv_rec_schedule),
                style = DialogButtonStyle.Primary,
                enabled = startMs != null,
                onClick = {
                    val start = startMs ?: return@DialogButton
                    val message = LiveTvRecordingScheduler.addManual(channel, title.trim(), start, start + minutes * 60_000L, repeatDays)
                    NuvioToastController.show(message)
                    onDismiss()
                },
            )
        }
    }
}

/** "20:30" or "8:30 pm" as minutes after midnight; null when it isn't a time. */
private fun parseMinuteOfDay(text: String): Int? {
    val m = Regex("""^\s*(\d{1,2})[:.](\d{2})\s*([ap]m?)?\s*$""", RegexOption.IGNORE_CASE).find(text) ?: return null
    var hour = m.groupValues[1].toInt()
    val minute = m.groupValues[2].toInt()
    val ampm = m.groupValues[3].lowercase()
    if (ampm.startsWith("p") && hour < 12) hour += 12
    if (ampm.startsWith("a") && hour == 12) hour = 0
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

/** The next start at [minuteOfDay] on one of [days] (the phone's time), from [now] on; null with no days. */
private fun nextAiring(now: Long, minuteOfDay: Int, days: Set<Int>): Long? {
    if (days.isEmpty()) return null
    val zone = LiveTvTimeZones.SYSTEM
    val today = CivilTime.at(now, zone)
    val todayDays = CivilTime.daysFromCivil(today.year, today.month, today.day)
    for (ahead in 0..7) {
        val epochDay = todayDays + ahead
        if (RecordingRules.weekday(epochDay) !in days) continue
        val date = CivilTime.atUtc(epochDay * 86_400_000L)
        val at = CivilTime.toEpochMs(date.year, date.month, date.day, minuteOfDay / 60, minuteOfDay % 60, 0, zone)
        if (at > now) return at
    }
    return null
}

/** "20:30" or "8:30 pm" as a time today (or tomorrow), in the device's zone; null when it isn't one or has passed. */
internal fun parseTime(text: String, tomorrow: Boolean, now: Long): Long? {
    val m = Regex("""^\s*(\d{1,2})[:.](\d{2})\s*([ap]m?)?\s*$""", RegexOption.IGNORE_CASE).find(text) ?: return null
    var hour = m.groupValues[1].toInt()
    val minute = m.groupValues[2].toInt()
    val ampm = m.groupValues[3].lowercase()
    if (ampm.startsWith("p") && hour < 12) hour += 12
    if (ampm.startsWith("a") && hour == 12) hour = 0
    if (hour !in 0..23 || minute !in 0..59) return null
    val today = CivilTime.at(now, LiveTvTimeZones.SYSTEM)
    val dayOffset = if (tomorrow) 1 else 0
    val days = CivilTime.daysFromCivil(today.year, today.month, today.day) + dayOffset
    val date = CivilTime.atUtc(days * 86_400_000L)
    val at = CivilTime.toEpochMs(date.year, date.month, date.day, hour, minute, 0, LiveTvTimeZones.SYSTEM)
    return at.takeIf { it > now - 60_000L }
}

package com.nuvio.app.features.livetv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.features.livetv.model.guideDescriptionText
import com.nuvio.app.features.livetv.record.LiveTvRecording
import com.nuvio.app.features.livetv.record.NetworkLibrary
import com.nuvio.app.features.livetv.record.RecordedShow
import com.nuvio.app.features.livetv.record.RecordingRules
import com.nuvio.app.features.livetv.record.recordingAiredMs
import com.nuvio.app.features.livetv.record.recordingEntryKey
import com.nuvio.app.features.livetv.sports.SportsDates
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.action_delete
import nuvio.composeapp.generated.resources.action_play
import nuvio.composeapp.generated.resources.livetv_rec_delete_all
import nuvio.composeapp.generated.resources.livetv_rec_delete_show_title
import nuvio.composeapp.generated.resources.livetv_rec_delete_show_warning
import nuvio.composeapp.generated.resources.livetv_rec_episodes
import nuvio.composeapp.generated.resources.livetv_rec_episodes_delete
import nuvio.composeapp.generated.resources.livetv_rec_shared_delete_warning
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Completed recordings are organized visually; their files and series rules stay in place. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RecordedFolderCard(
    show: RecordedShow,
    poster: String?,
    statusLine: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val newest = show.episodes.maxBy { recordingAiredMs(it) }
    val count = pluralStringResource(Res.plurals.livetv_rec_episodes, show.episodes.size, show.episodes.size)
    Column(
        modifier = modifier.fillMaxWidth().clip(tokens.shapes.card)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth(0.84f).height(16.dp)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).background(tokens.colors.surfaceCard.copy(alpha = 0.55f)))
            Box(Modifier.align(Alignment.TopCenter).padding(top = 5.dp).fillMaxWidth(0.92f).height(16.dp)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).background(tokens.colors.surfaceCard))
            Box(Modifier.padding(top = 10.dp)) {
                PosterBox(poster, newest.channelLogo, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                Text(count, style = MaterialTheme.typography.labelMedium, color = tokens.colors.textPrimary,
                    modifier = Modifier.align(Alignment.BottomStart).padding(NuvioTokens.Space.s6)
                        .clip(RoundedCornerShape(NuvioTokens.Radius.md)).background(tokens.colors.surface)
                        .padding(horizontal = NuvioTokens.Space.s8, vertical = NuvioTokens.Space.s4))
            }
        }
        Spacer(Modifier.height(NuvioTokens.Space.s4))
        Text(show.title, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textPrimary,
            fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(SportsDates.dayLabel(recordingAiredMs(newest)), style = MaterialTheme.typography.bodySmall,
            color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        statusLine?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted,
            maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

/** Phone-sized show page, retaining the existing episode playback and long-press menus. */
@Composable
internal fun RecordedShowPage(
    show: RecordedShow,
    poster: String?,
    statusLines: Map<String, String> = emptyMap(),
    networkWarning: String? = null,
    deleteEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    onPlay: (LiveTvRecording) -> Unit,
    onMenu: (LiveTvRecording) -> Unit,
    onDeleteAll: () -> Unit,
    onClose: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    PlatformBackHandler(enabled = true, onBack = onClose)
    val first = show.episodes.first()
    val newest = show.episodes.maxBy { recordingAiredMs(it) }
    Column(modifier.fillMaxSize().background(tokens.colors.background).statusBarsPadding().liveTvSideInsets()) {
        NuvioScreenHeader(title = show.title, includeStatusBarPadding = false, onBack = onClose,
            modifier = Modifier.padding(horizontal = NuvioTokens.Space.s8))
        key(show.key) {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = NuvioTokens.Space.s16, end = NuvioTokens.Space.s16,
                    bottom = nuvioSafeBottomPadding(NuvioTokens.Space.s16)),
                verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
            ) {
                item(key = "summary") {
                    Row(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s16), verticalAlignment = Alignment.CenterVertically) {
                        PosterBox(poster, newest.channelLogo, Modifier.width(96.dp).aspectRatio(2f / 3f))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6)) {
                            Text(show.title, style = MaterialTheme.typography.titleLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold)
                            Text(listOfNotNull(
                                pluralStringResource(Res.plurals.livetv_rec_episodes, show.episodes.size, show.episodes.size),
                                RecordingRules.durationLabel(show.episodes.sumOf { it.durationSec }).takeIf { show.episodes.sumOf { it.durationSec } > 0 },
                                RecordingRules.sizeLabel(show.episodes.sumOf { it.sizeBytes }).takeIf { show.episodes.sumOf { it.sizeBytes } > 0 },
                            ).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted)
                            TextButton(onClick = { onPlay(first) }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Text(stringResource(Res.string.action_play) + " · " + recordingEpisodeLabel(first))
                            }
                        }
                    }
                }
                networkWarning?.let { warning -> item(key = "network") {
                    Text(warning, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted)
                } }
                items(show.episodes, key = { recordingEntryKey(it) }) { rec ->
                    RecordingRow(rec, now = recordingAiredMs(rec), onClick = { onPlay(rec) }, onLongClick = { onMenu(rec) },
                        title = recordingEpisodeLabel(rec), statusLine = statusLines[recordingEntryKey(rec)],
                        synopsis = guideDescriptionText(rec.description)?.trim()?.takeIf { it.isNotEmpty() })
                }
                item(key = "delete") {
                    TextButton(onClick = onDeleteAll, enabled = deleteEnabled, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = tokens.colors.danger)
                        Text(stringResource(Res.string.livetv_rec_delete_all), color = tokens.colors.danger)
                    }
                }
            }
        }
    }
}

private fun recordingEpisodeLabel(rec: LiveTvRecording): String =
    rec.episode?.takeIf { it.isNotBlank() } ?: SportsDates.dayLabel(recordingAiredMs(rec))

@Composable
internal fun DeleteRecordedShowDialog(show: RecordedShow, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    DialogSurface(onDismissRequest = onDismiss, title = stringResource(Res.string.livetv_rec_delete_show_title, show.title)) {
        Text(pluralStringResource(Res.plurals.livetv_rec_episodes_delete, show.episodes.size, show.episodes.size),
            style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textPrimary)
        Text(stringResource(Res.string.livetv_rec_delete_show_warning), style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted)
        if (show.episodes.any { NetworkLibrary.isNetwork(it) }) {
            Text(stringResource(Res.string.livetv_rec_shared_delete_warning), style = MaterialTheme.typography.bodySmall, color = tokens.colors.danger)
        }
        DialogButtons {
            DialogButton(text = stringResource(Res.string.action_cancel), onClick = onDismiss)
            DialogButton(text = stringResource(Res.string.action_delete), style = DialogButtonStyle.Destructive, onClick = onConfirm)
        }
    }
}

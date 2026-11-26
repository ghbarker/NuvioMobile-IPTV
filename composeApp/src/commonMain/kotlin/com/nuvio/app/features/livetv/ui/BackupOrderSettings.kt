package com.nuvio.app.features.livetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.model.BackupOrder
import com.nuvio.app.features.livetv.model.LiveTvSettings
import com.nuvio.app.features.livetv.sports.SportsAddonSource
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_close
import nuvio.composeapp.generated.resources.livetv_backup_order_by_quality
import nuvio.composeapp.generated.resources.livetv_backup_order_by_source
import nuvio.composeapp.generated.resources.livetv_backup_order_group_by
import nuvio.composeapp.generated.resources.livetv_backup_order_group_by_hint
import nuvio.composeapp.generated.resources.livetv_backup_order_no_headings
import nuvio.composeapp.generated.resources.livetv_backup_order_no_sources
import nuvio.composeapp.generated.resources.livetv_backup_order_qualities
import nuvio.composeapp.generated.resources.livetv_backup_order_qualities_hint
import nuvio.composeapp.generated.resources.livetv_backup_order_quality_unknown
import nuvio.composeapp.generated.resources.livetv_backup_order_reset
import nuvio.composeapp.generated.resources.livetv_backup_order_sources
import nuvio.composeapp.generated.resources.livetv_backup_order_sources_hint
import nuvio.composeapp.generated.resources.livetv_backup_order_title
import nuvio.composeapp.generated.resources.livetv_backups_addon_tag
import nuvio.composeapp.generated.resources.livetv_backups_playlist_tag
import nuvio.composeapp.generated.resources.livetv_game_sort_sources
import nuvio.composeapp.generated.resources.livetv_game_sort_sources_hint
import nuvio.composeapp.generated.resources.livetv_game_sort_apply
import nuvio.composeapp.generated.resources.livetv_game_sort_reset
import nuvio.composeapp.generated.resources.livetv_source_move_up
import nuvio.composeapp.generated.resources.livetv_source_move_down
import org.jetbrains.compose.resources.stringResource

/** The heading a quality gets in this settings page ("Quality not in the name" for none). */
@Composable
internal fun backupQualityLabel(q: String): String =
    if (q == BackupOrder.UNKNOWN) stringResource(Res.string.livetv_backup_order_quality_unknown) else BackupOrder.qualityHeading(q, "")

/** What Group by shows now. */
@Composable
internal fun backupGroupByLabel(mode: String): String = stringResource(
    when (mode) {
        BackupOrder.QUALITY -> Res.string.livetv_backup_order_by_quality
        BackupOrder.NONE -> Res.string.livetv_backup_order_no_headings
        else -> Res.string.livetv_backup_order_by_source
    }
)

/**
 * Settings › Live TV › Watching › Backups & Game Channels: the headings in the player's Backups
 * list, and the order of playlists, add-ons and qualities. Shared with the TVs (where it also
 * orders a game's channels in Quick Channels).
 */
@Composable
internal fun BackupOrderDialog(onDismiss: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val settings by LiveTvPreferences.settings.collectAsStateWithLifecycle()
    val playlists by LiveTvPreferences.playlists.collectAsStateWithLifecycle()
    val addons = remember { runCatching { SportsAddonSource.liveStreamAddonNames() }.getOrDefault(emptyList()) }
    val names = playlists.associate { it.id to it.name }
    val sources = BackupOrder.sources(settings, playlists.map { it.id }, addons)
    val qualities = BackupOrder.qualities(settings)
    val mode = BackupOrder.groupBy(settings)
    fun update(transform: (com.nuvio.app.features.livetv.model.LiveTvSettings) -> com.nuvio.app.features.livetv.model.LiveTvSettings) =
        LiveTvPreferences.updateSettings(transform)

    DialogSurface(onDismissRequest = onDismiss, title = stringResource(Res.string.livetv_backup_order_title)) {
        Column(
            modifier = Modifier.weight(1f, fill = false).heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
        ) {
            OrderHeading(stringResource(Res.string.livetv_backup_order_group_by), stringResource(Res.string.livetv_backup_order_group_by_hint))
            Row(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6), modifier = Modifier.fillMaxWidth()) {
                listOf(BackupOrder.SOURCE, BackupOrder.QUALITY, BackupOrder.NONE).forEach { m ->
                    val picked = m == mode
                    val shape = RoundedCornerShape(NuvioTokens.Radius.lg)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(shape)
                            .background(if (picked) tokens.colors.accent.copy(alpha = 0.22f) else tokens.colors.surface)
                            .border(if (picked) 1.5.dp else 0.dp, if (picked) tokens.colors.accent else Color.Transparent, shape)
                            .clickable { update { it.copy(backupGroupBy = m) } }
                            .padding(horizontal = NuvioTokens.Space.s6, vertical = NuvioTokens.Space.s8),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(backupGroupByLabel(m), style = MaterialTheme.typography.labelLarge, maxLines = 2,
                            color = if (picked) tokens.colors.textPrimary else tokens.colors.textSecondary,
                            fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }

            OrderHeading(stringResource(Res.string.livetv_backup_order_sources), stringResource(Res.string.livetv_backup_order_sources_hint))
            SourceOrderRows(sources, names) { order -> update { BackupOrder.withSources(it, order) } }

            OrderHeading(stringResource(Res.string.livetv_backup_order_qualities), stringResource(Res.string.livetv_backup_order_qualities_hint))
            qualities.forEachIndexed { i, q ->
                OrderRow(
                    title = backupQualityLabel(q), tag = null,
                    canUp = i > 0, canDown = i < qualities.lastIndex,
                    onUp = { update { BackupOrder.withQualities(it, moved(qualities, i, -1)) } },
                    onDown = { update { BackupOrder.withQualities(it, moved(qualities, i, 1)) } },
                )
            }
        }
        DialogButtons {
            DialogButton(text = stringResource(Res.string.livetv_backup_order_reset), onClick = { update { BackupOrder.reset(it) } })
            DialogButton(text = stringResource(Res.string.action_close), onClick = onDismiss)
        }
    }
}

/** Edit the existing Backups order without leaving a game. Arrow taps are a draft until Apply. */
@Composable
internal fun GameSourceOrderButton(
    settings: LiveTvSettings,
    playlistNames: Map<String, String>,
    gameAddons: List<String>,
    update: ((LiveTvSettings) -> LiveTvSettings) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Chip(label = stringResource(Res.string.livetv_game_sort_sources),
        selected = BackupOrder.groupBy(settings) == BackupOrder.SOURCE && settings.backupSourceOrder.isNotBlank(),
        onClick = { open = true })
    if (!open) return
    val addons = remember(gameAddons) {
        (runCatching { SportsAddonSource.liveStreamAddonNames() }.getOrDefault(emptyList()) + gameAddons).distinct()
    }
    val known = BackupOrder.sources(settings, playlistNames.keys.toList(), addons)
    var draft by remember { mutableStateOf<List<String>?>(null) }
    val order = draft?.let { saved -> saved.filter { it in known } + known.filter { it !in saved } } ?: known
    DialogSurface(onDismissRequest = { open = false }, title = stringResource(Res.string.livetv_game_sort_sources),
        message = stringResource(Res.string.livetv_game_sort_sources_hint)) {
        Column(Modifier.weight(1f, fill = false).heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6)) {
            SourceOrderRows(order, playlistNames) { draft = it }
        }
        DialogButtons {
            if (settings.backupSourceOrder.isNotBlank()) DialogButton(text = stringResource(Res.string.livetv_game_sort_reset),
                onClick = { update { it.copy(backupSourceOrder = "") }; open = false })
            DialogButton(text = stringResource(Res.string.action_close), onClick = { open = false })
            DialogButton(text = stringResource(Res.string.livetv_game_sort_apply), style = DialogButtonStyle.Primary,
                enabled = order.isNotEmpty(), onClick = { update { BackupOrder.withGameSources(it, order) }; open = false })
        }
    }
}

private fun <T> moved(list: List<T>, index: Int, by: Int): List<T> {
    val to = index + by
    if (to !in list.indices) return list
    return list.toMutableList().apply { add(to, removeAt(index)) }
}

@Composable
private fun SourceOrderRows(sources: List<String>, playlistNames: Map<String, String>, onMove: (List<String>) -> Unit) {
    val tokens = MaterialTheme.nuvio
    if (sources.isEmpty()) Text(stringResource(Res.string.livetv_backup_order_no_sources),
        style = MaterialTheme.typography.bodySmall, color = tokens.colors.textSecondary)
    sources.forEachIndexed { i, key ->
        val addon = BackupOrder.isAddonKey(key)
        OrderRow(
            title = if (addon) BackupOrder.addonName(key) else playlistNames[key] ?: key,
            tag = stringResource(if (addon) Res.string.livetv_backups_addon_tag else Res.string.livetv_backups_playlist_tag),
            canUp = i > 0, canDown = i < sources.lastIndex,
            onUp = { onMove(moved(sources, i, -1)) }, onDown = { onMove(moved(sources, i, 1)) },
        )
    }
}

@Composable
private fun OrderHeading(title: String, hint: String) {
    val tokens = MaterialTheme.nuvio
    Column(modifier = Modifier.padding(top = NuvioTokens.Space.s6)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textSecondary)
    }
}

@Composable
private fun OrderRow(title: String, tag: String?, canUp: Boolean, canDown: Boolean, onUp: () -> Unit, onDown: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NuvioTokens.Radius.lg))
            .background(tokens.colors.surface)
            .padding(start = NuvioTokens.Space.s8, end = NuvioTokens.Space.s4, top = NuvioTokens.Space.s4, bottom = NuvioTokens.Space.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (tag != null) Text(tag, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted)
        }
        ArrowButton(Icons.Rounded.ArrowUpward, stringResource(Res.string.livetv_source_move_up, title), canUp, onUp)
        ArrowButton(Icons.Rounded.ArrowDownward, stringResource(Res.string.livetv_source_move_down, title), canDown, onDown)
    }
}

@Composable
private fun ArrowButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .alpha(if (enabled) 1f else 0.3f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tokens.colors.textPrimary, modifier = Modifier.size(NuvioTokens.Icon.sm))
    }
}

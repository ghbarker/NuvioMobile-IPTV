package com.nuvio.app.features.livetv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.format.formatLocalDateTime
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogOption
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.data.LiveTvRepository
import com.nuvio.app.features.livetv.model.EpgSource
import com.nuvio.app.features.livetv.model.PlaylistAccount
import com.nuvio.app.features.livetv.model.PlaylistSource
import com.nuvio.app.features.livetv.sync.LiveTvDriveSync
import com.nuvio.app.features.livetv.sync.SignInPrompt
import com.nuvio.app.features.settings.SettingsGroup
import com.nuvio.app.features.settings.SettingsGroupDivider
import com.nuvio.app.features.settings.SettingsNavigationRow
import com.nuvio.app.features.settings.SettingsSection
import com.nuvio.app.features.settings.SettingsSwitchRow
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.action_delete
import nuvio.composeapp.generated.resources.action_edit
import nuvio.composeapp.generated.resources.action_save
import nuvio.composeapp.generated.resources.livetv_add_epg
import nuvio.composeapp.generated.resources.livetv_add_epg_description
import nuvio.composeapp.generated.resources.livetv_add_m3u
import nuvio.composeapp.generated.resources.livetv_add_m3u_description
import nuvio.composeapp.generated.resources.livetv_add_xtream
import nuvio.composeapp.generated.resources.livetv_add_xtream_description
import nuvio.composeapp.generated.resources.livetv_backup_now
import nuvio.composeapp.generated.resources.livetv_backup_now_description
import nuvio.composeapp.generated.resources.livetv_days_ahead
import nuvio.composeapp.generated.resources.livetv_days_ahead_description
import nuvio.composeapp.generated.resources.livetv_delete_source_message
import nuvio.composeapp.generated.resources.livetv_disable
import nuvio.composeapp.generated.resources.livetv_drive_account
import nuvio.composeapp.generated.resources.livetv_drive_auto_sync
import nuvio.composeapp.generated.resources.livetv_drive_auto_sync_description
import nuvio.composeapp.generated.resources.livetv_drive_body
import nuvio.composeapp.generated.resources.livetv_drive_code_instructions
import nuvio.composeapp.generated.resources.livetv_drive_code_waiting
import nuvio.composeapp.generated.resources.livetv_drive_sign_in
import nuvio.composeapp.generated.resources.livetv_drive_sign_in_description
import nuvio.composeapp.generated.resources.livetv_drive_sign_out
import nuvio.composeapp.generated.resources.livetv_drive_sign_out_description
import nuvio.composeapp.generated.resources.livetv_drive_unavailable
import nuvio.composeapp.generated.resources.livetv_enable
import nuvio.composeapp.generated.resources.livetv_epg_refresh
import nuvio.composeapp.generated.resources.livetv_epg_refresh_description
import nuvio.composeapp.generated.resources.livetv_field_name
import nuvio.composeapp.generated.resources.livetv_field_password
import nuvio.composeapp.generated.resources.livetv_field_server
import nuvio.composeapp.generated.resources.livetv_field_url
import nuvio.composeapp.generated.resources.livetv_field_user_agent
import nuvio.composeapp.generated.resources.livetv_field_username
import nuvio.composeapp.generated.resources.livetv_field_xmltv_url
import nuvio.composeapp.generated.resources.livetv_load_embedded_epg
import nuvio.composeapp.generated.resources.livetv_never_updated
import nuvio.composeapp.generated.resources.livetv_playlist_refresh
import nuvio.composeapp.generated.resources.livetv_playlist_refresh_description
import nuvio.composeapp.generated.resources.livetv_remember_audio
import nuvio.composeapp.generated.resources.livetv_remember_audio_description
import nuvio.composeapp.generated.resources.livetv_restore
import nuvio.composeapp.generated.resources.livetv_restore_description
import nuvio.composeapp.generated.resources.livetv_restore_message
import nuvio.composeapp.generated.resources.livetv_section_drive
import nuvio.composeapp.generated.resources.livetv_section_general
import nuvio.composeapp.generated.resources.livetv_section_guides
import nuvio.composeapp.generated.resources.livetv_section_playlists
import nuvio.composeapp.generated.resources.livetv_section_watching
import nuvio.composeapp.generated.resources.livetv_show_tab
import nuvio.composeapp.generated.resources.livetv_show_tab_description
import nuvio.composeapp.generated.resources.livetv_stream_format
import nuvio.composeapp.generated.resources.livetv_stream_format_description
import nuvio.composeapp.generated.resources.livetv_switch_backup
import nuvio.composeapp.generated.resources.livetv_switch_backup_description
import nuvio.composeapp.generated.resources.livetv_update_all
import nuvio.composeapp.generated.resources.livetv_update_all_description
import nuvio.composeapp.generated.resources.livetv_update_now
import nuvio.composeapp.generated.resources.livetv_updated_at
import org.jetbrains.compose.resources.stringResource

private val REFRESH_HOURS = listOf(3, 6, 12, 24, 48, 72, 168)
private val FUTURE_DAYS = listOf(1, 2, 3, 5, 7, 10, 14)

internal fun hoursLabel(h: Int): String = when {
    h >= 24 && h % 24 == 0 -> if (h == 24) "1 day" else "${h / 24} days"
    else -> "$h hours"
}

private sealed interface LiveDialog {
    data object AddM3u : LiveDialog
    data object AddXtream : LiveDialog
    data object AddEpg : LiveDialog
    data class PlaylistActions(val source: PlaylistSource) : LiveDialog
    data class EditPlaylist(val source: PlaylistSource) : LiveDialog
    data class DeletePlaylist(val source: PlaylistSource) : LiveDialog
    data class EpgActions(val source: EpgSource) : LiveDialog
    data class EditEpg(val source: EpgSource) : LiveDialog
    data class DeleteEpg(val source: EpgSource) : LiveDialog
    data object PlaylistRefresh : LiveDialog
    data object EpgRefresh : LiveDialog
    data object FutureDays : LiveDialog
    data object StreamFormat : LiveDialog
    data object ConfirmRestore : LiveDialog
    data object ConfirmSignOut : LiveDialog
}

/** The Live TV page in Settings: playlists, guides, watching options, Google Drive backup. */
internal fun LazyListScope.liveTvSettingsContent(isTablet: Boolean) {
    item(key = "livetv_settings") {
        LiveTvSettingsBody(isTablet)
    }
}

@Composable
private fun LiveTvSettingsBody(isTablet: Boolean) {
    val tokens = MaterialTheme.nuvio
    val playlists by LiveTvPreferences.playlists.collectAsStateWithLifecycle()
    val epgs by LiveTvPreferences.epgSources.collectAsStateWithLifecycle()
    val settings by LiveTvPreferences.settings.collectAsStateWithLifecycle()
    val accounts by LiveTvPreferences.playlistAccounts.collectAsStateWithLifecycle()
    val status by remember { LiveTvRepository.ensureLoaded(); LiveTvRepository.status }.collectAsStateWithLifecycle()
    val drive by remember { LiveTvDriveSync.start(); LiveTvDriveSync.state }.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<LiveDialog?>(null) }
    fun update(transform: (com.nuvio.app.features.livetv.model.LiveTvSettings) -> com.nuvio.app.features.livetv.model.LiveTvSettings) =
        LiveTvPreferences.updateSettings(transform)

    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.listGap)) {
        SettingsSection(title = stringResource(Res.string.livetv_section_playlists), isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                playlists.forEachIndexed { i, pl ->
                    if (i > 0) SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = pl.name,
                        description = playlistStatus(pl, accounts[pl.id]),
                        icon = if (pl.isXtream) Icons.Filled.Login else Icons.Filled.ListAlt,
                        isTablet = isTablet,
                        onClick = { dialog = LiveDialog.PlaylistActions(pl) },
                    )
                }
                if (playlists.isNotEmpty()) SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_add_m3u),
                    description = stringResource(Res.string.livetv_add_m3u_description),
                    icon = Icons.Filled.Add,
                    isTablet = isTablet,
                    onClick = { dialog = LiveDialog.AddM3u },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_add_xtream),
                    description = stringResource(Res.string.livetv_add_xtream_description),
                    icon = Icons.Filled.Add,
                    isTablet = isTablet,
                    onClick = { dialog = LiveDialog.AddXtream },
                )
            }
        }

        SettingsSection(title = stringResource(Res.string.livetv_section_guides), isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                epgs.forEachIndexed { i, src ->
                    if (i > 0) SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = src.name,
                        description = epgStatus(src),
                        icon = Icons.Filled.Schedule,
                        isTablet = isTablet,
                        onClick = { dialog = LiveDialog.EpgActions(src) },
                    )
                }
                if (epgs.isNotEmpty()) SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_add_epg),
                    description = stringResource(Res.string.livetv_add_epg_description),
                    icon = Icons.Filled.Add,
                    isTablet = isTablet,
                    onClick = { dialog = LiveDialog.AddEpg },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_days_ahead),
                    description = stringResource(Res.string.livetv_days_ahead_description),
                    isTablet = isTablet,
                    trailingContent = { ValueLabel(if (settings.epgFutureDays == 1) "1 day" else "${settings.epgFutureDays} days") },
                    onClick = { dialog = LiveDialog.FutureDays },
                )
            }
        }

        SettingsSection(title = stringResource(Res.string.livetv_section_general), isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_update_all),
                    description = if (status.loading) status.message else stringResource(Res.string.livetv_update_all_description),
                    isTablet = isTablet,
                    enabled = !status.loading,
                    trailingContent = { if (status.loading) NuvioLoadingIndicator(size = NuvioTokens.Space.s20) },
                    onClick = { LiveTvRepository.refreshAll(force = true) },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_playlist_refresh),
                    description = stringResource(Res.string.livetv_playlist_refresh_description),
                    isTablet = isTablet,
                    trailingContent = { ValueLabel(hoursLabel(settings.playlistRefreshHours)) },
                    onClick = { dialog = LiveDialog.PlaylistRefresh },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_epg_refresh),
                    description = stringResource(Res.string.livetv_epg_refresh_description),
                    isTablet = isTablet,
                    trailingContent = { ValueLabel(hoursLabel(settings.epgRefreshHours)) },
                    onClick = { dialog = LiveDialog.EpgRefresh },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_show_tab),
                    description = stringResource(Res.string.livetv_show_tab_description),
                    checked = settings.showInSidebar,
                    isTablet = isTablet,
                    onCheckedChange = { on -> update { it.copy(showInSidebar = on) } },
                )
            }
        }

        SettingsSection(title = stringResource(Res.string.livetv_section_watching), isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_stream_format),
                    description = stringResource(Res.string.livetv_stream_format_description),
                    isTablet = isTablet,
                    trailingContent = { ValueLabel(streamFormatLabel(playlists.firstOrNull { it.isXtream }?.streamFormat ?: "auto")) },
                    onClick = { dialog = LiveDialog.StreamFormat },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_switch_backup),
                    description = stringResource(Res.string.livetv_switch_backup_description),
                    checked = settings.autoBackupChannel,
                    isTablet = isTablet,
                    onCheckedChange = { on -> update { it.copy(autoBackupChannel = on) } },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_remember_audio),
                    description = stringResource(Res.string.livetv_remember_audio_description),
                    checked = true,
                    enabled = false,
                    isTablet = isTablet,
                    onCheckedChange = {},
                )
            }
        }

        SettingsSection(title = stringResource(Res.string.livetv_section_drive), isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                when {
                    !drive.available -> Text(
                        text = stringResource(Res.string.livetv_drive_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.colors.textMuted,
                        modifier = Modifier.padding(NuvioTokens.Space.s16),
                    )
                    !drive.connected -> {
                        Text(
                            text = stringResource(Res.string.livetv_drive_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.colors.textMuted,
                            modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s12),
                        )
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsNavigationRow(
                            title = stringResource(Res.string.livetv_drive_sign_in),
                            description = stringResource(Res.string.livetv_drive_sign_in_description),
                            icon = Icons.Filled.Backup,
                            isTablet = isTablet,
                            enabled = !drive.busy,
                            onClick = { LiveTvDriveSync.beginSignIn() },
                        )
                    }
                    else -> {
                        SettingsNavigationRow(
                            title = stringResource(Res.string.livetv_drive_account),
                            description = (drive.email ?: "Connected") + (drive.lastSyncMs.takeIf { it > 0 }?.let { " · " + formatLocalDateTime(it) } ?: ""),
                            icon = Icons.Filled.Backup,
                            isTablet = isTablet,
                            onClick = {},
                        )
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsSwitchRow(
                            title = stringResource(Res.string.livetv_drive_auto_sync),
                            description = stringResource(Res.string.livetv_drive_auto_sync_description),
                            checked = drive.autoSync,
                            isTablet = isTablet,
                            onCheckedChange = LiveTvDriveSync::setAutoSync,
                        )
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsNavigationRow(
                            title = stringResource(Res.string.livetv_backup_now),
                            description = stringResource(Res.string.livetv_backup_now_description),
                            isTablet = isTablet,
                            enabled = !drive.busy,
                            trailingContent = { if (drive.busy) NuvioLoadingIndicator(size = NuvioTokens.Space.s20) },
                            onClick = { LiveTvDriveSync.backUpNow() },
                        )
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsNavigationRow(
                            title = stringResource(Res.string.livetv_restore),
                            description = stringResource(Res.string.livetv_restore_description),
                            isTablet = isTablet,
                            enabled = !drive.busy,
                            onClick = { dialog = LiveDialog.ConfirmRestore },
                        )
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsNavigationRow(
                            title = stringResource(Res.string.livetv_drive_sign_out),
                            description = stringResource(Res.string.livetv_drive_sign_out_description),
                            isTablet = isTablet,
                            onClick = { dialog = LiveDialog.ConfirmSignOut },
                        )
                    }
                }
                drive.message?.let {
                    SettingsGroupDivider(isTablet = isTablet)
                    Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textSecondary, modifier = Modifier.padding(NuvioTokens.Space.s16))
                }
            }
        }
    }

    drive.signIn?.let { prompt -> DriveSignInDialog(prompt, onCancel = { LiveTvDriveSync.cancelSignIn() }) }

    val close = { dialog = null }
    when (val d = dialog) {
        null -> Unit
        LiveDialog.AddM3u -> M3uForm(null, onDismiss = close)
        LiveDialog.AddXtream -> XtreamForm(null, onDismiss = close)
        LiveDialog.AddEpg -> EpgForm(null, onDismiss = close)
        is LiveDialog.EditPlaylist -> if (d.source.isXtream) XtreamForm(d.source, onDismiss = close) else M3uForm(d.source, onDismiss = close)
        is LiveDialog.EditEpg -> EpgForm(d.source, onDismiss = close)
        is LiveDialog.PlaylistActions -> ActionsDialog(
            title = d.source.name,
            subtitle = playlistStatus(d.source, accounts[d.source.id]),
            onDismiss = close,
            actions = listOf(
                stringResource(Res.string.action_edit) to { dialog = LiveDialog.EditPlaylist(d.source) },
                stringResource(Res.string.livetv_update_now) to { LiveTvRepository.updatePlaylistNow(d.source.id); close() },
                stringResource(if (d.source.enabled) Res.string.livetv_disable else Res.string.livetv_enable) to {
                    LiveTvRepository.setPlaylistEnabled(d.source.id, !d.source.enabled); close()
                },
                stringResource(Res.string.action_delete) to { dialog = LiveDialog.DeletePlaylist(d.source) },
            ),
        )
        is LiveDialog.EpgActions -> ActionsDialog(
            title = d.source.name,
            subtitle = epgStatus(d.source),
            onDismiss = close,
            actions = listOf(
                stringResource(Res.string.action_edit) to { dialog = LiveDialog.EditEpg(d.source) },
                stringResource(Res.string.livetv_update_now) to { LiveTvRepository.updateEpg(d.source); close() },
                stringResource(if (d.source.enabled) Res.string.livetv_disable else Res.string.livetv_enable) to {
                    LiveTvRepository.setEpgEnabled(d.source.id, !d.source.enabled); close()
                },
                stringResource(Res.string.action_delete) to { dialog = LiveDialog.DeleteEpg(d.source) },
            ),
        )
        is LiveDialog.DeletePlaylist -> ConfirmDialog(
            title = stringResource(Res.string.action_delete) + " \"${d.source.name}\"?",
            message = stringResource(Res.string.livetv_delete_source_message),
            confirm = stringResource(Res.string.action_delete),
            destructive = true,
            onDismiss = close,
        ) { LiveTvRepository.removePlaylist(d.source.id) }
        is LiveDialog.DeleteEpg -> ConfirmDialog(
            title = stringResource(Res.string.action_delete) + " \"${d.source.name}\"?",
            message = stringResource(Res.string.livetv_delete_source_message),
            confirm = stringResource(Res.string.action_delete),
            destructive = true,
            onDismiss = close,
        ) { LiveTvRepository.removeEpg(d.source.id) }
        LiveDialog.PlaylistRefresh -> ChoiceDialog(
            title = stringResource(Res.string.livetv_playlist_refresh),
            options = REFRESH_HOURS.map { it to hoursLabel(it) },
            selected = settings.playlistRefreshHours,
            onDismiss = close,
        ) { v -> update { it.copy(playlistRefreshHours = v) } }
        LiveDialog.EpgRefresh -> ChoiceDialog(
            title = stringResource(Res.string.livetv_epg_refresh),
            options = REFRESH_HOURS.map { it to hoursLabel(it) },
            selected = settings.epgRefreshHours,
            onDismiss = close,
        ) { v -> update { it.copy(epgRefreshHours = v) } }
        LiveDialog.FutureDays -> ChoiceDialog(
            title = stringResource(Res.string.livetv_days_ahead),
            options = FUTURE_DAYS.map { it to if (it == 1) "1 day" else "$it days" },
            selected = settings.epgFutureDays,
            onDismiss = close,
        ) { v -> update { it.copy(epgFutureDays = v) }; LiveTvRepository.refreshEpgOnly() }
        LiveDialog.StreamFormat -> ChoiceDialog(
            title = stringResource(Res.string.livetv_stream_format),
            options = listOf("auto" to "Auto", "ts" to "MPEG-TS", "m3u8" to "HLS (m3u8)"),
            selected = playlists.firstOrNull { it.isXtream }?.streamFormat ?: "auto",
            onDismiss = close,
        ) { v ->
            playlists.filter { it.isXtream && it.streamFormat != v }.forEach { LiveTvRepository.updatePlaylist(it.copy(streamFormat = v)) }
        }
        LiveDialog.ConfirmRestore -> ConfirmDialog(
            title = stringResource(Res.string.livetv_restore) + "?",
            message = stringResource(Res.string.livetv_restore_message),
            confirm = stringResource(Res.string.livetv_restore),
            onDismiss = close,
        ) { LiveTvDriveSync.restoreNow() }
        LiveDialog.ConfirmSignOut -> ConfirmDialog(
            title = stringResource(Res.string.livetv_drive_sign_out) + "?",
            message = stringResource(Res.string.livetv_drive_sign_out_description),
            confirm = stringResource(Res.string.livetv_drive_sign_out),
            onDismiss = close,
        ) { LiveTvDriveSync.disconnect() }
    }
}

@Composable
private fun ValueLabel(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.nuvio.colors.accent)
}

private fun streamFormatLabel(f: String) = when (f) { "ts" -> "MPEG-TS"; "m3u8" -> "HLS (m3u8)"; else -> "Auto" }

@Composable
private fun playlistStatus(pl: PlaylistSource, account: PlaylistAccount?): String {
    val parts = ArrayList<String>()
    if (!pl.enabled) parts += "Off"
    if (pl.lastError != null) parts += pl.lastError
    else if (pl.lastUpdatedMs > 0) parts += stringResource(Res.string.livetv_updated_at, formatLocalDateTime(pl.lastUpdatedMs))
    else parts += stringResource(Res.string.livetv_never_updated)
    if (pl.channelCount > 0) parts += "${pl.channelCount} channels"
    account?.let { a ->
        a.daysLeftLabel()?.let { parts += if (it == "expired") "Expired" else "Expires in $it" }
        if (a.status.isNotBlank() && !a.status.equals("Active", true)) parts += a.status
        if (a.maxConnections > 0) parts += "${a.activeConnections}/${a.maxConnections} connections"
    }
    return parts.joinToString(" · ")
}

@Composable
private fun epgStatus(src: EpgSource): String {
    val parts = ArrayList<String>()
    if (!src.enabled) parts += "Off"
    if (src.lastError != null) parts += src.lastError
    else if (src.lastUpdatedMs > 0) parts += stringResource(Res.string.livetv_updated_at, formatLocalDateTime(src.lastUpdatedMs))
    else parts += stringResource(Res.string.livetv_never_updated)
    return parts.joinToString(" · ")
}

// ---------------------------------------------------------------- dialogs

@Composable
private fun ActionsDialog(title: String, subtitle: String?, actions: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    DialogSurface(onDismissRequest = onDismiss, title = title, message = subtitle) {
        Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6)) {
            actions.forEach { (label, act) -> DialogOption(text = label, selected = false, onClick = act) }
        }
        DialogButtons { DialogButton(text = stringResource(Res.string.action_cancel), onClick = onDismiss) }
    }
}

@Composable
private fun ConfirmDialog(title: String, message: String, confirm: String, destructive: Boolean = false, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    DialogSurface(onDismissRequest = onDismiss, title = title, message = message) {
        DialogButtons {
            DialogButton(text = stringResource(Res.string.action_cancel), onClick = onDismiss)
            DialogButton(text = confirm, style = if (destructive) DialogButtonStyle.Destructive else DialogButtonStyle.Primary, onClick = { onConfirm(); onDismiss() })
        }
    }
}

@Composable
private fun <T> ChoiceDialog(title: String, options: List<Pair<T, String>>, selected: T, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    DialogSurface(onDismissRequest = onDismiss, title = title) {
        Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6)) {
            options.forEach { (v, label) -> DialogOption(text = label, selected = v == selected, onClick = { onPick(v); onDismiss() }) }
        }
        DialogButtons { DialogButton(text = stringResource(Res.string.action_cancel), onClick = onDismiss) }
    }
}

@Composable
private fun M3uForm(existing: PlaylistSource?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var url by remember { mutableStateOf(existing?.url.orEmpty()) }
    var ua by remember { mutableStateOf(existing?.userAgent.orEmpty()) }
    var embedded by remember { mutableStateOf(existing?.useEmbeddedEpg ?: true) }
    DialogSurface(onDismissRequest = onDismiss, title = stringResource(if (existing == null) Res.string.livetv_add_m3u else Res.string.action_edit)) {
        NuvioInputField(value = name, onValueChange = { name = it }, placeholder = stringResource(Res.string.livetv_field_name))
        NuvioInputField(value = url, onValueChange = { url = it }, placeholder = stringResource(Res.string.livetv_field_url))
        NuvioInputField(value = ua, onValueChange = { ua = it }, placeholder = stringResource(Res.string.livetv_field_user_agent))
        SettingsSwitchRow(title = stringResource(Res.string.livetv_load_embedded_epg), checked = embedded, isTablet = false, horizontalPadding = 0.dp, onCheckedChange = { embedded = it })
        DialogButtons {
            DialogButton(text = stringResource(Res.string.action_cancel), onClick = onDismiss)
            DialogButton(
                text = stringResource(Res.string.action_save),
                style = DialogButtonStyle.Primary,
                enabled = url.isNotBlank(),
                onClick = {
                    if (existing == null) LiveTvRepository.addPlaylist(name, url, ua)
                    else LiveTvRepository.updatePlaylist(existing.copy(name = name.ifBlank { "Playlist" }, url = url.trim(), userAgent = ua.trim(), useEmbeddedEpg = embedded))
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun XtreamForm(existing: PlaylistSource?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var server by remember { mutableStateOf(existing?.xtreamServer.orEmpty()) }
    var user by remember { mutableStateOf(existing?.xtreamUsername.orEmpty()) }
    var pass by remember { mutableStateOf(existing?.xtreamPassword.orEmpty()) }
    var ua by remember { mutableStateOf(existing?.userAgent.orEmpty()) }
    var embedded by remember { mutableStateOf(existing?.useEmbeddedEpg ?: true) }
    DialogSurface(onDismissRequest = onDismiss, title = stringResource(if (existing == null) Res.string.livetv_add_xtream else Res.string.action_edit)) {
        NuvioInputField(value = name, onValueChange = { name = it }, placeholder = stringResource(Res.string.livetv_field_name))
        NuvioInputField(value = server, onValueChange = { server = it }, placeholder = stringResource(Res.string.livetv_field_server))
        NuvioInputField(value = user, onValueChange = { user = it }, placeholder = stringResource(Res.string.livetv_field_username))
        NuvioInputField(value = pass, onValueChange = { pass = it }, placeholder = stringResource(Res.string.livetv_field_password))
        NuvioInputField(value = ua, onValueChange = { ua = it }, placeholder = stringResource(Res.string.livetv_field_user_agent))
        SettingsSwitchRow(title = stringResource(Res.string.livetv_load_embedded_epg), checked = embedded, isTablet = false, horizontalPadding = 0.dp, onCheckedChange = { embedded = it })
        DialogButtons {
            DialogButton(text = stringResource(Res.string.action_cancel), onClick = onDismiss)
            DialogButton(
                text = stringResource(Res.string.action_save),
                style = DialogButtonStyle.Primary,
                enabled = server.isNotBlank() && (PlaylistSource.credentialsFromLink(server) != null || (user.isNotBlank() && pass.isNotBlank())),
                onClick = {
                    if (existing == null) LiveTvRepository.addXtream(name, server, user, pass)
                    else LiveTvRepository.updatePlaylist(
                        existing.copy(name = name.ifBlank { "Xtream" }, xtreamServer = server, xtreamUsername = user, xtreamPassword = pass, userAgent = ua.trim(), useEmbeddedEpg = embedded)
                    )
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun EpgForm(existing: EpgSource?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var url by remember { mutableStateOf(existing?.url.orEmpty()) }
    DialogSurface(onDismissRequest = onDismiss, title = stringResource(if (existing == null) Res.string.livetv_add_epg else Res.string.action_edit)) {
        NuvioInputField(value = name, onValueChange = { name = it }, placeholder = stringResource(Res.string.livetv_field_name))
        NuvioInputField(value = url, onValueChange = { url = it }, placeholder = stringResource(Res.string.livetv_field_xmltv_url))
        DialogButtons {
            DialogButton(text = stringResource(Res.string.action_cancel), onClick = onDismiss)
            DialogButton(
                text = stringResource(Res.string.action_save),
                style = DialogButtonStyle.Primary,
                enabled = url.isNotBlank(),
                onClick = {
                    if (existing == null) LiveTvRepository.addEpg(name, url)
                    else LiveTvRepository.updateEpg(existing.copy(name = name.ifBlank { "EPG" }, url = url.trim()))
                    onDismiss()
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DriveSignInDialog(prompt: SignInPrompt, onCancel: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val shortUrl = prompt.url.removePrefix("https://").removePrefix("http://")
    DialogSurface(onDismissRequest = onCancel, title = stringResource(Res.string.livetv_drive_sign_in)) {
        Text(
            text = stringResource(Res.string.livetv_drive_code_instructions, shortUrl),
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = prompt.userCode,
            style = MaterialTheme.typography.displaySmall,
            color = tokens.colors.accent,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8, Alignment.CenterHorizontally)) {
            Chip(label = shortUrl, selected = true, onClick = { runCatching { uriHandler.openUri(prompt.qrUrl) } })
        }
        Text(
            text = stringResource(Res.string.livetv_drive_code_waiting),
            style = MaterialTheme.typography.bodySmall,
            color = tokens.colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        DialogButtons { DialogButton(text = stringResource(Res.string.action_cancel), onClick = onCancel) }
    }
}

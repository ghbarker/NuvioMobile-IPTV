package com.nuvio.app.features.livetv.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.format.formatLocalDateTime
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.NuvioSectionLabel
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.livetv.sync.CloudChoice
import com.nuvio.app.features.livetv.sync.CloudPrompt
import com.nuvio.app.features.livetv.sync.LiveTvCloudSync
import com.nuvio.app.features.livetv.sync.SyncPart
import com.nuvio.app.features.livetv.sync.SyncParts
import com.nuvio.app.features.settings.SettingsGroup
import com.nuvio.app.features.settings.SettingsGroupDivider
import com.nuvio.app.features.settings.SettingsNavigationRow
import com.nuvio.app.features.settings.SettingsSwitchRow
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.action_continue
import nuvio.composeapp.generated.resources.livetv_calendar
import nuvio.composeapp.generated.resources.livetv_settings_sync_calendar_description
import nuvio.composeapp.generated.resources.livetv_settings_sync_choose_message
import nuvio.composeapp.generated.resources.livetv_settings_sync_choose_title
import nuvio.composeapp.generated.resources.livetv_settings_sync_combine
import nuvio.composeapp.generated.resources.livetv_settings_sync_delete
import nuvio.composeapp.generated.resources.livetv_settings_sync_delete_confirm
import nuvio.composeapp.generated.resources.livetv_settings_sync_delete_description
import nuvio.composeapp.generated.resources.livetv_settings_sync_delete_message
import nuvio.composeapp.generated.resources.livetv_settings_sync_description
import nuvio.composeapp.generated.resources.livetv_settings_sync_favorites
import nuvio.composeapp.generated.resources.livetv_settings_sync_favorites_description
import nuvio.composeapp.generated.resources.livetv_settings_sync_last_synced
import nuvio.composeapp.generated.resources.livetv_settings_sync_logins
import nuvio.composeapp.generated.resources.livetv_settings_sync_logins_description
import nuvio.composeapp.generated.resources.livetv_settings_sync_look
import nuvio.composeapp.generated.resources.livetv_settings_sync_look_description
import nuvio.composeapp.generated.resources.livetv_settings_sync_not_now
import nuvio.composeapp.generated.resources.livetv_settings_sync_now
import nuvio.composeapp.generated.resources.livetv_settings_sync_now_description
import nuvio.composeapp.generated.resources.livetv_settings_sync_off
import nuvio.composeapp.generated.resources.livetv_settings_sync_off_confirm
import nuvio.composeapp.generated.resources.livetv_settings_sync_off_description
import nuvio.composeapp.generated.resources.livetv_settings_sync_off_message
import nuvio.composeapp.generated.resources.livetv_settings_sync_on
import nuvio.composeapp.generated.resources.livetv_settings_sync_parts
import nuvio.composeapp.generated.resources.livetv_settings_sync_restore
import nuvio.composeapp.generated.resources.livetv_settings_sync_restore_confirm
import nuvio.composeapp.generated.resources.livetv_settings_sync_restore_description
import nuvio.composeapp.generated.resources.livetv_settings_sync_restore_message
import nuvio.composeapp.generated.resources.livetv_settings_sync_save_action
import nuvio.composeapp.generated.resources.livetv_settings_sync_save_message
import nuvio.composeapp.generated.resources.livetv_settings_sync_save_title
import nuvio.composeapp.generated.resources.livetv_settings_sync_sign_in
import nuvio.composeapp.generated.resources.livetv_settings_sync_sports_description
import nuvio.composeapp.generated.resources.livetv_settings_sync_title
import nuvio.composeapp.generated.resources.livetv_settings_sync_turn_on
import nuvio.composeapp.generated.resources.livetv_settings_sync_unavailable
import nuvio.composeapp.generated.resources.livetv_settings_sync_use_saved
import nuvio.composeapp.generated.resources.livetv_sports
import org.jetbrains.compose.resources.stringResource

/**
 * The two questions Setup Sync asks, shown over whatever screen is open: save this
 * device's Live TV setup, or choose between the saved setup and this one.
 */
@Composable
internal fun LiveTvCloudSyncPrompts() {
    val state by remember { LiveTvCloudSync.start(); LiveTvCloudSync.state }.collectAsStateWithLifecycle()
    when (state.prompt) {
        CloudPrompt.BackUp -> DialogSurface(
            onDismissRequest = { LiveTvCloudSync.answerBackUp(false) },
            title = stringResource(Res.string.livetv_settings_sync_save_title),
            message = stringResource(Res.string.livetv_settings_sync_save_message),
        ) {
            DialogButtons {
                DialogButton(text = stringResource(Res.string.livetv_settings_sync_not_now), onClick = { LiveTvCloudSync.answerBackUp(false) })
                DialogButton(text = stringResource(Res.string.livetv_settings_sync_save_action), style = DialogButtonStyle.Primary, onClick = { LiveTvCloudSync.answerBackUp(true) })
            }
        }
        CloudPrompt.Choose -> DialogSurface(
            onDismissRequest = { LiveTvCloudSync.answerChoose(CloudChoice.Later) },
            title = stringResource(Res.string.livetv_settings_sync_choose_title),
            message = stringResource(Res.string.livetv_settings_sync_choose_message),
        ) {
            DialogButtons {
                DialogButton(text = stringResource(Res.string.livetv_settings_sync_not_now), onClick = { LiveTvCloudSync.answerChoose(CloudChoice.Later) })
                DialogButton(text = stringResource(Res.string.livetv_settings_sync_use_saved), onClick = { LiveTvCloudSync.answerChoose(CloudChoice.UseSaved) })
                DialogButton(text = stringResource(Res.string.livetv_settings_sync_combine), style = DialogButtonStyle.Primary, onClick = { LiveTvCloudSync.answerChoose(CloudChoice.Combine) })
            }
        }
        null -> Unit
    }
}

private enum class CloudConfirm { Off, Delete, Earlier }

/** Live TV settings: the Setup Sync card (what to sync, turn on or off, sync now, earlier copy, delete). */
@Composable
internal fun LiveTvCloudSyncSection(isTablet: Boolean) {
    val tokens = MaterialTheme.nuvio
    val state by remember { LiveTvCloudSync.start(); LiveTvCloudSync.state }.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<CloudConfirm?>(null) }
    val partsRevision by SyncParts.revision.collectAsStateWithLifecycle()
    val partOn = remember(partsRevision) { SyncPart.entries.associateWith { SyncParts.isOn(it) } }

    NuvioSectionLabel(
        text = stringResource(Res.string.livetv_settings_sync_title),
        modifier = Modifier.padding(horizontal = NuvioTokens.Radius.card).padding(top = NuvioTokens.Space.s16, bottom = NuvioTokens.Space.s10),
    )
    SettingsGroup(isTablet = isTablet) {
        when {
            !state.available -> Text(
                text = stringResource(Res.string.livetv_settings_sync_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
                modifier = Modifier.padding(NuvioTokens.Space.s16),
            )
            !state.signedIn -> Text(
                text = stringResource(Res.string.livetv_settings_sync_sign_in),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
                modifier = Modifier.padding(NuvioTokens.Space.s16),
            )
            !state.enabled -> {
                Text(
                    text = stringResource(Res.string.livetv_settings_sync_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textMuted,
                    modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s12),
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_settings_sync_turn_on),
                    description = state.email,
                    icon = Icons.Filled.Backup,
                    isTablet = isTablet,
                    enabled = !state.busy,
                    trailingContent = { if (state.busy) NuvioLoadingIndicator(size = NuvioTokens.Space.s20) },
                    onClick = { LiveTvCloudSync.turnOn() },
                )
            }
            else -> {
                Text(
                    text = stringResource(Res.string.livetv_settings_sync_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textMuted,
                    modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s12),
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_settings_sync_on),
                    description = (state.email ?: "") + (state.lastSyncMs.takeIf { it > 0 }?.let { " · " + stringResource(Res.string.livetv_settings_sync_last_synced, formatLocalDateTime(it)) } ?: ""),
                    icon = Icons.Filled.Backup,
                    isTablet = isTablet,
                    onClick = {},
                )
                NuvioSectionLabel(
                    text = stringResource(Res.string.livetv_settings_sync_parts),
                    modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16).padding(top = NuvioTokens.Space.s12, bottom = NuvioTokens.Space.s6),
                )
                SyncPart.entries.filter { it != SyncPart.LOOK }.forEach { part ->
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsSwitchRow(
                        title = stringResource(part.labelResource()),
                        description = stringResource(part.descriptionResource()),
                        checked = partOn[part] == true,
                        isTablet = isTablet,
                        onCheckedChange = { on -> SyncParts.set(part, on); LiveTvCloudSync.partsChanged() },
                    )
                }
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_settings_sync_now),
                    description = stringResource(Res.string.livetv_settings_sync_now_description),
                    isTablet = isTablet,
                    enabled = !state.busy,
                    trailingContent = { if (state.busy) NuvioLoadingIndicator(size = NuvioTokens.Space.s20) },
                    onClick = { LiveTvCloudSync.syncNow() },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_settings_sync_restore),
                    description = stringResource(Res.string.livetv_settings_sync_restore_description),
                    isTablet = isTablet,
                    enabled = !state.busy,
                    onClick = { confirm = CloudConfirm.Earlier },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_settings_sync_off),
                    description = stringResource(Res.string.livetv_settings_sync_off_description),
                    isTablet = isTablet,
                    onClick = { confirm = CloudConfirm.Off },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_settings_sync_delete),
                    description = stringResource(Res.string.livetv_settings_sync_delete_description),
                    isTablet = isTablet,
                    onClick = { confirm = CloudConfirm.Delete },
                )
            }
        }
        state.message?.let {
            SettingsGroupDivider(isTablet = isTablet)
            Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textSecondary, modifier = Modifier.padding(NuvioTokens.Space.s16))
        }
    }

    confirm?.let { which ->
        val dismiss = { confirm = null }
        val (title, message, action) = when (which) {
            CloudConfirm.Off -> Triple(stringResource(Res.string.livetv_settings_sync_off_confirm), stringResource(Res.string.livetv_settings_sync_off_message), { LiveTvCloudSync.turnOff() })
            CloudConfirm.Delete -> Triple(stringResource(Res.string.livetv_settings_sync_delete_confirm), stringResource(Res.string.livetv_settings_sync_delete_message), { LiveTvCloudSync.deleteCloudCopy() })
            CloudConfirm.Earlier -> Triple(stringResource(Res.string.livetv_settings_sync_restore_confirm), stringResource(Res.string.livetv_settings_sync_restore_message), { LiveTvCloudSync.restorePrevious() })
        }
        DialogSurface(onDismissRequest = dismiss, title = title, message = message) {
            DialogButtons {
                DialogButton(text = stringResource(Res.string.action_cancel), onClick = dismiss)
                DialogButton(
                    text = stringResource(Res.string.action_continue),
                    style = if (which == CloudConfirm.Delete) DialogButtonStyle.Destructive else DialogButtonStyle.Primary,
                    onClick = { action(); dismiss() },
                )
            }
        }
    }
}

private fun SyncPart.labelResource() = when (this) {
    SyncPart.LOGINS -> Res.string.livetv_settings_sync_logins
    SyncPart.FAVORITES -> Res.string.livetv_settings_sync_favorites
    SyncPart.SPORTS -> Res.string.livetv_sports
    SyncPart.CALENDAR -> Res.string.livetv_calendar
    SyncPart.LOOK -> Res.string.livetv_settings_sync_look
}

private fun SyncPart.descriptionResource() = when (this) {
    SyncPart.LOGINS -> Res.string.livetv_settings_sync_logins_description
    SyncPart.FAVORITES -> Res.string.livetv_settings_sync_favorites_description
    SyncPart.SPORTS -> Res.string.livetv_settings_sync_sports_description
    SyncPart.CALENDAR -> Res.string.livetv_settings_sync_calendar_description
    SyncPart.LOOK -> Res.string.livetv_settings_sync_look_description
}

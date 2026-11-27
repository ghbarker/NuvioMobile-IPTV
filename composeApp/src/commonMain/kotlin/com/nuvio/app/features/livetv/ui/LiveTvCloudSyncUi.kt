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
import nuvio.composeapp.generated.resources.livetv_ui_cancel
import nuvio.composeapp.generated.resources.livetv_ui_continue
import nuvio.composeapp.generated.resources.livetv_ui_sync_choose_msg
import nuvio.composeapp.generated.resources.livetv_ui_sync_combine
import nuvio.composeapp.generated.resources.livetv_ui_sync_delete
import nuvio.composeapp.generated.resources.livetv_ui_sync_delete_desc
import nuvio.composeapp.generated.resources.livetv_ui_sync_delete_msg
import nuvio.composeapp.generated.resources.livetv_ui_sync_delete_q
import nuvio.composeapp.generated.resources.livetv_ui_sync_earlier
import nuvio.composeapp.generated.resources.livetv_ui_sync_earlier_desc
import nuvio.composeapp.generated.resources.livetv_ui_sync_earlier_msg
import nuvio.composeapp.generated.resources.livetv_ui_sync_earlier_q
import nuvio.composeapp.generated.resources.livetv_ui_sync_have_saved
import nuvio.composeapp.generated.resources.livetv_ui_sync_keeps
import nuvio.composeapp.generated.resources.livetv_ui_sync_last
import nuvio.composeapp.generated.resources.livetv_ui_sync_not_now
import nuvio.composeapp.generated.resources.livetv_ui_sync_now
import nuvio.composeapp.generated.resources.livetv_ui_sync_now_desc
import nuvio.composeapp.generated.resources.livetv_ui_sync_off
import nuvio.composeapp.generated.resources.livetv_ui_sync_off_desc
import nuvio.composeapp.generated.resources.livetv_ui_sync_off_msg
import nuvio.composeapp.generated.resources.livetv_ui_sync_off_q
import nuvio.composeapp.generated.resources.livetv_ui_sync_official_only
import nuvio.composeapp.generated.resources.livetv_ui_sync_on
import nuvio.composeapp.generated.resources.livetv_ui_sync_save_mine
import nuvio.composeapp.generated.resources.livetv_ui_sync_save_msg
import nuvio.composeapp.generated.resources.livetv_ui_sync_save_title
import nuvio.composeapp.generated.resources.livetv_ui_sync_sign_in
import nuvio.composeapp.generated.resources.livetv_ui_sync_title
import nuvio.composeapp.generated.resources.livetv_ui_sync_turn_on
import nuvio.composeapp.generated.resources.livetv_ui_sync_use_saved
import nuvio.composeapp.generated.resources.livetv_ui_sync_what
import nuvio.composeapp.generated.resources.livetv_ui_syncpart_behavior
import nuvio.composeapp.generated.resources.livetv_ui_syncpart_behavior_desc
import nuvio.composeapp.generated.resources.livetv_ui_syncpart_calendar
import nuvio.composeapp.generated.resources.livetv_ui_syncpart_calendar_desc
import nuvio.composeapp.generated.resources.livetv_ui_syncpart_favorites
import nuvio.composeapp.generated.resources.livetv_ui_syncpart_favorites_desc
import nuvio.composeapp.generated.resources.livetv_ui_syncpart_home_rows
import nuvio.composeapp.generated.resources.livetv_ui_syncpart_home_rows_desc
import nuvio.composeapp.generated.resources.livetv_ui_syncpart_logins
import nuvio.composeapp.generated.resources.livetv_ui_syncpart_logins_desc
import nuvio.composeapp.generated.resources.livetv_ui_syncpart_look
import nuvio.composeapp.generated.resources.livetv_ui_syncpart_look_desc
import nuvio.composeapp.generated.resources.livetv_ui_syncpart_sports
import nuvio.composeapp.generated.resources.livetv_ui_syncpart_sports_desc
import nuvio.composeapp.generated.resources.livetv_ui_syncpart_vpn
import nuvio.composeapp.generated.resources.livetv_ui_syncpart_vpn_desc
import nuvio.composeapp.generated.resources.Res
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
            title = stringResource(Res.string.livetv_ui_sync_save_title),
            message = stringResource(Res.string.livetv_ui_sync_save_msg),
        ) {
            DialogButtons {
                DialogButton(text = stringResource(Res.string.livetv_ui_sync_not_now), onClick = { LiveTvCloudSync.answerBackUp(false) })
                DialogButton(text = stringResource(Res.string.livetv_ui_sync_save_mine), style = DialogButtonStyle.Primary, onClick = { LiveTvCloudSync.answerBackUp(true) })
            }
        }
        CloudPrompt.Choose -> DialogSurface(
            onDismissRequest = { LiveTvCloudSync.answerChoose(CloudChoice.Later) },
            title = stringResource(Res.string.livetv_ui_sync_have_saved),
            message = stringResource(Res.string.livetv_ui_sync_choose_msg),
        ) {
            DialogButtons {
                DialogButton(text = stringResource(Res.string.livetv_ui_sync_not_now), onClick = { LiveTvCloudSync.answerChoose(CloudChoice.Later) })
                DialogButton(text = stringResource(Res.string.livetv_ui_sync_use_saved), onClick = { LiveTvCloudSync.answerChoose(CloudChoice.UseSaved) })
                DialogButton(text = stringResource(Res.string.livetv_ui_sync_combine), style = DialogButtonStyle.Primary, onClick = { LiveTvCloudSync.answerChoose(CloudChoice.Combine) })
            }
        }
        null -> Unit
    }
}

@Composable
private fun syncPartLabel(part: SyncPart): String = stringResource(when (part) {
    SyncPart.LOGINS -> Res.string.livetv_ui_syncpart_logins
    SyncPart.VPN -> Res.string.livetv_ui_syncpart_vpn
    SyncPart.FAVORITES -> Res.string.livetv_ui_syncpart_favorites
    SyncPart.HOME_ROWS -> Res.string.livetv_ui_syncpart_home_rows
    SyncPart.SPORTS -> Res.string.livetv_ui_syncpart_sports
    SyncPart.CALENDAR -> Res.string.livetv_ui_syncpart_calendar
    SyncPart.BEHAVIOR -> Res.string.livetv_ui_syncpart_behavior
    SyncPart.LOOK -> Res.string.livetv_ui_syncpart_look
})

@Composable
private fun syncPartDescription(part: SyncPart): String = stringResource(when (part) {
    SyncPart.LOGINS -> Res.string.livetv_ui_syncpart_logins_desc
    SyncPart.VPN -> Res.string.livetv_ui_syncpart_vpn_desc
    SyncPart.FAVORITES -> Res.string.livetv_ui_syncpart_favorites_desc
    SyncPart.HOME_ROWS -> Res.string.livetv_ui_syncpart_home_rows_desc
    SyncPart.SPORTS -> Res.string.livetv_ui_syncpart_sports_desc
    SyncPart.CALENDAR -> Res.string.livetv_ui_syncpart_calendar_desc
    SyncPart.BEHAVIOR -> Res.string.livetv_ui_syncpart_behavior_desc
    SyncPart.LOOK -> Res.string.livetv_ui_syncpart_look_desc
})

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
        text = stringResource(Res.string.livetv_ui_sync_title),
        modifier = Modifier.padding(horizontal = NuvioTokens.Radius.card).padding(top = NuvioTokens.Space.s16, bottom = NuvioTokens.Space.s10),
    )
    SettingsGroup(isTablet = isTablet) {
        when {
            !state.available -> Text(
                text = stringResource(Res.string.livetv_ui_sync_official_only),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
                modifier = Modifier.padding(NuvioTokens.Space.s16),
            )
            !state.signedIn -> Text(
                text = stringResource(Res.string.livetv_ui_sync_sign_in),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
                modifier = Modifier.padding(NuvioTokens.Space.s16),
            )
            !state.enabled -> {
                Text(
                    text = stringResource(Res.string.livetv_ui_sync_keeps),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textMuted,
                    modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s12),
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_ui_sync_turn_on),
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
                    text = stringResource(Res.string.livetv_ui_sync_keeps),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textMuted,
                    modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s12),
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_ui_sync_on),
                    description = (state.email ?: "") + (state.lastSyncMs.takeIf { it > 0 }?.let { " · " + stringResource(Res.string.livetv_ui_sync_last, formatLocalDateTime(it)) } ?: ""),
                    icon = Icons.Filled.Backup,
                    isTablet = isTablet,
                    onClick = {},
                )
                NuvioSectionLabel(
                    text = stringResource(Res.string.livetv_ui_sync_what),
                    modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16).padding(top = NuvioTokens.Space.s12, bottom = NuvioTokens.Space.s6),
                )
                SyncPart.entries.forEach { part ->
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsSwitchRow(
                        title = syncPartLabel(part),
                        description = syncPartDescription(part),
                        checked = partOn[part] == true,
                        isTablet = isTablet,
                        onCheckedChange = { on -> SyncParts.set(part, on); LiveTvCloudSync.partsChanged() },
                    )
                }
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_ui_sync_now),
                    description = stringResource(Res.string.livetv_ui_sync_now_desc),
                    isTablet = isTablet,
                    enabled = !state.busy,
                    trailingContent = { if (state.busy) NuvioLoadingIndicator(size = NuvioTokens.Space.s20) },
                    onClick = { LiveTvCloudSync.syncNow() },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_ui_sync_earlier),
                    description = stringResource(Res.string.livetv_ui_sync_earlier_desc),
                    isTablet = isTablet,
                    enabled = !state.busy,
                    onClick = { confirm = CloudConfirm.Earlier },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_ui_sync_off),
                    description = stringResource(Res.string.livetv_ui_sync_off_desc),
                    isTablet = isTablet,
                    onClick = { confirm = CloudConfirm.Off },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_ui_sync_delete),
                    description = stringResource(Res.string.livetv_ui_sync_delete_desc),
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
            CloudConfirm.Off -> Triple(stringResource(Res.string.livetv_ui_sync_off_q), stringResource(Res.string.livetv_ui_sync_off_msg), { LiveTvCloudSync.turnOff() })
            CloudConfirm.Delete -> Triple(stringResource(Res.string.livetv_ui_sync_delete_q), stringResource(Res.string.livetv_ui_sync_delete_msg), { LiveTvCloudSync.deleteCloudCopy() })
            CloudConfirm.Earlier -> Triple(stringResource(Res.string.livetv_ui_sync_earlier_q), stringResource(Res.string.livetv_ui_sync_earlier_msg), { LiveTvCloudSync.restorePrevious() })
        }
        DialogSurface(onDismissRequest = dismiss, title = title, message = message) {
            DialogButtons {
                DialogButton(text = stringResource(Res.string.livetv_ui_cancel), onClick = dismiss)
                DialogButton(
                    text = stringResource(Res.string.livetv_ui_continue),
                    style = if (which == CloudConfirm.Delete) DialogButtonStyle.Destructive else DialogButtonStyle.Primary,
                    onClick = { action(); dismiss() },
                )
            }
        }
    }
}

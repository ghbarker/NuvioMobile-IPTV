package com.nuvio.app.features.livetv.ui.sports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogOption
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.livetv.sports.ChannelMatch
import com.nuvio.app.features.livetv.sports.SportsAddonSource
import com.nuvio.app.features.livetv.sports.SportsEvent
import com.nuvio.app.features.livetv.sports.SportsWatchController
import kotlinx.coroutines.CancellationException
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.livetv_channel_gone
import nuvio.composeapp.generated.resources.livetv_sports_choose_another_channel
import nuvio.composeapp.generated.resources.livetv_sports_final_tune_hint
import nuvio.composeapp.generated.resources.livetv_sports_no_live_channels
import nuvio.composeapp.generated.resources.livetv_sports_watch_live_channel
import org.jetbrains.compose.resources.stringResource

/** Final describes the score feed. A viewer can still pick the channel's current broadcast. */
@Composable
internal fun FinishedGameLiveChannelAction(
    event: SportsEvent,
    now: Long,
    matches: List<ChannelMatch>,
    hasPlaylistChannels: Boolean,
    onPlay: (LiveChannel, String?) -> Unit,
) {
    val addons by AddonRepository.uiState.collectAsStateWithLifecycle()
    val searchAddons = remember(event, now, addons.addons) {
        SportsAddonSource.canLookForOvertime(event, now, addons.addons)
    }
    if (!hasPlaylistChannels && !searchAddons && matches.none { it.channel.sourceId != SportsAddonSource.SOURCE_ID }) return

    val profile by LiveTvPreferences.activeProfile.collectAsStateWithLifecycle()
    var open by remember(event.id, profile) { mutableStateOf(false) }
    Chip(label = stringResource(Res.string.livetv_sports_watch_live_channel), selected = false, onClick = { open = true })
    if (!open) return

    val controller = remember(profile) { SportsWatchController(profile) }
    DisposableEffect(controller) { onDispose(controller::close) }
    val allowed by controller.allowedChannels.collectAsStateWithLifecycle()
    val rules by controller.permissionRules.collectAsStateWithLifecycle(initialValue = null)
    val playlist = remember(allowed, rules, event.leagueId) {
        allowed.filter { it.sourceId != SportsAddonSource.SOURCE_ID && rules?.permitsOvertime(event, it, matched = false) == true }
    }
    val streams by produceState<List<ChannelMatch>?>(null, event.id, searchAddons, addons.addons) {
        value = null
        value = if (!searchAddons) emptyList() else try {
            SportsAddonSource.find(event, liveOnly = true).live
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }
    val choices = remember(matches, playlist, streams, rules) {
        val liveAddons = streams.orEmpty().map { it.channel }
        val current = (playlist + liveAddons).associateBy { it.key }
        (matches.mapNotNull { current[it.channel.key] } + liveAddons)
            .distinctBy { it.key }.filter { rules?.permitsOvertime(event, it, matched = true) == true }
    }
    val unavailable = stringResource(Res.string.livetv_channel_gone)
    fun tune(candidate: LiveChannel, matched: Boolean) {
        val channel = controller.overtimeChannel(event, candidate, matched)
        open = false
        if (channel != null) onPlay(channel, null) else NuvioToastController.show(unavailable)
    }
    FinishedGameChannelPicker(choices, playlist, searching = searchAddons && streams == null, onPick = ::tune, onDismiss = { open = false })
}

@Composable
internal fun FinishedGameChannelPicker(
    matches: List<LiveChannel>,
    playlist: List<LiveChannel>,
    searching: Boolean,
    onPick: (channel: LiveChannel, matched: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var browsing by remember { mutableStateOf(false) }
    val title = stringResource(Res.string.livetv_sports_watch_live_channel)
    if (browsing || (matches.isEmpty() && !searching && playlist.isNotEmpty())) {
        ChannelPickerDialog(title = title, onPick = { onPick(it, false) }, onDismiss = onDismiss, channels = playlist)
        return
    }
    DialogSurface(onDismissRequest = onDismiss, title = title, message = stringResource(Res.string.livetv_sports_final_tune_hint)) {
        if (searching) NuvioLoadingIndicator(size = NuvioTokens.Space.s16)
        if (!searching && matches.isEmpty()) Text(stringResource(Res.string.livetv_sports_no_live_channels))
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4)) {
            items(matches, key = { it.key }) { channel ->
                DialogOption(text = channel.name, description = channel.sourceName, selected = false, onClick = { onPick(channel, true) })
            }
        }
        if (playlist.isNotEmpty()) DialogOption(text = stringResource(Res.string.livetv_sports_choose_another_channel), selected = false, onClick = { browsing = true })
        DialogButtons { DialogButton(text = stringResource(Res.string.action_cancel), onClick = onDismiss) }
    }
}

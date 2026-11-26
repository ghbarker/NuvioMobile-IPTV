package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.livetv.player.LiveTvCaptions
import com.nuvio.app.features.livetv.multiview.LocalOpenMultiview
import com.nuvio.app.features.livetv.multiview.isMultiviewSupported
import com.nuvio.app.features.livetv.player.LiveTvPlayback
import com.nuvio.app.features.livetv.ui.LiveTvPlayerOverlay
import com.nuvio.app.features.livetv.ui.LiveTvViewState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString

/** Whether a Live TV channel (or a catch-up replay of one) is playing. */
internal val PlayerScreenRuntime.isLiveTv: Boolean
    get() = LiveTvPlayback.owns(activeProviderAddonId)

internal val PlayerScreenRuntime.isContinuousLivePlayback: Boolean
    get() = LiveTvPlayback.isContinuousLive(activeProviderAddonId, activeSourceUrl)

/**
 * Where the caption and audio choices are kept: per title, and for Live TV per channel — the one
 * now on screen, which changes as you switch channels in the player (the title it was opened
 * with stays the first channel). So captions on or off, and the sound track, are as you left
 * them on each channel, also after switching to it in the player.
 */
internal val PlayerScreenRuntime.trackPreferenceId: String
    get() = if (isLiveTv) activeVideoId?.takeIf { it.isNotBlank() } ?: parentMetaId else parentMetaId

/** The title for the controls: the channel now on screen, which changes as you switch channels. */
internal fun PlayerScreenRuntime.liveTitleOr(fallback: String): String =
    activeStreamTitle.takeIf { isLiveTv && it.isNotBlank() } ?: fallback

/** The logo for the pause screen: the channel now on screen. */
internal fun PlayerScreenRuntime.liveLogoOr(fallback: String?): String? {
    if (!isLiveTv) return fallback
    val key = activeVideoId ?: return fallback
    return LiveTvViewState.list.value.visible.firstOrNull { it.key == key }?.logo ?: fallback
}

/** Changes channel in place (the channel panel or a swipe), keeping the player open. */
internal fun PlayerScreenRuntime.switchLiveChannel(channel: LiveChannel, nowTitle: String?) {
    if (channel.key == activeVideoId && LiveTvPlayback.owns(activeProviderAddonId) && errorMessage == null) return
    cancelLiveEndReconnect(resetAttempts = true)
    adaptiveBackupJob?.cancel()
    credentialRefreshJob?.cancel()
    credentialRefreshJob = scope.launch {
        try {
            val req = LiveTvPlayback.switchTo(channel)
            flushWatchProgress()
            errorMessage = null
            externalSubtitles = emptyList()
            activeSourceUrl = req.url
            activeSourceAudioUrl = null
            activeSourceHeaders = sanitizePlaybackHeaders(req.headers)
            activeStalkerMediaPolicy = req.mediaPolicy
            activeSourceResponseHeaders = sanitizePlaybackResponseHeaders(null)
            activeStreamType = req.streamType
            activeSourceIdentityKey = "url:${req.url}"
            activeSourceFilename = null
            activeStreamFilename = sourceFilenameFromUrl(req.url)
            activeStreamTitle = channel.name
            activeStreamSubtitle = nowTitle ?: channel.group
            activePauseDescription = nowTitle
            activeProviderName = channel.sourceName
            activeProviderAddonId = LiveTvPlayback.PROVIDER_ID
            activeVideoId = channel.key
            activeInitialPositionMs = 0L
            activeInitialProgressFraction = null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            errorMessage = getString(Res.string.livetv_error_open_channel)
            controlsVisible = !playerControlsLocked
        }
    }
}

/** The playback that "captions on by default" was last applied to (once per channel opened). */
private object LiveCaptionDefaults {
    var appliedFor: String? = null
}

/**
 * Closed captions on Live TV, after each track refresh: "Captions on by default" switches them on
 * once per channel (not when you've chosen something for that channel yourself).
 */
internal fun PlayerScreenRuntime.applyLiveCaptionDefaults() {
    if (!isLiveTv) return
    val captions = subtitleTracks.filter { it.id in LiveTvCaptions.ids }
    if (captions.isEmpty()) return
    val key = "$activePlaybackIdentity:$livePlaybackGeneration"
    if (LiveCaptionDefaults.appliedFor == key) return
    LiveCaptionDefaults.appliedFor = key
    if (!LiveTvPreferences.currentSettings().captionsByDefault) return
    if (isUserExplicitSubtitleSelection || useCustomSubtitles) return
    if (subtitleTracks.any { it.isSelected }) return
    if (PlayerTrackPreferenceStorage.load(trackPreferenceId)?.subtitleType != null) return
    val pick = captions.first()
    playerController?.selectSubtitleTrack(pick.index)
    selectedSubtitleIndex = pick.index
    selectedAddonSubtitleId = null
    preferredSubtitleSelectionApplied = true
}

/**
 * Captions switched on but the copy picked has shown nothing for 10 s while the other copy of
 * the same captions (the old kind or the digital kind) is carrying text: switch to that one.
 */
private fun PlayerScreenRuntime.switchToCaptionsWithText() {
    val found = LiveTvCaptions.found.value
    val selected = subtitleTracks.firstOrNull { it.isSelected } ?: return
    if (selected.id !in LiveTvCaptions.ids || selected.id in found) return
    val other = subtitleTracks.firstOrNull {
        it.id != selected.id && it.id in found && (it.id == LiveTvCaptions.CC1 || it.id == LiveTvCaptions.DIGITAL)
    } ?: return
    playerController?.selectSubtitleTrack(other.index)
    selectedSubtitleIndex = other.index
}

/** The channel panel and channel swiping, over a playing Live TV channel. */
@Composable
internal fun PlayerScreenRuntime.RenderLiveTvChannels() {
    if (!isLiveTv) return
    val settings by LiveTvPreferences.settings.collectAsStateWithLifecycle()
    LaunchedEffect(settings.autoReconnect, shouldPlay) {
        if (!settings.autoReconnect || !shouldPlay) cancelLiveEndReconnect()
    }
    // Caption types seen carrying text: offer them in the Subtitles menu as they turn up.
    val captionsFound by LiveTvCaptions.found.collectAsStateWithLifecycle()
    LaunchedEffect(captionsFound, activeSourceUrl) {
        if (captionsFound.isEmpty()) return@LaunchedEffect
        refreshTracks()
        delay(10_000)
        refreshTracks()
        switchToCaptionsWithText()
    }
    val isInPip = rememberIsInPictureInPicture()
    val openMultiview = LocalOpenMultiview.current
    LiveTvPlayerOverlay(
        currentKey = activeVideoId,
        controlsVisible = controlsVisible,
        enabled = !isInPip && !playerControlsLocked,
        onPanelOpened = { controlsVisible = false },
        onSwitch = { channel, nowTitle -> switchLiveChannel(channel, nowTitle) },
        // Works for any live channel: Multiview opens with this channel in the first pane.
        onOpenMultiview = if (!isMultiviewSupported || openMultiview == null) null else {
            {
                // Release the single audible source before navigation creates any pane owners.
                cancelLiveEndReconnect()
                shouldPlay = false
                playerController?.setMuted(true)
                playerController?.pause()
                openMultiview(activeVideoId)
            }
        },
    )
}

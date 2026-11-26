package com.nuvio.app.features.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink

/** One instance per full-screen, continuous network channel player. The existing UI pulse drives it. */
@OptIn(UnstableApi::class)
internal class MobileAdaptiveLiveBuffer(private val session: AdaptiveLiveBufferSession) {
    @Volatile private var pcm16 = false
    @Volatile private var tunneling = false
    private var lastSampleMs: Long? = null
    private var addedDelayMs = 0.0
    private var byteBudgetReached: ((Long) -> Boolean)? = null

    fun observeByteBudget(loadControl: DefaultLoadControl, bytes: Int, minBufferedMs: Int) {
        byteBudgetReached = { bufferedMs ->
            bufferedMs >= minBufferedMs && loadControl.allocator.totalBytesAllocated >= bytes
        }
    }

    fun trackAudio(sink: AudioSink, hardwarePlaybackParameters: Boolean): AudioSink {
        if (hardwarePlaybackParameters) return sink
        return object : ForwardingAudioSink(sink) {
            override fun configure(format: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
                pcm16 = false
                super.configure(format, specifiedBufferSize, outputChannels)
                pcm16 = format.sampleMimeType == MimeTypes.AUDIO_RAW && format.pcmEncoding == C.ENCODING_PCM_16BIT
            }

            override fun enableTunnelingV21() { tunneling = true; super.enableTunnelingV21() }
            override fun disableTunneling() { super.disableTunneling(); tunneling = false }
            override fun reset() { pcm16 = false; super.reset() }
            override fun release() { pcm16 = false; super.release() }
        }
    }

    fun onParametersChanged(value: PlaybackParameters) = session.onParametersChanged(value.asRate())

    fun selectUserSpeed() = session.userSelectedRate()

    fun userSpeed(player: ExoPlayer): Float {
        val params = player.playbackParameters
        return if (session.owns(params.asRate())) 1f else params.speed
    }

    fun suspend(player: ExoPlayer) {
        lastSampleMs = null
        apply(player, session.suspend(player.playbackParameters.asRate()))
    }

    fun update(player: ExoPlayer, enabled: Boolean, foreground: Boolean, nowMs: Long) {
        if (!enabled || !foreground || !pcm16 || tunneling || !player.isPlaying ||
            !player.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM) ||
            !player.isCommandAvailable(Player.COMMAND_GET_TIMELINE) ||
            !player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH) ||
            player.isPlayingAd || player.isSleepingForOffload || player.audioFormat == null
        ) {
            // If the speed command disappeared during restoration, retry when it returns.
            if (lastSampleMs != null || session.owns(player.playbackParameters.asRate())) suspend(player)
            return
        }
        val previous = lastSampleMs
        if (previous != null && nowMs - previous in 0 until 1_000L) return
        lastSampleMs = nowMs
        val current = player.playbackParameters.asRate()
        val bufferMs = player.totalBufferedDuration
        if (bufferMs >= AdaptiveLiveBufferPolicy.TARGET_BUFFER_MS) addedDelayMs = 0.0
        else if (byteBudgetReached?.invoke(bufferMs) == true) session.byteCap(bufferMs)
        else if (previous != null && session.owns(current)) {
            addedDelayMs += (nowMs - previous).coerceIn(0L, 2_000L) * (1.0 - current.speed)
            // A custom time/byte ceiling or constantly depleted source may never hold 20 seconds.
            // Stop accumulating live delay after paying for a full cushion without reaching it.
            if (addedDelayMs >= AdaptiveLiveBufferPolicy.TARGET_BUFFER_MS) session.byteCap(bufferMs)
        }
        apply(player, session.sample(current, true, bufferMs, nowMs))
    }

    private fun apply(player: ExoPlayer, rate: AdaptiveLiveRate?) {
        if (rate != null && player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)) {
            session.onApplied(rate)
            player.playbackParameters = PlaybackParameters(rate.speed, rate.pitch)
        }
    }

    private fun PlaybackParameters.asRate() = AdaptiveLiveRate(speed, pitch)
}

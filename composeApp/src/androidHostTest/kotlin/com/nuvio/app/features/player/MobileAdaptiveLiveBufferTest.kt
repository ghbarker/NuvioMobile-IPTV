package com.nuvio.app.features.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MobileAdaptiveLiveBufferTest {
    private val adaptive = MobileAdaptiveLiveBuffer(AdaptiveLiveBufferSession())
    private val player = mockk<ExoPlayer>(relaxed = true)
    private val sink = adaptive.trackAudio(mockk<AudioSink>(relaxed = true), hardwarePlaybackParameters = false)
    private val pcm = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW).setPcmEncoding(C.ENCODING_PCM_16BIT).build()
    private var rate = PlaybackParameters.DEFAULT
    private var bufferMs = 1_000L
    private var seconds = 0L

    init {
        every { player.isCommandAvailable(any()) } returns true
        every { player.isPlaying } returns true
        every { player.audioFormat } returns pcm
        every { player.playbackParameters } answers { rate }
        every { player.totalBufferedDuration } answers { bufferMs }
        every { player.playbackParameters = any() } answers {
            rate = firstArg()
            adaptive.onParametersChanged(rate)
        }
    }

    private fun tick(times: Int = 1, enabled: Boolean = true) {
        repeat(times) { adaptive.update(player, enabled, foreground = true, nowMs = 1_000L * seconds++) }
    }

    @Test fun decodedLiveAudioSlowsAtNaturalPitchAndTheViewerCanTakeOver() {
        sink.configure(pcm, 0, null)
        tick(25)
        assertEquals(PlaybackParameters(.90f, 1f), rate)
        val restoreAfterGesture = adaptive.userSpeed(player)
        assertEquals(1f, restoreAfterGesture, 0f)
        adaptive.selectUserSpeed()
        player.setPlaybackParameters(PlaybackParameters(2f))
        player.setPlaybackParameters(PlaybackParameters(restoreAfterGesture))
        tick(25)
        assertEquals(PlaybackParameters.DEFAULT, rate)
    }

    @Test fun turningOffWhileCommandsAreMissingRestoresOwnedSpeedWhenAllowed() {
        sink.configure(pcm, 0, null)
        tick(25)
        every { player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH) } returns false
        tick(enabled = false)
        assertEquals(.90f, rate.speed, 0f)
        every { player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH) } returns true
        tick(enabled = false)
        assertEquals(PlaybackParameters.DEFAULT, rate)
    }

    @Test fun passthroughTunnelOffloadAndUnavailableMeasurementsNeverChangeSpeed() {
        sink.configure(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AC3).build(), 0, null)
        tick(25)
        sink.configure(pcm, 0, null)
        sink.enableTunnelingV21()
        tick(25)
        sink.disableTunneling()
        every { player.isSleepingForOffload } returns true
        tick(25)
        every { player.isSleepingForOffload } returns false
        every { player.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM) } returns false
        tick(25)
        verify(exactly = 0) { player.totalBufferedDuration }
        verify(exactly = 0) { player.playbackParameters = any() }
    }

    @Test fun anExhaustedAllocatorAndALiveBufferThatCannotGrowBothBoundDelay() {
        sink.configure(pcm, 0, null)
        val load = mockk<DefaultLoadControl>()
        var bytesAllocated = 0
        every { load.allocator.totalBytesAllocated } answers { bytesAllocated }
        adaptive.observeByteBudget(load, 4_000)
        tick(25)
        bytesAllocated = 4_000
        tick(50)
        assertEquals(PlaybackParameters.DEFAULT, rate)
        bytesAllocated = 0
        bufferMs = 21_000
        tick(5)
        bufferMs = 1_000
        tick(25)
        assertEquals(.90f, rate.speed, 0f)
        tick(300)
        assertEquals(PlaybackParameters.DEFAULT, rate)
    }
}

package com.nuvio.vpn.android

import android.app.Application
import androidx.media3.common.MimeTypes
import com.nuvio.app.features.livetv.data.LiveTvFiles
import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.livetv.model.LiveTvClock
import com.nuvio.app.features.livetv.record.InMemoryNetworkFolder
import com.nuvio.app.features.livetv.record.LiveTvRecorder
import com.nuvio.app.features.livetv.record.LiveTvRecording
import com.nuvio.app.features.livetv.record.NetworkLibrary
import com.nuvio.app.features.livetv.record.NetworkPublish
import com.nuvio.app.features.livetv.record.RecordingContentError
import com.nuvio.app.features.livetv.record.RecordingPlatform
import com.nuvio.app.features.livetv.record.RecordingStatus
import com.nuvio.app.features.player.playbackMediaItemFromUrl
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Actual HTTP/file capture and recorder transitions; only the provider and SMB disk are fixtures. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class RecordingCaptureFormatTest {
    private lateinit var server: MockWebServer
    private lateinit var ts: ByteArray
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val recordings = mutableListOf<LiveTvRecording>()

    @Before fun prepare() {
        val context = RuntimeEnvironment.getApplication()
        LiveTvFiles.initialize(context)
        RecordingPlatform.initialize(context)
        VpnPlaybackProtection.initialize(context)
        VpnPlaybackProtection.setActiveUserProfile(1)
        AndroidVpnSettings(context).save(com.nuvio.vpn.core.VpnPreferences.defaults())
        VpnPlaybackProtection.settings()
        ts = checkNotNull(javaClass.getResourceAsStream("/livetv/continuous-multi-audio.ts.gz"))
            .use { GZIPInputStream(it).use { gzip -> gzip.readBytes() } }
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.path?.substringBefore('?')
                return when (path) {
                    "/redirect" -> MockResponse().setResponseCode(302).setHeader("Location", "/hls/list.m3u8")
                    "/redirect-ts" -> MockResponse().setResponseCode(302).setHeader("Location", "/hls/but-ts.m3u8")
                    "/hls/list.m3u8" -> playlist("segment.ts")
                    "/mime" -> playlist("hls/segment.ts")
                    "/sniff" -> playlist("hls/segment.ts", type = "application/octet-stream", bom = true)
                    "/hls/segment.ts", "/plain" -> MockResponse().setHeader("Content-Type", "video/mp2t").setBody(Buffer().write(ts))
                    "/lying-mime", "/hls/but-ts.m3u8" -> MockResponse().setHeader("Content-Type", "application/vnd.apple.mpegurl").setBody(Buffer().write(ts))
                    "/html" -> MockResponse().setHeader("Content-Type", "text/html").setBody("<!doctype html><html>Provider error</html>")
                    "/json" -> MockResponse().setHeader("Content-Type", "application/json").setBody("{\"error\":\"synthetic-private-detail\"}")
                    "/large" -> MockResponse().setHeader("Content-Type", "application/vnd.apple.mpegurl").setBody("#EXTM3U\n" + "# padded\n".repeat(150_000))
                    "/slow" -> MockResponse().setHeader("Content-Type", "video/mp2t").setBody(Buffer().write(ts))
                        .throttleBody(1024, 10, TimeUnit.SECONDS)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After fun close() {
        recordings.forEach { LiveTvRecorder.delete(it.id) }
        server.shutdown()
    }

    @Test fun extensionlessHlsUsesItsFirstResponseAndPublishesRealVideo() = runBlocking {
        val rec = capture("/mime")
        val video = video(rec)
        assertEquals(RecordingStatus.DONE, rec.status)
        assertTrue(video.inputStream().use { it.read() } == 0x47,
            "The extensionless HLS recording saved a playlist/error body instead of MPEG-TS media")
        assertContentEquals(ts, video.readBytes())
        assertEquals(4.0, rec.durationSec)
        assertEquals(MimeTypes.VIDEO_MP2T, playbackMediaItemFromUrl(RecordingPlatform.playUrl(video.path)).localConfiguration?.mimeType)
        assertEquals(listOf("/mime", "/hls/segment.ts"), requests.map { it.path })
        assertTrue(requests.all { it.method == "GET" && it.getHeader("User-Agent") == "Recording fixture" && it.getHeader("X-Fixture") == "preserved" })
        val network = InMemoryNetworkFolder()
        val id = NetworkPublish.publish(network.folder, rec, video) {}
        assertTrue(network.files.keys.any { it == "${NetworkLibrary.META_DIR}/$id/info.json" })
        assertTrue(network.files.keys.any { it == "${NetworkLibrary.META_DIR}/$id/index.m3u8" })
    }

    @Test fun redirectedHlsKeepsTheFinalBaseForRelativeVideoWithoutAProbeOpen() = runBlocking {
        val rec = capture("/redirect")
        assertEquals(RecordingStatus.DONE, rec.status)
        assertContentEquals(ts, video(rec).readBytes())
        assertEquals(listOf("/redirect", "/hls/list.m3u8", "/hls/segment.ts"), requests.map { it.path })
    }

    @Test fun aBomPrefixedHlsPayloadIsDetectedEvenWithoutAUsefulTypeOrExtension() = runBlocking {
        val rec = capture("/sniff")
        assertEquals(RecordingStatus.DONE, rec.status)
        assertContentEquals(ts, video(rec).readBytes())
        assertEquals(listOf("/sniff", "/hls/segment.ts"), requests.map { it.path })
    }

    @Test fun ordinaryTsKeepsItsOriginalPrefixAndUsesOneConnection() = runBlocking {
        val rec = capture("/plain")
        assertEquals(RecordingStatus.DONE, rec.status)
        assertContentEquals(ts, video(rec).readBytes())
        assertEquals(listOf("/plain"), requests.map { it.path })
    }

    @Test fun realTsWinsOverMisleadingHlsMimeAndRedirectedFileName() = runBlocking {
        for (path in listOf("/lying-mime", "/redirect-ts")) {
            val rec = capture(path)
            assertEquals(RecordingStatus.DONE, rec.status)
            assertContentEquals(ts, video(rec).readBytes())
        }
        assertEquals(listOf("/lying-mime", "/redirect-ts", "/hls/but-ts.m3u8"), requests.map { it.path })
    }

    @Test fun http200HtmlAndJsonCannotBecomeSuccessfulRecordings() = runBlocking {
        for (path in listOf("/html", "/json")) {
            val rec = capture(path)
            assertEquals(RecordingStatus.FAILED, rec.status)
            assertFalse(video(rec).exists())
            assertEquals("The provider returned an error page instead of video", rec.problem)
            assertFalse(rec.problem.orEmpty().contains("synthetic-private-detail"))
        }
        assertEquals(listOf("/html", "/json"), requests.map { it.path })
    }

    @Test fun aDiscoveredPlaylistHasABoundedSizeAndNeverWritesIntoTheVideo() = runBlocking {
        val output = File(RecordingPlatform.recordingsRoot(), "oversized.ts")
        assertFailsWith<RecordingContentError> {
            RecordingPlatform.download(server.url("/large").toString(), emptyMap(), output.path, false, { false }) {}
        }
        assertFalse(output.exists())
        assertEquals(listOf("/large"), requests.map { it.path })
    }

    @Test fun cancellingAStalledBodyClosesItsCallBeforeTheNextDownload() = runBlocking {
        val output = File(RecordingPlatform.recordingsRoot(), "cancelled.ts")
        val reading = CompletableDeferred<Unit>()
        val pending = async {
            RecordingPlatform.download(server.url("/slow").toString(), emptyMap(), output.path, false, { false }) {
                reading.complete(Unit)
            }
        }
        withTimeout(5_000) { reading.await() }
        val began = System.nanoTime()
        withTimeout(3_000) { pending.cancelAndJoin() }
        assertTrue(pending.isCancelled)
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began) < 3_000)
        val next = File(RecordingPlatform.recordingsRoot(), "after-cancel.ts")
        RecordingPlatform.download(server.url("/plain").toString(), emptyMap(), next.path, false, { false }) {}
        assertContentEquals(ts, next.readBytes())
    }

    private fun playlist(segment: String, type: String = "application/vnd.apple.mpegurl", bom: Boolean = false): MockResponse =
        MockResponse().setHeader("Content-Type", type).setBody((if (bom) "\uFEFF  " else "") +
            "#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXT-X-MEDIA-SEQUENCE:1\n#EXTINF:4.0,\n$segment\n#EXT-X-ENDLIST\n")

    private suspend fun capture(path: String): LiveTvRecording {
        val now = LiveTvClock.nowMs()
        val url = server.url(path).toString()
        val channel = LiveChannel("fixture:$path", "fixture", "Fixture", "Fixture channel", null, null, null,
            "sports", "Sports", 1, url, headers = mapOf("User-Agent" to "Recording fixture", "X-Fixture" to "preserved"))
        val completed = CompletableDeferred<LiveTvRecording>()
        val pending = LiveTvRecorder.start(channel, url, "Fixture $path", "S01E01", null, now, now + 10_000,
            now + 10_000, finite = true, onFinished = { completed.complete(it) })
        recordings += pending
        return withTimeout(15_000) { completed.await() }
    }

    private fun video(rec: LiveTvRecording) = File(rec.dir, rec.fileName)
}

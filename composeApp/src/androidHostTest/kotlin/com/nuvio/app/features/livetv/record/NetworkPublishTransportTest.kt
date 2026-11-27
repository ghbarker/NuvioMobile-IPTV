package com.nuvio.app.features.livetv.record

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import java.io.IOException
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Real upload formatting over an in-memory folder; no SMB connection or account is involved. */
class NetworkPublishTransportTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun rawHlsPlaylistMisSavedAsTsCannotBePublishedAndKeepsTheLocalCopy() {
        val playlist = "#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXT-X-MEDIA-SEQUENCE:1\n#EXTINF:4.0,\nsegment.ts\n"
            .toByteArray()
        val local = temporary.newFolder("recording").resolve("recording.ts").apply { writeBytes(playlist) }
        val remote = InMemoryNetworkFolder()

        val failure = assertFailsWith<IOException> {
            NetworkPublish.publish(remote.folder, recording(local.parent), local) {}
        }

        assertEquals("no video could be read from the recording", failure.message)
        assertFalse(remote.files.keys.any { it.endsWith("/info.json") || it.endsWith("/index.m3u8") })
        assertContentEquals(playlist, local.readBytes())
        assertTrue(local.parentFile.resolve(NetworkUploads.MARKER).isFile)
    }

    @Test
    fun realTsPublishesJoinedMediaAndResolvablePlaylistBeforeItsCompletedMetadata() {
        val ts = checkNotNull(javaClass.getResourceAsStream("/livetv/continuous-multi-audio.ts.gz"))
            .use { input -> GZIPInputStream(input).use { it.readBytes() } }
        val local = temporary.newFolder("recording").resolve("recording.ts").apply { writeBytes(ts) }
        val remote = InMemoryNetworkFolder()
        val progress = mutableListOf<Float>()

        val id = NetworkPublish.publish(remote.folder, recording(local.parent), local) { progress += it }

        assertEquals("fixture-recording", id)
        val metadataDir = "${NetworkLibrary.META_DIR}/$id"
        val index = assertNotNull(JoinFiles.parse(checkNotNull(remote.files["$metadataDir/join.idx"]).decodeToString()))
        val joined = index.files.single()
        assertTrue(joined.onNetwork)
        assertTrue(joined.done)
        val video = checkNotNull(remote.files[joined.path])
        assertTrue(video.isNotEmpty())
        assertEquals(0, video.size % 188)
        assertTrue(video.indices.step(188).all { video[it] == 0x47.toByte() })

        val playlist = checkNotNull(remote.files["$metadataDir/index.m3u8"]).decodeToString()
        assertTrue(playlist.startsWith("#EXTM3U\n"))
        assertTrue(playlist.contains("#EXT-X-ENDLIST"))
        val segments = playlist.lineSequence().filter { it.isNotBlank() && !it.startsWith('#') }.toList()
        assertTrue(segments.isNotEmpty())
        segments.forEach { name ->
            val piece = checkNotNull(index.pieces[name])
            assertEquals(joined.no, piece.file)
            assertTrue(piece.offset >= 0 && piece.length > 0 && piece.offset + piece.length <= video.size)
        }
        val info = Json.parseToJsonElement(checkNotNull(remote.files["$metadataDir/info.json"]).decodeToString()).jsonObject
        assertEquals("DONE", info.getValue("status").jsonPrimitive.content)
        assertEquals(video.size.toLong(), info.getValue("sizeBytes").jsonPrimitive.content.toLong())
        assertEquals("metadata:$metadataDir/info.json", remote.events.last())
        assertEquals(1f, progress.last())
        assertContentEquals(ts, local.readBytes())
    }

    private fun recording(dir: String) = LiveTvRecording(
        id = "fixture-recording", channelKey = "fixture", channelName = "Fixture channel", channelLogo = null,
        title = "Fixture Show", episode = "S01E01", programStartMs = 1_700_000_000_000L,
        startedAtMs = 1_700_000_000_000L, stopAtMs = 1_700_000_004_000L,
        status = RecordingStatus.DONE, durationSec = 4.0, dir = dir,
    )
}

/** Only the remote file operations are faked; NetworkPublish, TsCutter and JoinFiles are real. */
internal class InMemoryNetworkFolder {
    val files = linkedMapOf<String, ByteArray>()
    val events = mutableListOf<String>()
    val folder = mockk<SmbFolder>()

    init {
        every { folder.mkdirs(any()) } just Runs
        every { folder.fileExists(any()) } answers { files.containsKey(firstArg<String>()) }
        every { folder.listFolders(any()) } returns emptyList()
        every { folder.delete(any()) } answers { files.remove(firstArg<String>()); Unit }
        every { folder.writeBytesAt(any(), any(), any(), any()) } answers {
            val path = firstArg<String>()
            val offset = secondArg<Long>().toInt()
            val input = thirdArg<ByteArray>()
            val length = arg<Int>(3)
            val old = files[path] ?: byteArrayOf()
            val updated = old.copyOf(maxOf(old.size, offset + length))
            input.copyInto(updated, offset, 0, length)
            files[path] = updated
            events += "video:$path"
        }
        every { folder.setLength(any(), any()) } answers {
            val path = firstArg<String>()
            files[path] = checkNotNull(files[path]).copyOf(secondArg<Long>().toInt())
        }
        every { folder.size(any()) } answers { files[firstArg<String>()]?.size?.toLong() }
        every { folder.rename(any(), any()) } answers {
            val from = firstArg<String>()
            val to = secondArg<String>()
            files[to] = checkNotNull(files.remove(from))
            events += "rename:$from:$to"
        }
        every { folder.writeBytes(any(), any()) } answers {
            val path = firstArg<String>()
            files[path] = secondArg<ByteArray>().copyOf()
            events += "metadata:$path"
        }
    }
}

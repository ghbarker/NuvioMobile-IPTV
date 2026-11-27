package com.nuvio.app.features.livetv.record

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import java.io.OutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Real metadata transaction; only SMB file operations are replaced with an in-memory folder. */
class NetworkRecordingRenameTest {
    private val rec = LiveTvRecording("recording", "channel", "PPV", null, "No program guide available",
        startedAtMs = 10L, stopAtMs = 20L, status = RecordingStatus.DONE, dir = "net:recording")
    private val path = "${NetworkLibrary.META_DIR}/recording/info.json"
    private val original = JsonObject(rec.toJson() + ("tvFuture" to Json.parseToJsonElement("""{"joinedFiles":["Weekend/fight.ts"]}"""))).toString()

    @Test fun renameAtomicallyReplacesOnlyMetadataAndRetainsEveryUnknownField() {
        val remote = Remote()
        val result = NetworkRecordingRename.rename(remote.folder, rec, "  Weekend fight  ") { true }
        assertEquals(result, remote.files[path])
        assertEquals(Json.parseToJsonElement(original).jsonObject.filterKeys { it != "title" }, Json.parseToJsonElement(result).jsonObject.filterKeys { it != "title" })
        assertEquals(JsonPrimitive("Weekend fight"), Json.parseToJsonElement(result).jsonObject["title"])
        assertEquals(setOf(path, "Weekend/fight.ts", "${NetworkLibrary.META_DIR}/recording/join.idx"), remote.files.keys)
        assertEquals("synthetic media", remote.files["Weekend/fight.ts"])
        assertEquals(1, remote.replaces)
        assertEquals(listOf(1024L * 1024 + 1, 1024L * 1024 + 1), remote.readLimits)
    }

    @Test fun temporaryWriteFailureKeepsTheOriginalMetadataAndMedia() {
        val remote = Remote()
        every { remote.folder.writeBytes(any(), any()) } throws IOException("Synthetic write failure")
        assertFailsWith<IOException> { NetworkRecordingRename.rename(remote.folder, rec, "Fight") { true } }
        assertEquals(original, remote.files[path])
        assertEquals(0, remote.replaces)
    }

    @Test fun replacementFailureKeepsOriginalMetadataAndRemovesOnlyTheTemporaryFile() {
        val remote = Remote()
        every { remote.folder.rename(any(), path, true) } throws IOException("Synthetic replace failure")
        assertFailsWith<IOException> { NetworkRecordingRename.rename(remote.folder, rec, "Fight") { true } }
        assertEquals(original, remote.files[path])
        assertFalse(remote.files.keys.any { it.endsWith(".tmp") })
        assertEquals("synthetic media", remote.files["Weekend/fight.ts"])
    }

    @Test fun changedNetworkConfigurationAbortsBeforeReplacingTheOriginal() {
        val remote = Remote()
        var checks = 0
        assertFailsWith<IllegalStateException> { NetworkRecordingRename.rename(remote.folder, rec, "Fight") { ++checks == 1 } }
        assertEquals(original, remote.files[path])
        assertEquals(0, remote.replaces)
        assertFalse(remote.files.keys.any { it.endsWith(".tmp") })
    }

    @Test fun anotherDeviceChangingMetadataDuringTheEditIsNotOverwritten() {
        val remote = Remote()
        val other = JsonObject(Json.parseToJsonElement(original).jsonObject + ("title" to JsonPrimitive("Other device"))).toString()
        every { remote.folder.writeBytes(any(), any()) } answers {
            remote.files[firstArg()] = secondArg<ByteArray>().toString(Charsets.UTF_8)
            remote.files[path] = other
        }
        assertFailsWith<IllegalStateException> { NetworkRecordingRename.rename(remote.folder, rec, "Fight") { true } }
        assertEquals(other, remote.files[path])
        assertEquals(0, remote.replaces)
    }

    @Test fun configurationChangeDuringTheFinalMetadataReadAlsoAbortsTheCommit() {
        val remote = Remote()
        var checks = 0
        assertFailsWith<IllegalStateException> { NetworkRecordingRename.rename(remote.folder, rec, "Fight") { ++checks < 3 } }
        assertEquals(original, remote.files[path])
        assertEquals(0, remote.replaces)
        assertFalse(remote.files.keys.any { it.endsWith(".tmp") })
    }

    @Test fun staleTitlesAndUnsafeDirectoryIdsNeverWriteAReplacement() {
        val remote = Remote()
        assertFailsWith<IllegalStateException> { NetworkRecordingRename.rename(remote.folder, rec.copy(title = "stale"), "Fight") { true } }
        assertFailsWith<IllegalArgumentException> { NetworkRecordingRename.rename(remote.folder, rec.copy(id = "../other", dir = "net:../other"), "Fight") { true } }
        assertEquals(0, remote.replaces)
        assertTrue(remote.files.keys.none { it.endsWith(".tmp") })
    }

    @Test fun growingMetadataCannotTurnASmallAdvertisedSizeIntoAnUnboundedRead() {
        val remote = Remote()
        every { remote.folder.size(path) } returns 128L
        remote.files[path] = "x".repeat(1024 * 1024 + 4096)
        val before = remote.files[path]
        assertFailsWith<IllegalStateException> { NetworkRecordingRename.rename(remote.folder, rec, "Fight") { true } }
        assertEquals(listOf(1024L * 1024 + 1), remote.readLimits)
        assertEquals(before, remote.files[path])
        verify(exactly = 0) { remote.folder.size(path) }
        verify(exactly = 0) { remote.folder.writeBytes(any(), any()) }
        assertEquals(0, remote.replaces)
    }

    private inner class Remote {
        val files = linkedMapOf(path to original, "Weekend/fight.ts" to "synthetic media", "${NetworkLibrary.META_DIR}/recording/join.idx" to "synthetic index")
        val folder = mockk<SmbFolder>()
        var replaces = 0
        val readLimits = mutableListOf<Long>()
        init {
            every { folder.size(any()) } answers { files[firstArg<String>()]?.toByteArray()?.size?.toLong() }
            every { folder.read(any(), any(), any(), any()) } answers {
                val data = checkNotNull(files[firstArg<String>()]).toByteArray()
                val offset = secondArg<Long>().toInt()
                val count = thirdArg<Long>()
                readLimits += count
                arg<OutputStream>(3).write(data, offset, minOf(count, (data.size - offset).toLong()).toInt())
            }
            every { folder.writeBytes(any(), any()) } answers { files[firstArg()] = secondArg<ByteArray>().toString(Charsets.UTF_8) }
            every { folder.rename(any(), any(), true) } answers {
                files[secondArg()] = checkNotNull(files.remove(firstArg<String>()))
                replaces++
            }
            every { folder.delete(any()) } answers { files.remove(firstArg<String>()); Unit }
        }
    }
}

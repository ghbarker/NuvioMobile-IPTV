package com.nuvio.app.features.livetv.record

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class OfflineRecordingStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    private val folder = NetworkFolderConfig("home", "tv", password = "fixture-private-password")
    private val key = OfflineRecordingKey(folder.label, "game")
    private val recording = LiveTvRecording(
        id = key.id, channelKey = "ch", channelName = "Sports", channelLogo = null,
        title = "Game", startedAtMs = 510L, stopAtMs = 700L, durationSec = 190.0,
        description = "Final round", status = RecordingStatus.DONE, dir = NetworkLibrary.DIR_PREFIX + key.id,
    )

    private fun writeVideo(dir: File, bytes: ByteArray): Long {
        File(dir, "show.ts").writeBytes(bytes)
        File(dir, "index.m3u8").writeText("#EXTM3U\n#EXTINF:1,\nshow.ts\n#EXT-X-ENDLIST\n")
        return bytes.size.toLong()
    }

    private fun testStore(block: suspend (CoroutineScope, File) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try { withTimeout(10_000) { block(scope, temporary.newFolder()) } } finally { scope.cancel() }
    }

    @Test fun verifiedDownloadSurvivesRestartAndRemovingItLeavesSourceUntouched() = testStore { scope, root ->
        val source = temporary.newFile().apply { writeText("recording from network") }
        val calls = AtomicInteger()
        val resume = CompletableDeferred<Unit>()
        val store = OfflineRecordingStore({ root }, scope, { folder }) { _, _, dir, progress ->
            calls.incrementAndGet()
            progress(.4f)
            resume.await()
            writeVideo(dir, source.readBytes())
        }
        store.save(key, recording)
        store.state.first { it.progress[key] == .4f }
        assertTrue(store.state.value.ready.isEmpty())
        store.save(key, recording)
        resume.complete(Unit)
        val saved = store.state.first { key in it.ready && key !in it.progress }.ready.getValue(key)
        assertEquals(1, calls.get())
        assertEquals(recording.description, saved.description)
        assertEquals("media/index.m3u8", saved.fileName)
        assertFalse(File(saved.dir, "info.json").readText().contains(folder.password))

        val offline = OfflineRecordingStore({ root }, scope, { null }) { _, _, _, _ -> error("NAS is disconnected") }
        offline.start()
        val restored = offline.state.first { key in it.ready }.ready.getValue(key)
        assertEquals(saved, restored)
        assertTrue(File(restored.dir, restored.fileName).readText().contains("show.ts"))
        assertEquals(source.readText(), File(restored.dir, "media/show.ts").readText())
        offline.remove(key)
        offline.state.first { key !in it.ready }
        assertFalse(File(restored.dir).exists())
        assertEquals("recording from network", source.readText())
    }

    @Test fun cancelledAndFailedTransfersCanRetryAndNeverPublishPartialMedia() = testStore { scope, root ->
        val attempt = AtomicInteger()
        val store = OfflineRecordingStore({ root }, scope, { folder }) { _, _, dir, progress ->
            File(dir, "show.ts").writeText("partial")
            progress(.2f)
            when (attempt.incrementAndGet()) {
                1 -> awaitCancellation()
                2 -> throw IOException("fixture disconnect")
                else -> writeVideo(dir, "completed".toByteArray())
            }
        }
        store.save(key, recording)
        store.state.first { it.progress[key] == .2f }
        store.cancel(key)
        assertTrue(store.state.first { key !in it.progress }.ready.isEmpty())
        assertTrue(root.listFiles().orEmpty().isEmpty())
        store.save(key, recording)
        val failure = store.state.first { key in it.failed && key !in it.progress }
        assertTrue(failure.ready.isEmpty())
        assertTrue(root.listFiles().orEmpty().isEmpty())
        store.save(key, recording)
        val result = store.state.first { key in it.ready && key !in it.progress }
        assertFalse(key in result.failed)
        assertEquals("completed", File(result.ready.getValue(key).dir, "media/show.ts").readText())
    }

    @Test fun changingServersDoesNotMixRecordingsWithTheSameId() = testStore { scope, root ->
        var configured = folder
        val store = OfflineRecordingStore({ root }, scope, { configured }) { from, _, dir, _ ->
            writeVideo(dir, from.host.toByteArray())
        }
        store.save(key, recording)
        val first = store.state.first { key in it.ready }.ready.getValue(key)
        configured = folder.copy(host = "other")
        val other = key.copy(folderLabel = configured.label)
        store.save(other, recording)
        val second = store.state.first { other in it.ready }.ready.getValue(other)
        assertNotEquals(first.dir, second.dir)
        store.remove(key)
        assertTrue(other in store.state.first { key !in it.ready }.ready)
        assertEquals("other", File(second.dir, "media/show.ts").readText())
    }

    @Test fun abandonedStagingIsCleanedAndCanBeRetriedAfterRestart() = testStore { scope, root ->
        val interrupted = File(root, ".partial-from-last-process").apply { mkdir() }
        val info = JsonObject(recording.toJson() + ("offlineSource" to JsonPrimitive(folder.label)))
        File(interrupted, "info.json").writeText(info.toString())
        File(interrupted, "show.ts").writeText("unfinished")
        val store = OfflineRecordingStore({ root }, scope, { folder }) { _, _, dir, _ -> writeVideo(dir, byteArrayOf(1, 2)) }
        store.start()
        assertTrue(store.state.first { key in it.failed }.ready.isEmpty())
        assertFalse(interrupted.exists())
        store.save(key, recording)
        assertFalse(key in store.state.first { key in it.ready }.failed)
    }

    @Test fun selectedRecordingCopiesItsWholeLocalPlaylistThroughTheMediaCopier() = testStore { scope, root ->
        val directory = "${NetworkLibrary.META_DIR}/${key.id}"
        val index = "#EXTM3U\n#EXTINF:1,\nchunk0.ts\n#EXTINF:1,\nchunk1.ts\n#EXT-X-ENDLIST\n"
        val remote = mutableMapOf(
            "$directory/index.m3u8" to index.toByteArray(),
            "$directory/chunk0.ts" to byteArrayOf(47, 10),
            "$directory/chunk1.ts" to byteArrayOf(47, 11),
        )
        val copier = OfflineRecordingCopy({ remote[it]?.size?.toLong() }) { name, offset, count, out ->
            out.write(remote.getValue(name), offset.toInt(), count.toInt())
        }
        val store = OfflineRecordingStore({ root }, scope, { folder }) { _, id, dir, progress ->
            copier.copy(id, dir, progress)
        }
        store.save(key, recording)
        val published = store.state.first { key in it.ready }.ready.getValue(key)
        remote.clear()
        val manifest = File(published.dir, published.fileName)
        assertEquals(index, manifest.readText())
        val bytes = manifest.readLines().filter { it.endsWith(".ts") }.flatMap {
            File(manifest.parentFile, it).readBytes().toList()
        }
        assertEquals(listOf<Byte>(47, 10, 47, 11), bytes)
    }
}

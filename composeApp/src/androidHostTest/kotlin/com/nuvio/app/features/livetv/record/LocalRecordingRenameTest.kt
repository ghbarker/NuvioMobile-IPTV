package com.nuvio.app.features.livetv.record

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.just
import io.mockk.Runs
import io.mockk.unmockkObject
import io.mockk.verify
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Exercises the real recorder/store. Only Android's atomic file replacement is adapted to the host filesystem. */
class LocalRecordingRenameTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var state: MutableStateFlow<List<LiveTvRecording>>
    private lateinit var previous: List<LiveTvRecording>
    private lateinit var rec: LiveTvRecording
    private lateinit var info: File
    private lateinit var media: File

    @Before fun seedOnlySyntheticRecording() {
        @Suppress("UNCHECKED_CAST")
        state = LiveTvRecorder.javaClass.getDeclaredField("_recordings").apply { isAccessible = true }
            .get(LiveTvRecorder) as MutableStateFlow<List<LiveTvRecording>>
        previous = state.value
        val dir = temporary.newFolder("recording")
        rec = LiveTvRecording("synthetic", "channel", "PPV", null, "No program guide available", episode = "S01E02",
            startedAtMs = 10L, stopAtMs = 20L, status = RecordingStatus.DONE, dir = dir.absolutePath,
            seriesId = "future-rule", fromCatchup = true)
        info = dir.resolve("info.json").apply { writeText(JsonObject(rec.toJson() + ("unknown" to JsonPrimitive("retained"))).toString()) }
        media = dir.resolve(rec.fileName).apply { writeBytes(byteArrayOf(0x47, 1, 2, 3)) }
        state.value = listOf(rec)
        mockkObject(RecordingPlatform)
        every { RecordingPlatform.available } returns true
        every { RecordingPlatform.keepAlive(any()) } just Runs
        every { RecordingPlatform.replaceText(any(), any(), any()) } answers {
            val file = File(firstArg<String>())
            check(file.readText() == secondArg<String>())
            val temp = File.createTempFile("rename-test-", ".tmp", file.parentFile)
            try { temp.writeText(thirdArg()); Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            finally { temp.delete() }
            Unit
        }
    }

    @After fun restoreStore() { state.value = previous; unmockkObject(RecordingPlatform) }

    @Test fun persistedTitleAndListedTitleAgreeWithoutChangingMediaOrSeriesIdentity() = runBlocking {
        val bytes = media.readBytes()
        assertNull(LiveTvRecorder.rename(rec, "  Weekend fight  "))
        val raw = Json.parseToJsonElement(info.readText()).jsonObject
        assertEquals(rec.copy(title = "Weekend fight"), LiveTvRecording.fromJson(raw, rec.dir))
        assertEquals("retained", (raw["unknown"] as JsonPrimitive).content)
        assertEquals(rec.copy(title = "Weekend fight"), state.value.single())
        assertContentEquals(bytes, media.readBytes())
        assertEquals(setOf("info.json", "recording.ts"), info.parentFile.listFiles()!!.map { it.name }.toSet())
    }

    @Test fun failedMetadataWriteKeepsTheOldListAndOldFile() = runBlocking {
        val before = info.readText()
        every { RecordingPlatform.replaceText(any(), any(), any()) } throws IOException("Synthetic read-only storage")
        assertEquals("Synthetic read-only storage", LiveTvRecorder.rename(rec, "New title"))
        assertEquals(before, info.readText())
        assertEquals(listOf(rec), state.value)
    }

    @Test fun staleMenuDoesNotWriteOverAChangedOrRemovedRecording() = runBlocking {
        state.value = listOf(rec.copy(title = "Already renamed"))
        assertEquals(RecordingRename.CHANGED, LiveTvRecorder.rename(rec, "New title"))
        state.value = emptyList()
        assertEquals(RecordingRename.CHANGED, LiveTvRecorder.rename(rec, "New title"))
        verify(exactly = 0) { RecordingPlatform.replaceText(any(), any(), any()) }
    }

    @Test fun uploadLockRejectsTheEditInsteadOfChangingTheTitleAfterCopyingStarted() = runBlocking {
        @Suppress("UNCHECKED_CAST")
        val lock = NetworkUploads.javaClass.getDeclaredField("lock").apply { isAccessible = true }
            .get(NetworkUploads) as kotlinx.coroutines.sync.Mutex
        lock.lock()
        try {
            assertEquals(RecordingRename.COPYING, LiveTvRecorder.rename(rec, "New title"))
            assertEquals(listOf(rec), state.value)
        } finally { lock.unlock() }
        // A queued copy selects from the store only after this same lock is released.
        assertNull(LiveTvRecorder.rename(rec, "New title"))
        assertEquals("New title", state.value.single().title)
    }

    @Test fun finishedListEntryCannotBeRenamedWhileItsWriterJobIsStillFinishing() = runBlocking {
        @Suppress("UNCHECKED_CAST")
        val jobs = LiveTvRecorder.javaClass.getDeclaredField("jobs").apply { isAccessible = true }
            .get(LiveTvRecorder) as MutableMap<String, kotlinx.coroutines.Job>
        val job = kotlinx.coroutines.Job()
        jobs[rec.id] = job
        try {
            assertEquals("This recording is still finishing. Try again shortly", LiveTvRecorder.rename(rec, "New title"))
            verify(exactly = 0) { RecordingPlatform.replaceText(any(), any(), any()) }
        } finally { jobs.remove(rec.id); job.cancel() }
    }

    @Test fun missingAndNeverStartedWriterJobsDoNotLeaveFinishedRecordingsBlockedUntilRestart() = runBlocking {
        val jobs = writerJobs()
        val neverStarted = CoroutineScope(Dispatchers.Default).launch(start = CoroutineStart.LAZY) { error("Must not start") }
        try {
            for (writer in listOf(null, neverStarted)) {
                val active = rec.copy(status = RecordingStatus.RECORDING)
                state.value = listOf(active)
                info.writeText(active.toJson().toString())
                if (writer == null) jobs.remove(rec.id) else jobs[rec.id] = writer
                forceFinish()
                val finished = state.value.single()
                assertEquals(RecordingStatus.DONE, finished.status)
                assertNull(LiveTvRecorder.rename(finished, "Weekend fight"))
                assertEquals("Weekend fight", state.value.single().title)
                assertEquals("Weekend fight", Json.parseToJsonElement(info.readText()).jsonObject["title"]!!.let { (it as JsonPrimitive).content })
            }
        } finally { neverStarted.cancel(); jobs.remove(rec.id) }
    }

    @Test fun forcedWriterRemainsProtectedUntilCancellationHasActuallyCompleted() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val writer = CoroutineScope(Dispatchers.Default).launch {
            try { entered.complete(Unit); awaitCancellation() }
            finally { withContext(NonCancellable) { release.await() } }
        }
        entered.await()
        val jobs = writerJobs()
        val active = rec.copy(status = RecordingStatus.RECORDING)
        state.value = listOf(active)
        info.writeText(active.toJson().toString())
        jobs[rec.id] = writer
        try {
            forceFinish()
            val finished = state.value.single()
            assertEquals("This recording is still finishing. Try again shortly", LiveTvRecorder.rename(finished, "Weekend fight"))
            release.complete(Unit)
            writer.join()
            assertNull(LiveTvRecorder.rename(finished, "Weekend fight"))
        } finally { release.complete(Unit); writer.cancel(); writer.join(); jobs.remove(rec.id) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun writerJobs() = LiveTvRecorder.javaClass.getDeclaredField("jobs").apply { isAccessible = true }
        .get(LiveTvRecorder) as MutableMap<String, kotlinx.coroutines.Job>

    private fun forceFinish() {
        LiveTvRecorder.javaClass.getDeclaredMethod("forceFinish", String::class.java).apply { isAccessible = true }
            .invoke(LiveTvRecorder, rec.id)
    }
}

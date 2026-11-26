package com.nuvio.app.features.livetv

import com.nuvio.app.features.livetv.record.LiveTvRecording
import com.nuvio.app.features.livetv.record.RecordingRename
import com.nuvio.app.features.livetv.record.RecordingStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RecordingRenameTest {
    private val rec = LiveTvRecording("recording", "channel", "PPV", null, "No program guide available",
        episode = "S01E02", startedAtMs = 10L, stopAtMs = 20L, status = RecordingStatus.DONE,
        endedAtMs = 20L, durationSec = 10.0, sizeBytes = 1024L, dir = "/recordings/recording", seriesId = "keep-rule", fromCatchup = true)

    @Test fun validatesTrimmedTitlesWithoutTreatingThemAsPaths() {
        assertEquals("Weekend fight / main event: 2026", RecordingRename.title("  Weekend fight / main event: 2026  "))
        assertEquals("x".repeat(200), RecordingRename.title("x".repeat(200)))
        listOf("", "   ", "x".repeat(201), "Fight\nNight", "Fight\u0000Night").forEach { assertNull(RecordingRename.title(it)) }
    }

    @Test fun catchupRenamePatchesOnlyTitleAndKeepsUnknownTvMetadata() {
        val raw = JsonObject(rec.toJson() + mapOf("future" to Json.parseToJsonElement("""{"join":"unchanged","pieces":[1,2]}"""), "networkOnly" to JsonPrimitive(true)))
        val renamed = Json.parseToJsonElement(RecordingRename.info(raw.toString(), rec, "  Weekend fight  ")).jsonObject
        assertEquals(raw.filterKeys { it != "title" }, renamed.filterKeys { it != "title" })
        assertEquals(JsonPrimitive("Weekend fight"), renamed["title"])
        assertEquals(rec.copy(title = "Weekend fight"), LiveTvRecording.fromJson(renamed, rec.dir))
    }

    @Test fun failedRecordingsCanBeRenamedWithoutChangingTheirStatusOrProblem() {
        val failed = rec.copy(status = RecordingStatus.FAILED, problem = "Connection ended")
        val renamed = Json.parseToJsonElement(RecordingRename.info(failed.toJson().toString(), failed, "Correct title")).jsonObject
        assertEquals(failed.copy(title = "Correct title"), LiveTvRecording.fromJson(renamed, failed.dir))
    }

    @Test fun activeAndUnknownStatesAreNeverEditedEvenIfTheListCallsThemFinished() {
        listOf("RECORDING", "FUTURE_STATUS").forEach { status ->
            val raw = JsonObject(rec.toJson() + ("status" to JsonPrimitive(status)))
            assertFailsWith<IllegalStateException> { RecordingRename.info(raw.toString(), rec, "New title") }
        }
    }

    @Test fun staleOrReplacedRecordingsAreNotOverwritten() {
        listOf(rec.copy(id = "another"), rec.copy(startedAtMs = 11L), rec.copy(title = "Other device renamed it"), rec.copy(status = RecordingStatus.FAILED)).forEach { current ->
            assertFailsWith<IllegalStateException> { RecordingRename.info(current.toJson().toString(), rec, "New title") }
        }
    }

    @Test fun invalidAndUnreadableMetadataDoesNotProduceReplacementText() {
        assertFailsWith<IllegalArgumentException> { RecordingRename.info(rec.toJson().toString(), rec, "  ") }
        val error = assertFailsWith<IllegalStateException> { RecordingRename.info("{secret-malformed-fixture", rec, "New title") }
        assertEquals("The recording's details couldn't be read", error.message)
    }
}

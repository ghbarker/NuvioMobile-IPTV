package com.nuvio.app.features.livetv

import com.nuvio.app.features.livetv.calendar.CalendarGuideHit
import com.nuvio.app.features.livetv.model.EpgProgram
import com.nuvio.app.features.livetv.model.SUB_TITLE_MARK
import com.nuvio.app.features.livetv.model.guideDescriptionText
import com.nuvio.app.features.livetv.record.optionalGuideDetails
import com.nuvio.app.features.livetv.record.withGuideDetails
import kotlinx.coroutines.test.runTest
import com.nuvio.app.features.livetv.record.ChannelSnapshot
import com.nuvio.app.features.livetv.record.LiveTvRecording
import com.nuvio.app.features.livetv.record.RecordingRules
import com.nuvio.app.features.livetv.record.RecordingSchedule
import com.nuvio.app.features.livetv.record.RecordingStatus
import com.nuvio.app.features.livetv.record.SeriesRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordingRulesTest {
    private val min = 60_000L
    private val t0 = 1_700_000_000_000L

    private fun channel(key: String = "pl|1|A", source: String = "pl") =
        ChannelSnapshot(key, source, "A", null, "http://h/live/u/p/1.ts", mapOf("User-Agent" to "x"), null)

    private fun schedule(
        id: String, start: Long, stop: Long, source: String = "pl", catchup: Boolean = false, pre: Long = 2 * min, post: Long = 5 * min,
    ) = RecordingSchedule(id, channel(source = source), "Show $id", null, null, start, stop, catchup, preMs = pre, postMs = post)

    @Test
    fun paddingWidensTheRecordingWindow() {
        assertEquals((t0 - 2 * min) to (t0 + 60 * min + 5 * min), RecordingRules.window(t0, t0 + 60 * min, 2 * min, 5 * min))
        // No padding, and padding can't be negative.
        assertEquals(t0 to t0 + 60 * min, RecordingRules.window(t0, t0 + 60 * min, 0, 0))
        assertEquals(t0 to t0 + 60 * min, RecordingRules.window(t0, t0 + 60 * min, -5 * min, -5 * min))
    }

    @Test
    fun scheduleUsesItsOwnPaddingOrTheDefaults() {
        val own = schedule("a", t0, t0 + 30 * min, pre = 10 * min, post = 15 * min)
        assertEquals(10 * min, own.pre)
        assertEquals(15 * min, own.post)
        assertEquals(t0 - 10 * min, RecordingRules.fireTime(own))
        val defaults = schedule("b", t0, t0 + 30 * min, pre = -1, post = -1)
        assertEquals(RecordingRules.PRE_MS, defaults.pre)
        assertEquals(RecordingRules.POST_MS, defaults.post)
    }

    @Test
    fun fireTimesForLiveCatchupAndRetries() {
        val live = schedule("a", t0, t0 + 30 * min)
        assertEquals(t0 - 2 * min, RecordingRules.fireTime(live))
        val save = schedule("b", t0, t0 + 30 * min, catchup = true)
        assertEquals(t0 + 30 * min + RecordingRules.CATCHUP_DELAY_MS, RecordingRules.fireTime(save))
        assertEquals(t0 + 99 * min, RecordingRules.fireTime(save.copy(nextTryMs = t0 + 99 * min)))
        assertEquals(t0 + 30 * min + 5 * min, RecordingRules.recordNowStop(t0 + 30 * min, 5 * min))
    }

    @Test
    fun overlapsCountThePaddingOnBothSides() {
        val first = schedule("a", t0, t0 + 60 * min)
        // Starts 3 minutes after the first ends: the first runs 5 minutes late and this one starts 2 early, so they overlap.
        val next = schedule("b", t0 + 63 * min, t0 + 120 * min)
        assertEquals(first, RecordingRules.clashWith(next, listOf(first, next)))
        // Starts 8 minutes after: 5 late + 2 early = 7, no overlap.
        val later = schedule("c", t0 + 68 * min, t0 + 120 * min)
        assertNull(RecordingRules.clashWith(later, listOf(first, later)))
        // Without padding, back to back is fine.
        val a = schedule("d", t0, t0 + 60 * min, pre = 0, post = 0)
        val b = schedule("e", t0 + 60 * min, t0 + 120 * min, pre = 0, post = 0)
        assertNull(RecordingRules.clashWith(b, listOf(a, b)))
    }

    @Test
    fun otherProvidersAndCatchupSavesNeverClash() {
        val first = schedule("a", t0, t0 + 60 * min)
        val otherProvider = schedule("b", t0 + 10 * min, t0 + 50 * min, source = "pl2")
        assertNull(RecordingRules.clashWith(otherProvider, listOf(first, otherProvider)))
        val save = schedule("c", t0 + 10 * min, t0 + 50 * min, catchup = true)
        assertNull(RecordingRules.clashWith(save, listOf(first, save)))
        // A catch-up save on the other side doesn't count either.
        val liveOverSave = schedule("d", t0 + 10 * min, t0 + 50 * min)
        assertNull(RecordingRules.clashWith(liveOverSave, listOf(save, liveOverSave)))
        // A schedule never clashes with itself.
        assertNull(RecordingRules.clashWith(first, listOf(first)))
    }

    @Test
    fun scheduledLookupMatchesChannelAndStart() {
        val s = schedule("a", t0, t0 + 60 * min)
        assertTrue(RecordingRules.isScheduled(listOf(s), "pl|1|A", EpgProgram(t0, t0 + 60 * min, "Show a")))
        assertFalse(RecordingRules.isScheduled(listOf(s), "pl|1|A", EpgProgram(t0 + min, t0 + 60 * min, "Show a")))
        assertFalse(RecordingRules.isScheduled(listOf(s), "pl|2|B", EpgProgram(t0, t0 + 60 * min, "Show a")))
    }

    @Test
    fun episodeKeysReadEveryGuideForm() {
        assertEquals("s2e5", RecordingRules.episodeKey("S02E05"))
        assertEquals("s2e5", RecordingRules.episodeKey("S2 E5"))
        assertEquals("s2e5", RecordingRules.episodeKey("Season 2 Episode 5"))
        assertEquals("s2e5", RecordingRules.episodeKey("2x05"))
        assertEquals("s2e5", RecordingRules.episodeKey("1.4."))
        assertEquals("e7", RecordingRules.episodeKey("Ep 7"))
        assertEquals("x:abc123", RecordingRules.episodeKey("ABC-123"))
        assertNull(RecordingRules.episodeKey(null))
        assertNull(RecordingRules.episodeKey("  "))
    }

    @Test
    fun calendarRecordingRetainsTheAiringSynopsisAfterTheGuideExpires() = runTest {
        val guide = EpgProgram(t0, t0 + 60 * min, "Show", episode = "S1E2",
            description = "${SUB_TITLE_MARK}The guest\nAn old letter arrives.")
        val displayed = EpgProgram(t0 + min, guide.stopMs + min, "Show")
        val hit = CalendarGuideHit("pl|1|A", "A", null, displayed, guideStartMs = guide.startMs)
        val queued = schedule("a", hit.startMs, hit.stopMs).copy(title = hit.program.title, guideStartMs = hit.guideStartMs)
        val hydrated = RecordingSchedule.fromJson(queued.toJson()).withGuideDetails(5 * min) { channelKey, guideStart ->
            assertEquals(hit.channelKey, channelKey)
            assertEquals(t0, guideStart)
            guide
        }
        assertEquals(displayed.startMs, hydrated.startMs)
        assertEquals(displayed.stopMs, hydrated.stopMs)
        val saved = RecordingSchedule.fromJson(hydrated.toJson()).withGuideDetails(0) { _, _ -> error("Guide expired") }
        val recording = LiveTvRecording(id = "r", channelKey = hit.channelKey, channelName = hit.channelName,
            channelLogo = null, title = saved.title, episode = saved.episode, description = saved.description,
            startedAtMs = t0, stopAtMs = hit.stopMs, dir = "/shows")
        val reopened = assertNotNull(LiveTvRecording.fromJson(recording.toJson(), recording.dir))
        assertEquals("S1E2", reopened.episode)
        assertEquals("An old letter arrives.", guideDescriptionText(reopened.description))
    }

    @Test
    fun aReplacementAiringCannotSupplyTheWrongEpisodeDescription() = runTest {
        val light = schedule("a", t0, t0 + min).copy(title = "Show", episode = "S2E1", guideStartMs = t0)
        val full = EpgProgram(t0, t0 + min, "NEW: Show", episode = "S02E01", description = "Opening episode")
        assertEquals("Opening episode", light.withGuideDetails(0) { _, _ -> full }.description)
        assertEquals(light, light.withGuideDetails(0) { _, _ -> null })
        for (other in listOf(full.copy(startMs = t0 + min), full.copy(stopMs = t0 + 2 * min),
            full.copy(title = "Another show"), full.copy(episode = "S2E2"), full.copy(stalkerSourceIdentity = "replacement"))) {
            assertEquals(light, light.withGuideDetails(0) { _, _ -> other })
        }
        val archive = light.copy(stalkerProgramId = "42")
        assertEquals(archive, archive.withGuideDetails(0) { _, _ -> full.copy(stalkerProgramId = "43") })
        val named = light.copy(description = "${SUB_TITLE_MARK}Pilot")
        assertEquals(named, named.withGuideDetails(0) { _, _ -> full.copy(description = "${SUB_TITLE_MARK}Finale\nDifferent episode") })
    }

    @Test
    fun legacySchedulesRequireOneExactGuideAiringAndCustomTimeSlotsSkipTheGuide() = runTest {
        val legacy = schedule("a", t0, t0 + 30 * min)
        val reloaded = RecordingSchedule.fromJson(legacy.toJson())
        val full = EpgProgram(t0, t0 + 30 * min, legacy.title, description = "Original synopsis")
        assertEquals(full.description, reloaded.withGuideDetails(min) { _, start -> full.takeIf { start == t0 } }.description)
        assertNull(reloaded.withGuideDetails(min) { _, start ->
            full.copy(startMs = start, stopMs = start + 30 * min)
        }.description)
        val manual = legacy.copy(guideStartMs = 0L)
        assertEquals(manual, RecordingSchedule.fromJson(manual.toJson()).withGuideDetails(min) { _, _ -> error("Manual slot") })
    }

    @Test
    fun optionalMetadataFailureAllowsRecordingAndPreservesCancellation() {
        assertNull(optionalGuideDetails<String> { throw IllegalStateException("Guide unavailable") })
        assertFailsWith<kotlinx.coroutines.CancellationException> {
            optionalGuideDetails<String> { throw kotlinx.coroutines.CancellationException() }
        }
    }

    @Test
    fun aGuideSubtitleAloneIsNotAnEpisodeSynopsis() {
        assertNull(guideDescriptionText("${SUB_TITLE_MARK}The guest"))
        assertNull(guideDescriptionText(null))
        assertEquals("Plain description", guideDescriptionText("Plain description"))
    }

    @Test
    fun recordingsAndSchedulesRoundTripThroughJson() {
        val rec = LiveTvRecording(
            id = "rec1", channelKey = "pl|1|A", channelName = "A", channelLogo = null, title = "Show", episode = "S1E2",
            description = "About", programStartMs = t0, programStopMs = t0 + 60 * min, startedAtMs = t0 - 2 * min, stopAtMs = t0 + 65 * min,
            endedAtMs = t0 + 65 * min, status = RecordingStatus.DONE, durationSec = 4020.5, sizeBytes = 1234L, problem = null,
            dir = "/x/rec1", seriesId = "r1", fromCatchup = true, fileName = "recording.mp4",
        )
        assertEquals(rec, LiveTvRecording.fromJson(rec.toJson(), "/x/rec1"))
        val s = schedule("a", t0, t0 + 60 * min, catchup = true).copy(attempts = 2, nextTryMs = t0 + 5 * min, seriesId = "r1")
        assertEquals(s, RecordingSchedule.fromJson(s.toJson()))
        val rule = SeriesRule("r1", channel(), "Show", keepLast = 3, seen = listOf("s1e1", "s1e2"))
        assertEquals(rule, SeriesRule.fromJson(rule.toJson()))
        assertNotNull(rule.norm)
        assertEquals("show", rule.norm)
    }
}

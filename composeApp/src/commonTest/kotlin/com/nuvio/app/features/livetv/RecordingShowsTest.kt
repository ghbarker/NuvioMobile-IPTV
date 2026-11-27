package com.nuvio.app.features.livetv

import com.nuvio.app.features.livetv.record.LiveTvRecording
import com.nuvio.app.features.livetv.record.RecordedShow
import com.nuvio.app.features.livetv.record.RecordingStatus
import com.nuvio.app.features.livetv.record.SingleRecording
import com.nuvio.app.features.livetv.record.groupRecorded
import com.nuvio.app.features.livetv.record.recordingAiredMs
import com.nuvio.app.features.livetv.record.recordingEntryKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RecordingShowsTest {
    @Test
    fun completedCopiesFoldByTitleAcrossRulesChannelsAndStorageAtTheFirstMember() {
        val single = rec("single", title = "A film")
        val newest = rec("latest", title = "  The   SHOW ", seriesId = "rule-one", channel = "one")
        val failed = rec("failed", title = "the show", status = RecordingStatus.FAILED)
        val network = rec("network", title = "the\tshow", seriesId = "rule-two", channel = "two", dir = "net:network")
        val manual = rec("manual", title = "The Show", seriesId = null)
        val input = listOf(single, newest, failed, network, manual)

        val entries = groupRecorded(input)

        assertEquals(listOf("d:single", "show:the show", "d:failed"), entries.map { it.key })
        val folder = assertIs<RecordedShow>(entries[1])
        assertEquals(newest.title, folder.title)
        assertEquals(setOf(newest, network, manual), folder.episodes.toSet())
        folder.episodes.forEach { episode -> assertSame(input.single { it === episode }, episode) }
        assertSame(failed, assertIs<SingleRecording>(entries.last()).rec)
    }

    @Test
    fun episodesUseNumericSeasonEpisodeOrderThenAirTimeAndPutUnnumberedOnesLast() {
        val seasonTwoTen = rec("2-10", episode = "s 02 e 0010", aired = 10)
        val seasonTwoTwoLater = rec("2-2-later", episode = "S2.E2", aired = 30)
        val seasonTwoTwoEarlier = rec("2-2-earlier", episode = "S002-E0002", aired = 20)
        val seasonOne = rec("1-12", episode = "S01E12", aired = 99)
        val unknownLater = rec("unknown-later", episode = "provider episode id", aired = 70)
        val unknownEarlier = rec("unknown-earlier", episode = null, aired = 60)
        val input = listOf(unknownLater, seasonTwoTen, seasonTwoTwoLater, seasonOne, unknownEarlier, seasonTwoTwoEarlier)

        val folder = assertIs<RecordedShow>(groupRecorded(input).single())

        assertEquals(listOf("1-12", "2-2-earlier", "2-2-later", "2-10", "unknown-earlier", "unknown-later"),
            folder.episodes.map { it.id })
        assertEquals("unknown-later", input.first().id)
    }

    @Test
    fun unknownOrEqualEpisodeNumbersFallBackToRecordingTimeWhenGuideTimeIsMissing() {
        val late = rec("late", episode = "1x01", aired = 0, started = 30)
        val early = rec("early", episode = "Episode one", aired = -1, started = 10)
        val withGuide = rec("guide", episode = null, aired = 20, started = 99)

        assertEquals(listOf("early", "guide", "late"),
            assertIs<RecordedShow>(groupRecorded(listOf(late, early, withGuide)).single()).episodes.map { it.id })
        assertEquals(30L, recordingAiredMs(late))
        assertEquals(10L, recordingAiredMs(early))
        assertEquals(20L, recordingAiredMs(withGuide))
    }

    @Test
    fun activeFailedBlankAndSingleCompletedRecordingsNeverDisappearIntoFolders() {
        val input = listOf(
            rec("active", status = RecordingStatus.RECORDING),
            rec("failed", status = RecordingStatus.FAILED),
            rec("done"),
            rec("blank-one", title = ""),
            rec("blank-two", title = " \t "),
        )

        val entries = groupRecorded(input)

        assertEquals(input.size, entries.size)
        entries.forEachIndexed { index, entry -> assertSame(input[index], assertIs<SingleRecording>(entry).rec) }
        assertTrue(groupRecorded(emptyList()).isEmpty())
    }

    @Test
    fun punctuationAndLiveOrNewMarksAreNotSilentlyRemovedFromFolderTitles() {
        val titles = listOf("The Show", "The: Show", "NEW: The Show", "The Show (New)")

        assertEquals(titles, groupRecorded(titles.mapIndexed { i, title -> rec("$i", title = title) })
            .map { assertIs<SingleRecording>(it).rec.title })
    }

    @Test
    fun deletingUntilOnlyOneEpisodeRemainsReturnsThatRecordingAsASingleTile() {
        val first = rec("first", episode = "S01E01")
        val second = rec("second", episode = "S01E02")
        val before = assertIs<RecordedShow>(groupRecorded(listOf(second, first)).single())
        assertEquals("show:the show", before.key)

        val after = assertIs<SingleRecording>(groupRecorded(before.episodes.filterNot { it.id == first.id }).single())

        assertSame(second, after.rec)
        assertEquals("d:second", after.key)
    }

    @Test
    fun localAndNetworkCopiesWithTheSameIdKeepSeparateRowAndActionIdentities() {
        val local = rec("same", title = "Local film", dir = "/recordings/same")
        val network = rec("same", title = "TV film", dir = "net:same")
        assertEquals(listOf("d:same", "n:same"), groupRecorded(listOf(local, network)).map { it.key })

        val networkEpisode = network.copy(title = local.title)
        val folder = assertIs<RecordedShow>(groupRecorded(listOf(local, networkEpisode)).single())
        assertEquals(2, folder.episodes.size)
        assertEquals(setOf("d:same", "n:same"), folder.episodes.map(::recordingEntryKey).toSet())
    }

    private fun rec(
        id: String,
        title: String = "The Show",
        episode: String? = null,
        status: RecordingStatus = RecordingStatus.DONE,
        aired: Long = 1,
        started: Long = 1,
        seriesId: String? = "series",
        channel: String = "channel",
        dir: String = "/recordings/$id",
    ) = LiveTvRecording(
        id = id, channelKey = channel, channelName = channel, channelLogo = null,
        title = title, episode = episode, programStartMs = aired, startedAtMs = started,
        stopAtMs = started + 1_000, status = status, dir = dir, seriesId = seriesId,
    )
}

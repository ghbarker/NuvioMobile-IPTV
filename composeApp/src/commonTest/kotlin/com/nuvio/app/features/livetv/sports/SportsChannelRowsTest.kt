package com.nuvio.app.features.livetv.sports

import com.nuvio.app.features.livetv.model.LiveChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SportsChannelRowsTest {
    private fun match(key: String, name: String, guideQuality: String? = null, catchup: Boolean = false) = ChannelMatch(
        LiveChannel(key, "provider", "Playlist", name, null, null, null, "group", "Sports", 1, ""),
        80, "Guide", null, guideQuality, catchup,
    )

    @Test
    fun ordersIndividualCopiesByHeightThenRateAndKeepsTiesStable() {
        val names = listOf("TNT Sports 4", "beIN Sports 1", "TNT Sports 4 (576p50)", "TNT Sports 4 (720p25)",
            "TNT Sports 4 (575p25)", "TNT Sports 4 (480p25)", "TNT Sports 4 (720p25)",
            "TNT Sports 4 (1080p50)", "TNT Sports 4 (1080p25)")
        val matches = names.mapIndexed { i, name -> match(i.toString(), name) }
        val rows = sportsGameChannelRows(matches + matches.last(), emptyMap(), true)
        assertEquals(listOf("7", "8", "3", "6", "2", "4", "5", "0", "1"), rows.map { it.match.channel.key })
        assertEquals(listOf("1080p50", "1080p25", "720p25", "720p25", "576p50", "575p25", "480p25", null, null),
            rows.map { it.quality?.label })
        assertEquals(SportsQualitySource.PROVIDER, rows.first().quality?.source)
    }

    @Test
    fun selectedFormatSupersedesMisleadingNamesAndNeverBorrowsTheirFrameRate() {
        val matches = listOf(match("a", "TNT FHD"), match("b", "TNT 2160p60"), match("c", "TNT 4K"))
        val small = measuredSportsQuality(1280, 720, 59.94f)!!
        val noRate = measuredSportsQuality(1920, 1080, -1f)!!
        val rows = sportsGameChannelRows(matches, mapOf("a" to small.label, "b" to noRate.label), true)
        assertEquals(listOf("c", "b", "a"), rows.map { it.match.channel.key })
        assertEquals(SportsQualitySource.PLAYBACK, rows.last().quality?.source)
        assertEquals("1280×720 · 59.94 fps", rows.last().quality?.label)
        assertEquals("1920×1080", rows[1].quality?.label)
        assertEquals(0f, rows[1].quality?.frameRate)
        assertEquals("HD", matchQualityTier(small.label))
        assertEquals("FHD", matchQualityTier(noRate.label))
        assertEquals("FHD", matchQualityTier("FHD"))
    }

    @Test
    fun oldCacheTagsAreSavedHintsAndBadInputsNeverBecomeMeasurements() {
        val rows = sportsGameChannelRows(listOf(match("a", "Sports"), match("b", "TNT 1080i50"),
            match("c", "Sports2", "HD")), mapOf("a" to "FHD", "b" to "broken"), true)
        assertEquals(listOf(SportsQualitySource.PROVIDER, SportsQualitySource.SAVED, SportsQualitySource.PROVIDER),
            rows.map { it.quality?.source })
        assertEquals("1080i50", rows.first().quality?.label)
        assertEquals(25f, rows.first().quality?.frameRate)
        assertNull(measuredSportsQuality(null, 1080, 60f))
        assertNull(measuredSportsQuality(1920, -1, 60f))
        assertEquals(0f, measuredSportsQuality(720, 576, Float.NaN)?.frameRate)
    }

    @Test
    fun savedGradesOverrideConflictsAndConsistentProviderRatesHaveTheirOwnAttribution() {
        val matches = listOf(match("a", "TNT 4K"), match("b", "TNT 1080p50"), match("c", "TNT 1080p25"),
            match("d", "TNT 720p60"), match("e", "TNT 1080i50"))
        val rows = sportsGameChannelRows(matches, mapOf("a" to "SD", "b" to "1920×1080", "c" to "FHD",
            "d" to "1920×1080", "e" to "1920×1080"), true)
        assertEquals(listOf("b", "c", "e", "d", "a"), rows.map { it.match.channel.key })
        assertEquals("SD", rows.last().quality?.label)
        assertEquals(SportsQualitySource.SAVED, rows.last().quality?.source)
        assertEquals("1920×1080", rows.first().quality?.label)
        assertEquals("1080p50", rows.first().quality?.reportedRateLabel)
        assertEquals(25f, rows[2].quality?.frameRate)
        assertEquals("1080i50", rows[2].quality?.reportedRateLabel)
        assertNull(rows[3].quality?.reportedRateLabel)
    }

    @Test
    fun disablingQualityOrderKeepsOriginalGroupingOrderAndCatchupCopies() {
        val matches = listOf(match("a", "ABC SD"), match("b", "DEF 4K"), match("c", "ABC HD", catchup = true))
        val rows = sportsGameChannelRows(matches, emptyMap(), false)
        assertEquals(listOf("a", "b"), rows.map { it.match.channel.key })
        assertEquals(listOf(2, 1), rows.map { it.copies })
        assertEquals(listOf(true, false), rows.map { it.hasCatchup })
    }
}

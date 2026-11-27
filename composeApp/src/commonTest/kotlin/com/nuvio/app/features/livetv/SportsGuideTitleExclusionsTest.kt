package com.nuvio.app.features.livetv

import com.nuvio.app.features.livetv.model.EpgProgram
import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.livetv.sports.ChannelMatch
import com.nuvio.app.features.livetv.sports.EventSource
import com.nuvio.app.features.livetv.sports.GuideSearch
import com.nuvio.app.features.livetv.sports.SportsCatalog
import com.nuvio.app.features.livetv.sports.SportsFollows
import com.nuvio.app.features.livetv.sports.SportsGuideTitleExclusions
import com.nuvio.app.features.livetv.sports.SportsMatcher
import com.nuvio.app.features.livetv.sports.SportsSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SportsGuideTitleExclusionsTest {
    private val now = 1_800_000_000_000L
    private val none = SportsGuideTitleExclusions(emptyList())
    private val shows = listOf("5 Live Boxing with Steve Bunce", "MotoGP Paddock Live Show")
    private val excluded = SportsGuideTitleExclusions(shows)

    private fun channel(key: String) =
        LiveChannel(key, "pl", "Playlist", "Feed $key", null, null, null, "pl::Sports", "Sports", 1, "https://example.com/$key")

    private fun program(title: String, description: String? = null, start: Long = now) =
        EpgProgram(start, start + 3_600_000L, title, description = description)

    @Test
    fun matchesWholePhrasesIgnoringCasePunctuationAndUnicodeComposition() {
        val rules = SportsGuideTitleExclusions(listOf("Paddock live show", "Équipe Москва"))
        assertTrue(rules.matches("MotoGP: PADDOCK—Live   Show (Italy)"))
        assertTrue(rules.matches("E\u0301QUIPE / МОСКВА"))
        assertFalse(rules.matches("MotoGP Paddock Live Showcase"))
        assertFalse(rules.matches("MotoGP Paddock is live on the show"))
        assertFalse(rules.matches("Équipe Московская"))
        assertFalse(none.matches(shows.first()))
    }

    @Test
    fun bothTalkShowsCanBeHiddenAndRestoredWithoutMatchingDescriptions() {
        val rows = mapOf("a" to listOf(program(shows[0])), "b" to listOf(program(shows[1])),
            "c" to listOf(program("Live: MotoGP Sprint", "Interviews from MotoGP Paddock Live Show follow.")))
        val channels = rows.keys.associateWith(::channel)
        val matcher = SportsMatcher(GuideSearch.InMemory(rows))
        val candidates = matcher.guideCandidates(now, now + 1L, SportsCatalog.all.filter { it.guideOnly },
            listOf("Steve Bunce", "Paddock"), channels, 0)
        assertEquals(3, matcher.guideEvents(candidates, none, now).size)
        val visible = matcher.guideEvents(candidates, excluded, now)
        assertEquals(listOf("MotoGP Sprint"), visible.map { it.title })
        assertEquals(listOf("c"), visible.single().guideHits.map { it.first })
        assertEquals(3, matcher.guideEvents(candidates, none, now).size)
    }

    @Test
    fun removingOneAiringPreservesOtherAiringMetadataAndChannel() {
        val rows = mapOf("excluded" to listOf(program("Live: MotoGP Race", "Hidden airing")),
            "kept" to listOf(program("MotoGP Race", "Live coverage", now + 60_000L)))
        val matcher = SportsMatcher(GuideSearch.InMemory(rows))
        val candidates = matcher.guideCandidates(now, now + 120_000L, SportsCatalog.all.filter { it.guideOnly },
            emptyList(), rows.keys.associateWith(::channel), 0)
        assertEquals(2, matcher.guideEvents(candidates, none, now).single().guideHits.size)
        val event = matcher.guideEvents(candidates, SportsGuideTitleExclusions(listOf("Live MotoGP")), now).single()
        assertEquals("Live coverage", event.subtitle)
        assertEquals(now + 60_000L, event.startMs)
        assertEquals(listOf("kept"), event.guideHits.map { it.first })
    }

    @Test
    fun removingAutomaticEvidenceKeepsTheFixtureAndManualChannel() {
        val rows = mapOf("studio" to listOf(program(shows[1])), "race" to listOf(program("Live: MotoGP Sprint")))
        val channels = rows.keys.associateWith(::channel)
        val matcher = SportsMatcher(GuideSearch.InMemory(rows))
        val candidates = matcher.guideCandidates(now, now + 1L, SportsCatalog.all.filter { it.guideOnly },
            emptyList(), channels, 0)
        val guide = matcher.guideEvents(candidates, none, now)
        val fixture = guide.last().copy(id = "fixture", source = EventSource.FIXTURES, title = shows[1], guideHits = emptyList())
        fun results(s: SportsSettings, rules: SportsGuideTitleExclusions) = matcher.match(SportsMatcher.Input(
            guide + fixture, channels.values.toList(), emptyMap(), 0, s, now, excludedGuideTitles = rules)).matches
        val before = results(SportsSettings(), none)
        assertEquals(setOf("studio", "race"), before.getValue(fixture.id).map { it.channel.key }.toSet())
        assertEquals(listOf("race"), excluded.visibleMatches(before).getValue(fixture.id).map { it.channel.key })
        assertEquals(listOf("race"), results(SportsSettings(), excluded).getValue(fixture.id).map { it.channel.key })
        val fixed = results(SportsSettings(eventChannels = mapOf(fixture.id to "studio|$now")), excluded)
        assertEquals("Your pick", excluded.visibleMatches(fixed).getValue(fixture.id).first().how)
    }

    @Test
    fun storedRulesSurviveRoundTripAndOldProfilesStartEmpty() {
        val profile = SportsFollows.fromJson("""{"words":["MotoGP"],"excludedGuideTitles":[" Steve Bunce ","STEVE—BUNCE","---","MotoGP Paddock Live Show"]}""")
        assertEquals(listOf("Steve Bunce", shows[1]), profile.excludedGuideTitles)
        assertEquals(profile, SportsFollows.fromJson(profile.toJson()))
        assertEquals(listOf("MotoGP"), profile.words)
        assertEquals(emptyList(), SportsFollows.fromJson("""{"words":["MotoGP"]}""").excludedGuideTitles)
    }

    @Test
    fun staleAndRestoredMatchesKeepIndependentChannelEvidence() {
        val feed = channel("feed")
        val airing = program(shows[1])
        val picks = listOf(
            ChannelMatch(feed, 200, "Your pick", airing, null, true),
            ChannelMatch(feed, 95, "Channel name", airing, null, false),
            ChannelMatch(feed, 100, "Guide: ${airing.title}", null, null, false),
            ChannelMatch(feed, 62, "ESPN listed by ESPN", null, null, false, airing.title),
            ChannelMatch(feed, 62, "ESPN listed by ESPN", null, null, false),
            ChannelMatch(feed, 45, "ESPN listed by ESPN", null, null, false),
        )
        val cache = mapOf("fixture" to picks)
        assertEquals(cache, none.visibleMatches(cache))
        val kept = excluded.visibleMatches(cache).getValue("fixture")
        assertEquals(listOf(200, 95, 45), kept.map { it.score })
        assertTrue(kept.all { it.program == null && !it.catchup })
    }
}

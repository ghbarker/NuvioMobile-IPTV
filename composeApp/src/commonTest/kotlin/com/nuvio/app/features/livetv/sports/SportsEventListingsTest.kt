package com.nuvio.app.features.livetv.sports

import com.nuvio.app.features.livetv.model.EpgProgram
import com.nuvio.app.features.livetv.model.LiveChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SportsEventListingsTest {
    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L
    private val moto = "guide/motogp"
    private fun event(id: String, source: EventSource, league: String = moto, at: Long = now, word: String? = null) =
        SportsEvent(id, league, at, true, GameState.LIVE, id, null, null, null, null,
            source = source, endMs = at + 2 * hour, word = word)
    private fun follows(source: SportsListingSource) = SportsFollows(pins = listOf(moto)).withListingSource(moto, source)

    @Test fun defaultCombinedModeKeepsOriginalListIdentityAndDuplicates() {
        val guide = event("guide", EventSource.GUIDE)
        val list = listOf(guide, guide, event("scheduled", EventSource.FIXTURES))
        assertSame(list, SportsEventListings.visibleKnown(list, follows(SportsListingSource.BOTH), now))
        assertSame(list, SportsEventListings.forPage(list, list, follows(SportsListingSource.BOTH), now - hour, now + hour, now))
    }

    @Test fun scheduleAndGuideChoicesRecoverOnlyTheirCardsBeforeMySportsCap() {
        val guides = (1..30).map { event("guide-$it", EventSource.GUIDE, at = now + it) }
        val scheduled = event("Portugal Sprint Race", EventSource.FIXTURES)
        val unrelated = event("Formula One", EventSource.ESPN, league = "racing/f1")
        val raw = guides + unrelated
        val known = raw + scheduled
        val selected = follows(SportsListingSource.SCHEDULE)
        val page = SportsEventListings.forPage(raw, known, selected, now - hour, now + 3 * hour, now)
        assertEquals(listOf(unrelated.id, scheduled.id), page.map { it.id })
        val rows = SportsRows.build(known, selected, now) { it.leagueId == moto }
        assertEquals(listOf(scheduled.id), rows.rows.first { it.kind == SportsRow.Kind.UPCOMING }.events.map { it.id })
        val guidePage = SportsEventListings.forPage(listOf(scheduled), known,
            follows(SportsListingSource.GUIDE), now - hour, now + 3 * hour, now)
        assertEquals(guides.map { it.id }, guidePage.map { it.id })
    }

    @Test fun separatelyFollowedNamesRemainIndependentOfLeagueSourceChoice() {
        val guide = event("guide", EventSource.GUIDE)
        val word = event("guide-word", EventSource.GUIDE, word = "Rider")
        val selected = follows(SportsListingSource.SCHEDULE).copy(words = listOf("rider"))
        assertEquals(listOf(word), SportsEventListings.visibleKnown(listOf(guide, word), selected, now))
        assertEquals(listOf(word), SportsEventListings.forPage(emptyList(), listOf(guide, word), selected,
            now - hour, now + hour, now))
    }

    @Test fun restoredCardsAreDeduplicatedAndBoundedToTheRequestedHorizon() {
        val scheduled = event("scheduled", EventSource.FIXTURES)
        val future = event("later", EventSource.FIXTURES, at = now + 10 * hour)
        val raw = listOf(scheduled)
        val known = listOf(scheduled, scheduled, future)
        assertEquals(raw, SportsEventListings.forPage(raw, known, follows(SportsListingSource.SCHEDULE), now - hour, now + hour, now))
        assertSame(known, SportsEventListings.withContext(known, emptyList(), now))
        assertEquals(known + future.copy(id = "new", state = GameState.PRE), SportsEventListings.withContext(known,
            listOf(scheduled, future.copy(id = "new")), now))
    }

    @Test fun freshGuideContextStillSuppliesChannelsToHiddenScheduleCardsAcrossSports() {
        val channel = LiveChannel("station", "fixture", "Fixture", "Station One", null, null, null,
            "fixture::Events", "Events", 1, "http://example.invalid/live.ts")
        for (league in listOf(moto, "guide/wwe", "guide/darts")) {
            val programme = EpgProgram(now - hour, now + 2 * hour, "Guide event")
            val guide = event("guide-$league", EventSource.GUIDE, league).copy(guideHits = listOf(channel.key to programme))
            val scheduled = event("scheduled-$league", EventSource.FIXTURES, league)
            val f = SportsFollows(pins = listOf(league)).withListingSource(league, SportsListingSource.SCHEDULE)
            val input = SportsEventListings.forMatching(listOf(guide), listOf(guide, scheduled), f,
                SportsSettings(), now, listOf(guide))
            assertTrue(input.any { it.id == guide.id })
            val result = SportsMatcher(GuideSearch.InMemory(emptyMap())).match(SportsMatcher.Input(
                input, listOf(channel), emptyMap(), 0L, SportsSettings(), now, readGuide = false))
            val found = result.matches.getValue(scheduled.id).single()
            assertEquals(channel.key, found.channel.key)
            assertEquals(programme, found.program)
            assertTrue(found.how.startsWith("Guide:"))
            assertEquals(listOf(scheduled.id), SportsEventListings.forPage(listOf(guide), listOf(guide, scheduled), f,
                now - hour, now + hour, now).map { it.id })
        }
    }

    @Test fun freshEmptyGuideContextCannotReuseStaleSelectedLeagueChannels() {
        val guide = event("stale-guide", EventSource.GUIDE)
        val scheduled = event("scheduled", EventSource.FIXTURES)
        val other = event("other-guide", EventSource.GUIDE, "guide/wwe")
        val selected = follows(SportsListingSource.SCHEDULE)
        val input = SportsEventListings.forMatching(listOf(guide, other), listOf(guide, scheduled), selected,
            SportsSettings(), now, emptyList())
        assertEquals(listOf(other.id, scheduled.id), input.map { it.id })
        assertFalse(input.any { it.id == guide.id })
    }

    @Test fun matchingExtraCardsStayInsideTheExistingWindowUnlessExplicitlyPicked() {
        val near = event("near", EventSource.FIXTURES)
        val old = event("old", EventSource.FIXTURES, at = now - 9 * hour)
        val future = event("future", EventSource.FIXTURES, at = now + SportsMatcher.AHEAD_MS + hour)
        val known = listOf(near, old, future)
        val selected = follows(SportsListingSource.SCHEDULE)
        assertEquals(listOf(near), SportsEventListings.forMatching(emptyList(), known, selected, SportsSettings(), now))
        val picked = SportsSettings(eventChannels = mapOf(future.id to "channel|${future.startMs}"))
        assertEquals(setOf(near.id, future.id), SportsEventListings.forMatching(emptyList(), known, selected, picked, now).map { it.id }.toSet())
    }

    @Test fun dayPreparationUsesTheSameChoiceForRestoredScheduledAndGuideCards() = runTest {
        val guides = (1..12).map { event("guide-$it", EventSource.GUIDE) }
        val scheduled = event("scheduled", EventSource.FIXTURES)
        suspend fun page(raw: List<SportsEvent>, known: List<SportsEvent>, source: SportsListingSource): List<String> {
            val selected = follows(source)
            val query = SportsDayQuery(0, null, now - hour, now + 23 * hour, setOf(moto), false, selected)
            return buildSportsDayPage(SportsDayInputs(query, raw, SportsSettings(), emptyMap(), now, known)) { ev, _ ->
                ev.leagueId == moto
            }.sections.live.map { it.id }
        }
        assertEquals(listOf(scheduled.id), page(guides, guides + scheduled, SportsListingSource.SCHEDULE))
        assertEquals(guides.map { it.id }, page(listOf(scheduled), guides + scheduled, SportsListingSource.GUIDE))
        assertEquals(guides.map { it.id }, page(guides, guides + scheduled, SportsListingSource.BOTH))
        assertTrue(page(guides, emptyList(), SportsListingSource.SCHEDULE).isEmpty())
        assertEquals(listOf(scheduled.id), page(guides, listOf(scheduled), SportsListingSource.SCHEDULE))
    }

    @Test fun guideOnlyRecoveredCardsKeepTheirOwnChannelsWithoutExposingScheduleCards() {
        val current = event("scheduled", EventSource.FIXTURES)
        val programme = EpgProgram(now - hour, now + hour, "Live event")
        val channel = LiveChannel("station", "fixture", "Fixture", "Station One", null, null, null,
            "fixture::Events", "Events", 1, "http://example.invalid/live.ts")
        val fresh = event("guide", EventSource.GUIDE).copy(guideHits = listOf(channel.key to programme))
        val selected = follows(SportsListingSource.GUIDE)
        val input = SportsEventListings.forMatching(listOf(current), emptyList(), selected,
            SportsSettings(), now, listOf(fresh))
        val matched = SportsMatcher(GuideSearch.InMemory(emptyMap())).match(SportsMatcher.Input(
            input, listOf(channel), emptyMap(), 0L, SportsSettings(), now, readGuide = false))
        assertEquals(channel.key, matched.matches.getValue(fresh.id).single().channel.key)
        assertEquals(listOf(fresh.id), SportsEventListings.forPage(listOf(current), listOf(current, fresh), selected,
            now - hour, now + hour, now).map { it.id })
    }

    @Test fun anExplicitReminderKeepsLegacyScheduleChannelsAfterResettingTheSourceChoice() {
        val channel = LiveChannel("station", "fixture", "Fixture", "Station One", null, null, null,
            "fixture::Events", "Events", 1, "http://example.invalid/live.ts")
        val programme = EpgProgram(now - hour, now + 2 * hour, "Guide race")
        val guide = event("guide", EventSource.GUIDE).copy(guideHits = listOf(channel.key to programme))
        val legacy = event("legacy-fixture", EventSource.FIXTURES)
        val raw = listOf(guide)
        val context = SportsEventListings.withContext(raw, listOf(legacy), now)
        val selected = follows(SportsListingSource.SCHEDULE)
            .copy(reminders = listOf(SportsReminder(legacy.id, legacy.title, legacy.startMs)))
        for (f in listOf(selected, selected.withListingSource(moto, SportsListingSource.BOTH))) {
            val input = SportsEventListings.forMatching(raw, context, f, SportsSettings(), now, raw)
            // A fresh matcher cannot succeed by retaining the preceding round's channels.
            val result = SportsMatcher(GuideSearch.InMemory(emptyMap())).match(SportsMatcher.Input(
                input, listOf(channel), emptyMap(), 0L, SportsSettings(), now, readGuide = false))
            assertEquals(channel.key, result.matches.getValue(legacy.id).single().channel.key)
        }
        assertSame(raw, SportsEventListings.forMatching(raw, context, follows(SportsListingSource.BOTH), SportsSettings(), now, raw))
    }

    @Test fun anOpenedLegacyCardKeepsChannelsAfterResetUntilItClosesOrLeavesTheMatchWindow() {
        val channel = LiveChannel("station", "fixture", "Fixture", "Station One", null, null, null,
            "fixture::Events", "Events", 1, "http://example.invalid/live.ts")
        val guide = event("guide", EventSource.GUIDE).copy(guideHits = listOf(channel.key to
            EpgProgram(now - hour, now + 2 * hour, "Guide race")))
        val legacy = event("legacy-open", EventSource.FIXTURES)
        val raw = listOf(guide)
        val context = listOf(guide, legacy)
        val reset = follows(SportsListingSource.BOTH)
        val input = SportsEventListings.forMatching(raw, context, reset, SportsSettings(), now, raw, setOf(legacy.id))
        val matched = SportsMatcher(GuideSearch.InMemory(emptyMap())).match(SportsMatcher.Input(
            input, listOf(channel), emptyMap(), 0L, SportsSettings(), now, readGuide = false))
        assertEquals(channel.key, matched.matches.getValue(legacy.id).single().channel.key)
        assertSame(raw, SportsEventListings.forMatching(raw, context, reset, SportsSettings(), now, raw, emptySet()))
        val distant = legacy.copy(startMs = now + SportsMatcher.AHEAD_MS + hour)
        assertSame(raw, SportsEventListings.forMatching(raw, listOf(guide, distant), reset,
            SportsSettings(leagueChannels = mapOf(moto to channel.key)), now, raw, setOf(distant.id)))
    }
}

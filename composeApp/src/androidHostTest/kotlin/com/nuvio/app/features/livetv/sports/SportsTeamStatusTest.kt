package com.nuvio.app.features.livetv.sports

import org.junit.Assert.*
import org.junit.Test

class SportsTeamStatusTest {
    private fun game(
        id: String = "football/college-football", sport: Sport = Sport.AMERICAN_FOOTBALL,
        phase: String = "STATUS_IN_PROGRESS", state: String = "in", detail: String = "8:10 - 2nd",
        description: String = "In Progress", possession: Int = 11,
        homeInnings: String = "[]", awayInnings: String = "[]"
    ): SportsEvent {
        val league = requireNotNull(SportsCatalog.league("football/nfl")).copy(id = id, sport = sport)
        val raw = """{"events":[{"id":"101","date":"2026-11-26T12:00:00Z","competitions":[{
            "status":{"type":{"name":"$phase","state":"$state","description":"$description","shortDetail":"$detail"}},
            "situation":{"possession":$possession},"competitors":[
              {"homeAway":"home","team":{"id":"11","name":"Eagles"},"curatedRank":{"current":7},"linescores":$homeInnings,
               "records":[{"type":"home","summary":"3-0"},{"name":"overall","type":"total","summary":"8-2"}]},
              {"homeAway":"away","team":{"id":"12","name":"Tigers"},"curatedRank":{"current":99},"linescores":$awayInnings,
               "records":[{"type":"away","summary":"1-2"}]}]}]}]}"""
        return SportsFeeds.parseScoreboard(league, raw).single()
    }

    @Test fun collegeRankOverallRecordAndLivePossessionAreIndependent() {
        val ev = game()
        val h = requireNotNull(ev.home)
        assertEquals("#7 Eagles", h.rankedName)
        assertEquals("8-2", h.record)
        assertEquals("Tigers", ev.away!!.rankedName)
        assertNull(ev.away!!.record)
        assertTrue(h.onOffense)
        assertFalse(ev.away!!.onOffense)
        assertNull(ev.situation)
        assertNull(game(id = "football/nfl").home!!.collegeRank)
        assertFalse(game(possession = 999).home!!.onOffense)
        assertFalse(game(phase = "STATUS_HALFTIME").home!!.onOffense)
        assertFalse(game(state = "post").home!!.onOffense)
        assertFalse(game(id = "basketball/nba", sport = Sport.BASKETBALL).home!!.onOffense)
    }

    @Test fun baseballMarksBattingDuringExplicitHalfInningsOnly() {
        fun batter(detail: String): String? =
            game(id = "baseball/mlb", sport = Sport.BASEBALL, detail = detail)
                .ordered(Sport.BASEBALL).singleOrNull { it.onOffense }?.team?.id
        assertEquals("12", batter("Top 2nd"))
        assertEquals("11", batter("Bot 6th"))
        assertEquals("11", batter("Bottom of the 8th"))
        for (detail in listOf("Mid 6th", "End 7th", "Rain Delay", "6th", "Final")) assertNull(batter(detail))
    }

    @Test fun cricketRequiresOneCurrentBattingTeamDuringPlay() {
        val current = """[{"isCurrent":1,"isBatting":true}]"""
        val previous = """[{"isCurrent":0,"isBatting":true}]"""
        fun batting(home: String, away: String, status: String = "Live") =
            game(id = "cricket/test", sport = Sport.CRICKET, description = status, homeInnings = home, awayInnings = away)
                .ordered(Sport.CRICKET).singleOrNull { it.onOffense }?.team?.id
        assertEquals("12", batting(previous, current))
        assertNull(batting(current, current))
        assertNull(batting(current, previous, "Stumps"))
        assertNull(batting(previous, previous))
    }

    @Test fun cachedAndDelayedPossessionTracksTheDisplayedScoreboard() {
        val first = game()
        val swap = game(possession = 12)
        val packed = SportsEventCodec.encode(listOf(first))
        val unpacked = SportsEventCodec.decode(packed)!!.single()
        assertEquals(first.home, unpacked.home)
        val legacy = packed.replace(",\"rk\":7", "").replace(",\"of\":true", "")
        assertFalse(SportsEventCodec.decode(legacy)!!.single().home!!.onOffense)
        assertNull(SportsEventCodec.decode(legacy)!!.single().home!!.collegeRank)
        val delay = SportsScoreDelay()
        val now = first.startMs
        delay.apply(listOf(first), 15_000L, now)
        assertTrue(delay.apply(listOf(swap), 15_000L, now + 2_000).single().home!!.onOffense)
        assertTrue(delay.apply(listOf(swap), 15_000L, now + 18_000).single().away!!.onOffense)
    }
}

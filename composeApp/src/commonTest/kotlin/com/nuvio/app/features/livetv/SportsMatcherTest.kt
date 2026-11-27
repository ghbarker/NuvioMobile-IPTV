package com.nuvio.app.features.livetv

import com.nuvio.app.features.livetv.model.EpgProgram
import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.livetv.sports.ChannelMatch
import com.nuvio.app.features.livetv.sports.restoreUnsearchedMatches
import com.nuvio.app.features.livetv.sports.EventSource
import com.nuvio.app.features.livetv.sports.GameState
import com.nuvio.app.features.livetv.sports.GuideSearch
import com.nuvio.app.features.livetv.sports.Side
import com.nuvio.app.features.livetv.sports.SportsEvent
import com.nuvio.app.features.livetv.sports.SportsMatcher
import com.nuvio.app.features.livetv.sports.SportsSettings
import com.nuvio.app.features.livetv.sports.TeamInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SportsMatcherTest {
    private val hour = 3_600_000L
    private val now = 1_800_000_000_000L
    private val kickoff = now + hour

    private fun channel(key: String, name: String, group: String = "Sports") =
        LiveChannel(key, "pl", "Playlist", name, null, null, null, "pl::$group", group, 1, "http://h/$key.ts")

    private fun team(id: String, name: String, location: String, abbr: String) =
        TeamInfo("football/nfl", id, name, "$location $name", abbr, location, null, null, null)

    private val lions = team("8", "Lions", "Detroit", "DET")
    private val bears = team("3", "Bears", "Chicago", "CHI")
    private val packers = team("9", "Packers", "Green Bay", "GB")

    private fun game(id: String, away: TeamInfo, home: TeamInfo, broadcasts: List<String> = emptyList()) = SportsEvent(
        id = "espn:football/nfl:$id", leagueId = "football/nfl", startMs = kickoff, hasTime = true, state = GameState.PRE,
        title = "${away.name} at ${home.name}", subtitle = null, status = null,
        away = Side(away, null, null, false, false), home = Side(home, null, null, false, true),
        broadcasts = broadcasts, source = EventSource.ESPN, espnId = id,
    )

    private val espn = channel("espn", "US: ESPN HD")
    private val fox = channel("fox", "US: FOX HD")
    private val nflEvent = channel("nfl03", "NFL 03: Bears @ Lions")
    private val movies = channel("movies", "Movies 24/7")

    private val guide = GuideSearch.InMemory(
        mapOf(
            // ESPN: the game itself, named both ways.
            "espn" to listOf(EpgProgram(kickoff - 15 * 60_000L, kickoff + 3 * hour, "NFL Football: Chicago Bears at Detroit Lions")),
            // FOX: a different game at the same time.
            "fox" to listOf(EpgProgram(kickoff, kickoff + 3 * hour, "NFL Football: Packers at Vikings")),
            "movies" to listOf(EpgProgram(kickoff - hour, kickoff + 2 * hour, "Chicago Bears at Detroit Lions")),
        )
    )

    private fun match(vararg events: SportsEvent, settings: SportsSettings = SportsSettings(), channels: List<LiveChannel> = listOf(espn, fox, nflEvent, movies)) =
        SportsMatcher(guide).match(SportsMatcher.Input(events.toList(), channels, emptyMap(), 0L, settings, now)).matches

    @Test
    fun theGuideNamingBothTeamsIsTheBestMatch() {
        val g = game("1", bears, lions)
        val list = match(g)[g.id]
        assertTrue(list != null && list.isNotEmpty())
        val best = list!!.first()
        assertEquals("espn", best.channel.key)
        assertTrue(best.sure)
        assertTrue(best.how.startsWith("Guide:"))
        // The other game's channel isn't listed for this one.
        assertFalse(list.any { it.channel.key == "fox" })
    }

    @Test
    fun aChannelNamedForTheGameMatchesWithoutTheGuide() {
        val g = game("1", bears, lions)
        val list = match(g)[g.id].orEmpty()
        val byName = list.firstOrNull { it.channel.key == "nfl03" }
        assertTrue(byName != null && byName.sure)
        assertEquals("Channel name", byName!!.how)
    }

    @Test
    fun twentyFourSevenChannelsNeverCount() {
        val g = game("1", bears, lions)
        assertNull(match(g)[g.id].orEmpty().firstOrNull { it.channel.key == "movies" })
    }

    @Test
    fun yourOwnPickComesFirstAndBlockedChannelsAreLeftOut() {
        val g = game("1", bears, lions)
        val picked = SportsSettings(eventChannels = mapOf(g.id to "fox|$kickoff"), blocked = setOf("${g.id}|espn"))
        val list = match(g, settings = picked)[g.id].orEmpty()
        assertEquals("fox", list.first().channel.key)
        assertEquals(200, list.first().score)
        assertFalse(list.any { it.channel.key == "espn" })
    }

    @Test
    fun aGameNoListingNamesInFullIsNeverSure() {
        val g = game("2", packers, lions)
        // Packers at Lions: ESPN's listing names the Lions and FOX's the Packers, each with the
        // other game's opponent: "maybe" at best, never a sure match.
        val list = match(g)[g.id].orEmpty()
        assertFalse(list.any { it.sure })
        assertFalse(list.any { it.channel.key == "nfl03" })
    }

    @Test
    fun requestedFightFindsItsPrelimsAndMainCardAcrossChannels() {
        val fight = game("card", bears, lions).copy(
            leagueId = "mma/ufc", title = "UFC 321: Silva vs Jones", home = null, away = null, broadcasts = listOf("ESPN"),
        )
        fun programme(title: String, hours: Long) = EpgProgram(kickoff + hours * hour, kickoff + (hours + 1) * hour, title)
        val listings = GuideSearch.InMemory(mapOf(
            "early" to listOf(programme("UFC 321 Early Prelims", -4)),
            "main" to listOf(programme("UFC 321 Main Card", 2)),
            "blocked" to listOf(programme("UFC 321 Prelims", -2)),
            "wrong" to listOf(programme("UFC 320: Silva vs Jones", 0)),
            "generic" to listOf(programme("UFC Fight Night Prelims", -3)),
            "replay" to listOf(programme("UFC 321 Prelims Replay", -2)),
            "studio" to listOf(programme("Countdown to UFC 321", -2)),
            "network-studio" to listOf(programme("UFC 321 Press Conference", 0)),
        ))
        val channels = listOf("early", "main", "blocked", "wrong", "generic", "replay", "studio", "pinned").map { channel(it, "Station $it") } +
            listOf(channel("event", "US: UFC 321 Main Card HD"), channel("wrong-event", "ESPN HD: UFC 320 Silva vs Jones"),
                channel("replay-event", "ESPN HD: UFC 321 Replay"), channel("network-studio", "ESPN HD"))
        val picks = SportsSettings(eventChannels = mapOf(fight.id to "pinned|${fight.startMs}"), blocked = setOf("${fight.id}|blocked"))
        val result = SportsMatcher(listings).match(SportsMatcher.Input(listOf(fight, game("unrelated", bears, lions)),
            channels, emptyMap(), 0, picks, now, onlyEventId = fight.id)).matches
        assertEquals(setOf(fight.id), result.keys)
        val rows = result.getValue(fight.id)
        assertEquals("pinned", rows.first().channel.key)
        assertEquals(setOf("pinned", "early", "main", "event"), rows.map { it.channel.key }.toSet())
    }

    @Test
    fun selectedGuideEventReadsCurrentListingsAndCanSearchOutsideAutomaticHorizon() {
        val start = now + 4 * 24 * hour
        val programme = EpgProgram(start, start + hour, "British Darts Final")
        val event = game("darts", bears, lions).copy(leagueId = "guide:other", startMs = start, title = programme.title,
            home = null, away = null, source = EventSource.GUIDE, guideHits = listOf("espn" to programme))
        val matcher = SportsMatcher(GuideSearch.InMemory(mapOf("fox" to listOf(programme))))
        val result = matcher.match(SportsMatcher.Input(listOf(event), listOf(espn, fox), emptyMap(), 0,
            SportsSettings(), now, onlyEventId = event.id)).matches
        assertEquals(listOf("fox"), result.getValue(event.id).map { it.channel.key })
    }

    @Test
    fun selectingATournamentDoesNotClaimGenericGolfFromAConcurrentTournament() {
        val event = game("golf", bears, lions).copy(leagueId = "golf/pga", title = "Bermuda Championship",
            hasTime = false, startMs = now - hour, endMs = now + hour, home = null, away = null)
        val other = event.copy(id = "other-tour", title = "Madrid Open", leagueId = "golf/eur")
        val matcher = SportsMatcher(GuideSearch.InMemory(emptyMap()))
        val rows = matcher.match(SportsMatcher.Input(listOf(event, other), listOf(channel("g", "Golf HD"),
            channel("p", "PGA Tour HD")), emptyMap(), 0, SportsSettings(), now, onlyEventId = event.id)).matches
        assertEquals(setOf(event.id), rows.keys)
        assertEquals(listOf("p"), rows.getValue(event.id).map { it.channel.key })
    }

    @Test
    fun manualLookupReadsOnlyTheSelectedGuideWindowAndPropagatesReadFailures() {
        val target = game("target", bears, lions)
        val later = game("later", packers, bears).copy(startMs = kickoff + 25 * hour)
        val db = object : GuideSearch by guide {
            override fun scanListings(fromMs: Long, toMs: Long, minStopMs: Long, requireComplete: Boolean,
                onRow: (String, Long, Long, String, String?, String?) -> Unit) {
                assertTrue(requireComplete)
                assertTrue(fromMs < kickoff && toMs > kickoff && toMs < later.startMs)
                throw IllegalStateException("Guide was replaced")
            }
        }
        assertFailsWith<IllegalStateException> {
            SportsMatcher(db).match(SportsMatcher.Input(listOf(target, later), listOf(espn, fox), emptyMap(), 0,
                SportsSettings(), now, onlyEventId = target.id))
        }
    }

    @Test
    fun diskRestoreKeepsOtherGamesAndHonorsCompletedEmptyAndNonemptySearches() {
        val saved = listOf(ChannelMatch(espn, 100, "Guide", null, null, false))
        val fresh = listOf(ChannelMatch(fox, 100, "Guide", null, null, false))
        val previousRun = mapOf("untouched" to saved, "new" to saved, "now-empty" to saved)
        assertEquals(mapOf("untouched" to saved, "new" to fresh),
            restoreUnsearchedMatches(previousRun, mapOf("new" to fresh), setOf("new", "now-empty")))
    }

    @Test
    fun networkChannelsHelpersReadNames() {
        assertEquals("espn plus", SportsMatcher.networkKey("ESPN+"))
        assertEquals("4K", SportsMatcher.nameQuality("US: ESPN 4K"))
        assertEquals("FHD", SportsMatcher.nameQuality("ESPN FHD"))
        assertNull(SportsMatcher.nameQuality("ESPN"))
        assertTrue(SportsMatcher.isBanner("##### USA SPORTS #####"))
        assertFalse(SportsMatcher.isBanner("ESPN"))
        assertEquals("US", SportsMatcher.channelRegionCode(channel("x", "US: ESPN")))
        assertEquals("lang:es", SportsMatcher.channelRegionCode(channel("x", "ES: ESPN Deportes")))
        assertEquals(SportsMatcher.copyKey(channel("a", "ESPN FHD")), SportsMatcher.copyKey(channel("b", "ESPN HD")))
    }

    @Test
    fun keepsHighQualityCopiesBeforeTheNationalNetworkCandidateCap() {
        val event = game("abc", bears, lions, broadcasts = listOf("ESPN"))
        val channels = (1..10).map { channel("low$it", "ESPN (720p25)") } +
            channel("fast", "ESPN (1080p50)") + channel("slow", "ESPN (1080p25)")
        val result = match(event, channels = channels).getValue(event.id)
        assertEquals(8, result.size)
        assertEquals(listOf("fast", "slow"), result.take(2).map { it.channel.key })
    }
}

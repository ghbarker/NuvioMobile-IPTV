package com.nuvio.app.features.livetv.sports

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class SportsRowsTest {
    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L
    private val day = 24 * hour

    private fun followed(league: String = "football/nfl", id: String = "1", name: String = id) =
        FollowedTeam(league, id, name, id, null, null)

    private fun event(id: String, league: String = "football/nfl", at: Long = now + hour,
                      state: GameState = GameState.PRE, away: String? = "1", home: String? = "2",
                      word: String? = null, timed: Boolean = true): SportsEvent {
        fun side(id: String, home: Boolean) = Side(
            TeamInfo(league, id, id, id, id, null, null, null, null), null, null, false, home,
        )
        return SportsEvent(id, league, at, timed, state, id, null, null,
            away?.let { side(it, false) }, home?.let { side(it, true) }, word = word)
    }

    private fun mine(ev: SportsEvent, f: SportsFollows): Boolean =
        ev.leagueId in f.leagues || ev.word != null ||
            (ev.leagueId in f.pins && SportsCatalog.league(ev.leagueId)?.kind == LeagueKind.EVENT) ||
            ev.sides.any { f.followsTeam(ev.leagueId, it.team.id) } || f.reminders.any { it.eventId == ev.id }

    private fun events(expected: List<SportsEvent>, actual: List<SportsEvent>) {
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { assertSame("Event order/identity at $it", expected[it], actual[it]) }
    }

    private fun equivalent(known: List<SportsEvent>, follows: SportsFollows, sport: Sport? = null): MyTeamsPage {
        val expected = ReferenceSportsRows.build(known, follows, now, sport) { mine(it, follows) }
        val actual = SportsRows.build(known, follows, now, sport) { mine(it, follows) }
        events(expected.heroes, actual.heroes)
        assertEquals(expected.rows.size, actual.rows.size)
        expected.rows.zip(actual.rows).forEach { (a, b) ->
            assertEquals(a.key, b.key)
            assertEquals(a.title, b.title)
            assertEquals(a.kind, b.kind)
            assertSame(a.team, b.team)
            assertSame(a.league, b.league)
            assertEquals(a.startIndex, b.startIndex)
            events(a.events, b.events)
        }
        return actual
    }

    @Test fun `empty rows and mobile pinned team league membership remain unchanged`() {
        val empty = equivalent(emptyList(), SportsFollows(teams = listOf(followed()),
            leagues = setOf("football/nfl"), words = listOf("Name")))
        assertEquals(listOf("t:football/nfl:1"), empty.rows.map { it.key })
        assertEquals(0, empty.rows.single().startIndex)
        val page = equivalent(listOf(event("pinned", state = GameState.LIVE)),
            SportsFollows(pins = listOf("football/nfl")))
        assertTrue(page.heroes.isEmpty())
        assertEquals(listOf(SportsRow.Kind.LEAGUE), page.rows.map { it.kind })
    }

    @Test fun `team families use event leagues without crossing unrelated sports`() {
        val original = event("nfl")
        val side = original.away!!
        val misleading = original.copy(away = side.copy(team = side.team.copy(leagueId = "soccer/eng.1")))
        val known = listOf(event("epl", "soccer/eng.1"), event("cup", "soccer/uefa.champions"),
            misleading, event("college", "football/college-football"))
        val rows = equivalent(known, SportsFollows(teams = listOf(followed("soccer/eng.1"), followed())))
            .rows.filter { it.kind == SportsRow.Kind.TEAM }
        assertEquals(listOf("epl", "cup"), rows[0].events.map { it.id })
        assertEquals(listOf("nfl"), rows[1].events.map { it.id })
    }

    @Test fun `duplicate input sides followed teams and saved ordering retain their meaning`() {
        val both = event("both", away = "1", home = "1")
        val known = listOf(event("none", away = null, home = null), event("away", home = null),
            event("home", away = null, home = "1"), both, both, event("name", word = "Name"))
        val follows = SportsFollows(teams = listOf(followed(name = "First"), followed(name = "Again")),
            pins = listOf("football/nfl", "football/nfl"), words = listOf("Name", "name", "Name"),
            order = listOf("w:Name", "missing", "l:football/nfl", "w:Name", "t:football/nfl:1"))
        val page = equivalent(known, follows)
        assertEquals(listOf("First", "Again"), page.rows.filter { it.kind == SportsRow.Kind.TEAM }.map { it.title })
        assertEquals(listOf("away", "home", "both", "both", "name"),
            page.rows.first { it.kind == SportsRow.Kind.TEAM }.events.map { it.id })
        assertEquals(2, page.rows.count { it.key == "w:Name" })
    }

    @Test fun `case insensitive Unicode empty and null words preserve every matching row`() {
        val words = listOf("I", "\u0130", "\u0131", "SS", "\u00df", "\u03a3", "\u03c2", "\u03c3", "", "Name", "name", "Name")
        val known = words.distinct().mapIndexed { i, word -> event("word-$i", word = word, away = null, home = null) } +
            event("null", away = null, home = null)
        val page = equivalent(known, SportsFollows(words = words + "absent"))
        assertEquals(words.size, page.rows.count { it.kind == SportsRow.Kind.WORD })
    }

    @Test fun `stable ties hero caps timeline caps and start indexes match the old builder`() {
        val live = List(40) { event("live-$it", at = now, state = GameState.LIVE, timed = it % 3 != 0) }
        val page = equivalent(live, SportsFollows(teams = listOf(followed())))
        events(live.take(24), page.rows.first().events)
        events(live.take(24).filter { it.hasTime }.take(8), page.heroes)
        val past = List(12) { event("done-$it", at = now - (12 - it) * hour, state = GameState.POST, word = "Name") }
        val future = List(4) { event("next-$it", at = now + (it + 1) * hour, word = "Name") }
        val f = SportsFollows(teams = listOf(followed()), leagues = setOf("football/nfl"), words = listOf("Name"))
        val rows = equivalent(past + future, f).rows.filter { it.kind != SportsRow.Kind.UPCOMING }
        assertEquals(listOf(5, 12, 5), rows.map { it.startIndex })
        assertEquals(listOf(9, 16, 9), rows.map { it.events.size })
        assertEquals(listOf(4, 11, 4), equivalent(past, f).rows.map { it.startIndex })
    }

    @Test fun `league rows retain the current weekly and event season history`() {
        val weekly = List(20) { event("week-$it", at = now - (20 - it) * hour, state = GameState.POST) }
        val next = event("next")
        val leagueFollows = SportsFollows(leagues = setOf("football/nfl"))
        val leagueRow = SportsRows.build(weekly + next, leagueFollows, now) { mine(it, leagueFollows) }
            .rows.single { it.kind == SportsRow.Kind.LEAGUE }
        events(weekly.takeLast(16) + next, leagueRow.events)
        assertEquals(16, leagueRow.startIndex)

        val moto = "guide/motogp"
        val season = List(90) { event("race-$it", moto, now - (90 - it) * day,
            GameState.POST, away = null, home = null) }
        val upcoming = event("next-race", moto, now + day, away = null, home = null)
        val raceFollows = SportsFollows(pins = listOf(moto))
        val raceRow = SportsRows.build(season + upcoming, raceFollows, now) { mine(it, raceFollows) }
            .rows.single { it.kind == SportsRow.Kind.LEAGUE }
        events(season.takeLast(80) + upcoming, raceRow.events)
        assertEquals(80, raceRow.startIndex)

        val boundary = event("season-boundary", moto, now - 12 * 31L * day, GameState.POST)
        val tooOld = boundary.copy(id = "outside-season", startMs = boundary.startMs - 1)
        val boundaryRow = SportsRows.build(listOf(tooOld, boundary), raceFollows, now) { mine(it, raceFollows) }
            .rows.single { it.kind == SportsRow.Kind.LEAGUE }
        events(listOf(boundary), boundaryRow.events)
    }

    @Test fun `future postponed copies cannot replace an earlier upcoming copy as a recent result`() {
        val postponed = event("same-id", at = now + 2 * hour, state = GameState.OFF)
        val upcoming = event("same-id", at = now + hour)
        val recent = event("last-round", at = now - 3 * day, state = GameState.POST)
        val follows = SportsFollows(leagues = setOf("football/nfl"))
        val row = SportsRows.build(listOf(postponed, upcoming, recent), follows, now) { mine(it, follows) }
            .rows.single { it.kind == SportsRow.Kind.LEAGUE }
        events(listOf(recent, upcoming), row.events)
        assertEquals(1, row.startIndex)
    }

    @Test fun `time boundaries postponed duplicate ids and reminders keep selection rules`() {
        val offsets = listOf(-3 * hour - 1, -3 * hour, -hour - 1, -hour, 8 * day, 8 * day + 1)
        val known = offsets.mapIndexed { i, delta -> event("pre-$i", at = now + delta) }
        val page = equivalent(known, SportsFollows(teams = listOf(followed())))
        assertEquals(listOf("pre-1", "pre-2", "pre-3", "pre-4"), page.rows.first().events.map { it.id })
        assertEquals(listOf("pre-3", "pre-4"), page.heroes.map { it.id })
        equivalent(listOf(event("dup", at = now - hour, state = GameState.POST), event("dup"),
            event("old", at = now - 2 * day - 1, state = GameState.POST), event("off", at = now, state = GameState.OFF)),
            SportsFollows(teams = listOf(followed()), leagues = setOf("football/nfl"),
                reminders = listOf(SportsReminder("dup", "Duplicate", now))))
    }

    @Test fun `sport filters preserve callback semantics and exclude word rows`() {
        val known = listOf(event("nfl"), event("club", "soccer/eng.1"), event("word", "soccer/eng.1", word = "Name"))
        val f = SportsFollows(teams = listOf(followed(), followed("soccer/eng.1")),
            pins = listOf("football/nfl", "soccer/eng.1"), words = listOf("Name"))
        for (sport in listOf(Sport.FOOTBALL, Sport.AMERICAN_FOOTBALL, Sport.BASKETBALL)) {
            val page = equivalent(known, f, sport)
            assertFalse(page.rows.any { it.kind == SportsRow.Kind.WORD })
            assertTrue(page.rows.flatMap { it.events }.all { SportsCatalog.league(it.leagueId)?.sport == sport })
        }
    }

    @Test fun `replacement snapshots do not change or retain the previous page buffers`() {
        val follows = SportsFollows(teams = listOf(followed()))
        val original = event("same", state = GameState.LIVE)
        val first = equivalent(listOf(original), follows)
        val replacement = original.copy(state = GameState.POST, title = "Replacement")
        val second = equivalent(listOf(replacement), follows)
        assertSame(original, first.rows.first().events.single())
        assertSame(replacement, second.rows.single().events.single())
        assertTrue(equivalent(emptyList(), SportsFollows()).rows.isEmpty())
    }

    @Test fun `grouping uses only one extra full source pass and preserves callback call order`() {
        class Counted(private val source: List<SportsEvent>) : AbstractList<SportsEvent>() {
            var reads = 0
            override val size get() = source.size
            override fun get(index: Int) = source[index].also { reads++ }
        }
        val known = List(1_000) { event("game-$it", away = "${it % 20}", home = "${(it + 1) % 20}") }
        val f = SportsFollows(teams = List(10) { followed(id = "$it") },
            leagues = linkedSetOf("football/nfl", "basketball/nba", "hockey/nhl"), words = listOf("A", "B"))
        val old = Counted(known)
        ReferenceSportsRows.build(old, f, now) { false }
        assertEquals(16_000, old.reads)
        val current = Counted(known)
        val seen = ArrayList<SportsEvent>()
        SportsRows.build(current, f, now) { seen += it; false }
        assertEquals(2_000, current.reads)
        events(known, seen)
    }

    @Test fun `randomized full page follows duplicates and sport filters match stock mobile`() {
        val random = Random(86)
        val leagues = listOf("football/nfl", "basketball/nba", "soccer/eng.1", "soccer/uefa.champions",
            "rugby/union", "rugby/league", "lacrosse/pll", "tsdb/one", "tsdb/two", "unknown")
        val words = listOf<String?>(null, null, "Name", "name", "\u0130", "i", "")
        repeat(300) { revision ->
            val known = List(random.nextInt(100)) { i ->
                event("id-${random.nextInt(30)}", leagues.random(random), now + random.nextInt(-72, 220) * hour,
                    GameState.entries.random(random), if (random.nextInt(5) == 0) null else "${random.nextInt(8)}",
                    if (random.nextInt(5) == 0) null else "${random.nextInt(8)}", words.random(random), random.nextBoolean())
                    .copy(title = "revision-$revision-$i")
            }.shuffled(random)
            val f = SportsFollows(teams = List(random.nextInt(12)) { followed(leagues.random(random), "${random.nextInt(8)}", "Team $it") },
                leagues = leagues.shuffled(random).take(random.nextInt(5)).toSet(),
                pins = leagues.shuffled(random).take(random.nextInt(5)), words = List(random.nextInt(6)) { words.random(random).orEmpty() },
                reminders = known.take(3).map { SportsReminder(it.id, it.title, it.startMs) })
            equivalent(known, f)
            equivalent(known, f.copy(order = SportsRows.rowKeys(f).shuffled(random)))
            equivalent(known, f, Sport.FOOTBALL)
        }
    }
}

/** Independent row scans with the Mobile history policy documented at base 4ad8083a. */
private object ReferenceSportsRows {
    private const val HOUR = 3_600_000L
    private const val DAY = 24 * HOUR
    /** Results kept at the start of a team's row. */
    private const val RESULTS = 5
    private const val LEAGUE_RESULTS = 16
    private const val SEASON_RESULTS = 80
    private const val LEAGUE_BACK_DAYS = 45
    private const val SEASON_MONTHS = 12

    /** The keys of My Sports' rows (not Live & Upcoming), in your order (see [SportsFollows.order]). */
    fun rowKeys(f: SportsFollows): List<String> {
        val all = LinkedHashSet<String>()
        f.teams.forEach { all += "t:${it.key}" }
        (f.pins + f.leagues).forEach { all += "l:$it" }
        f.words.forEach { all += "w:$it" }
        val placed = f.order.filter { it in all }
        return placed + all.filter { it !in placed }
    }

    /** Sports you follow something in (a team, a whole league, a league on the top bar). */
    fun followedSports(follows: SportsFollows, order: List<Sport>): List<Sport> {
        val ids = follows.teams.map { it.leagueId } + follows.leagues + follows.pins
        val sports = ids.mapNotNull { SportsCatalog.league(it)?.sport }.toSet()
        return sports.sortedBy { s -> order.indexOf(s).let { if (it < 0) 99 else it } }
    }

    /**
     * The rows; with [sport], only that sport's: its teams, its leagues and its games ("Football"
     * from the top bar shows Arsenal, England and the Premier League, not the NFL).
     */
    fun build(known: List<SportsEvent>, follows: SportsFollows, now: Long, sport: Sport? = null, isMine: (SportsEvent) -> Boolean): MyTeamsPage {
        if (sport != null) {
            fun of(id: String) = SportsCatalog.league(id)?.sport == sport
            val f = follows.copy(
                teams = follows.teams.filter { of(it.leagueId) },
                leagues = follows.leagues.filter { of(it) }.toSet(),
                pins = follows.pins.filter { of(it) },
                words = emptyList()
            )
            return build(known.filter { of(it.leagueId) }, f, now, null, isMine)
        }
        val rows = ArrayList<SportsRow>()

        // ---- Live & Upcoming: everything you follow, on now or in the next week
        val soon = known.filter { ev ->
            isMine(ev) && (ev.state == GameState.LIVE ||
                (ev.state == GameState.PRE && ev.startMs in (now - 3 * HOUR)..(now + 8 * DAY)))
        }.sortedWith(compareBy<SportsEvent> { if (it.state == GameState.LIVE) 0 else 1 }.thenBy { it.startMs })
            .take(24)
        if (soon.isNotEmpty()) rows += SportsRow("up", "Live & Upcoming", SportsRow.Kind.UPCOMING, events = soon)

        // ---- a row per team or country
        for (t in follows.teams) {
            val family = SportsCatalog.teamFamily(t.leagueId)
            val games = known.filter { ev ->
                ev.sides.any { s -> s.team.id == t.teamId && (ev.leagueId == t.leagueId || SportsCatalog.teamFamily(ev.leagueId) == family) }
            }
            rows += timeline("t:${t.key}", t.name, SportsRow.Kind.TEAM, games, now, team = t)
        }

        // ---- a row per top-bar league, then whole leagues and sports you follow
        val leagueIds = LinkedHashSet<String>().apply { addAll(follows.pins); addAll(follows.leagues) }
        for (id in leagueIds) {
            val l = SportsCatalog.league(id) ?: continue
            val all = known.filter { it.leagueId == id }
            // The current base keeps the last week, or the last round of a quiet league;
            // races and tournaments retain their season. Future OFF entries belong ahead.
            val done = all.filter { (it.state == GameState.POST || it.state == GameState.OFF) && it.startMs < now }
            val eventLeague = l.kind == LeagueKind.EVENT
            val recent = if (eventLeague) done.filter { it.startMs >= now - SEASON_MONTHS * 31L * DAY }
            else done.filter { it.startMs >= now - 7 * DAY }.ifEmpty {
                val newest = done.maxOfOrNull { it.startMs } ?: 0L
                done.filter { it.startMs >= now - LEAGUE_BACK_DAYS * DAY && it.startMs >= newest - 4 * DAY }
            }
            val ahead = all.filter { it.state == GameState.LIVE || ((it.state == GameState.PRE || it.state == GameState.OFF) && it.startMs >= now - 3 * HOUR) }
                .sortedBy { it.startMs }.take(60)
            val games = recent + ahead
            if (games.isEmpty()) continue
            rows += timeline("l:$id", l.name, SportsRow.Kind.LEAGUE, games.distinctBy { it.id }, now, league = l,
                results = if (eventLeague) SEASON_RESULTS else LEAGUE_RESULTS)
        }

        // ---- names you follow
        for (w in follows.words) {
            val games = known.filter { it.word.equals(w, ignoreCase = true) }
            if (games.isEmpty()) continue
            rows += timeline("w:$w", "“$w”", SportsRow.Kind.WORD, games, now)
        }

        // The big card: games and events at a set time (not a week-long tournament).
        val heroes = soon.filter { it.hasTime && (it.state == GameState.LIVE || (it.state == GameState.PRE && it.startMs >= now - HOUR)) }.take(8)
        // Your order (Arrange My Sports); Live & Upcoming always first.
        val rank = rowKeys(follows).withIndex().associate { (i, k) -> k to i }
        val sorted = rows.filter { it.kind == SportsRow.Kind.UPCOMING } +
            rows.filter { it.kind != SportsRow.Kind.UPCOMING }.sortedBy { rank[it.key] ?: Int.MAX_VALUE }
        return MyTeamsPage(heroes, sorted)
    }

    /** Games in date order: the last few results, then what's on and what's next. */
    private fun timeline(
        key: String, title: String, kind: SportsRow.Kind, games: List<SportsEvent>, now: Long,
        team: FollowedTeam? = null, league: League? = null, results: Int = RESULTS
    ): SportsRow {
        val sorted = games.sortedBy { it.startMs }
        val done = sorted.filter { it.state == GameState.POST || (it.state == GameState.OFF && it.startMs < now) }
        val doneIds = done.mapTo(HashSet()) { it.id }
        val ahead = sorted.filter { it.id !in doneIds }
        val past = done.takeLast(results)
        return SportsRow(key, title, kind, team, league, past + ahead, startIndex = if (ahead.isEmpty()) (past.size - 1).coerceAtLeast(0) else past.size)
    }
}

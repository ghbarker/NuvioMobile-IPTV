package com.nuvio.app.features.livetv.sports

/** Which event cards a league shows; channel matching still uses the TV guide in every mode. */
enum class SportsListingSource { BOTH, SCHEDULE, GUIDE }

/** Display-only projection. Raw guide events remain available to matching and saved reminders. */
internal object SportsEventListings {
    fun hasSourceChoice(league: League): Boolean = league.guideOnly &&
        (league.fixtureSlug != null || league.sport == Sport.WRESTLING)

    private fun hasChoices(follows: SportsFollows): Boolean =
        follows.listingSources.values.any { it != SportsListingSource.BOTH }

    private fun allows(event: SportsEvent, follows: SportsFollows): Boolean {
        val source = follows.listingSources[event.leagueId] ?: SportsListingSource.BOTH
        // A name followed separately is its own subscription, rather than another league card.
        if (event.source == EventSource.GUIDE && event.word != null &&
            follows.words.any { it.equals(event.word, ignoreCase = true) }) return true
        return when (source) {
            SportsListingSource.BOTH -> true
            SportsListingSource.SCHEDULE -> event.source != EventSource.GUIDE
            SportsListingSource.GUIDE -> event.source == EventSource.GUIDE
        }
    }

    fun visibleKnown(known: List<SportsEvent>, follows: SportsFollows, now: Long): List<SportsEvent> {
        if (!hasChoices(follows)) return known
        return known.filter { allows(it, follows) }
    }

    /**
     * The repository can replace a scheduled card with a guide airing. Its clean scheduled copy
     * is still in [known]; restore it only for leagues explicitly set to show their schedule.
     */
    fun forPage(
        current: List<SportsEvent>, known: List<SportsEvent>, follows: SportsFollows,
        from: Long, to: Long, now: Long
    ): List<SportsEvent> {
        if (!hasChoices(follows)) return current
        val out = current.filterTo(ArrayList()) { allows(it, follows) }
        val seen = out.mapTo(HashSet()) { it.id }
        for (event in known) {
            val source = follows.listingSource(event.leagueId)
            val selected = source != SportsListingSource.BOTH && allows(event, follows)
            if (!selected || event.startMs !in from until to || !seen.add(event.id)) continue
            out += event
        }
        return out
    }

    /** Only explicit listing context is added; the normal list remains unchanged. */
    fun withContext(known: List<SportsEvent>, context: List<SportsEvent>, now: Long): List<SportsEvent> {
        if (context.isEmpty()) return known
        val seen = known.mapTo(HashSet()) { it.id }
        val extra = context.filter { seen.add(it.id) }.map { at(it, now) }
        return if (extra.isEmpty()) known else known + extra
    }

    /** Display filtering must not remove the guide context that supplies a scheduled card's channels. */
    fun forMatching(
        current: List<SportsEvent>, known: List<SportsEvent>, follows: SportsFollows,
        settings: SportsSettings, now: Long, guideContext: List<SportsEvent>? = null,
        opened: Set<String> = emptySet(),
    ): List<SportsEvent> {
        val selected = follows.listingSources.filterValues { it != SportsListingSource.BOTH }
        val currentIds = if (follows.reminders.isEmpty()) emptySet() else current.mapTo(HashSet()) { it.id }
        val reminded = follows.reminders.mapNotNullTo(HashSet()) { it.eventId.takeIf { id -> id !in currentIds } }
        val retained = if (reminded.isEmpty() && opened.isEmpty()) emptySet() else known.asSequence().filter {
            (it.id in reminded && eligible(it, settings, now)) || (it.id in opened && inWindow(it, now))
        }.map { it.id }.toSet()
        if (selected.isEmpty() && retained.isEmpty()) return current
        val contextLeagues = if (retained.isEmpty()) selected.keys else
            selected.keys + known.filter { it.id in retained }.map { it.leagueId }
        val base = if (guideContext == null) current else current.filterNot {
            it.source == EventSource.GUIDE && it.leagueId in contextLeagues
        }
        val seen = base.mapTo(HashSet()) { it.id }
        val extra = known.filter { event ->
            ((selected[event.leagueId] == SportsListingSource.SCHEDULE && event.source != EventSource.GUIDE) ||
                (guideContext == null && selected[event.leagueId] == SportsListingSource.GUIDE && event.source == EventSource.GUIDE) ||
                (event.id in retained &&
                    (guideContext == null || event.source != EventSource.GUIDE))) &&
                eligible(event, settings, now) && seen.add(event.id)
        }.map { at(it, now) }
        val guides = guideContext.orEmpty().filter { it.leagueId in contextLeagues && seen.add(it.id) }
        return if (extra.isEmpty() && guides.isEmpty()) base else base + extra.sortedBy { it.id } + guides
    }

    private fun startMs(event: SportsEvent, now: Long): Long? = when {
        event.hasTime -> event.startMs
        event.endMs == null || event.endMs + 24 * HOUR < now -> null
        else -> maxOf(event.startMs, now)
    }

    fun inWindow(event: SportsEvent, now: Long): Boolean = startMs(event, now)?.let { start ->
        event.state != GameState.OFF && start in (now - 8 * HOUR)..(now + SportsMatcher.AHEAD_MS)
    } == true

    private fun eligible(event: SportsEvent, settings: SportsSettings, now: Long): Boolean {
        val start = startMs(event, now) ?: return false
        if (event.state == GameState.OFF || start < now - 8 * HOUR) return false
        return start <= now + SportsMatcher.AHEAD_MS || event.id in settings.eventChannels ||
            event.leagueId in settings.leagueChannels || event.sides.any {
                teamChannelKey(event.leagueId, it.team.id) in settings.teamChannels
            }
    }

    private const val HOUR = 3_600_000L

    /** Named fixture files store a clock-derived state, unlike feed-scored team games. */
    private fun at(event: SportsEvent, now: Long): SportsEvent {
        if (event.source == EventSource.ESPN || !event.hasTime || event.state == GameState.OFF ||
            event.home != null || event.away != null) return event
        val end = event.endMs ?: return event
        val state = when {
            now >= end -> GameState.POST
            now >= event.startMs -> GameState.LIVE
            else -> GameState.PRE
        }
        return if (event.state == state) event else event.copy(state = state)
    }
}

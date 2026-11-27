package com.nuvio.app.features.livetv.sports

internal class SportsGuideTitleExclusions(titles: List<String>) {
    private val phrases = titles.map(::key).filter { it.isNotEmpty() }.map { " $it " }
    val isEmpty: Boolean get() = phrases.isEmpty()

    fun matches(title: String): Boolean {
        if (isEmpty) return false
        val words = " " + key(title) + " "
        return phrases.any { it in words }
    }

    fun hides(match: ChannelMatch): Boolean {
        if (isEmpty) return false
        match.guideEvidenceTitle?.let { return matches(it) }
        // Older match caches omit the title that established a network match. Keep unverified
        // network hints, but wait for fresh guide evidence before showing those stronger results.
        return match.how.endsWith(" listed by ESPN") && match.score > 45
    }

    fun visibleMatches(byEvent: Map<String, List<ChannelMatch>>): Map<String, List<ChannelMatch>> {
        if (isEmpty) return byEvent
        return buildMap {
            for ((id, list) in byEvent) {
                val visible = list.mapNotNull { match ->
                    when {
                        hides(match) -> null
                        // A separately matched channel or manual pick can survive without the excluded airing.
                        match.program?.let { matches(it.title) } == true -> match.copy(program = null, catchup = false)
                        else -> match
                    }
                }
                if (visible.isNotEmpty()) put(id, visible)
            }
        }
    }

    companion object {
        private val separators = Regex("""[^\p{L}\p{M}\p{N}]+""")

        fun key(title: String): String = SportsPlatform.composeUnicode(title.lowercase()).replace(separators, " ").trim()

        fun unique(titles: List<String>): List<String> {
            if (titles.isEmpty()) return emptyList()
            val keys = HashSet<String>()
            return titles.map { it.trim() }.filter {
                val normalized = key(it)
                normalized.isNotEmpty() && keys.add(normalized)
            }
        }
    }
}

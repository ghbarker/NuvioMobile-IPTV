package com.nuvio.app.features.livetv.record

/** What Recorded shows: a recording on its own, or a show's completed episodes together. */
internal sealed interface RecordedEntry {
    val key: String
}

internal data class SingleRecording(val rec: LiveTvRecording) : RecordedEntry {
    override val key: String get() = recordingEntryKey(rec)
}

internal data class RecordedShow(
    override val key: String,
    val title: String,
    /** Watching order: season/episode when supplied by the guide, then the time it aired. */
    val episodes: List<LiveTvRecording>,
) : RecordedEntry

/** Network and local recordings can have the same stored id. Keep their actions and rows distinct. */
internal fun recordingEntryKey(rec: LiveTvRecording): String =
    (if (rec.dir.startsWith(NetworkLibrary.DIR_PREFIX)) "n:" else "d:") + rec.id

internal val RECORDING_EPISODE_NUMBERS = Regex("""(?i)s\s*0*(\d{1,3})\s*[.\- ]?\s*e\s*0*(\d{1,4})""")

internal fun recordingAiredMs(rec: LiveTvRecording): Long =
    rec.programStartMs.takeIf { it > 0 } ?: rec.startedAtMs

private fun episodeNumbers(episode: String?): Pair<Int, Int>? =
    episode?.let { RECORDING_EPISODE_NUMBERS.find(it) }
        ?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }

private val EPISODE_ORDER = compareBy<LiveTvRecording>(
    { episodeNumbers(it.episode)?.first ?: Int.MAX_VALUE },
    { episodeNumbers(it.episode)?.second ?: Int.MAX_VALUE },
    { recordingAiredMs(it) },
)

private val TITLE_WHITESPACE = Regex("\\s+")

/**
 * Matches TV: two or more completed recordings with the same title form one folder at the
 * first member's position in the input. Recording and failed entries stay separate. Grouping
 * is presentation only: it neither moves files nor changes the series rule that made them.
 */
internal fun groupRecorded(recordings: List<LiveTvRecording>): List<RecordedEntry> {
    fun normalized(title: String) = title.trim().lowercase().replace(TITLE_WHITESPACE, " ")
    fun foldable(rec: LiveTvRecording) = rec.status == RecordingStatus.DONE && rec.title.isNotBlank()

    val byTitle = recordings.filter(::foldable).groupBy { normalized(it.title) }.filterValues { it.size >= 2 }
    val placed = HashSet<String>()
    val entries = ArrayList<RecordedEntry>()
    for (rec in recordings) {
        val title = normalized(rec.title)
        val group = byTitle[title]
        if (group != null && foldable(rec)) {
            if (placed.add(title)) entries += RecordedShow("show:$title", group.first().title, group.sortedWith(EPISODE_ORDER))
        } else entries += SingleRecording(rec)
    }
    return entries
}

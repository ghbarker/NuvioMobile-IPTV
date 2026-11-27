package com.nuvio.app.features.livetv.record

import com.nuvio.app.features.livetv.model.EpgProgram
import com.nuvio.app.features.livetv.model.SUB_TITLE_MARK
import com.nuvio.app.features.livetv.model.descriptionText
import com.nuvio.app.features.livetv.model.guideDescriptionText
import com.nuvio.app.features.livetv.model.subTitle

/** A guide refresh or unavailable preferences must never prevent a recording. */
internal inline fun <T> optionalGuideDetails(read: () -> T): T? = try {
    read()
} catch (cancelled: kotlinx.coroutines.CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}

internal fun EpgProgram.matchesGuideAiring(row: EpgProgram, guideStart: Long = startMs): Boolean {
    if (row.startMs != guideStart || row.stopMs - row.startMs != stopMs - startMs ||
        !RecordingRules.cleanTitle(title).equals(RecordingRules.cleanTitle(row.title), ignoreCase = true)
    ) return false
    val selectedEpisode = RecordingRules.episodeKey(episode)
    val guideEpisode = RecordingRules.episodeKey(row.episode)
    if (selectedEpisode != null && guideEpisode != null && selectedEpisode != guideEpisode) return false
    val selectedSubtitle = subTitle
    val guideSubtitle = row.subTitle
    if (selectedSubtitle != null && guideSubtitle != null && !selectedSubtitle.equals(guideSubtitle, ignoreCase = true)) return false
    return (stalkerProgramId == null || stalkerProgramId == row.stalkerProgramId) &&
        (stalkerSourceIdentity == null || stalkerSourceIdentity == row.stalkerSourceIdentity)
}

/** Keep the synopsis of this airing without changing its recording window or playback source. */
internal suspend fun RecordingSchedule.withGuideDetails(
    calendarOffsetMs: Long,
    lookup: suspend (String, Long) -> EpgProgram?,
): RecordingSchedule {
    if (guideStartMs == 0L || !guideDescriptionText(description).isNullOrBlank()) return this
    val selected = EpgProgram(startMs, stopMs, title, description, episode = episode,
        stalkerProgramId = stalkerProgramId, stalkerSourceIdentity = stalkerSourceIdentity)
    val selectedSubtitle = selected.subTitle
    // Old schedules did not distinguish guide times from the Calendar's adjusted display times.
    val starts = guideStartMs?.let { listOf(it) } ?: listOf(startMs, startMs - calendarOffsetMs).distinct()
    val match = starts.mapNotNull { guideStart ->
        lookup(channel.key, guideStart)?.takeIf { row ->
            selected.matchesGuideAiring(row, guideStart) &&
                (row.stalkerSourceIdentity == null || row.stalkerSourceIdentity == channel.stalker?.sourceIdentity)
        }
    }.singleOrNull() ?: return this
    val synopsis = match.descriptionText
    val savedText = when {
        synopsis.isNullOrBlank() -> description ?: match.description
        selectedSubtitle != null && match.subTitle == null -> "$SUB_TITLE_MARK$selectedSubtitle\n$synopsis"
        else -> match.description
    }
    return copy(episode = episode?.takeIf { it.isNotBlank() } ?: match.episode,
        description = savedText, guideStartMs = match.startMs)
}

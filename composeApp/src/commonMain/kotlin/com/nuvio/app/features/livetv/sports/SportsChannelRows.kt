package com.nuvio.app.features.livetv.sports

import com.nuvio.app.features.livetv.model.BackupOrder
import kotlin.math.roundToInt

internal enum class SportsQualitySource { PLAYBACK, PROVIDER, SAVED }

internal data class SportsChannelQuality(
    val height: Int,
    val frameRate: Float,
    val label: String,
    val source: SportsQualitySource,
    val reportedRateLabel: String? = null,
)

/** Device-local channel quality also contains plain FHD/HD/etc. from older versions. */
private val measuredFormat = Regex("""(\d{1,5})×(\d{1,5})(?: · ([\d.]+) fps)?""")

internal fun measuredSportsQuality(width: Int?, height: Int?, frameRate: Float?): SportsChannelQuality? {
    if (width == null || width !in 1..16384 || height == null || height !in 1..16384) return null
    val rate = frameRate?.takeIf { it in 1f..240f }?.let { (it * 1000).roundToInt() / 1000f } ?: 0f
    val label = buildString {
        append("${width}×${height}")
        if (rate != 0f) {
            append(" · ")
            append(if (rate == rate.toInt().toFloat()) rate.toInt().toString() else rate.toString())
            append(" fps")
        }
    }
    return SportsChannelQuality(height, rate, label, SportsQualitySource.PLAYBACK)
}

private fun readMeasuredQuality(value: String?): SportsChannelQuality? {
    if (value == null || '×' !in value) return null
    val result = measuredFormat.matchEntire(value) ?: return null
    return measuredSportsQuality(result.groupValues[1].toIntOrNull(), result.groupValues[2].toIntOrNull(),
        result.groupValues[3].toFloatOrNull())
}

/** Keep the matcher's existing coarse grades for selecting possible channels and the Watch button. */
internal fun matchQualityTier(cached: String): String {
    val height = readMeasuredQuality(cached)?.height ?: return cached
    return sportsQualityTier(height)
}

private fun sportsQualityTier(height: Int): String = when {
    height >= 1800 -> "4K"
    height >= 1000 -> "FHD"
    height >= 700 -> "HD"
    else -> "SD"
}

internal data class SportsChannelRowInfo(
    val match: ChannelMatch, val copies: Int, val hasCatchup: Boolean, val quality: SportsChannelQuality?
)

internal fun sportsChannelQuality(name: String, cached: String?, providerHint: String? = null): SportsChannelQuality? {
    val measured = readMeasuredQuality(cached)
    val reported = reportedSportsQuality(name)
    if (measured != null) {
        return if (measured.frameRate == 0f && reported != null && reported.height == measured.height && reported.frameRate > 0f) {
            measured.copy(frameRate = reported.frameRate, reportedRateLabel = reported.label)
        } else measured
    }
    val saved = cached?.let(::reportedSportsQuality)
    val label = when {
        saved != null && (reported == null || sportsQualityTier(reported.height) != saved.label) -> saved
        reported != null -> reported
        else -> providerHint?.let(::reportedSportsQuality)
    } ?: return null
    val source = if (label == saved && label != reported) SportsQualitySource.SAVED else SportsQualitySource.PROVIDER
    return SportsChannelQuality(label.height, label.frameRate, label.label, source)
}

internal fun sportsGameChannelRows(
    matches: List<ChannelMatch>,
    channelQuality: Map<String, String>,
    bestFirst: Boolean,
    sourceOrder: List<String> = emptyList(),
): List<SportsChannelRowInfo> {
    fun row(match: ChannelMatch, copies: Int, hasCatchup: Boolean) = SportsChannelRowInfo(
        match, copies, hasCatchup, sportsChannelQuality(match.channel.name, channelQuality[match.channel.key], match.quality)
    )
    fun rowsWithinSource(channels: List<ChannelMatch>): List<SportsChannelRowInfo> {
        if (!bestFirst) return SportsMatcher.groupCopies(channels).map { group ->
            row(group.first(), group.size, group.any { it.catchup })
        }
        return channels.distinctBy { it.channel.key }.map { row(it, 1, it.catchup) }.sortedWith(
            compareByDescending<SportsChannelRowInfo> { it.quality?.height ?: 0 }
                .thenByDescending { it.quality?.frameRate ?: 0f }
        )
    }
    if (sourceOrder.isEmpty()) return rowsWithinSource(matches)
    val ranks = sourceOrder.withIndex().associate { it.value to it.index }
    return matches.groupBy { BackupOrder.sourceKey(it.channel) }.entries
        .sortedBy { ranks[it.key] ?: Int.MAX_VALUE }
        .flatMap { rowsWithinSource(it.value) }
}

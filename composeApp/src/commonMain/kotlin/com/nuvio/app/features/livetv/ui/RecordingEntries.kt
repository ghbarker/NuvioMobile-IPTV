package com.nuvio.app.features.livetv.ui

import com.nuvio.app.features.livetv.record.LiveTvRecording
import com.nuvio.app.features.livetv.record.NetworkLibrary
import com.nuvio.app.features.livetv.record.OfflineRecordingKey
import com.nuvio.app.features.livetv.record.OfflineRecordingState
import com.nuvio.app.features.livetv.record.RecordingStatus

internal enum class OfflineAction { SAVE, RETRY, CANCEL, REMOVE }

internal data class RecordingEntry(val recording: LiveTvRecording, val source: OfflineRecordingKey? = null) {
    val rowKey: String get() = source?.let { "n:${it.folderLabel.length}:${it.folderLabel}:${it.id}" } ?: "d:${recording.id}"

    fun playable(downloads: Map<OfflineRecordingKey, LiveTvRecording>, networkAway: Boolean): LiveTvRecording? {
        val key = source ?: return recording
        return downloads[key] ?: recording.takeIf { !networkAway && NetworkLibrary.isNetwork(it) }
    }

    fun downloadAction(downloads: OfflineRecordingState, network: NetworkLibrary.State): OfflineAction? {
        val key = source ?: return null
        return when {
            key in downloads.ready -> OfflineAction.REMOVE
            key in downloads.progress -> OfflineAction.CANCEL
            recording.status != RecordingStatus.DONE || !onReachableShare(network) -> null
            key in downloads.failed -> OfflineAction.RETRY
            else -> OfflineAction.SAVE
        }
    }

    fun canDeleteFromNetwork(downloads: OfflineRecordingState, network: NetworkLibrary.State): Boolean =
        onReachableShare(network) && !recording.isRecording && source !in downloads.progress

    private fun onReachableShare(network: NetworkLibrary.State): Boolean =
        source != null && NetworkLibrary.isNetwork(recording) && source.folderLabel == network.folderLabel && network.reachable != false
}

/** A download and its NAS original share one card. Copies from another share remain available. */
internal fun recordingEntries(
    device: List<LiveTvRecording>,
    network: NetworkLibrary.State,
    downloads: Map<OfflineRecordingKey, LiveTvRecording>,
): List<RecordingEntry> = buildList {
    device.forEach { add(RecordingEntry(it)) }
    val listed = mutableSetOf<OfflineRecordingKey>()
    network.folderLabel?.let { folder ->
        network.recordings.forEach { recording ->
            val key = OfflineRecordingKey(folder, recording.id)
            listed += key
            add(RecordingEntry(recording, key))
        }
    }
    downloads.entries.filterNot { it.key in listed }.sortedByDescending { it.value.startedAtMs }
        .forEach { (key, recording) -> add(RecordingEntry(recording, key)) }
}

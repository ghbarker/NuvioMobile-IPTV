package com.nuvio.app.features.livetv.record

import kotlinx.coroutines.flow.StateFlow

/** Recording IDs are scoped to a shared folder. Two linked TVs can use the same ID. */
internal data class OfflineRecordingKey(val folderLabel: String, val id: String)

internal data class OfflineRecordingState(
    /** Ready recordings have a local directory and can be played without opening an SMB connection. */
    val ready: Map<OfflineRecordingKey, LiveTvRecording> = emptyMap(),
    val progress: Map<OfflineRecordingKey, Float> = emptyMap(),
    val failed: Set<OfflineRecordingKey> = emptySet(),
)

internal expect object OfflineRecordings {
    val available: Boolean
    val state: StateFlow<OfflineRecordingState>
    fun start()
    fun save(key: OfflineRecordingKey, recording: LiveTvRecording)
    fun cancel(key: OfflineRecordingKey)
    fun remove(key: OfflineRecordingKey)
}

package com.nuvio.app.features.livetv.record

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal actual object OfflineRecordings {
    actual val available = false
    actual val state: StateFlow<OfflineRecordingState> = MutableStateFlow(OfflineRecordingState())
    actual fun start() = Unit
    actual fun save(key: OfflineRecordingKey, recording: LiveTvRecording) = Unit
    actual fun cancel(key: OfflineRecordingKey) = Unit
    actual fun remove(key: OfflineRecordingKey) = Unit
}

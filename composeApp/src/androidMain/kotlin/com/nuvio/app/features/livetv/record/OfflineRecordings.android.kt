package com.nuvio.app.features.livetv.record

import com.nuvio.app.features.livetv.data.LiveTvPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import java.io.File

internal actual object OfflineRecordings {
    actual val available: Boolean get() = RecordingPlatform.context() != null
    private val store by lazy {
        OfflineRecordingStore(
            root = { File(requireNotNull(RecordingPlatform.context()).noBackupFilesDir, "offline-network-recordings") },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            configuredFolder = { NetworkFolderConfig.fromJson(LiveTvPreferences.currentNetworkFolderJson()) },
            copyMedia = OfflineRecordingCopy.Companion::copy,
        )
    }
    actual val state: StateFlow<OfflineRecordingState> get() = store.state
    actual fun start() { if (available) store.start() }
    actual fun save(key: OfflineRecordingKey, recording: LiveTvRecording) = store.save(key, recording)
    actual fun cancel(key: OfflineRecordingKey) = store.cancel(key)
    actual fun remove(key: OfflineRecordingKey) = store.remove(key)
}

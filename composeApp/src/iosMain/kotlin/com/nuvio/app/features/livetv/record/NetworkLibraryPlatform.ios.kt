package com.nuvio.app.features.livetv.record

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Network folders aren't built on iOS yet: the library stays hidden there. */
internal actual object NetworkLibraryPlatform {
    actual val available: Boolean = false
    actual suspend fun list(config: NetworkFolderConfig): Map<String, String> = throw UnsupportedOperationException("Not available on this device")
    actual fun playUrl(config: NetworkFolderConfig, id: String): String = ""
    actual suspend fun delete(config: NetworkFolderConfig, id: String): Unit = throw UnsupportedOperationException("Not available on this device")
    actual suspend fun rename(config: NetworkFolderConfig, rec: LiveTvRecording, title: String, isCurrent: () -> Boolean): String =
        throw UnsupportedOperationException("Not available on this device")
    actual fun configChanged(config: NetworkFolderConfig?) = Unit
    actual fun describe(e: Throwable, config: NetworkFolderConfig): String = "Network folders aren't available on this device"
    actual suspend fun test(config: NetworkFolderConfig, onVpnWait: () -> Unit): String? = "Network folders aren't available on this device"
    actual fun search(): Flow<NetworkSearchState> = flowOf(NetworkSearchState(searching = false, noNetwork = true))
    actual suspend fun letPastVpn(host: String, onVpnWait: () -> Unit) = Unit
    actual suspend fun shares(host: String, username: String, password: String): List<SmbShare> = throw UnsupportedOperationException("Not available on this device")
    actual suspend fun folders(config: NetworkFolderConfig): List<String> = throw UnsupportedOperationException("Not available on this device")
    actual suspend fun makeFolder(config: NetworkFolderConfig, name: String): Unit = throw UnsupportedOperationException("Not available on this device")
    actual suspend fun publish(config: NetworkFolderConfig, rec: LiveTvRecording, localPath: String, onProgress: (Float) -> Unit): String =
        throw UnsupportedOperationException("Not available on this device")
}

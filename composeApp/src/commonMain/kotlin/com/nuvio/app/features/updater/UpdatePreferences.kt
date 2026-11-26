package com.nuvio.app.features.updater

import com.nuvio.app.core.build.AppVersionConfig
import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.data.MobileSetupSettings
import com.nuvio.app.features.livetv.data.PrefValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class UpdatePreferences(versionName: String = AppVersionConfig.VERSION_NAME) {
    private val storedChannel = UpdateChannel.fromStoredValue(AppUpdaterPlatform.getUpdateChannel())
    private val _channel = MutableStateFlow(storedChannel ?: UpdateChannel.defaultForVersion(versionName))
    val channel = _channel.asStateFlow()

    init {
        if (storedChannel == null && AppUpdaterPlatform.isSupported) {
            AppUpdaterPlatform.setUpdateChannel(_channel.value.storedValue)
        }
    }

    fun setChannel(channel: UpdateChannel) {
        if (_channel.value == channel) return
        AppUpdaterPlatform.setUpdateChannel(channel.storedValue)
        AppUpdaterPlatform.setIgnoredTag(null)
        _channel.value = channel
        // Startup also stores a channel. Only a deliberate selection should initialize an
        // account whose setup otherwise contains nothing but first-run defaults.
        LiveTvPreferences.edit { it[MobileSetupSettings.CHOSE_UPDATE_CHANNEL] = PrefValue.Bool(true) }
    }

    fun reloadAfterRestore() {
        val restored = UpdateChannel.fromStoredValue(AppUpdaterPlatform.getUpdateChannel()) ?: return
        if (_channel.value != restored) {
            AppUpdaterPlatform.setIgnoredTag(null)
            _channel.value = restored
        }
    }

    companion object {
        val shared by lazy { UpdatePreferences() }
    }
}

package com.nuvio.app.features.livetv.data

import com.nuvio.app.features.settings.AppIconPlatform
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.nuvio.app.features.updater.AppUpdaterPlatform
import com.nuvio.app.features.updater.UpdatePreferences

/** Apply restored preferences to their owners so the running app reflects the new setup. */
internal fun afterMobileSetupPresentationRestore(fileName: String) {
    when (fileName) {
        "app_icon_settings" -> AppIconPlatform.applyRestoredIcon()
        "nuvio_theme_settings" -> ThemeSettingsRepository.onProfileChanged()
        "nuvio_updater" -> if (AppUpdaterPlatform.isSupported) {
            UpdatePreferences.shared.reloadAfterRestore()
        }
    }
}

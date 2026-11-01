package com.nuvio.app.features.livetv

import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.data.LiveTvRepository
import com.nuvio.app.features.livetv.ondemand.OnDemandRepository
import com.nuvio.app.features.livetv.sync.LiveTvDriveSync

/** Starts Live TV's background work once the preferences are installed: updates and Drive sync. */
internal object LiveTvStartup {
    private var started = false

    fun start() {
        if (started) return
        started = true
        // Nothing to do until a playlist exists; the first one added starts the repository.
        if (LiveTvPreferences.currentPlaylists().isNotEmpty() || LiveTvPreferences.currentEpgSources().isNotEmpty()) {
            LiveTvRepository.ensureLoaded(updateCheckDelayMs = 8_000L)
        }
        LiveTvDriveSync.start()
        OnDemandRepository.start()
    }
}

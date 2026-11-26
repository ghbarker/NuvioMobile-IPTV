package com.nuvio.app.features.livetv

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.features.livetv.data.EpgStore
import com.nuvio.app.features.livetv.data.LiveTvFiles
import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.data.LiveTvBackup
import com.nuvio.app.features.livetv.data.LiveTvBackupSecretsAndroid
import com.nuvio.app.features.livetv.data.PrefValue
import com.nuvio.app.features.livetv.data.UsenetBackupCompatibility
import com.nuvio.app.features.livetv.ondemand.OnDemandStore
import com.nuvio.app.features.livetv.record.RecordingPlatform
import com.nuvio.app.features.livetv.sync.LiveTvDriveHttp

internal typealias BackupPreferenceProtection = (String, PrefValue, String?) -> String

/**
 * Persists Live TV's preferences in SharedPreferences. Each stored value keeps its own type
 * (boolean, int, long, string, string set), the same five the TV app's DataStore holds, so the
 * map reads back exactly as it was written.
 */
internal object LiveTvPreferencesAndroid {
    private const val preferencesName = "livetv_prefs"
    private const val devicePreferencesName = "livetv_device_prefs"

    private var initialized = false
    private val protectBackupPreference: BackupPreferenceProtection = { key, value, stored ->
        LiveTvBackupSecretsAndroid.seal(key, value, stored)
    }

    /** Sets Live TV up once (the activity, a recording alarm or the recording service may each be first). */
    fun initialize(context: Context) {
        if (initialized) return
        LiveTvFiles.initialize(context)
        // The DNS setting, before any Live TV request.
        com.nuvio.app.features.livetv.data.ForkDns.init(context)
        EpgStore.initialize(context)
        OnDemandStore.initialize(context)
        RecordingPlatform.initialize(context)
        LiveTvDriveHttp.initialize(context)
        // What this phone can decode, for Best quality (AV1 / Dolby Vision-only links go last when it can't).
        com.nuvio.app.features.streams.quality.PlaybackDeviceSupportAndroid.init(context)
        VpnReportLines.start()
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val devicePreferences = context.getSharedPreferences(devicePreferencesName, Context.MODE_PRIVATE)
        LiveTvPreferences.installPersistence(
            load = { read(preferences) },
            save = { data -> write(preferences, data) },
            loadDevice = { read(devicePreferences) },
            saveDevice = { data -> write(devicePreferences, data) },
        )
        com.nuvio.app.features.livetv.data.MobileSetupPreferencesAndroid(context, LiveTvPreferences).install()
        initialized = true
    }

    internal fun read(preferences: SharedPreferences, protect: BackupPreferenceProtection = protectBackupPreference): Map<String, PrefValue> {
        val out = HashMap<String, PrefValue>()
        var migrateSecrets = false
        val original = preferences.all
        original.forEach { (key, value) ->
            val v = if (UsenetBackupCompatibility.isKey(key)) {
                require(value is String) { "Invalid saved TV source settings" }
                if (!LiveTvBackupSecretsAndroid.isSealed(value)) migrateSecrets = true
                LiveTvBackupSecretsAndroid.open(key, value)
            } else when (value) {
                is Boolean -> PrefValue.Bool(value)
                is Int -> PrefValue.Int(value)
                is Long -> PrefValue.Long(value)
                is String -> PrefValue.Str(value)
                is Set<*> -> PrefValue.StringSet(value.mapNotNull { it as? String }.toSet())
                else -> null
            }
            if (v != null) out[key] = v
        }
        fun validateReadable() = UsenetBackupCompatibility.validate(
            LiveTvBackup.exportForSync(out.filterValues { it !is PrefValue.UnavailableBackupSecret }))
        if (migrateSecrets) {
            try {
                validateReadable()
                // Adopt legacy plaintext only after the complete protected map is durably saved.
                write(preferences, out, protect)
            } catch (_: Exception) {
                original.forEach { (key, value) ->
                    if (UsenetBackupCompatibility.isKey(key) && value is String && !LiveTvBackupSecretsAndroid.isSealed(value)) {
                        out[key] = PrefValue.UnavailableBackupSecret(value)
                    }
                }
            }
        } else validateReadable()
        return out
    }

    internal fun write(preferences: SharedPreferences, data: Map<String, PrefValue>, protect: BackupPreferenceProtection = protectBackupPreference) {
        data.forEach { (key, value) ->
            if (UsenetBackupCompatibility.isReserved(key)) {
                require(UsenetBackupCompatibility.isKey(key) &&
                    (value is PrefValue.Str || value is PrefValue.UnavailableBackupSecret)) { "Invalid Usenet backup entry" }
            }
        }
        val previous = preferences.all
        // Finish all encryption before clearing the editor or publishing the candidate map.
        val sealedData = data.mapValues { (key, value) ->
            if (UsenetBackupCompatibility.isKey(key)) {
                if (value is PrefValue.UnavailableBackupSecret) {
                    check(value.stored == previous[key]) { "Saved TV source settings could not be opened" }
                    PrefValue.Str(value.stored)
                } else PrefValue.Str(protect(key, value, previous[key] as? String))
            } else value
        }
        val secretsChanged = sealedData.any { (key, value) ->
            UsenetBackupCompatibility.isKey(key) && (value as PrefValue.Str).value != previous[key]
        } || previous.keys.any { UsenetBackupCompatibility.isKey(it) && it !in data }
        val editor = preferences.edit().clear()
        sealedData.forEach { (key, value) ->
            when (value) {
                is PrefValue.Bool -> editor.putBoolean(key, value.value)
                is PrefValue.Int -> editor.putInt(key, value.value)
                is PrefValue.Long -> editor.putLong(key, value.value)
                is PrefValue.Str -> editor.putString(key, value.value)
                is PrefValue.StringSet -> editor.putStringSet(key, value.value)
                is PrefValue.UnavailableBackupSecret -> error("Unavailable source settings cannot be written as a portable value")
            }
        }
        // Watching another channel does not need another Keystore round trip or a synchronous disk write.
        if (secretsChanged) {
            if (!runCatching { editor.commit() }.getOrDefault(false)) {
                // commit(false) can still replace SharedPreferences' in-memory map. Restore the exact
                // old values so ordinary edits preserve existing opaque input after a failed migration.
                val rollback = preferences.edit().clear()
                previous.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> rollback.putBoolean(key, value)
                        is Int -> rollback.putInt(key, value)
                        is Long -> rollback.putLong(key, value)
                        is Float -> rollback.putFloat(key, value)
                        is String -> rollback.putString(key, value)
                        is Set<*> -> rollback.putStringSet(key, value.filterIsInstance<String>().toSet())
                        else -> Unit
                    }
                }
                rollback.apply()
                error("Could not save TV source settings")
            }
        } else editor.apply()
    }
}

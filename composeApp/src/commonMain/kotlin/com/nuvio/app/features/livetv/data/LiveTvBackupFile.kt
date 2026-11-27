package com.nuvio.app.features.livetv.data

import com.nuvio.app.features.livetv.model.LiveTvClock
import com.nuvio.app.features.livetv.sync.LiveTvDriveSync
import com.nuvio.app.features.livetv.sync.SyncPart
import com.nuvio.app.features.livetv.sync.SyncParts
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Live TV backups as files the user keeps themselves: the same JSON the Google Drive backup
 * holds (see [LiveTvBackup]), so a file exported from a Nuvio TV, from this app or pulled out of
 * Drive imports anywhere.
 */

/** What a backup file says about itself, for the confirm step before importing it. */
internal class BackupFileInfo(
    val savedAtMs: Long,
    val deviceId: String,
    val device: String,
    val keyCount: Int,
    val playlistCount: Int,
    val epgCount: Int,
    val data: JsonObject,
) {
    val hasSources: Boolean get() = playlistCount > 0 || epgCount > 0 || UsenetBackupCompatibility.hasSources(data)
}

/** What an import did. */
internal class ImportSummary(val playlistCount: Int, val epgCount: Int, val changedPlaylists: Set<String>)

/** Why a file couldn't be read as a backup. The settings screen turns [reason] into a sentence. */
internal class BackupFileException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason {
        /** Not JSON, or not a JSON object. */
        NOT_JSON,
        /** JSON, but not the backup shape (no `data` map of `{"t", "v"}` entries). */
        NOT_BACKUP,
        /** Written by a newer app with a format this one doesn't know. */
        NEWER_FORMAT,
        /** A valid backup with no IPTV or TV Usenet sources: importing it would empty this device. */
        NO_SOURCES,
    }
}

/**
 * Reads [text] as a backup: the Drive envelope (`{"format", "savedAt", "deviceId", "device",
 * "data"}`) or a bare `data` map. Fails with a [BackupFileException] when it's neither, or was
 * written in a newer format.
 */
internal fun parseBackupFile(text: String): Result<BackupFileInfo> = runCatching {
    val json = runCatching { parseObject(text) }.getOrElse { throw BackupFileException(BackupFileException.Reason.NOT_JSON, it) }
    val envelope = json.optJSONObject("data")
    val data: JsonObject
    val info: LiveTvBackup.EnvelopeInfo?
    if (envelope != null) {
        val marker = json["format"] as? JsonPrimitive
        val isTvFile = marker == JsonPrimitive("nuvio-plus-iptv-backup")
        if (marker?.isString == true && !isTvFile && marker.content.toIntOrNull() == null) {
            throw BackupFileException(BackupFileException.Reason.NOT_BACKUP)
        }
        val format = if (isTvFile) json.optInt("version", 0) else json.optInt("format", LiveTvBackup.FORMAT)
        if (format > LiveTvBackup.FORMAT) throw BackupFileException(BackupFileException.Reason.NEWER_FORMAT)
        if (format < 1) throw BackupFileException(BackupFileException.Reason.NOT_BACKUP)
        data = envelope
        info = if (isTvFile) LiveTvBackup.EnvelopeInfo(json.optLong("createdAt"), "", json.optString("device"), data)
            else LiveTvBackup.readEnvelope(json)
    } else {
        data = json
        info = null
    }
    if (!looksLikeBackupData(data)) throw BackupFileException(BackupFileException.Reason.NOT_BACKUP)
    runCatching {
        UsenetBackupCompatibility.validate(data)
        DeviceSettingsBackupCompatibility.containsValidSettings(data)
    }.getOrElse {
        throw BackupFileException(BackupFileException.Reason.NOT_BACKUP)
    }
    val playlists = runCatching { LiveTvCodec.decodePlaylists(data.optJSONObject(LiveTvKeys.playlists)?.optString("v")) }.getOrDefault(emptyList())
    val epgs = runCatching { LiveTvCodec.decodeEpgs(data.optJSONObject(LiveTvKeys.epgs)?.optString("v")) }.getOrDefault(emptyList())
    BackupFileInfo(
        savedAtMs = info?.savedAtMs ?: 0L,
        deviceId = info?.deviceId.orEmpty(),
        device = info?.device.orEmpty(),
        keyCount = data.keys.count { it !in LiveTvKeys.SYNC_EXCLUDED },
        playlistCount = playlists.size,
        epgCount = epgs.size,
        data = data,
    )
}

/** A `data` map: at least one entry, and every entry an object with a type tag the app knows. */
private fun looksLikeBackupData(data: JsonObject): Boolean {
    if (data.isEmpty()) return false
    return data.values.all { e -> e is JsonObject && e.optString("t") in TYPE_TAGS && e.containsKey("v") }
}

private val TYPE_TAGS = setOf("b", "i", "l", "s", "str")

/**
 * Replaces this device's Live TV setup with the backup in [text], the way Restore from Google
 * Drive does: same import, same reload afterwards. A backup without sources is refused.
 */
internal suspend fun importBackupFile(
    text: String,
    prefs: LiveTvPreferencesStore = LiveTvPreferences,
    reload: (changedPlaylists: Set<String>) -> Unit = LiveTvRepository::reloadAfterSync,
): Result<ImportSummary> = runCatching {
    val info = parseBackupFile(text).getOrThrow()
    val tvDeviceSettings = DeviceSettingsBackupCompatibility.containsValidSettings(info.data)
    val settingsOnly = !info.hasSources && (tvDeviceSettings || MobileSetupSettings.hasChoices(info.data))
    if (!info.hasSources && !settingsOnly) throw BackupFileException(BackupFileException.Reason.NO_SOURCES)
    val unavailable = prefs.rawData.value.filterValues { it is PrefValue.UnavailableBackupSecret }.keys
    // Explicit recovery: the file's copies (shape-validated above) replace the ones this phone
    // can't open. Any the file doesn't have stay as they are, so the rest of the file still restores.
    val replacements = unavailable.filter { UsenetBackupCompatibility.isKey(it) && info.data.containsKey(it) }
    if (replacements.isNotEmpty()) {
        prefs.edit { local ->
            replacements.forEach { key -> local[key] = PrefValue.Str(info.data.optJSONObject(key)!!.optString("v")) }
        }
        // The platform has sealed and committed the replacements before any VPN or full setup restore.
    }
    val changed = if (settingsOnly) {
        val before = prefs.exportForSync()
        // A hardware-settings-only file must not clear this phone's existing source setup.
        val options = info.data.filterKeys {
            if (tvDeviceSettings) SyncParts.partOf(it) !in setOf(SyncPart.LOGINS, SyncPart.VPN) && !UsenetBackupCompatibility.isKey(it)
            else MobileSetupSettings.isPortableKey(it)
        }
        val merged = JsonObject(before + options)
        val sources = prefs.importFromSync(merged, applyVpnBackup = false)
        if (LiveTvBackup.changedKeys(before, prefs.exportForSync()).isNotEmpty()) reload(sources)
        sources
    } else LiveTvBackupApply.apply(info.data, prefs, reload).changedPlaylists
    ImportSummary(playlistCount = info.playlistCount, epgCount = info.epgCount, changedPlaylists = changed)
}

/** The backup file for this device: the Drive envelope around its setup, as text. */
internal fun exportBackupFileText(
    prefs: LiveTvPreferencesStore = LiveTvPreferences,
    deviceId: String = LiveTvDriveSync.deviceId,
    deviceName: String = LiveTvFiles.deviceName,
    nowMs: Long = LiveTvClock.nowMs(),
): String = LiveTvBackup.envelope(prefs.exportForSync(), nowMs, deviceId, deviceName).toString()

/** The suggested name for an exported file; also the Drive file's name. */
internal const val LIVE_TV_BACKUP_FILE_NAME = "Nuvio Live TV backup.json"

/**
 * Applying a backup to this device: what Restore from Google Drive and Import backup file share.
 * The import keeps per-device state ([LiveTvBackup.importInto]); then the repository rebuilds
 * from the new sources, downloads playlists whose address or login changed and reloads the
 * guide. Sports follows, favorites and the rest read the store directly, so they pick the new
 * setup up on their own.
 */
internal object LiveTvBackupApply {
    class Applied(val changedKeys: Set<String>, val changedPlaylists: Set<String>) {
        val changedAnything: Boolean get() = changedKeys.isNotEmpty() || changedPlaylists.isNotEmpty()
    }

    suspend fun apply(
        data: JsonObject,
        prefs: LiveTvPreferencesStore = LiveTvPreferences,
        reload: (changedPlaylists: Set<String>) -> Unit = LiveTvRepository::reloadAfterSync,
    ): Applied {
        val before = prefs.exportForSync()
        val changedPlaylists = prefs.importFromSync(data)
        val after = prefs.exportForSync()
        val applied = Applied(LiveTvBackup.changedKeys(before, after), changedPlaylists)
        if (applied.changedAnything) reload(changedPlaylists)
        return applied
    }
}

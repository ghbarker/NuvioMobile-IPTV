package com.nuvio.app.features.livetv.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Live TV's setup as the TV app saves it to Google Drive (and as its import reads it), so a
 * phone and a TV can share one backup.
 *
 * The data is a JSON object with one entry per stored key: `{"t": "b"|"i"|"l"|"s"|"str", "v": …}`
 * (boolean, int, long, string set, string). Per-device keys ([LiveTvKeys.SYNC_EXCLUDED], with
 * each profile's "_p<N>" copy) are never in it. The Drive file wraps it in an envelope:
 * `{"format": 1, "savedAt": …, "deviceId": …, "device": …, "data": {…}}`.
 */
internal object LiveTvBackup {

    const val FORMAT = 1

    /**
     * Everything worth carrying to another device: sources, settings, favorites, hidden
     * channels, groups, renames and EPG assignments. Left out: what this device last watched,
     * and download status (last updated, errors, counts), which differs per device and changes
     * constantly.
     */
    fun exportForSync(data: Map<String, PrefValue>): JsonObject = buildJsonObject {
        data.forEach { (name, value) ->
            if (name in LiveTvKeys.SYNC_EXCLUDED) return@forEach
            val entry = when (value) {
                is PrefValue.Bool -> buildJsonObject { put("t", "b"); put("v", value.value) }
                is PrefValue.Int -> buildJsonObject { put("t", "i"); put("v", value.value) }
                is PrefValue.Long -> buildJsonObject { put("t", "l"); put("v", value.value) }
                is PrefValue.StringSet -> buildJsonObject {
                    put("t", "s")
                    put("v", buildJsonArray { value.value.forEach { add(it) } })
                }
                is PrefValue.Str -> {
                    val v = when (name) {
                        // Download status and what this device learned about the server (its time
                        // zone, whether HLS catch-up works) stay per device: they differ between
                        // devices, and syncing them made each look changed to the other.
                        LiveTvKeys.playlists -> LiveTvCodec.encodePlaylists(LiveTvCodec.decodePlaylists(value.value).map {
                            it.copy(lastUpdatedMs = 0, lastError = null, channelCount = 0, serverTimezone = "", catchupNoHlsSince = 0L)
                        })
                        LiveTvKeys.epgs -> LiveTvCodec.encodeEpgs(LiveTvCodec.decodeEpgs(value.value).map {
                            it.copy(lastUpdatedMs = 0, lastError = null, programCount = 0, fallbackUserAgent = "")
                        })
                        else -> value.value
                    }
                    buildJsonObject { put("t", "str"); put("v", v) }
                }
                // This phone lost the key to its copy. Leave it out instead of stopping sync: the
                // combine step keeps the copy other devices and the cloud have, which then repairs it.
                is PrefValue.UnavailableBackupSecret -> return@forEach
            }
            put(name, entry)
        }
        // Carry the untouched default too, so an explicit settings restore can turn off an
        // option that was enabled on the receiving device, just as a TV export does.
        if (LiveTvKeys.cleanVodGroupNames !in data) {
            put(LiveTvKeys.cleanVodGroupNames, buildJsonObject { put("t", "b"); put("v", false) })
        }
    }

    /** Whether a setup from [exportForSync] has any playlist or guide in it. */
    fun syncHasSources(data: JsonObject?): Boolean {
        if (data == null) return false
        fun raw(key: String) = data.optJSONObject(key)?.optString("v")
        return runCatching { parseObject(raw("vpn_profiles_v1").orEmpty()).optJSONArray("profiles")?.isNotEmpty() == true }.getOrDefault(false) ||
            runCatching { LiveTvCodec.decodePlaylists(raw(LiveTvKeys.playlists)).isNotEmpty() }.getOrDefault(false) ||
            runCatching { LiveTvCodec.decodeEpgs(raw(LiveTvKeys.epgs)).isNotEmpty() }.getOrDefault(false) ||
            UsenetBackupCompatibility.hasSources(data)
    }

    /** The result of [importInto]: the new map, and the playlists that need downloading again. */
    class Imported(val data: Map<String, PrefValue>, val changed: Set<String>)

    /**
     * [local] with its Live TV setup replaced by [data] (from [exportForSync]), keeping per-device
     * state: excluded keys stay as they are, and each source this device already had keeps its
     * download status. [Imported.changed] lists playlists whose address or login changed.
     */
    fun importInto(local: Map<String, PrefValue>, data: JsonObject): Imported {
        UsenetBackupCompatibility.validate(data)
        val changed = HashSet<String>()
        val p = local.toMutableMap()
        val localPlaylists = LiveTvCodec.decodePlaylists(local.str(LiveTvKeys.playlists)).associateBy { it.id }
        val localEpgs = LiveTvCodec.decodeEpgs(local.str(LiveTvKeys.epgs)).associateBy { it.id }
        // Remove synced keys the other device doesn't have, then write everything it does.
        local.keys.filter {
            it !in LiveTvKeys.SYNC_EXCLUDED && !data.containsKey(it) && !MobileSetupSettings.isPortableKey(it) &&
                local[it] !is PrefValue.UnavailableBackupSecret
        }.forEach { p.remove(it) }
        data.forEach { (name, element) ->
            if (name in LiveTvKeys.SYNC_EXCLUDED) return@forEach
            val e = element as? JsonObject ?: return@forEach
            when (e.optString("t")) {
                "b" -> p[name] = e.optBoolean("v")
                "i" -> p[name] = e.optInt("v")
                "l" -> p[name] = e.optLong("v")
                "s" -> p[name] = (e.optJSONArray("v") ?: JsonArray(emptyList())).strings().toSet()
                "str" -> {
                    var v = e.optString("v")
                    // Keep this device's download status for sources it already had.
                    if (name == LiveTvKeys.playlists) {
                        v = LiveTvCodec.encodePlaylists(LiveTvCodec.decodePlaylists(v).map { r ->
                            localPlaylists[r.id]?.let { l ->
                                if (!l.sameConnection(r)) changed += r.id
                                r.copy(
                                    lastUpdatedMs = l.lastUpdatedMs, lastError = l.lastError, channelCount = l.channelCount,
                                    serverTimezone = l.serverTimezone, catchupNoHlsSince = l.catchupNoHlsSince,
                                    fallbackUserAgent = l.fallbackUserAgent.ifBlank { r.fallbackUserAgent },
                                    linkServer = l.linkServer.ifBlank { r.linkServer },
                                    linkUsername = l.linkUsername.ifBlank { r.linkUsername },
                                    linkPassword = l.linkPassword.ifBlank { r.linkPassword },
                                    vodInPlaylist = l.vodInPlaylist || r.vodInPlaylist
                                )
                            } ?: r
                        })
                    } else if (name == LiveTvKeys.epgs) {
                        v = LiveTvCodec.encodeEpgs(LiveTvCodec.decodeEpgs(v).map { r ->
                            localEpgs[r.id]?.let { l ->
                                // A new link or user agent from the other device: downloaded again.
                                if (l.url != r.url || l.userAgent != r.userAgent) changed += r.id
                                r.copy(
                                    lastUpdatedMs = l.lastUpdatedMs, lastError = l.lastError, programCount = l.programCount,
                                    fallbackUserAgent = if (l.url == r.url) l.fallbackUserAgent.ifBlank { r.fallbackUserAgent } else ""
                                )
                            } ?: r
                        })
                    }
                    p[name] = v
                }
            }
        }
        return Imported(p, changed)
    }

    // ---------- the Drive file ----------

    /** The file as saved to Drive: the envelope around [data]. */
    fun envelope(data: JsonObject, savedAtMs: Long, deviceId: String, device: String): JsonObject = buildJsonObject {
        put("format", FORMAT)
        put("savedAt", savedAtMs)
        put("deviceId", deviceId)
        put("device", device)
        put("data", data)
    }

    /** What an envelope says about itself. */
    class EnvelopeInfo(val savedAtMs: Long, val deviceId: String, val device: String, val data: JsonObject?)

    fun readEnvelope(json: JsonObject): EnvelopeInfo = EnvelopeInfo(
        savedAtMs = json.optLong("savedAt"),
        deviceId = json.optString("deviceId"),
        device = json.optString("device"),
        data = json.optJSONObject("data")
    )

    fun parseEnvelope(text: String): EnvelopeInfo? = runCatching { readEnvelope(parseObject(text)) }.getOrNull()

    /** Two exports are the same setup when their JSON is, key by key. */
    fun same(a: JsonObject?, b: JsonObject?): Boolean = a.toString() == b.toString()

    /** The keys whose value differs between two exports. */
    fun changedKeys(before: JsonObject, after: JsonObject): Set<String> =
        (before.keys + after.keys).filter { k -> before[k]?.toString() != after[k]?.toString() }.toSet()

    /** The "v" of a key's entry as a plain string (for comparing one setting across exports). */
    fun valueOf(data: JsonObject, key: String): String? = (data[key] as? JsonObject)?.get("v")?.let {
        if (it is JsonPrimitive) it.content else it.toString()
    }
}

/** This device's Live TV setup for sync (see [LiveTvBackup.exportForSync]). */
internal fun LiveTvPreferencesStore.exportForSync(): JsonObject {
    val data = LiveTvBackup.exportForSync(rawData.value).toMutableMap()
    val mobile = readPlatformSettings()
    data.putAll(LiveTvBackup.exportForSync(mobile.entries.sortedBy { it.key }.associate { it.toPair() }))
    if (usesPlatformVpnBackup) exportVpnBackup(data["vpn_profiles_v1"]?.let { (it as? JsonObject)?.optString("v") })?.let { native ->
        // Android owns the encrypted native copy; migrate then remove any older plaintext preference.
        if (rawData.value.containsKey("vpn_profiles_v1")) edit { it.remove("vpn_profiles_v1") }
        data["vpn_profiles_v1"] = buildJsonObject { put("t", "str"); put("v", native) }
    }
    return JsonObject(data)
}

/**
 * Replaces this device's Live TV setup with [data] from [exportForSync], keeping per-device
 * state. Returns the playlists whose address or login changed: they need downloading again.
 */
internal fun LiveTvPreferencesStore.importFromSync(data: JsonObject, applyVpnBackup: Boolean = true): Set<String> {
    // Validate before native VPN restore or any preference changes.
    UsenetBackupCompatibility.validate(data)
    data.forEach { (key, entry) ->
        if (key in MobileSetupSettings.seekKeys || key.startsWith(MobileSetupSettings.PREFIX)) {
            require(entry is JsonObject && MobileSetupSettings.validEntry(key, entry)) { "Invalid mobile setup preference" }
        }
    }
    val nativeVpnRestored = applyVpnBackup && usesPlatformVpnBackup &&
        data.optJSONObject("vpn_profiles_v1")?.optString("v")?.let { restoreVpnBackup(it) } == true
    val preferences = if (usesPlatformVpnBackup && (nativeVpnRestored || !applyVpnBackup)) {
        JsonObject(data.filterKeys { it != "vpn_profiles_v1" })
    } else data
    val fullSetup = LiveTvBackup.syncHasSources(preferences)
    var changed: Set<String> = emptySet()
    var nativeChoices = emptyMap<String, PrefValue>()
    editImported { p ->
        val imported = LiveTvBackup.importInto(p, preferences)
        changed = imported.changed
        val restored = imported.data.toMutableMap()
        for ((key, value) in imported.data) {
            if (!key.startsWith(MobileSetupSettings.LAYOUT)) continue
            val raw = key.removePrefix(MobileSetupSettings.LAYOUT)
            if (MobileSetupSettings.isLocalLayout(raw, settingDefaults) &&
                settingDefaults[raw.replace(LiveTvKeys.PROFILE_SUFFIX, "")]?.let { it::class == value::class } == true
            ) {
                // A complete legacy backup resets absent raw options to their original
                // defaults. A source-free settings patch only changes entries it carries.
                if (key in preferences || (raw !in preferences && !fullSetup)) restored[raw] = value
                else if (restored[raw] != value) restored.remove(key)
            }
        }
        // A missing entry in an older backup is not a reset. Device-native preferences have
        // one owner; a platform that does not implement them can carry them opaquely.
        nativeChoices = restored.filterKeys { it in preferences && ownsPlatformSetting(it) }
        restored.keys.removeAll { ownsPlatformSetting(it) }
        p.clear()
        p.putAll(restored)
        // A setup from an older version: its one-for-everything time shifts onto each source.
        moveTimeShifts(p)
    }
    restorePlatformSettings(nativeChoices)
    return changed
}

internal fun LiveTvPreferencesStore.syncHasSources(data: JsonObject?): Boolean = LiveTvBackup.syncHasSources(data)

package com.nuvio.app.features.livetv.sync

import com.nuvio.app.features.livetv.data.MobileSetupSettings
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Source-free setups carry preference edits. An empty provider export cannot clear a configured device. */
internal object CloudPreferencePatch {
    private val profileSuffix = Regex("""_p\d+$""")

    private fun isChoice(key: String, entry: JsonObject?): Boolean {
        val name = key.replace(profileSuffix, "")
        if (name == "network_folder") {
            // A shared NAS can be configured without an IPTV playlist. Only a configured folder
            // can override an existing one in a settings-only patch. The Logins switch still applies.
            val raw = entry?.get("v") as? JsonPrimitive
            return raw?.isString == true && com.nuvio.app.features.livetv.record.NetworkFolderConfig.fromJson(raw.content) != null
        }
        if (name == "device_settings_v1") return false
        if (name == "usenet_sources_v1") return false
        return when (SyncParts.partOf(key)) {
            SyncPart.LOGINS, SyncPart.VPN -> false
            else -> true
        }
    }

    fun onlyChoices(data: JsonObject): JsonObject = JsonObject(data.filter { (key, value) -> isChoice(key, value as? JsonObject) })

    fun hasChoices(data: JsonObject, includeInitialValues: Boolean = true): Boolean =
        data.keys.any { key ->
            val entry = data[key] as? JsonObject ?: return@any false
            if (!isChoice(key, entry) || key.replace(profileSuffix, "") == "profile_seeded") return@any false
            val value = entry["v"]
            val isLong = entry["t"] == JsonPrimitive("l") && value is JsonPrimitive && !value.isString && value.longOrNull != null
            if (!isLong && !MobileSetupSettings.validEntry(key, entry)) return@any false
            if (includeInitialValues) return@any true
            // These two choices can be written by startup before the person opens Settings.
            when (key) {
                "mobile_setting_v1_update_channel" -> false
                "group_playlist_headings", "mobile_setting_v1_layout_group_playlist_headings" ->
                    value == JsonPrimitive(false)
                else -> true
            }
        }

    /** Preserve everything outside a partial restore, including this device's playlists and VPN. */
    fun overlay(current: JsonObject, patch: JsonObject): JsonObject = JsonObject(current + onlyChoices(patch))
}

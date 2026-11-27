package com.nuvio.app.features.livetv.data

import com.nuvio.app.features.livetv.sync.SyncPart
import com.nuvio.app.features.livetv.sync.SyncParts
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/** Extra user choices carried alongside the existing typed Live TV preferences. */
internal object MobileSetupSettings {
    const val PREFIX = "mobile_setting_v1_"
    const val LAYOUT = "${PREFIX}layout_"
    const val SEEK_ENABLED = "seek_thumbs_enabled"
    const val SEEK_4K = "seek_thumbs_include_4k"
    const val SEEK_WHEN = "seek_thumbs_when"
    const val SEEK_MB = "seek_thumbs_storage_mb"
    const val CHOSE_UPDATE_CHANNEL = "${PREFIX}chose_update_channel"
    const val REMEMBER_PROFILE = "${PREFIX}remember_last_profile"
    const val PLUGINS_ENABLED = "plugins_enabled"
    const val PLUGINS_GROUPED = "group_streams_by_repository"
    fun profileKey(key: String, profile: String) = "${PREFIX}${key}_profile_$profile"
    /** Include 4K ([SEEK_4K]) is not here: it stays on each device, whose default depends on its memory. */
    val seekKeys = setOf(SEEK_ENABLED, SEEK_WHEN, SEEK_MB)
    private val otherAppearance = setOf("selected_app_icon", "library_display_settings_payload",
        "season_view_mode", "show_addon_logo", "use_legacy_player_layout", "playback_brightness")

    fun isAppearanceKey(key: String): Boolean = key.startsWith(LAYOUT) || (key.startsWith(PREFIX) &&
        key.removePrefix(PREFIX).substringBefore("_profile_") in otherAppearance)

    fun isPortableKey(key: String) = key in seekKeys || key.startsWith(PREFIX) || key.startsWith("tv_setting_v1_")

    /** An older client can carry an entry whose setting it doesn't implement. */
    fun hasChoices(data: JsonObject): Boolean = data.any { (key, item) ->
        key != PREFIX + "update_channel" && (key != LAYOUT + LiveTvKeys.groupPlaylistHeadings ||
            (item as? JsonObject)?.get("v") == JsonPrimitive(false)) &&
            (key in seekKeys || key.startsWith(PREFIX)) && item is JsonObject && validEntry(key, item)
    }

    fun validEntry(key: String, entry: JsonObject): Boolean {
        val value = entry["v"] as? JsonPrimitive
        return when (key) {
            SEEK_ENABLED, SEEK_4K -> entry.optString("t") == "b" && value?.isString == false && value.booleanOrNull != null
            SEEK_WHEN -> entry.optString("t") == "str" && value?.isString == true && value.content in setOf("SEEKING", "WATCHING", "RIGHT_AWAY")
            SEEK_MB -> entry.optString("t") == "i" && value?.isString == false && value.intOrNull in setOf(50, 100, 250, 500)
            else -> validTypedEntry(entry)
        }
    }

    private fun validTypedEntry(entry: JsonObject): Boolean {
        val v = entry["v"]
        return when (entry.optString("t")) {
            "b" -> v is JsonPrimitive && !v.isString && v.booleanOrNull != null
            "i" -> v is JsonPrimitive && !v.isString && v.intOrNull != null
            "l" -> v is JsonPrimitive && !v.isString && v.content.toLongOrNull() != null
            "str" -> v is JsonPrimitive && v.isString
            "s" -> v is JsonArray && v.all { it is JsonPrimitive && it.isString }
            else -> false
        }
    }

    // Only keys actually written by the mobile settings editor are candidates. Opaque TV
    // entries, credentials, device grants and source lists can never become mobile layout.
    fun isLocalLayout(key: String, defaults: Map<String, PrefValue>): Boolean {
        val base = key.replace(LiveTvKeys.PROFILE_SUFFIX, "")
        return base in defaults && base != LiveTvKeys.recordingStorage && SyncParts.partOf(base) == SyncPart.LOOK
    }
}

package com.nuvio.app.features.livetv.data

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.features.autosync.AutoSyncPreferencesRepository
import com.nuvio.app.features.library.LibraryDisplaySettingsRepository
import com.nuvio.app.features.p2p.P2pSettingsRepository
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.thumbs.SeekThumbsPlatform
import com.nuvio.app.features.profiles.MAX_PROFILES
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.settings.SentrySettingsRepository
import com.nuvio.app.features.streams.StreamBadgeSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Maps user choices outside Live TV that aren't in upstream's profile-settings payload. */
internal class MobileSetupPreferencesAndroid(
    context: Context,
    private val settings: LiveTvPreferencesStore,
    private val profiles: () -> Map<Int, String> = {
        ProfileRepository.state.value.profiles.filter { it.id.isNotBlank() }.associate { it.profileIndex to it.id }
    },
) {
    private enum class Kind { BOOL, INT, TEXT, FLOAT }

    private data class Store(
        val file: String,
        val profiled: Boolean = false,
        val bools: Set<String> = emptySet(),
        val ints: Set<String> = emptySet(),
        val strings: Set<String> = emptySet(),
        val floats: Set<String> = emptySet(),
    ) {
        val keys = buildMap {
            bools.forEach { put(it, Kind.BOOL) }
            ints.forEach { put(it, Kind.INT) }
            strings.forEach { put(it, Kind.TEXT) }
            floats.forEach { put(it, Kind.FLOAT) }
        }

        // Platform stores use <key>_<profile id>. A malformed/unknown suffix never becomes a key.
        fun base(name: String): String? {
            if (!profiled) return name.takeIf { it in keys }
            val suffix = name.substringAfterLast('_')
            if (suffix.toIntOrNull()?.takeIf { it in 1..MAX_PROFILES }?.toString() != suffix) return null
            return name.removeSuffix("_$suffix").takeIf { it in keys }
        }

        fun wire(name: String, profiles: Map<Int, String>): String? = when {
            profiled -> profiles[name.substringAfterLast('_').toIntOrNull()]?.let { id ->
                MobileSetupSettings.profileKey(name.substringBeforeLast('_'), id)
            }
            else -> when (file) {
            "seek_thumbnails" -> seekNames.getValue(name)
            "nuvio_sentry_settings" -> MobileSetupSettings.PREFIX + "crash_reports_enabled"
            "livetv_dns" -> MobileSetupSettings.PREFIX + "dns_choice"
            else -> MobileSetupSettings.PREFIX + name
            }
        }

        fun local(wire: String, profiles: Map<Int, String>): String? {
            if (profiled) {
                if (!wire.startsWith(MobileSetupSettings.PREFIX)) return null
                val key = wire.removePrefix(MobileSetupSettings.PREFIX).substringBeforeLast("_profile_", "")
                if (key !in keys) return null
                val id = wire.substringAfterLast("_profile_")
                val slot = profiles.entries.firstOrNull { it.value == id && it.key in 1..MAX_PROFILES }?.key ?: return null
                return "${key}_$slot"
            }
            val name = when (file) {
                "seek_thumbnails" -> seekNames.entries.firstOrNull { it.value == wire }?.key
                "nuvio_sentry_settings" -> "enabled".takeIf { wire == MobileSetupSettings.PREFIX + "crash_reports_enabled" }
                "livetv_dns" -> "choice".takeIf { wire == MobileSetupSettings.PREFIX + "dns_choice" }
                else -> wire.removePrefix(MobileSetupSettings.PREFIX).takeIf { wire.startsWith(MobileSetupSettings.PREFIX) }
            }
            return name?.takeIf { base(it) != null }
        }
    }

    private val files = stores.associateWith { context.applicationContext.getSharedPreferences(it.file, Context.MODE_PRIVATE) }
    private val owners = context.applicationContext.getSharedPreferences("mobile_setup_profile_owners", Context.MODE_PRIVATE)
    private val plugins = context.applicationContext.getSharedPreferences("nuvio_plugins", Context.MODE_PRIVATE)
    // SharedPreferences keeps weak listener references; this instance is retained by the store.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, name ->
        val file = files.entries.firstOrNull { it.value === prefs }?.key
        if (file != null && (name == null || file.base(name) != null)) settings.platformSettingsChanged()
    }

    fun install() {
        settings.readPlatformSettings = ::snapshot
        settings.restorePlatformSettings = ::restore
        settings.ownsPlatformSetting = { wire -> stores.any { it.local(wire, profiles()) != null } }
        files.values.forEach { it.registerOnSharedPreferenceChangeListener(listener) }
        if (settings === LiveTvPreferences) {
            installed = this
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                ProfileRepository.state.map { it.profiles.map { p -> p.profileIndex to p.id } }.distinctUntilChanged().collect {
                    // Setup Sync can restore before upstream has loaded the signed-in account's profiles.
                    // Deferred entries are consumed only by their original profile, even if a slot is reused.
                    val pending = settings.rawData.value.filterKeys(settings.ownsPlatformSetting)
                    if (pending.isNotEmpty()) {
                        restore(pending)
                        settings.edit { p -> pending.forEach { (key, value) -> if (p[key] == value) p.remove(key) } }
                    }
                    migrateCompoundPreferences()
                    settings.platformSettingsChanged()
                }
            }
        }
    }

    private fun snapshot(): Map<String, PrefValue> = buildMap {
        val roster = currentProfiles()
        files.forEach { (store, prefs) ->
            for ((name, value) in prefs.all) {
                val base = store.base(name) ?: continue
                val portable = when (store.keys.getValue(base)) {
                    Kind.BOOL -> (value as? Boolean)?.let(PrefValue::Bool)
                    Kind.INT -> (value as? Int)?.let(PrefValue::Int)
                    Kind.TEXT -> (value as? String)?.let(PrefValue::Str)
                    // The Live TV format has no float tag. Keep its exact decimal representation.
                    Kind.FLOAT -> (value as? Float)?.takeIf { it.isFinite() }?.let { PrefValue.Str(it.toString()) }
                }
                if (portable != null) store.wire(name, roster)?.let { put(it, portable) }
            }
        }
    }

    private fun restore(values: Map<String, PrefValue>) {
        (settings.rawData.value[MobileSetupSettings.REMEMBER_PROFILE] as? PrefValue.Bool)?.let {
            ProfileRepository.setRememberLastProfileEnabled(it.value)
        }
        val roster = currentProfiles()
        files.forEach { (store, prefs) ->
            val before = prefs.all
            val editor = prefs.edit()
            var changed = false
            for ((wire, value) in values) {
                val name = store.local(wire, roster) ?: continue
                val base = store.base(name) ?: continue
                if (wire == MobileSetupSettings.SEEK_WHEN && (value as? PrefValue.Str)?.value !in seekModes) continue
                if (wire == MobileSetupSettings.SEEK_MB && (value as? PrefValue.Int)?.value !in seekSizes) continue
                when (store.keys.getValue(base)) {
                    Kind.BOOL -> {
                        val b = (value as? PrefValue.Bool)?.value ?: continue
                        if (before[name] == b) continue
                        editor.putBoolean(name, b)
                    }
                    Kind.INT -> {
                        val i = (value as? PrefValue.Int)?.value ?: continue
                        if (before[name] == i) continue
                        editor.putInt(name, i)
                    }
                    Kind.TEXT -> {
                        val str = (value as? PrefValue.Str)?.value ?: continue
                        if (before[name] == str) continue
                        editor.putString(name, str)
                    }
                    Kind.FLOAT -> {
                        val f = (value as? PrefValue.Str)?.value?.toFloatOrNull()?.takeIf { it.isFinite() } ?: continue
                        if (before[name] == f) continue
                        editor.putFloat(name, f)
                    }
                }
                changed = true
            }
            if (changed) {
                check(editor.commit()) { "Could not save mobile settings" }
                when (store.file) {
                    "seek_thumbnails" -> SeekThumbsPlatform.reloadSettings()
                    "nuvio_player_settings" -> PlayerSettingsRepository.onProfileChanged()
                    "nuvio_stream_badge_settings" -> StreamBadgeSettingsRepository.onProfileChanged()
                    "nuvio_library_display_settings" -> LibraryDisplaySettingsRepository.onProfileChanged()
                    "torrent_settings" -> P2pSettingsRepository.onProfileChanged()
                    "nuvio_autosync_settings" -> AutoSyncPreferencesRepository.reloadAfterRestore()
                    "nuvio_sentry_settings" -> SentrySettingsRepository.setEnabled(prefs.getBoolean("enabled", true))
                    "livetv_dns" -> runCatching { DnsChoice.valueOf(prefs.getString("choice", null).orEmpty()) }
                        .getOrNull()?.let(ForkDns::set)
                    else -> afterMobileSetupPresentationRestore(store.file)
                }
            }
        }
    }

    private fun forgetProfile(slot: Int) {
        files.filterKeys { it.profiled }.forEach { (store, prefs) ->
            prefs.edit().apply { store.keys.forEach { (base, _) -> remove("${base}_$slot") } }.apply()
        }
    }

    // Plugin state also holds private repository URLs and executable scrapers. Only adopt the
    // two user switches from existing installations. Future changes use the setting's own key.
    private fun migrateCompoundPreferences() {
        val roster = currentProfiles()
        if (roster.isEmpty()) return
        val legacy = mutableMapOf<String, PrefValue>()
        if (ProfileRepository.state.value.rememberLastProfileEnabled)
            legacy[MobileSetupSettings.REMEMBER_PROFILE] = PrefValue.Bool(true)
        for ((slot, id) in roster) {
            if (owners.getString("skip_legacy_plugins_$slot", null) == id) continue
            val text = plugins.getString("plugins_state_$slot", null) ?: continue
            val state = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: continue
            if ((state["pluginsEnabled"] as? JsonPrimitive)?.booleanOrNull == false)
                legacy[MobileSetupSettings.profileKey(MobileSetupSettings.PLUGINS_ENABLED, id)] = PrefValue.Bool(false)
            if ((state["groupStreamsByRepository"] as? JsonPrimitive)?.booleanOrNull == true)
                legacy[MobileSetupSettings.profileKey(MobileSetupSettings.PLUGINS_GROUPED, id)] = PrefValue.Bool(true)
        }
        settings.edit { p -> legacy.forEach { (key, value) -> if (key !in p) p[key] = value } }
        (settings.rawData.value[MobileSetupSettings.REMEMBER_PROFILE] as? PrefValue.Bool)?.let {
            ProfileRepository.setRememberLastProfileEnabled(it.value)
        }
    }

    @Synchronized
    private fun currentProfiles(): Map<Int, String> {
        val roster = profiles().filter { (slot, id) -> slot in 1..MAX_PROFILES && id.isNotBlank() }
        roster.forEach { (slot, id) ->
            val key = slot.toString()
            val previous = owners.getString(key, null)
            if (previous != id) {
                if (previous != null) {
                    forgetProfile(slot)
                    owners.edit().putString("skip_legacy_plugins_$slot", id).apply()
                }
                owners.edit().putString(key, id).apply()
            }
        }
        return roster
    }

    companion object {
        private var installed: MobileSetupPreferencesAndroid? = null
        fun onProfileRemoved(slot: Int) { installed?.forgetProfile(slot) }
        private val seekModes = com.nuvio.app.features.player.thumbs.SeekThumbWhen.entries.map { it.name }
        private val seekSizes = com.nuvio.app.features.player.thumbs.SEEK_THUMB_STORAGE_CHOICES
        private val seekNames = mapOf(
            "enabled" to MobileSetupSettings.SEEK_ENABLED, "include_4k" to MobileSetupSettings.SEEK_4K,
            "when" to MobileSetupSettings.SEEK_WHEN, "storage_mb" to MobileSetupSettings.SEEK_MB,
        )
        // Do not export whole preference files. Player, updater and other stores also hold
        // account credentials, pending playback, diagnostics and identifiers local to a device.
        private val stores = listOf(
            Store("seek_thumbnails", bools = setOf("enabled", "include_4k"), ints = setOf("storage_mb"), strings = setOf("when")),
            Store("nuvio_player_settings", profiled = true,
                bools = setOf("use_legacy_player_layout", "exo_native_memory_enabled", "vod_cache_enabled", "buffer_engine_enabled", "intro_submit_enabled"),
                ints = setOf("vod_cache_size_mb", "min_buffer_ms", "max_buffer_ms", "buffer_for_playback_ms", "buffer_for_playback_after_rebuffer_ms", "back_buffer_duration_ms", "target_buffer_size_mb"),
                strings = setOf("vod_cache_size_mode", "libass_render_scale", "libass_render_mode"),
                floats = setOf("playback_brightness")),
            Store("nuvio_autosync_settings", profiled = true, bools = setOf("preferred_subtitle_auto_sync_on_start", "auto_sync_aggressive_mode"), ints = setOf("auto_sync_tolerance_ms")),
            Store("torrent_settings", profiled = true, bools = setOf("p2p_enabled", "enable_upload", "hide_torrent_stats"), strings = setOf("torrent_profile", "cache_size")),
            Store("nuvio_library_display_settings", profiled = true, strings = setOf("library_display_settings_payload")),
            Store("nuvio_season_view_mode", profiled = true, strings = setOf("season_view_mode")),
            Store("nuvio_discover_selection", profiled = true, strings = setOf("discover_catalog_key")),
            Store("nuvio_theme_settings", strings = setOf("selected_app_language")),
            Store("app_icon_settings", strings = setOf("selected_app_icon")),
            Store("nuvio_sentry_settings", bools = setOf("enabled")),
            Store("nuvio_stream_badge_settings", profiled = true, bools = setOf("show_addon_logo")),
            Store("nuvio_updater", strings = setOf("update_channel")),
            Store("livetv_dns", strings = setOf("choice")),
        )
    }
}

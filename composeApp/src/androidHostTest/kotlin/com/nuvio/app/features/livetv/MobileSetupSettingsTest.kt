package com.nuvio.app.features.livetv

import android.content.Context
import com.nuvio.app.features.livetv.data.*
import com.nuvio.app.features.livetv.record.RecordingPlatform
import com.nuvio.app.features.livetv.sync.LiveTvDriveSync
import com.nuvio.app.features.livetv.sync.SyncParts
import com.nuvio.app.features.player.thumbs.SeekThumbWhen
import com.nuvio.app.features.player.thumbs.SeekThumbsPlatform
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MobileSetupSettingsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private fun file(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private fun phone() = LiveTvPreferencesStore().also {
        it.installPersistence({ null }, {})
        MobileSetupPreferencesAndroid(context, it) { mapOf(1 to "profile-one", 2 to "profile-two") }.install()
    }

    @Test fun legacyChoicesRestoreToAnEmptyPhoneAndUpdateTheRunningThumbnailEngine() {
        RecordingPlatform.initialize(context)
        val seek = file("seek_thumbnails")
        seek.edit().putBoolean("enabled", true).putBoolean("include_4k", true)
            .putString("when", "WATCHING").putInt("storage_mb", 250).putLong("low_memory_at", 42).commit()
        val playback = file("nuvio_player_settings")
        playback.edit().putInt("min_buffer_ms_1", 40_000).putInt("min_buffer_ms_2", 25_000)
            .putFloat("playback_brightness_2", 0.4f)
            .putString("introdb_api_key_1", "test-secret").putString("preferred_audio_language_1", "en")
            .putString("pending_external_playback", "test-session").commit()

        val source = phone()
        source.updateSettings { it.copy(phoneChannelNames = false, compactRows = true) }
        val saved = SyncParts.strip(source.exportForSync())
        assertTrue(MobileSetupSettings.hasChoices(saved))
        assertTrue(MobileSetupSettings.SEEK_ENABLED in saved)
        assertFalse("compact_rows" in saved)
        assertTrue(MobileSetupSettings.LAYOUT + LiveTvKeys.compactRows in saved)
        for (excluded in listOf("test-secret", "test-session", "low_memory_at", "preferred_audio_language", "recording_storage"))
            assertFalse(excluded in saved.toString())

        seek.edit().clear().putLong("low_memory_at", 99).commit()
        playback.edit().clear().putString("preferred_audio_language_1", "de").commit()
        SeekThumbsPlatform.reloadSettings()
        assertFalse(SeekThumbsPlatform.settings.value.enabled)
        val destination = phone()
        assertFalse(MobileSetupSettings.hasChoices(SyncParts.strip(destination.exportForSync())))
        destination.importFromSync(SyncParts.keep(saved, destination.exportForSync()))
        val restored = SeekThumbsPlatform.settings.value
        assertTrue(restored.enabled)
        assertTrue(restored.include4k)
        assertEquals(SeekThumbWhen.WATCHING, restored.whenToMake)
        assertEquals(250, restored.storageMb)
        assertEquals(99L, restored.lastLowMemoryAt)
        assertEquals(40_000, playback.getInt("min_buffer_ms_1", 0))
        assertEquals(25_000, playback.getInt("min_buffer_ms_2", 0))
        assertEquals(0.4f, playback.getFloat("playback_brightness_2", 0f))
        assertEquals("de", playback.getString("preferred_audio_language_1", null))
        assertFalse(destination.currentSettings().phoneChannelNames)
        assertTrue(destination.currentSettings().compactRows)
        assertEquals(saved, SyncParts.strip(destination.exportForSync()))
    }

    @Test fun combiningAnOlderOrForeignSetupRetainsSettingsAndIndependentEdits() {
        val enabled = JsonObject(mapOf("t" to JsonPrimitive("b"), "v" to JsonPrimitive(true)))
        val disabled = JsonObject(enabled + ("v" to JsonPrimitive(false)))
        val size = JsonObject(mapOf("t" to JsonPrimitive("i"), "v" to JsonPrimitive(250)))
        val base = JsonObject(mapOf(MobileSetupSettings.SEEK_ENABLED to enabled))
        val local = JsonObject(mapOf(MobileSetupSettings.SEEK_ENABLED to disabled))
        val remote = JsonObject(base + (MobileSetupSettings.SEEK_MB to size))
        val expected = JsonObject(local + (MobileSetupSettings.SEEK_MB to size))
        assertEquals(expected, LiveTvDriveSync.merge(base, local, remote, false, emptySet()))
        assertEquals(local, LiveTvDriveSync.merge(null, local, JsonObject(emptyMap()), false, emptySet()))
        val layout = JsonObject(mapOf("compact_rows" to enabled, "tv_setting_v1_example" to enabled))
        assertTrue(SyncParts.strip(layout).isEmpty())
        assertEquals(layout, SyncParts.keep(JsonObject(emptyMap()), layout))
    }

    @Test fun existingPhoneLayoutMigratesOnceAndFutureTvImportsDoNotAdoptIt() {
        val store = LiveTvPreferencesStore()
        val raw = LiveTvKeys.compactRows
        val alias = MobileSetupSettings.LAYOUT + raw
        val prior = mapOf(raw to PrefValue.Bool(true))
        var device = emptyMap<String, PrefValue>()
        store.installPersistence({ prior }, {}, { device }, { device = it })
        assertEquals(PrefValue.Bool(true), store.rawData.value[alias])
        val tv = JsonObject(mapOf(raw to JsonObject(mapOf("t" to JsonPrimitive("b"), "v" to JsonPrimitive(false)))))
        store.importFromSync(tv)
        assertFalse(alias in store.exportForSync())
        store.installPersistence({ prior }, {}, { device }, { device = it })
        assertFalse(alias in store.exportForSync())
    }

    @Test fun invalidSeekDataCannotClearExistingPreferences() {
        val phone = phone()
        file("seek_thumbnails").edit().putBoolean("enabled", true).commit()
        val invalid = JsonObject(mapOf(MobileSetupSettings.SEEK_ENABLED to
            JsonObject(mapOf("t" to JsonPrimitive("b"), "v" to JsonPrimitive("false")))))
        assertFalse(MobileSetupSettings.hasChoices(invalid))
        assertFailsWith<IllegalArgumentException> { phone.importFromSync(invalid) }
        assertTrue(file("seek_thumbnails").getBoolean("enabled", false))
        phone.importFromSync(JsonObject(emptyMap()))
        assertTrue(file("seek_thumbnails").getBoolean("enabled", false))
    }

    @Test fun settingsCannotMoveFromADeletedProfileToItsReplacement() {
        var roster = mapOf(1 to "primary-id", 2 to "old-id")
        val store = LiveTvPreferencesStore()
        MobileSetupPreferencesAndroid(context, store) { roster }.install()
        val playback = file("nuvio_player_settings")
        playback.edit().putInt("min_buffer_ms_2", 45_000).putInt("min_buffer_ms_6", 60_000).commit()
        val saved = store.exportForSync()
        assertTrue(saved.keys.any { it.endsWith("_profile_old-id") })
        assertFalse("60000" in saved.toString())
        roster = mapOf(1 to "primary-id", 2 to "replacement-id")
        store.importFromSync(saved)
        assertFalse(playback.contains("min_buffer_ms_2"))
        assertFalse(store.exportForSync().keys.any { it.endsWith("_profile_replacement-id") })
    }

    @Test fun failedPreferenceCommitLeavesNativeChoicesAndTheOldSetupIntact() {
        var failing = false
        val store = LiveTvPreferencesStore()
        store.installPersistence({ null }, { if (failing) error("simulated storage failure") })
        MobileSetupPreferencesAndroid(context, store).install()
        file("seek_thumbnails").edit().putBoolean("enabled", false).commit()
        failing = true
        val on = JsonObject(mapOf("t" to JsonPrimitive("b"), "v" to JsonPrimitive(true)))
        val saved = JsonObject(mapOf(MobileSetupSettings.SEEK_ENABLED to on, "captions_by_default" to on))
        assertFailsWith<IllegalStateException> { store.importFromSync(saved) }
        assertFalse(file("seek_thumbnails").getBoolean("enabled", false))
        assertFalse(store.currentSettings().captionsByDefault)
        failing = false
        store.importFromSync(saved)
        assertTrue(file("seek_thumbnails").getBoolean("enabled", false))
        assertTrue(store.currentSettings().captionsByDefault)
    }
}

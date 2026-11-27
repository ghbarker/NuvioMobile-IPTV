package com.nuvio.app.features.livetv

import com.nuvio.app.features.livetv.data.LiveTvKeys
import com.nuvio.app.features.livetv.data.LiveTvPreferencesStore
import com.nuvio.app.features.livetv.data.PrefValue
import com.nuvio.app.features.livetv.data.exportForSync
import com.nuvio.app.features.livetv.data.importBackupFile
import com.nuvio.app.features.livetv.data.importFromSync
import com.nuvio.app.features.livetv.sync.SyncPart
import com.nuvio.app.features.livetv.sync.SyncParts
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnDemandGroupCleanupBackupTest {
    private fun store() = LiveTvPreferencesStore().apply { installPersistence(load = { null }, save = {}) }

    @Test fun toggleAndSharedWordsSurvivePortableBackupForAllProfiles() {
        val original = store()
        assertFalse(original.currentSettings().cleanVodGroupNames)
        assertFalse(original.rawData.value.containsKey(LiveTvKeys.cleanVodGroupNames))
        assertEquals("false", original.exportForSync()[LiveTvKeys.cleanVodGroupNames]!!.jsonObject["v"]!!.jsonPrimitive.content)
        original.updateSettings { it.copy(cleanVodGroupNames = true, nameRemovals = "UK, HD") }
        val data = original.exportForSync()
        assertEquals("true", data[LiveTvKeys.cleanVodGroupNames]!!.jsonObject["v"]!!.jsonPrimitive.content)
        assertEquals(SyncPart.BEHAVIOR, SyncParts.partOf(LiveTvKeys.cleanVodGroupNames))
        val restored = store()
        restored.importFromSync(data)
        assertTrue(restored.currentSettings().cleanVodGroupNames)
        assertEquals("UK, HD", restored.currentSettings().nameRemovals)
        restored.onProfileChanged(2)
        assertTrue(restored.currentSettings().cleanVodGroupNames)
        original.updateSettings { it.copy(cleanVodGroupNames = false) }
        restored.importFromSync(original.exportForSync())
        assertFalse(restored.currentSettings().cleanVodGroupNames)
    }

    @Test fun olderBackupWithoutOptionResetsItToOff() {
        val phone = store()
        phone.updateSettings { it.copy(cleanVodGroupNames = true) }
        phone.importFromSync(JsonObject(emptyMap()))
        assertFalse(phone.currentSettings().cleanVodGroupNames)
    }

    @Test fun changingGroupOrderDoesNotInventOrLoseManualNames() {
        val phone = store()
        phone.updateSettings { it.copy(cleanVodGroupNames = true, nameRemovals = "UK, HD") }
        val names = mapOf("movie:provider:1" to "Movies")
        phone.saveVodGroups(emptySet(), listOf("movie:provider:1"), names)
        phone.saveVodGroups(emptySet(), listOf("series:provider:2", "movie:provider:1"), phone.currentUserState().vodGroupNames)
        assertEquals(names, phone.currentUserState().vodGroupNames)
        assertEquals(PrefValue.Bool(true), phone.rawData.value[LiveTvKeys.cleanVodGroupNames])
    }

    @Test fun settingsOnlyFileCanExplicitlyTurnItOffWithoutRemovingSources() = runTest {
        val phone = store()
        phone.updateSettings { it.copy(cleanVodGroupNames = true) }
        phone.edit { it[LiveTvKeys.playlists] = PrefValue.Str("""[{"id":"local","name":"Local","url":"https://example.test/list.m3u"}]""") }
        val sources = phone.exportForSync()[LiveTvKeys.playlists]
        val data = buildJsonObject {
            put("device_settings_v1", buildJsonObject {
                put("t", "str")
                put("v", """{"version":1,"liveTv":{},"playback":{}}""")
            })
            put(LiveTvKeys.cleanVodGroupNames, store().exportForSync()[LiveTvKeys.cleanVodGroupNames]!!)
        }
        importBackupFile(data.toString(), phone) {}.getOrThrow()
        assertFalse(phone.currentSettings().cleanVodGroupNames)
        assertEquals(sources, phone.exportForSync()[LiveTvKeys.playlists])
    }
}

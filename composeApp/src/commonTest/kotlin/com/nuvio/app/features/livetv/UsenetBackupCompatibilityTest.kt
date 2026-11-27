package com.nuvio.app.features.livetv

import com.nuvio.app.features.livetv.data.LiveTvBackup
import com.nuvio.app.features.livetv.data.LiveTvKeys
import com.nuvio.app.features.livetv.data.LiveTvPreferencesStore
import com.nuvio.app.features.livetv.data.PrefValue
import com.nuvio.app.features.livetv.data.exportBackupFileText
import com.nuvio.app.features.livetv.data.exportForSync
import com.nuvio.app.features.livetv.data.importBackupFile
import com.nuvio.app.features.livetv.data.importFromSync
import com.nuvio.app.features.livetv.data.parseBackupFile
import com.nuvio.app.features.livetv.sync.LiveTvDriveSync
import com.nuvio.app.features.livetv.sync.SyncPart
import com.nuvio.app.features.livetv.sync.SyncParts
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UsenetBackupCompatibilityTest {
    private val key = "usenet_sources_v1_p3"
    private val config = """{ "providers":[{"id":"provider-1","name":"Synthetic provider","host":"news.example.test","password":"synthetic-provider-secret"}],"indexers":[{"id":"indexer-1","name":"Synthetic indexer","apiUrl":"https://indexer.example.test","apiKey":"synthetic-indexer-secret"}],"future":{"preserve":true} }"""

    private fun entry(raw: String) = buildJsonObject { put("t", "str"); put("v", raw) }
    private fun data(raw: String = config, name: String = key) = buildJsonObject { put(name, entry(raw)) }
    private val cleanupDefault = mapOf(LiveTvKeys.cleanVodGroupNames to buildJsonObject { put("t", "b"); put("v", false) })
    private fun store(save: (Map<String, PrefValue>) -> Unit = {}) =
        object : LiveTvPreferencesStore() {
            init { installPersistence(load = { null }, save = save) }
        }

    @Test fun usenetOnlyFileIsUsableAndPreservesOriginalTypedPayload() = runTest {
        val incoming = data()
        assertTrue(LiveTvBackup.syncHasSources(incoming))
        assertTrue(parseBackupFile(incoming.toString()).getOrThrow().hasSources)
        val phone = store()
        importBackupFile(incoming.toString(), phone) {}.getOrThrow()
        assertEquals(incoming[key], phone.exportForSync()[key])
        val exported = exportBackupFileText(phone, deviceId = "synthetic-phone", deviceName = "Phone", nowMs = 1L)
        assertEquals(JsonObject(incoming + cleanupDefault), parseBackupFile(exported).getOrThrow().data)
        assertTrue(LiveTvBackup.syncHasSources(data("""{"providers":[{"host":"news.example.test"}]}""")))
        assertTrue(LiveTvBackup.syncHasSources(data("""{"indexers":[{"apiUrl":"https://indexer.example.test"}]}""")))
        assertFalse(LiveTvBackup.syncHasSources(data("{}")))
    }

    @Test fun unsupportedSourcesStayOutOfCloudButRemoteCopyAndDeviceOptionsSurvive() {
        val device = entry("""{"version":1,"liveTv":{},"playback":{"transparent_letterbox":true}}""")
        val incoming = JsonObject(data() + ("device_settings_v1" to device))
        assertEquals(SyncPart.LOOK, SyncParts.partOf(key))
        assertEquals(SyncPart.LOOK, SyncParts.partOf("device_settings_v1"))
        assertEquals(JsonObject(emptyMap()), SyncParts.strip(incoming))
        assertEquals(incoming, SyncParts.keep(JsonObject(emptyMap()), incoming))
        val phone = store()
        phone.importFromSync(incoming)
        assertEquals(JsonObject(incoming + cleanupDefault), phone.exportForSync())
    }

    @Test fun malformedReservedFamilyIsRejectedBeforeReplacingExistingSettings() {
        val phone = store()
        phone.importFromSync(data())
        val before = phone.exportForSync()
        val malformed = listOf(
            data(name = "usenet_sources_v1_p0"), data(name = "usenet_sources_v1"),
            data(name = "usenet_sources_v1bad"), data(name = "usenet_sources_v1_p01"),
            data(name = "usenet_sources_v1_p999999999999999999999"),
            data("[]"), data("""{"providers":"bad"}"""), data("""{"indexers":[1]}"""),
            data("synthetic-secret-not-json"),
            buildJsonObject { put(key, buildJsonObject { put("t", "b"); put("v", true) }) },
            buildJsonObject { put(key, buildJsonObject { put("t", "str"); put("v", JsonPrimitive(1)) }) },
        )
        malformed.forEach {
            assertTrue(parseBackupFile(it.toString()).isFailure)
            assertFailsWith<IllegalArgumentException> { phone.importFromSync(it) }
            assertEquals(before, phone.exportForSync())
        }
    }

    @Test fun failedPersistenceLeavesLastSavedCredentialsExportable() {
        var fail = false
        val phone = store { if (fail) error("Synthetic protected write failure") }
        phone.importFromSync(data())
        val before = phone.exportForSync()
        fail = true
        assertFailsWith<IllegalStateException> { phone.importFromSync(data("{}")) }
        assertEquals(before, phone.exportForSync())
    }

    @Test fun settingsOnlyTvFileKeepsEveryExistingSourceAndDoesNotBecomeAnAutomaticSource() = runTest {
        val phone = store()
        phone.importFromSync(JsonObject(data() + mapOf(
            "playlists" to entry("""[{"id":"existing","name":"Local","url":"https://example.test/list.m3u"}]"""),
            "vpn_profiles_v1" to entry("synthetic-existing-vpn"),
        )))
        val before = phone.exportForSync()
        val device = entry("""{"version":1,"liveTv":{"adaptive_live_buffer":{"t":"b","v":true}},"playback":{"futureHardwareOption":true}}""")
        val incoming = buildJsonObject {
            put("device_settings_v1", device)
            put("playlists", entry("[]"))
            put("vpn_profiles_v1", entry("""{"profiles":[]}"""))
            put(key, entry("{}"))
        }
        val tvFile = buildJsonObject {
            put("format", "nuvio-plus-iptv-backup"); put("version", 1)
            put("createdAt", 123L); put("device", "Synthetic TV"); put("data", incoming)
        }
        val info = parseBackupFile(tvFile.toString()).getOrThrow()
        assertEquals(123L, info.savedAtMs)
        assertFalse(info.hasSources)
        assertFalse(LiveTvBackup.syncHasSources(incoming))
        importBackupFile(tvFile.toString(), phone) {}.getOrThrow()
        val after = phone.exportForSync()
        for (source in listOf("playlists", "vpn_profiles_v1", key)) assertEquals(before[source], after[source])
        assertEquals(device, after["device_settings_v1"])
        assertTrue(parseBackupFile(JsonObject(tvFile + ("version" to JsonPrimitive(2))).toString()).isFailure)
        assertTrue(parseBackupFile(JsonObject(incoming + ("device_settings_v1" to entry("{}"))).toString()).isFailure)
    }

    @Test fun explicitRecoveryNeedsEveryReplacementAndACompletedProtectedWrite() = runTest {
        val unavailable = PrefValue.UnavailableBackupSecret("synthetic-preserved-ciphertext")
        val initial = mapOf(key to unavailable, "playlists" to PrefValue.Str(
            """[{"id":"existing","name":"Local","url":"https://example.test/list.m3u"}]"""))
        var keyAvailable = false
        var durable: Map<String, PrefValue> = initial
        val phone = object : LiveTvPreferencesStore() {
            init {
                installPersistence(load = { initial }, save = { candidate ->
                    if (candidate[key] is PrefValue.Str && !keyAvailable) error("Synthetic key unavailable")
                    durable = candidate.toMap()
                })
            }
        }
        val before = phone.rawData.value.toMap()
        assertTrue(importBackupFile(data().toString(), phone) {}.isFailure)
        assertEquals(before, phone.rawData.value)
        assertEquals("existing", phone.currentPlaylists().single().id)
        keyAvailable = true
        val recoveryFile = JsonObject(data() + ("playlists" to entry((before["playlists"] as PrefValue.Str).value)))
        importBackupFile(recoveryFile.toString(), phone) {}.getOrThrow()
        assertEquals(config, (durable[key] as PrefValue.Str).value)
        assertEquals(data()[key], phone.exportForSync()[key])
        assertEquals("existing", phone.currentPlaylists().single().id)
    }

    @Test fun aDeviceMissingItsCopyKeepsTheCloudCopyInEveryCombine() {
        val remote = JsonObject(data() + ("playlists" to entry("[]")))
        val local = buildJsonObject { put("playlists", entry("[]")) }
        // With and without an earlier agreed copy: absence here never removes the cloud's copy.
        assertEquals(remote[key], LiveTvDriveSync.merge(remote, local, remote, false, emptySet())[key])
        assertEquals(remote[key], LiveTvDriveSync.merge(null, local, remote, true, emptySet())[key])
        assertEquals(remote[key], LiveTvDriveSync.merge(null, local, remote, false, emptySet())[key])
        // A copy only this device has is still sent up.
        assertEquals(remote[key], LiveTvDriveSync.merge(local, remote, local, false, emptySet())[key])
    }

    @Test fun anUnopenedCopyIsLeftOutOfSyncAndABackupWithoutItStillRestores() = runTest {
        val unavailable = PrefValue.UnavailableBackupSecret("synthetic-preserved-ciphertext")
        var durable: Map<String, PrefValue> = emptyMap()
        val phone = object : LiveTvPreferencesStore() {
            init {
                installPersistence(load = {
                    mapOf(key to unavailable, "playlists" to PrefValue.Str("""[{"id":"existing","name":"Local","url":"https://example.test/list.m3u"}]"""))
                }, save = { durable = it.toMap() })
            }
        }
        assertFalse(phone.exportForSync().containsKey(key))
        assertTrue(phone.exportForSync().containsKey("playlists"))
        val file = buildJsonObject { put("playlists", entry("""[{"id":"other","name":"Other","url":"https://example.test/new"}]""")) }
        importBackupFile(file.toString(), phone) {}.getOrThrow()
        assertEquals("other", phone.currentPlaylists().single().id)
        assertEquals(unavailable, phone.rawData.value[key])
        assertEquals(unavailable, durable[key])
    }
}

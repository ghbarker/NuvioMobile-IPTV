package com.nuvio.app.features.livetv

import android.content.Context
import android.content.ContextWrapper
import com.nuvio.app.features.livetv.data.LiveTvDirs
import com.nuvio.app.features.livetv.data.LiveTvFiles
import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.data.exportForSync
import com.nuvio.app.features.livetv.data.optJSONObject
import com.nuvio.app.features.livetv.data.optString
import com.nuvio.app.features.livetv.data.parseObject
import com.nuvio.app.features.livetv.sync.CloudSyncState
import com.nuvio.app.features.livetv.sync.DriveHttpResponse
import com.nuvio.app.features.livetv.sync.DriveSyncState
import com.nuvio.app.features.livetv.sync.LiveTvCloudSync
import com.nuvio.app.features.livetv.sync.LiveTvDriveHttp
import com.nuvio.app.features.livetv.sync.LiveTvDriveSync
import com.nuvio.app.features.livetv.sync.SyncParts
import com.nuvio.vpn.android.VpnBackup
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import java.io.File
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Real cache owners and Android file storage; HTTP answers and source settings stay in memory. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SyncComparisonCacheRecoveryTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var local: JsonObject
    private var sessionOwner = "account-a"
    private val applied = mutableListOf<JsonObject>()
    private val removed = setOf("removed", "removed-guide", "readded", "readded-guide")

    @Before fun setUp() {
        context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = temporary.root
        }
        context.getSharedPreferences("livetv_drive_sync", Context.MODE_PRIVATE).edit().clear().commit()
        LiveTvDriveHttp.initialize(context)
        LiveTvFiles.initialize(context)
        local = setup(listOf("local", "shared", "readded"), listOf("local-guide", "shared-guide", "readded-guide"))
        mockkObject(LiveTvPreferences, LiveTvDriveSync, LiveTvCloudSync, LiveTvDriveHttp, recordPrivateCalls = true)
        every { LiveTvCloudSync["tokenFor"](any<String>()) } answers {
            if (firstArg<String>() == sessionOwner) "token-$sessionOwner" else null
        }
        mockkStatic("com.nuvio.app.features.livetv.data.LiveTvBackupKt")
        every { LiveTvPreferences.exportForSync() } answers { local }
        every { LiveTvPreferences.removedSourceIds() } returns removed
        every { LiveTvPreferences.forgetRemovedSources(any()) } just Runs
        coEvery { LiveTvDriveSync.applyLocally(any()) } answers {
            firstArg<JsonObject>().also { applied += it; local = it }
            Unit
        }
        driveState().value = DriveSyncState()
        cloudState().value = CloudSyncState()
        setField(LiveTvCloudSync, "userId", null)
        store.putBoolean("sync_part_fresh_BEHAVIOR", true)
    }

    @After fun tearDown() {
        listOf(LiveTvDriveSync, LiveTvCloudSync).forEach {
            (field(it, "scope") as CoroutineScope).coroutineContext.cancelChildren()
        }
        unmockkAll()
    }

    @Test fun missingAndValidLegacyCachesKeepAccountBoundaries() {
        store.putString("cloud_base_user", "account-a")
        assertNull(readDrive())
        assertNull(readCloud("account-a"))
        val legacy = setup(listOf("saved"), listOf("saved-guide"))
        cache("drive").writeText(legacy.toString())
        cache("cloud").writeText(legacy.toString())
        assertEquals(legacy, readDrive())
        assertEquals(legacy, readCloud("account-a"))
        assertNull(readCloud("account-b"))
    }

    @Test fun malformedJsonAndDamagedCiphertextBecomeAbsentCaches() {
        store.putString("cloud_base_user", "account-a")
        for (damaged in listOf("not JSON", """{"usenet_sources_v1_p2":{"localEncrypted":"damaged","iv":"AAAA"}}""")) {
            cache("drive").writeText(damaged)
            cache("cloud").writeText(damaged)
            assertNull(readDrive())
            assertNull(readCloud("account-a"))
        }
    }

    @Test fun cacheKeyAndFilesystemReadFailuresDoNotEscapeTheOwners() {
        store.putString("cloud_base_user", "account-a")
        cache("drive").writeText(local.toString())
        cache("cloud").writeText(local.toString())
        mockkStatic(VpnBackup::class)
        every { VpnBackup.openCache(any<String>()) } throws IOException("Synthetic unavailable cache key")
        assertNull(readDrive())
        assertNull(readCloud("account-a"))
        mockkObject(LiveTvFiles)
        every { LiveTvFiles.readSmall(LiveTvDirs.STATE, any()) } throws IOException("Synthetic read failure")
        assertNull(readDrive())
        assertNull(readCloud("account-a"))
    }

    @Test fun firstTimeUnionHonorsRemovalsAndReaddsWithMissingSourceFamilies() {
        val remote = setup(listOf("removed", "remote"), listOf("removed-guide", "remote-guide"))
        for (preferLocal in listOf(false, true)) {
            for (emptyLocal in listOf(JsonObject(emptyMap()), setup(emptyList(), emptyList()))) {
                val merged = LiveTvDriveSync.merge(null, emptyLocal, remote, preferLocal, removed)
                assertEquals(setOf("remote"), ids(merged, "playlists"))
                assertEquals(setOf("remote-guide"), ids(merged, "epg_sources"))
            }
            val readded = setup(listOf("readded"), listOf("readded-guide"))
            val merged = LiveTvDriveSync.merge(null, readded, JsonObject(emptyMap()), preferLocal, removed)
            assertEquals(setOf("readded"), ids(merged, "playlists"))
            assertEquals(setOf("readded-guide"), ids(merged, "epg_sources"))
        }
    }

    @Test fun filesystemWriteFailureLeavesCloudBookkeepingAndInvalidationUsable() {
        store.putString("cloud_base_user", "account-a")
        for (kind in listOf("drive", "cloud")) {
            cache(kind).mkdir()
            File(cache(kind), "occupied").writeText("keep") // Also refuses deletion.
        }
        invoke(LiveTvDriveSync, "writeBase", local)
        invoke(LiveTvCloudSync, "saved", "account-a", local, removed)
        assertFalse(store.getBoolean("base_valid", true))
        assertNull(store.getString("cloud_base_user"))
        assertNull(readDrive())
        assertNull(readCloud("account-a"))
        assertCloudBookkeeping()
        verify(exactly = 1) { LiveTvPreferences.forgetRemovedSources(removed) }
    }

    @Test fun failedDriveSealingFencesAnUndeletableOldCacheAndCanRecover() {
        val old = setup(listOf("old"), emptyList())
        cache("drive").writeText(old.toString())
        refuseDeletion()
        failSealing()
        invoke(LiveTvDriveSync, "writeBase", local)
        assertEquals(old.toString(), cache("drive").readText())
        assertFalse(store.getBoolean("base_valid", true))
        LiveTvDriveHttp.initialize(context) // Reopen the persistent marker store.
        assertNull(readDrive())
        every { VpnBackup.sealCache(any<String>()) } answers { firstArg() }
        invoke(LiveTvDriveSync, "writeBase", local)
        LiveTvDriveHttp.initialize(context)
        assertTrue(store.getBoolean("base_valid", false))
        assertEquals(local, readDrive())
    }

    @Test fun failedCloudSealingNeverRelabelsAnOldAccountCacheAndCanRecover() {
        sessionOwner = "current-account"
        val old = setup(listOf("previous-account"), emptyList())
        cache("cloud").writeText(old.toString())
        store.putString("cloud_base_user", "previous-account")
        refuseDeletion()
        failSealing()
        invoke(LiveTvCloudSync, "saved", "current-account", local, removed)
        assertEquals(old.toString(), cache("cloud").readText())
        LiveTvDriveHttp.initialize(context)
        assertNull(store.getString("cloud_base_user"))
        assertNull(readCloud("previous-account"))
        assertNull(readCloud("current-account"))
        assertCloudBookkeeping()
        verify(exactly = 1) { LiveTvPreferences.forgetRemovedSources(removed) }
        every { VpnBackup.sealCache(any<String>()) } answers { firstArg() }
        invoke(LiveTvCloudSync, "saved", "current-account", local, emptySet<String>())
        LiveTvDriveHttp.initialize(context)
        assertEquals(local, readCloud("current-account"))
        assertNull(readCloud("previous-account"))
    }

    @Test fun automaticDrivePushFinishesMergingAndAcknowledgingDespiteCacheFailures() {
        prepareDrive()
        cache("drive").writeText("damaged comparison cache")
        failSealing()
        var uploaded: JsonObject? = null
        coEvery { LiveTvDriveHttp.request(any(), any(), any(), any(), any()) } answers {
            val response = when (firstArg<String>()) {
                "GET" -> envelope(remote())
                "PATCH" -> { uploaded = parseObject(arg<String>(4)).optJSONObject("data"); JsonObject(emptyMap()) }
                else -> error("Unexpected in-memory HTTP method")
            }
            DriveHttpResponse(200, response.toString())
        }
        suspendInvoke<Unit>(LiveTvDriveSync, "push", false, false)
        assertNotNull(LiveTvDriveSync.state.value.message, uploaded)
        assertMerged(checkNotNull(uploaded))
        assertMerged(applied.single())
        assertTrue(store.getLong("last_sync") > 0)
        assertTrue(store.getLong("applied_at") > 0)
        assertEquals(store.sha256(checkNotNull(uploaded).toString()), store.getString("last_hash"))
        assertFalse(LiveTvDriveSync.state.value.busy)
        assertNull(LiveTvDriveSync.state.value.message)
        assertNull(readDrive())
        verify(exactly = 1) { LiveTvPreferences.forgetRemovedSources(removed) }
    }

    @Test fun automaticDrivePullAppliesFirstTimeUnionDespiteCacheFailures() {
        prepareDrive()
        val remote = remote()
        cache("drive").writeText("damaged comparison cache")
        failSealing()
        coEvery { LiveTvDriveHttp.request(any(), any(), any(), any(), any()) } answers {
            check(firstArg<String>() == "GET")
            DriveHttpResponse(200, envelope(remote).toString())
        }
        val appliedRemote = suspendInvoke<Boolean>(LiveTvDriveSync, "pull", false, false)
        assertTrue(LiveTvDriveSync.state.value.message, appliedRemote)
        (field(LiveTvDriveSync, "scope") as CoroutineScope).coroutineContext.cancelChildren()
        assertMerged(applied.single())
        assertFalse(LiveTvDriveSync.state.value.busy)
        assertTrue(store.getLong("last_sync") > 0)
        assertEquals(42L, store.getLong("applied_at"))
        assertEquals(store.sha256(remote.toString()), store.getString("last_hash"))
        assertNull(readDrive())
    }

    @Test fun automaticCloudReconciliationFinishesMergeAndBookkeepingDespiteCacheFailures() {
        store.putString("cloud_base_user", "account-a")
        cache("cloud").writeText("damaged comparison cache")
        failSealing()
        val uploaded = mutableListOf<JsonObject>()
        cloudTransport { method, body ->
            when (method) {
                "GET" -> buildJsonObject { put("revision", 7); put("data", remote()) }
                "PUT" -> { uploaded += checkNotNull(body).optJSONObject("data")!!; buildJsonObject { put("revision", 8) } }
                else -> error("Unexpected in-memory HTTP method")
            }
        }
        suspendInvoke<Unit>(LiveTvCloudSync, "reconcileAndReport", "account-a", null)
        assertEquals(LiveTvCloudSync.state.value.message, 1, uploaded.size)
        assertMerged(uploaded.single())
        assertMerged(applied.single())
        assertCloudBookkeeping()
        assertFalse(LiveTvCloudSync.state.value.busy)
        assertEquals("Updated from your other device.", LiveTvCloudSync.state.value.message)
        assertTrue(LiveTvCloudSync.state.value.lastSyncMs > 0)
        assertNull(store.getString("cloud_base_user"))
        assertNull(readCloud("account-a"))
        verify(exactly = 1) { LiveTvPreferences.forgetRemovedSources(removed) }
    }

    @Test fun localExportFailureKeepsSourceDataAndReportsLocalSettingsWithoutTransport() {
        every { LiveTvPreferences.exportForSync() } throws IllegalStateException("Synthetic private settings unavailable")
        assertLocalPreparationFailure()
    }

    @Test fun changingAccountsDuringFetchDiscardsThePreviousUsersSetup() {
        val requests = mutableListOf<String>()
        cloudTransport { method, _ ->
            requests += method
            sessionOwner = "account-b"
            buildJsonObject { put("revision", 7); put("data", remote()) }
        }
        suspendInvoke<Unit>(LiveTvCloudSync, "reconcileAndReport", "account-a", null)
        assertEquals(listOf("GET"), requests)
        assertTrue(applied.isEmpty())
        assertEquals(0L, store.getLong("cloud_last_sync"))
        assertNull(store.getString("cloud_base_user"))
        verify(exactly = 0) { LiveTvPreferences.forgetRemovedSources(any()) }
    }

    @Test fun localHashFailureReportsLocalSettingsWithoutTransport() {
        every { LiveTvDriveHttp.sha256(any()) } throws IllegalStateException("Synthetic local digest failure")
        assertLocalPreparationFailure()
    }

    @Test fun localHashFailureAfterSuccessfulCloudWriteReportsTheLocalProblem() {
        var uploads = 0
        cloudTransport { method, body ->
            when (method) {
                "GET" -> buildJsonObject { put("revision", 7); put("data", remote()) }
                "PUT" -> {
                    assertMerged(checkNotNull(body).optJSONObject("data")!!)
                    uploads++
                    every { LiveTvDriveHttp.sha256(any()) } throws IllegalStateException("Synthetic private digest detail")
                    buildJsonObject { put("revision", 8) }
                }
                else -> error("Unexpected in-memory HTTP method")
            }
        }
        suspendInvoke<Unit>(LiveTvCloudSync, "reconcileAndReport", "account-a", null)
        assertEquals(LiveTvCloudSync.state.value.message, 1, uploads)
        assertMerged(local)
        assertMerged(applied.single())
        assertFalse(LiveTvCloudSync.state.value.busy)
        assertEquals("Couldn't prepare this phone's Live TV settings for sync. Try again.", LiveTvCloudSync.state.value.message)
        // The successful remote write/cache/timestamp are remembered; unfinished local
        // preparation must not clear fresh-part flags or acknowledge removed source ids.
        assertTrue(store.getLong("cloud_last_sync") > 0)
        assertNull(store.getString("cloud_local_hash"))
        assertTrue(store.getBoolean("sync_part_fresh_BEHAVIOR", false))
        verify(exactly = 0) { LiveTvPreferences.forgetRemovedSources(any()) }
    }

    @Test fun restorePreviousReportsLocalHashFailureAfterItsSuccessfulCloudWrite() {
        val previous = setup(listOf("previous"), listOf("previous-guide"))
        var uploads = 0
        cloudTransport { method, body ->
            when (method) {
                "GET" -> buildJsonObject { put("currentRevision", 7); put("data", previous) }
                "PUT" -> {
                    assertEquals(previous, checkNotNull(body).optJSONObject("data"))
                    uploads++
                    every { LiveTvDriveHttp.sha256(any()) } throws IllegalStateException("Synthetic private digest detail")
                    buildJsonObject { put("revision", 8) }
                }
                else -> error("Unexpected in-memory HTTP method")
            }
        }
        setField(LiveTvCloudSync, "userId", "account-a")
        LiveTvCloudSync.restorePrevious()
        runBlocking {
            (field(LiveTvCloudSync, "scope") as CoroutineScope).coroutineContext[Job]!!.children.toList().forEach { it.join() }
        }
        assertEquals(1, uploads)
        assertEquals(previous, local)
        assertEquals(previous, applied.single())
        assertFalse(LiveTvCloudSync.state.value.busy)
        assertEquals("Couldn't prepare this phone's Live TV settings for sync. Try again.", LiveTvCloudSync.state.value.message)
        assertTrue(store.getLong("cloud_last_sync") > 0)
        assertNull(store.getString("cloud_local_hash"))
        assertTrue(store.getBoolean("sync_part_fresh_BEHAVIOR", false))
        verify(exactly = 0) { LiveTvPreferences.forgetRemovedSources(any()) }
    }

    private fun assertLocalPreparationFailure() {
        coEvery { LiveTvDriveHttp.request(any(), any(), any(), any(), any()) } throws AssertionError("Unexpected Drive request")
        cloudTransport { _, _ -> throw AssertionError("Unexpected Cloud request") }
        val before = local
        suspendInvoke<Unit>(LiveTvCloudSync, "syncIfChanged")
        assertEquals("Couldn't prepare this phone's Live TV settings for sync. Try again.", LiveTvCloudSync.state.value.message)
        assertEquals(before, local)
        assertTrue(applied.isEmpty())
        coVerify(exactly = 0) { LiveTvCloudSync["callOnce"](any<String>(), any<String>(), any<String>(), matchNullable<JsonObject> { true }) }
        coVerify(exactly = 0) { LiveTvDriveHttp.request(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { LiveTvPreferences.forgetRemovedSources(any()) }
    }

    /** The private request boundary is replaced before auth or sockets; reconciliation stays real. */
    private fun cloudTransport(respond: (String, JsonObject?) -> JsonObject) {
        val reply = Class.forName("com.nuvio.app.features.livetv.sync.LiveTvCloudSync\$Reply")
            .getDeclaredConstructor(Int::class.javaPrimitiveType, JsonObject::class.java).apply { isAccessible = true }
        coEvery { LiveTvCloudSync["callOnce"](any<String>(), any<String>(), any<String>(), matchNullable<JsonObject> { true }) } answers {
            assertEquals(sessionOwner, firstArg<String>())
            reply.newInstance(200, respond(arg(1), arg(3)))
        }
    }

    private fun prepareDrive() {
        store.putString("refresh_token", "synthetic-refresh")
        store.putString("file_id", "synthetic-file")
        store.putString("device_id", "local-device")
        setField(LiveTvDriveSync, "accessToken", "synthetic-access")
        setField(LiveTvDriveSync, "accessTokenExpiresAt", Long.MAX_VALUE)
    }

    private fun failSealing() {
        mockkStatic(VpnBackup::class)
        every { VpnBackup.sealCache(any<String>()) } throws IOException("Synthetic unavailable cache key")
    }
    private fun refuseDeletion() {
        mockkObject(LiveTvFiles)
        every { LiveTvFiles.delete(LiveTvDirs.STATE, any()) } just Runs
    }
    private fun assertCloudBookkeeping() {
        assertTrue(store.getLong("cloud_last_sync") > 0)
        assertEquals(store.sha256(SyncParts.strip(local).toString()), store.getString("cloud_local_hash"))
        assertFalse(store.getBoolean("sync_part_fresh_BEHAVIOR", true))
    }
    private fun assertMerged(data: JsonObject) {
        assertEquals(setOf("local", "shared", "readded", "remote"), ids(data, "playlists"))
        assertEquals(setOf("local-guide", "shared-guide", "readded-guide", "remote-guide"), ids(data, "epg_sources"))
    }
    private fun remote() = setup(listOf("remote", "shared", "removed"), listOf("remote-guide", "shared-guide", "removed-guide"))
    private fun setup(playlists: List<String>, guides: List<String>) = buildJsonObject {
        put("playlists", entry(playlists))
        put("epg_sources", entry(guides))
    }
    private fun entry(ids: List<String>) = buildJsonObject {
        put("t", "str")
        put("v", JsonArray(ids.map { id -> buildJsonObject {
            put("id", id)
            put("name", id)
            put("url", "https://example.test/$id")
        } }).toString())
    }
    private fun ids(data: JsonObject, key: String): Set<String> = data.optJSONObject(key)?.optString("v")?.let { raw ->
        (kotlinx.serialization.json.Json.parseToJsonElement(raw) as JsonArray).map { (it as JsonObject).optString("id") }.toSet()
    }.orEmpty()
    private fun envelope(data: JsonObject) = buildJsonObject { put("data", data); put("deviceId", "other-device"); put("savedAt", 42) }
    private val store get() = LiveTvDriveHttp
    private fun cache(kind: String) = File(temporary.root, "livetv/state/${kind}_base.json").also { it.parentFile!!.mkdirs() }
    private fun readDrive() = invoke(LiveTvDriveSync, "readBase") as JsonObject?
    private fun readCloud(uid: String) = invoke(LiveTvCloudSync, "readBase", uid) as JsonObject?
    @Suppress("UNCHECKED_CAST") private fun driveState() = field(LiveTvDriveSync, "_state") as MutableStateFlow<DriveSyncState>
    @Suppress("UNCHECKED_CAST") private fun cloudState() = field(LiveTvCloudSync, "_state") as MutableStateFlow<CloudSyncState>
    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)
    private fun setField(owner: Any, name: String, value: Any?) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(owner, value)
    private fun method(owner: Any, name: String, count: Int) = owner.javaClass.declaredMethods.single { it.name == name && it.parameterCount == count }.apply { isAccessible = true }
    private fun invoke(owner: Any, name: String, vararg args: Any?): Any? = try { method(owner, name, args.size).invoke(owner, *args) }
        catch (wrapped: InvocationTargetException) { throw wrapped.targetException }
    private fun <T> suspendInvoke(owner: Any, name: String, vararg args: Any?): T = runBlocking {
        suspendCoroutineUninterceptedOrReturn<T> { continuation ->
            try { method(owner, name, args.size + 1).invoke(owner, *args, continuation) }
            catch (wrapped: InvocationTargetException) { throw wrapped.targetException }
        }
    }
}

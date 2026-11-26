package com.nuvio.app.features.livetv.record

import com.nuvio.app.features.livetv.data.LiveTvDirs
import com.nuvio.app.features.livetv.data.LiveTvFiles
import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.data.optLong
import com.nuvio.app.features.livetv.data.optString
import com.nuvio.app.features.livetv.model.LiveTvClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.concurrent.Volatile

/**
 * A shared folder on a computer or NAS (Windows file sharing, "SMB"), set up on a TV for its
 * recordings. Same fields and JSON as the TV app's. The first one set up is synced (Live TV
 * sync, Logins part) to start other devices off; after that each device keeps its own address.
 */
data class NetworkFolderConfig(
    val host: String,
    val share: String,
    val path: String = "",
    val username: String = "",
    val password: String = "",
    val domain: String = "",
) {
    /** How it's shown: \\192.168.1.20\Recordings\TV */
    val label: String get() = "\\\\" + host + "\\" + share + (if (path.isEmpty()) "" else "\\" + path.replace('/', '\\'))

    /** What's typed in the Address box. */
    val address: String get() = label

    /** The synced value (the TV app's JSON). */
    fun toJson(): String = buildJsonObject {
        put("host", host); put("share", share); put("path", path)
        put("username", username); put("password", password); put("domain", domain)
    }.toString()

    companion object {
        /**
         * Reads an address typed any common way: \\host\share\folder, //host/share/folder,
         * smb://host/share/folder or host/share/folder. Null when there's no shared folder name.
         */
        fun parseAddress(text: String): Triple<String, String, String>? {
            var t = text.trim()
            if (t.startsWith("smb://", ignoreCase = true)) t = t.substring(6)
            val parts = t.replace('\\', '/').split('/').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size < 2) return null
            return Triple(parts[0], parts[1], parts.drop(2).joinToString("/"))
        }

        /** The synced value: null for none (never set up, or removed: an empty value). */
        fun fromJson(text: String?): NetworkFolderConfig? {
            if (text.isNullOrBlank()) return null
            return runCatching {
                val o = Json.parseToJsonElement(text).jsonObject
                NetworkFolderConfig(
                    host = o.optString("host"), share = o.optString("share"), path = o.optString("path"),
                    username = o.optString("username"), password = o.optString("password"), domain = o.optString("domain"),
                ).takeIf { it.host.isNotBlank() && it.share.isNotBlank() }
            }.getOrNull()
        }
    }
}

/** What the phone needs from the device to reach the network folder (Android only; iOS has none yet). */
internal expect object NetworkLibraryPlatform {
    /** The device can open network folders. */
    val available: Boolean

    /**
     * Every recording on the folder: its id and its info.json text. Throws when the folder
     * can't be reached (away from home, the computer asleep, a wrong password).
     */
    suspend fun list(config: NetworkFolderConfig): Map<String, String>

    /** A link the player opens for recording [id] (an HLS playlist served from this phone). */
    fun playUrl(config: NetworkFolderConfig, id: String): String

    /** Deletes recording [id] from the folder: its show file(s) and its details. Throws when it can't. */
    suspend fun delete(config: NetworkFolderConfig, id: String)

    /** The folder set up changed (or went): drop the old connection; the VPN lets the new address through. */
    fun configChanged(config: NetworkFolderConfig?)

    /** A problem reaching the folder, in plain words. */
    fun describe(e: Throwable, config: NetworkFolderConfig): String

    /**
     * Checks [config] works (connects, logs in, saves and removes a small test file). Null when
     * it does, else why not in plain words. A Full app VPN lets the address through first
     * ([onVpnWait] is called while it reconnects).
     */
    suspend fun test(config: NetworkFolderConfig, onVpnWait: () -> Unit): String?

    // ── Find on network ──

    /** Looks for computers and NAS boxes with file sharing on the home network (about ten seconds). */
    fun search(): Flow<NetworkSearchState>

    /** In Full app VPN mode, lets [host] past the VPN first ([onVpnWait] is called while it reconnects). */
    suspend fun letPastVpn(host: String, onVpnWait: () -> Unit)

    /** [host]'s shared folders (guest when [username] is blank). Throws (see [describe]) when it can't. */
    suspend fun shares(host: String, username: String, password: String): List<SmbShare>

    /** The folders inside [config]'s folder, by name. Throws when it can't. */
    suspend fun folders(config: NetworkFolderConfig): List<String>

    /** Makes folder [name] inside [config]'s folder. Throws when it can't. */
    suspend fun makeFolder(config: NetworkFolderConfig, name: String)

    // ── Recording to the network folder ──

    /**
     * Copies the finished recording [rec] (its video at [localPath] on this phone) to the folder,
     * the way the TV app keeps its recordings there: one file per show in a folder named after
     * it, plus its details, playlist and join index, so the TVs and this phone list and play it.
     * Its details go last (they say the rest is complete). Returns its id there. Throws when it
     * can't (nothing half-copied is listed: a later try starts over).
     */
    suspend fun publish(config: NetworkFolderConfig, rec: LiveTvRecording, localPath: String, onProgress: (Float) -> Unit): String
}

/** A computer or NAS found on the home network that has file sharing. */
data class FoundComputer(
    /** Its IP address (what's saved in the folder's address). */
    val host: String,
    /** Its name, when it says one (like BRIAN-PC or DS920); null shows the address only. */
    val name: String? = null,
)

/** What a search has found so far. */
data class NetworkSearchState(
    val found: List<FoundComputer> = emptyList(),
    /** Still looking. */
    val searching: Boolean = true,
    /** A VPN carries this app's connections, so only devices that announce themselves can be found. */
    val vpnInTheWay: Boolean = false,
    /** The phone isn't on a home network (no Wi-Fi or Ethernet address). */
    val noNetwork: Boolean = false,
)

/** A shared folder a computer or NAS offers. */
data class SmbShare(val name: String, val remark: String = "")

/**
 * The TVs' recordings on the network folder, shown in the phone's Recordings (watch only: the
 * phone doesn't record there). Read when Recordings opens and every few minutes while it's
 * open; the last list is kept, so away from home the recordings still show, marked as
 * available at home.
 */
internal object NetworkLibrary {
    /** A network recording's [LiveTvRecording.dir] starts with this, then its id. */
    const val DIR_PREFIX = "net:"
    /** The TV app's folder for recordings' details on the network folder. */
    const val META_DIR = ".nuvio/recordings"
    private const val CACHE = "network_library.json"

    data class State(
        /** Null: no network folder is set up (on any TV). */
        val folderLabel: String? = null,
        val recordings: List<LiveTvRecording> = emptyList(),
        /** Null: not checked yet; false: couldn't be reached last time. */
        val reachable: Boolean? = null,
        val problem: String? = null,
        val checking: Boolean = false,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshLock = Mutex()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    @Volatile private var config: NetworkFolderConfig? = null
    @Volatile private var started = false
    @Volatile private var lastRefreshAt = 0L
    /**
     * Each recording's details as last read, and when they last changed / were last read
     * (kept with the saved list). A TV's recording sends new details every few seconds, so one
     * still saying "recording" that hasn't changed for [STALL_MS] while being looked at was given
     * up on by that TV (closed, asleep, off the network).
     */
    private data class Seen(val info: String, val changedAt: Long, val checkedAt: Long)
    private val seen = HashMap<String, Seen>()

    fun isNetwork(rec: LiveTvRecording): Boolean = rec.dir.startsWith(DIR_PREFIX)
    private fun idOf(rec: LiveTvRecording): String = rec.dir.removePrefix(DIR_PREFIX)

    /** Follows the synced folder (once). */
    fun start() {
        if (started || !NetworkLibraryPlatform.available) return
        started = true
        scope.launch {
            LiveTvPreferences.networkFolderJson.collect { json ->
                val c = NetworkFolderConfig.fromJson(json)
                if (c == config && _state.value.folderLabel == c?.label) return@collect
                config = c
                runCatching { NetworkLibraryPlatform.configChanged(c) }
                if (c == null) {
                    synchronized(seen) { seen.clear() }
                    _state.value = State()
                    runCatching { LiveTvFiles.delete(LiveTvDirs.STATE, CACHE) }
                } else {
                    _state.value = State(folderLabel = c.label, recordings = readCache(c.label))
                    refresh(force = true)
                }
            }
        }
    }

    /** Reads the folder again (Recordings opened, or every few minutes while it's open). */
    fun refresh(force: Boolean = false) {
        val c = config ?: return
        val now = LiveTvClock.nowMs()
        if (!force && now - lastRefreshAt < 20_000L) return
        lastRefreshAt = now
        scope.launch {
            refreshLock.withLock {
                if (config != c) return@withLock
                _state.update { it.copy(checking = true) }
                try {
                    val found = NetworkLibraryPlatform.list(c)
                    val checkedAt = LiveTvClock.nowMs()
                    synchronized(seen) {
                        seen.keys.retainAll(found.keys)
                        found.forEach { (id, text) ->
                            val before = seen[id]
                            seen[id] = Seen(text, if (before == null || before.info != text) checkedAt else before.changedAt, checkedAt)
                        }
                    }
                    val list = found.mapNotNull { (id, text) -> parse(id, text) }.sortedByDescending { it.startedAtMs }
                    if (config != c) return@withLock
                    _state.value = State(folderLabel = c.label, recordings = list, reachable = true)
                    writeCache(c.label, found)
                    // Reachable again: recordings waiting on this phone go across.
                    NetworkUploads.kick()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    if (config != c) return@withLock
                    _state.update { it.copy(reachable = false, problem = runCatching { NetworkLibraryPlatform.describe(e, c) }.getOrNull(), checking = false) }
                }
            }
        }
    }

    /** The link the player opens. */
    fun playUrl(rec: LiveTvRecording): String {
        val c = config ?: return ""
        return NetworkLibraryPlatform.playUrl(c, idOf(rec))
    }

    /** Deletes a finished recording from the network folder (so from every TV). Null when it worked, else why not. */
    private suspend fun delete(rec: LiveTvRecording, folderLabel: String): String? {
        val c = config ?: return "No network folder is set up"
        val listing = state.value
        if (c.label != folderLabel || listing.folderLabel != folderLabel || !isNetwork(rec)) return "The network folder has changed"
        val listed = listing.recordings.firstOrNull { it.dir == rec.dir } ?: return "This recording is no longer listed"
        if (listed.isRecording) return "It's still recording on a TV. Delete it once it has finished"
        return try {
            NetworkLibraryPlatform.delete(c, idOf(listed))
            _state.update { s -> if (s.folderLabel == folderLabel) s.copy(recordings = s.recordings.filter { it.id != listed.id }) else s }
            refresh(force = true)
            null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            runCatching { NetworkLibraryPlatform.describe(e, c) }.getOrDefault("The network folder couldn't be reached")
        }
    }

    /**
     * Saves the folder set up on this phone (null removes it), for this phone only: a TV's
     * address never replaces it, and it never replaces a TV's (the first one saved anywhere
     * still starts new devices off).
     */
    fun save(c: NetworkFolderConfig?) {
        LiveTvPreferences.setNetworkFolderJson(c?.toJson() ?: "")
        // No folder any more: new recordings stay on this phone.
        if (c == null && LiveTvPreferences.currentSettings().recordingStorage == NetworkUploads.STORAGE_ID) {
            LiveTvPreferences.updateSettings { it.copy(recordingStorage = "") }
        }
        start()
        if (c != null) NetworkUploads.kick(force = true)
    }

    /** The set-up screen closed without saving: the VPN goes back to the saved folder's address. */
    fun restoreVpn() {
        runCatching { NetworkLibraryPlatform.configChanged(NetworkFolderConfig.fromJson(LiveTvPreferences.currentNetworkFolderJson())) }
    }

    /** [delete] in the background (the sheet asking closes straight away), saying how it went. */
    fun deleteAndTell(rec: LiveTvRecording, folderLabel: String) {
        scope.launch {
            val problem = delete(rec, folderLabel)
            com.nuvio.app.core.ui.NuvioToastController.show(problem ?: "Deleted from the network folder")
        }
    }

    private fun parse(id: String, text: String): LiveTvRecording? = runCatching {
        LiveTvRecording.fromJson(Json.parseToJsonElement(text).jsonObject, DIR_PREFIX + id)
            ?.copy(id = id, fileName = "index.m3u8")
            ?.let(::givenUpOn)
    }.getOrNull()

    /**
     * A TV's recording that stopped being updated before it finished shows as ended, so it can
     * be watched as far as it got, or deleted. If that TV carries on, its next details bring it
     * back as recording.
     */
    private fun givenUpOn(rec: LiveTvRecording): LiveTvRecording {
        if (!rec.isRecording) return rec
        val s = synchronized(seen) { seen[rec.id] } ?: return rec
        if (s.checkedAt - s.changedAt < STALL_MS) return rec
        return rec.copy(
            status = RecordingStatus.DONE,
            endedAtMs = rec.endedAtMs?.takeIf { it > 0L } ?: s.changedAt,
            problem = "The TV stopped sending this before it finished, so it may stop early",
        )
    }

    /** A recording whose details haven't changed this long is taken as given up on. */
    private const val STALL_MS = 10L * 60_000

    // ---- the last list read, for when the folder can't be reached

    private fun readCache(label: String): List<LiveTvRecording> = runCatching {
        val o = Json.parseToJsonElement(LiveTvFiles.readSmall(LiveTvDirs.STATE, CACHE) ?: return emptyList()).jsonObject
        if (o.optString("folder") != label) return emptyList()
        val items = o["items"]!!.jsonArray.map { it.jsonObject }
        synchronized(seen) {
            items.forEach { item ->
                val id = item.optString("id")
                val changedAt = item.optLong("changedAt")
                if (id.isNotEmpty() && changedAt > 0L && id !in seen)
                    seen[id] = Seen(item.optString("info"), changedAt, item.optLong("checkedAt", changedAt))
            }
        }
        items.mapNotNull { item -> parse(item.optString("id"), item.optString("info")) }
            .sortedByDescending { it.startedAtMs }
    }.getOrDefault(emptyList())

    private fun writeCache(label: String, found: Map<String, String>) {
        runCatching {
            val o: JsonObject = buildJsonObject {
                put("folder", label)
                put("items", buildJsonArray {
                    found.forEach { (id, info) ->
                        val s = synchronized(seen) { seen[id] }
                        add(buildJsonObject {
                            put("id", id); put("info", info)
                            if (s != null) { put("changedAt", s.changedAt); put("checkedAt", s.checkedAt) }
                        })
                    }
                })
            }
            LiveTvFiles.writeSmall(LiveTvDirs.STATE, CACHE, o.toString())
        }
    }
}

/**
 * Recordings made on this phone with Storage set to the network folder: recorded here as usual,
 * then copied to the network folder once they finish (the TVs and this phone then play them
 * from there) and removed from the phone. One that can't be copied yet (away from home, the
 * computer asleep) waits on the phone, still playable here, and is tried again later.
 */
internal object NetworkUploads {
    /** In a recording's folder on the phone: it goes to the network folder once it finishes. */
    const val MARKER = "network.pending"

    data class State(
        /** The recording being copied, and how far (0..1). */
        val copyingId: String? = null,
        val progress: Float = 0f,
        /** Why the last try didn't work, in plain words (null: it did, or none yet). */
        val problem: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    @Volatile private var lastTryAt = 0L

    /** Copying (or about to): keeps the app running in the background meanwhile. */
    val busy: Boolean get() = _state.value.copyingId != null

    /** Recordings started now go to the network folder (Storage is set to it and one is set up). */
    fun wanted(): Boolean =
        NetworkLibraryPlatform.available &&
            LiveTvPreferences.currentSettings().recordingStorage == STORAGE_ID &&
            NetworkFolderConfig.fromJson(LiveTvPreferences.currentNetworkFolderJson()) != null

    fun mark(dir: String) { runCatching { RecordingPlatform.writeText("$dir/$MARKER", "") } }

    fun isPending(rec: LiveTvRecording): Boolean =
        !NetworkLibrary.isNetwork(rec) && rec.status == RecordingStatus.DONE && runCatching { RecordingPlatform.exists(rec.dir + "/" + MARKER) }.getOrDefault(false)

    /** A recording finished: copy it across if it's one for the network folder. */
    fun finished(rec: LiveTvRecording) {
        if (isPending(rec)) kick(force = true)
    }

    /**
     * Copies every finished recording waiting for the network folder, one at a time (the app
     * opening, Recordings opening, a recording finishing). Not more than every minute unless [force].
     */
    fun kick(force: Boolean = false) {
        if (!NetworkLibraryPlatform.available) return
        val now = LiveTvClock.nowMs()
        if (!force && now - lastTryAt < 60_000L) return
        lastTryAt = now
        scope.launch {
            if (lock.isLocked) return@launch
            lock.withLock { copyAll() }
        }
    }

    private suspend fun copyAll() {
        while (true) {
            val config = NetworkFolderConfig.fromJson(LiveTvPreferences.currentNetworkFolderJson()) ?: return
            // (One with nothing in it just stays on the phone.)
            val rec = LiveTvRecorder.recordings.value
                .filter { isPending(it) && RecordingPlatform.fileSize(LiveTvRecorder.filePath(it)) > 0L }
                .minByOrNull { it.startedAtMs } ?: return
            val path = LiveTvRecorder.filePath(rec)
            _state.value = State(copyingId = rec.id, progress = 0f)
            RecordingPlatform.keepAlive(true)
            try {
                NetworkLibraryPlatform.publish(config, rec, path) { p -> _state.update { it.copy(progress = p) } }
                com.nuvio.app.features.livetv.model.LiveTvLoadReport.add("Recording \"${rec.title}\": copied to the network folder")
                // On the network folder now (listed from there): off the phone.
                LiveTvRecorder.removeCopied(rec.id)
                _state.value = State()
                NetworkLibrary.refresh(force = true)
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.value = State()
                throw e
            } catch (e: Throwable) {
                val why = runCatching { NetworkLibraryPlatform.describe(e, config) }.getOrDefault("The network folder couldn't be reached")
                com.nuvio.app.features.livetv.model.LiveTvLoadReport.add("Recording \"${rec.title}\": not copied to the network folder yet ($why)")
                _state.value = State(problem = why)
                return
            } finally {
                LiveTvRecorder.stopServiceIfIdle()
            }
        }
    }

    /** The "Save recordings to" choice for the network folder (the TV app's id). */
    const val STORAGE_ID = "network"
}


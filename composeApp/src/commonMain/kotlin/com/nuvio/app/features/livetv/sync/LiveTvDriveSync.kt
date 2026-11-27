package com.nuvio.app.features.livetv.sync

import com.nuvio.app.features.livetv.data.LiveTvBackup
import com.nuvio.app.features.livetv.data.LiveTvBackupApply
import com.nuvio.app.features.livetv.data.LiveTvDirs
import com.nuvio.app.features.livetv.data.LiveTvFiles
import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.data.LiveTvRepository
import com.nuvio.app.features.livetv.data.MobileSetupSettings
import com.nuvio.app.features.livetv.data.UsenetBackupCompatibility
import com.nuvio.app.features.livetv.data.asString
import com.nuvio.app.features.livetv.data.exportForSync
import com.nuvio.app.features.livetv.data.liveTvJson
import com.nuvio.app.features.livetv.data.optJSONArray
import com.nuvio.app.features.livetv.data.optJSONObject
import com.nuvio.app.features.livetv.data.optLong
import com.nuvio.app.features.livetv.data.optString
import com.nuvio.app.features.livetv.data.parseObject
import com.nuvio.app.features.livetv.data.syncHasSources
import com.nuvio.app.features.livetv.model.LiveTvClock
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** What the settings screen shows. */
data class DriveSyncState(
    val available: Boolean = false,
    val connected: Boolean = false,
    val email: String? = null,
    val autoSync: Boolean = true,
    val busy: Boolean = false,
    val message: String? = null,
    val lastSyncMs: Long = 0L,
    /** Set while signing in: show this code and address to the user. */
    val signIn: SignInPrompt? = null,
)

/** [qrUrl] is a link with the code filled in, when Google gives one. */
data class SignInPrompt(val userCode: String, val url: String, val qrUrl: String, val expiresAtMs: Long)

/**
 * Backs up and syncs the Live TV setup through the user's own Google Drive, the TV app's way:
 * the data goes in Drive's hidden app data folder, sign-in uses Google's device-code flow
 * (a code approved in a browser), changes are backed up shortly after they happen, and a newer
 * copy from another device is merged in at start-up or on Restore. Three-way merging keeps
 * what changed here since the last sync and takes everything else from the other device.
 */
internal object LiveTvDriveSync {
    private val prefs = LiveTvPreferences
    private val repository = LiveTvRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val store = LiveTvDriveHttp

    private val clientId = LiveTvDriveConfig.CLIENT_ID
    private val clientSecret = LiveTvDriveConfig.CLIENT_SECRET

    private val _state = MutableStateFlow(DriveSyncState())
    val state: StateFlow<DriveSyncState> = _state.asStateFlow()

    private var started = false
    private var signInJob: Job? = null
    private var accessToken: String? = null
    private var accessTokenExpiresAt = 0L
    private var applying = false
    private var appliedAtMs = 0L
    private var emptyRefused = false

    /** The copy in Google Drive: when it was saved, on which device, and whether that was this one. */
    data class BackupInfo(val savedAtMs: Long, val device: String, val fromThisDevice: Boolean)

    /** This device's id in backups it writes (Drive and exported files). */
    @OptIn(ExperimentalUuidApi::class)
    val deviceId: String
        get() = store.getString(KEY_DEVICE_ID) ?: Uuid.random().toString().also { store.putString(KEY_DEVICE_ID, it) }

    private fun loadState() = DriveSyncState(
        available = clientId.isNotBlank(),
        connected = store.getString(KEY_REFRESH) != null,
        email = store.getString(KEY_EMAIL),
        autoSync = store.getBoolean(KEY_AUTO, true),
        lastSyncMs = store.getLong(KEY_LAST_SYNC)
    )

    /** Starts automatic sync: check Drive for a newer setup, then back up local changes. */
    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        _state.value = loadState()
        scope.launch {
            if (_state.value.connected && _state.value.autoSync) runCatching { pull(force = false) }
        }
        scope.launch {
            prefs.changes.drop(1).collect {
                val now = LiveTvClock.nowMs()
                if (!applying && now - appliedAtMs > 20_000) store.putLong(KEY_LOCAL_CHANGED_AT, now)
            }
        }
        scope.launch {
            prefs.changes.drop(1).debounce(8_000).collect {
                val s = _state.value
                if (s.connected && s.autoSync && !applying) runCatching { push(force = false) }
            }
        }
    }

    /** When this device's setup last changed here (0: not since this version). */
    fun localChangedAt(): Long = store.getLong(KEY_LOCAL_CHANGED_AT)

    /** About the saved copy, for the restore prompt (null: none, or Drive couldn't be reached). */
    suspend fun backupInfo(): BackupInfo? = runCatching {
        val token = token() ?: return@runCatching null
        val json = remoteCopy(token) ?: return@runCatching null
        BackupInfo(json.optLong("savedAt"), json.optString("device").ifBlank { "another device" }, json.optString("deviceId") == deviceId)
    }.getOrNull()

    // ------------------------------------------------------------------ sign-in

    fun beginSignIn() {
        if (!_state.value.available) return
        signInJob?.cancel()
        signInJob = scope.launch {
            _state.value = _state.value.copy(busy = true, message = null, signIn = null)
            var codes = JsonObject(emptyMap())
            var mode = MODE_APPDATA
            for ((scopes, m) in SCOPE_ATTEMPTS) {
                codes = runCatching {
                    postJson("https://oauth2.googleapis.com/device/code", mapOf("client_id" to clientId, "scope" to scopes))
                }.getOrElse {
                    _state.value = _state.value.copy(busy = false, message = "Couldn't reach Google. Try again.")
                    return@launch
                }
                mode = m
                if (codes.optString("error") != "invalid_scope") break
            }
            val deviceCode = codes.optString("device_code")
            val userCode = codes.optString("user_code")
            if (deviceCode.isBlank() || userCode.isBlank()) {
                _state.value = _state.value.copy(busy = false, message = googleError(codes))
                return@launch
            }
            val url = codes.optString("verification_url").ifBlank { "https://www.google.com/device" }
            val qrUrl = codes.optString("verification_uri_complete").ifBlank { url }
            val expiresAt = LiveTvClock.nowMs() + codes.optLong("expires_in", 1800) * 1000
            var interval = codes.optLong("interval", 5).coerceAtLeast(5)
            _state.value = _state.value.copy(busy = false, signIn = SignInPrompt(userCode, url, qrUrl, expiresAt))

            while (LiveTvClock.nowMs() < expiresAt) {
                delay(interval * 1000)
                val token = runCatching {
                    postJson(
                        "https://oauth2.googleapis.com/token",
                        mapOf(
                            "client_id" to clientId,
                            "client_secret" to clientSecret,
                            "device_code" to deviceCode,
                            "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"
                        )
                    )
                }.getOrNull() ?: continue
                when (token.optString("error")) {
                    "" -> {
                        store.putString(KEY_MODE, mode)
                        onSignedIn(token)
                        return@launch
                    }
                    "authorization_pending" -> Unit
                    "slow_down" -> interval += 5
                    "access_denied" -> {
                        _state.value = _state.value.copy(signIn = null, message = "Sign-in was declined.")
                        return@launch
                    }
                    else -> {
                        _state.value = _state.value.copy(signIn = null, message = googleError(token))
                        return@launch
                    }
                }
            }
            _state.value = _state.value.copy(signIn = null, message = "The code expired. Try again.")
        }
    }

    fun cancelSignIn() {
        signInJob?.cancel()
        _state.value = _state.value.copy(signIn = null, busy = false)
    }

    private suspend fun onSignedIn(token: JsonObject) {
        val refresh = token.optString("refresh_token")
        if (refresh.isBlank()) {
            _state.value = _state.value.copy(signIn = null, message = "Google didn't return a sign-in. Try again.")
            return
        }
        accessToken = token.optString("access_token")
        accessTokenExpiresAt = LiveTvClock.nowMs() + token.optLong("expires_in", 3600) * 1000
        val email = emailFromIdToken(token.optString("id_token"))
        store.putString(KEY_REFRESH, refresh)
        store.putString(KEY_EMAIL, email)
        _state.value = _state.value.copy(connected = true, email = email, signIn = null, message = null)
        // First connection: use the Drive copy if there is one, otherwise back this device up.
        if (!pull(force = true, onlyIfExists = true)) push(force = true)
    }

    fun disconnect() {
        val refresh = store.getString(KEY_REFRESH)
        listOf(KEY_REFRESH, KEY_EMAIL, KEY_FILE_ID, KEY_LAST_HASH, KEY_MODE).forEach { store.putString(it, null) }
        LiveTvFiles.delete(LiveTvDirs.STATE, BASE_FILE)
        accessToken = null
        _state.value = _state.value.copy(connected = false, email = null, message = "Signed out. Your backup stays in your Google Drive.")
        if (refresh != null) scope.launch {
            runCatching { store.postForm("https://oauth2.googleapis.com/revoke", mapOf("token" to refresh)) }
        }
    }

    fun setAutoSync(enabled: Boolean) {
        store.putBoolean(KEY_AUTO, enabled)
        _state.value = _state.value.copy(autoSync = enabled)
    }

    fun backUpNow() { scope.launch { runCatching { push(force = true) } } }
    fun restoreNow() { scope.launch { runCatching { pull(force = true) } } }

    /**
     * After a backup file was imported: when this device syncs automatically, send the new setup
     * to Drive right away so the saved copy matches it. Does nothing when signed out or sync is off.
     */
    fun backUpAfterImport() {
        val s = _state.value
        if (s.connected && s.autoSync) scope.launch { runCatching { push(force = true, replaceRemote = true) } }
    }

    // ------------------------------------------------------------------ backup / restore

    /** [replaceRemote]: the Drive copy is overwritten as is, without merging in what it has. */
    private suspend fun push(force: Boolean, replaceRemote: Boolean = false) = mutex.withLock {
        val token = token() ?: return@withLock
        emptyRefused = false
        _state.value = _state.value.copy(busy = true, message = if (force) "Backing up…" else null)
        val result = runCatching {
            var data = prefs.exportForSync()
            val removedHere = prefs.removedSourceIds()
            val hash0 = store.sha256(data.toString())
            if (!force && hash0 == store.getString(KEY_LAST_HASH)) return@runCatching false
            // Another device saved since we last synced: combine its changes with ours first.
            if (!replaceRemote) remoteCopy(token)?.let { remote ->
                val remoteData = remote.optJSONObject("data")
                val base = readBase()
                if (remoteData != null && remoteData.toString() != base?.toString()) {
                    val merged = merge(base, local = data, remote = remoteData, localWinsWithoutBase = true, removedHere = removedHere)
                    if (merged.toString() != data.toString()) applyLocally(merged)
                    data = merged
                }
            }
            // No playlists and no guides here: never write that over the backup.
            if (!prefs.syncHasSources(data)) {
                if (force) emptyRefused = true
                return@runCatching false
            }
            val hash = store.sha256(data.toString())
            val now = LiveTvClock.nowMs()
            val body = LiveTvBackup.envelope(data, now, deviceId, LiveTvFiles.deviceName).toString()
            val fileId = fileId(token)
            if (fileId == null) {
                val meta = buildJsonObject {
                    put("name", FILE_NAME)
                    if (isAppDataMode) put("parents", buildJsonArray { add(JsonPrimitive("appDataFolder")) })
                }
                val boundary = "nuvio_livetv_boundary"
                val multipart = buildString {
                    append("--").append(boundary).append("\r\n")
                    append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                    append(meta.toString()).append("\r\n")
                    append("--").append(boundary).append("\r\n")
                    append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                    append(body).append("\r\n")
                    append("--").append(boundary).append("--")
                }
                val created = execute(
                    "POST",
                    "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id",
                    token,
                    contentType = "multipart/related; boundary=$boundary",
                    body = multipart
                )
                store.putString(KEY_FILE_ID, parseObject(created).optString("id"))
            } else {
                execute("PATCH", "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media", token, contentType = JSON, body = body)
            }
            store.putString(KEY_LAST_HASH, hash)
            store.putLong(KEY_LAST_SYNC, now)
            store.putLong(KEY_APPLIED_AT, now)
            writeBase(data)
            prefs.forgetRemovedSources(removedHere)
            true
        }
        _state.value = _state.value.copy(
            busy = false,
            lastSyncMs = store.getLong(KEY_LAST_SYNC),
            message = when {
                result.isFailure -> "Backup failed: ${result.exceptionOrNull()?.message ?: "network error"}"
                emptyRefused -> "Nothing backed up: add a playlist or guide first. Your saved copy in Google Drive was left as it was."
                force -> "Backed up to Google Drive."
                else -> _state.value.message
            }
        )
    }

    /**
     * Applies the Drive copy if it's newer than what this device has (or always, when [force]).
     * Returns true if a copy was found and applied.
     */
    private suspend fun pull(force: Boolean, onlyIfExists: Boolean = false): Boolean = mutex.withLock {
        val token = token() ?: return@withLock false
        _state.value = _state.value.copy(busy = true, message = if (force && !onlyIfExists) "Restoring…" else null)
        val result = runCatching {
            val json = remoteCopy(token) ?: return@runCatching null
            val savedAt = json.optLong("savedAt")
            val fromThisDevice = json.optString("deviceId") == deviceId
            val remoteData = json.optJSONObject("data") ?: return@runCatching false
            val base = readBase()
            if (!force && (fromThisDevice || remoteData.toString() == base?.toString())) return@runCatching false
            // A saved copy with no playlists or guides: never let it empty this device.
            if (!prefs.syncHasSources(remoteData)) return@runCatching null
            val data = if (force) remoteData
            else merge(base, local = prefs.exportForSync(), remote = remoteData, localWinsWithoutBase = false, removedHere = prefs.removedSourceIds())
            applyLocally(data)
            store.putLong(KEY_APPLIED_AT, savedAt)
            store.putLong(KEY_LAST_SYNC, LiveTvClock.nowMs())
            store.putString(KEY_LAST_HASH, store.sha256(remoteData.toString()))
            writeBase(remoteData)
            // Our own changes were kept on top: send the combined setup back up.
            if (data.toString() != remoteData.toString()) scope.launch { delay(2_000); runCatching { push(force = false) } }
            true
        }
        val applied = result.getOrNull() == true
        _state.value = _state.value.copy(
            busy = false,
            lastSyncMs = store.getLong(KEY_LAST_SYNC),
            message = when {
                result.isFailure -> "Restore failed: ${result.exceptionOrNull()?.message ?: "network error"}"
                result.getOrNull() == null && force && !onlyIfExists -> "No backup found in Google Drive yet."
                applied && force -> "Restored from Google Drive."
                applied -> "Updated from your other device."
                else -> _state.value.message
            }
        )
        applied
    }

    // ------------------------------------------------------------------ merging

    internal suspend fun applyLocally(data: JsonObject) {
        applying = true
        appliedAtMs = LiveTvClock.nowMs()
        try {
            // The same import and reload as Import backup file (see LiveTvBackupApply).
            val playlistsBefore = prefs.currentPlaylists()
            val applied = LiveTvBackupApply.apply(data, prefs, repository::reloadAfterSync)
            com.nuvio.app.features.livetv.model.PlaylistServerChanges.lines(playlistsBefore, prefs.currentPlaylists())
                .forEach { com.nuvio.app.features.livetv.model.LiveTvLoadReport.add(it) }
            com.nuvio.app.features.livetv.model.LiveTvLoadReport.add(if (applied.changedAnything) "Sync: applied a saved setup to this phone" else "Sync: the saved setup is the same as this phone's; nothing to reload")
            if (!applied.changedAnything) return
            delay(500)
        } finally {
            applying = false
            appliedAtMs = LiveTvClock.nowMs()
        }
    }

    private suspend fun remoteCopy(token: String): JsonObject? = runCatching {
        val fileId = fileId(token) ?: return null
        parseObject(execute("GET", "https://www.googleapis.com/drive/v3/files/$fileId?alt=media", token))
    }.getOrNull()

    /**
     * Combines two setups setting by setting: whatever this device changed since the last sync
     * ([base]) is kept, everything else comes from the other device. Without a base,
     * [localWinsWithoutBase] decides, but playlists and guides from both are kept.
     */
    internal fun merge(
        base: JsonObject?,
        local: JsonObject,
        remote: JsonObject,
        localWinsWithoutBase: Boolean,
        removedHere: Set<String>,
    ): JsonObject {
        if (base == null) {
            val (first, second) = if (localWinsWithoutBase) local to remote else remote to local
            val out = first.toMutableMap()
            // A device that lacks TV source settings (it can't open its copy, or never had them)
            // must not erase the copy the other side holds on the first combine.
            second.forEach { (key, value) ->
                if ((UsenetBackupCompatibility.isKey(key) || MobileSetupSettings.isPortableKey(key)) && key !in out)
                    out[key] = value
            }
            for (k in SOURCE_LISTS) {
                val a = first.optJSONObject(k)
                val b = second.optJSONObject(k)
                val shell = a ?: b ?: continue
                runCatching { unionSources(a?.optString("v") ?: "[]", b?.optString("v") ?: "[]", removedHere, local.optJSONObject(k)?.optString("v")) }.getOrNull()
                    ?.let { out[k] = withValue(shell, it) }
            }
            return JsonObject(out)
        }
        val out = remote.toMutableMap()
        val keys = (local.keys + base.keys).toSet()
        keys.forEach { k ->
            if (UsenetBackupCompatibility.isKey(k) || MobileSetupSettings.isPortableKey(k)) {
                // As on the TV: absence alone is never a deletion (the phone only carries these, and
                // may have lost the key to its copy). An explicit empty configuration still resets them.
                if (!local.containsKey(k)) return@forEach
                if (!remote.containsKey(k)) { out[k] = local.getValue(k); return@forEach }
            }
            val l = local[k]?.toString()
            val b = base[k]?.toString()
            if (l != b) {
                val listMerged = if (k in SOURCE_LISTS && (local.containsKey(k) || remote.containsKey(k))) {
                    runCatching {
                        mergeSources(
                            base.optJSONObject(k)?.optString("v"),
                            local.optJSONObject(k)?.optString("v") ?: "[]",
                            remote.optJSONObject(k)?.optString("v") ?: "[]",
                            removedHere
                        )
                    }.getOrNull()
                } else null
                val shell = local.optJSONObject(k) ?: remote.optJSONObject(k)
                // Favorites, hidden channels and groups, your own groups: combined item by item, so
                // a favorite added on the phone and another added on a TV are both kept.
                val itemsMerged = if (listMerged == null) runCatching {
                    mergeItems(k, base.optJSONObject(k), local.optJSONObject(k), remote.optJSONObject(k))
                }.getOrNull() else null
                when {
                    itemsMerged != null -> out[k] = itemsMerged
                    listMerged != null && shell != null -> out[k] = withValue(shell, listMerged)
                    local.containsKey(k) -> out[k] = local.getValue(k)
                    else -> out.remove(k)
                }
            }
        }
        return JsonObject(out)
    }

    private fun withValue(shell: JsonObject, v: String): JsonObject = JsonObject(shell.toMutableMap().apply { this["v"] = JsonPrimitive(v) })

    /**
     * Three-way merge of two source lists (JSON arrays of objects with an "id"): sources added
     * or removed on either device are added or removed, and within a source each setting changed
     * here since the last sync is kept, everything else comes from the other device.
     */
    internal fun mergeSources(baseRaw: String?, localRaw: String, remoteRaw: String, removedHere: Set<String>): String {
        val base = readSources(baseRaw)
        val local = readSources(localRaw)
        val remote = readSources(remoteRaw)
        val result = ArrayList<JsonElement>()
        for ((id, r) in remote) {
            val l = local[id]
            val b = base[id]
            when {
                l == null && id in removedHere -> continue
                l == null -> result += r
                b == null -> result += r
                else -> {
                    val merged = r.toMutableMap()
                    l.forEach { (f, v) -> if (v.toString() != b[f]?.toString()) merged[f] = v }
                    // A setting cleared on this device since the last sync. Some are only saved while
                    // they're set (a playlist's switched-off guides: turning the last one back on
                    // leaves nothing saved), so the other device's old value kept coming back.
                    b.keys.forEach { f -> if (f !in l) merged.remove(f) }
                    result += JsonObject(merged)
                }
            }
        }
        for ((id, l) in local) {
            if (id !in remote && id !in base) result += l
        }
        // Moved on this device since the last sync: that order is kept (it used to be dropped, so
        // a reorder came back undone and was never saved). Sources only the other device has follow after.
        val baseOrder = base.keys.filter { it in local }
        val localOrder = local.keys.filter { it in base }
        if (baseOrder != localOrder) {
            val byId = LinkedHashMap<String, JsonElement>()
            result.forEach { e -> byId[(e as JsonObject).optString("id")] = e }
            val ordered = ArrayList<JsonElement>()
            local.keys.forEach { id -> byId.remove(id)?.let { ordered += it } }
            ordered += byId.values
            return JsonArray(ordered).toString()
        }
        return JsonArray(result).toString()
    }

    /**
     * Three-way merge of one of the item lists in [ITEM_KEYS] (per profile too, "favorites_p2"):
     * items added or removed on this device since the last sync are added or removed, everything
     * else comes from the other device. Null (whole-value rules apply) for any other key, or when
     * the other device has no value for it.
     */
    private fun mergeItems(k: String, b: JsonObject?, l: JsonObject?, r: JsonObject?): JsonObject? {
        val name = k.replace(PROFILE_SUFFIX, "")
        if (name !in ITEM_KEYS || r == null) return null
        val shell = (l ?: r).toMutableMap()
        fun strArray(list: List<String>) = JsonArray(list.map { JsonPrimitive(it) })
        fun arr(e: JsonElement?): List<String> = (e as? JsonArray)?.map { it.asString() }.orEmpty()
        fun parsed(e: JsonObject?): JsonElement? = e?.optString("v")?.takeIf { it.isNotBlank() }?.let { liveTvJson.parseToJsonElement(it) }
        when (name) {
            in SET_ITEM_KEYS -> {
                shell["t"] = JsonPrimitive("s")
                shell["v"] = strArray(mergeList(arr(b?.get("v")), arr(l?.get("v")), arr(r["v"])))
            }
            "favorites" -> shell["v"] = JsonPrimitive(strArray(mergeList(arr(parsed(b)), arr(parsed(l)), arr(parsed(r)))).toString())
            "channel_copies" -> {
                fun map(e: JsonObject?): Map<String, List<String>> =
                    (parsed(e) as? JsonObject)?.mapValues { (_, v) -> arr(v) }.orEmpty()
                val bm = map(b); val lm = map(l); val rm = map(r)
                val out = LinkedHashMap<String, JsonElement>()
                mergeList(bm.keys.toList(), lm.keys.toList(), rm.keys.toList()).forEach { g ->
                    val v = mergeList(bm[g].orEmpty(), lm[g].orEmpty(), rm[g].orEmpty())
                    if (v.isNotEmpty()) out[g] = strArray(v)
                }
                shell["v"] = JsonPrimitive(JsonObject(out).toString())
            }
            "custom_groups" -> {
                fun groups(e: JsonObject?) = readSources(e?.optString("v"))
                fun keys(o: JsonObject?) = arr(o?.get("keys"))
                val bg = groups(b); val lg = groups(l); val rg = groups(r)
                val out = ArrayList<JsonElement>()
                mergeList(bg.keys.toList(), lg.keys.toList(), rg.keys.toList()).forEach { id ->
                    val lo = lg[id]; val bo = bg[id]; val ro = rg[id]
                    val g = (ro ?: lo)!!.toMutableMap()
                    if (lo != null && bo != null && ro != null) {
                        if (lo.optString("name") != bo.optString("name")) g["name"] = JsonPrimitive(lo.optString("name"))
                        g["keys"] = strArray(mergeList(keys(bo), keys(lo), keys(ro)))
                    }
                    out += JsonObject(g)
                }
                shell["v"] = JsonPrimitive(JsonArray(out).toString())
            }
            else -> return null
        }
        return JsonObject(shell)
    }

    /**
     * [remote] without what this device removed since [base], plus what it added. If this device
     * also reordered the items, its order is kept (the other device's additions go after).
     */
    internal fun mergeList(base: List<String>, local: List<String>, remote: List<String>): List<String> {
        val baseSet = base.toHashSet(); val localSet = local.toHashSet()
        val removedHere = base.filterTo(HashSet()) { it !in localSet }
        val kept = LinkedHashSet<String>()
        remote.forEach { if (it !in removedHere) kept += it }
        local.forEach { if (it !in baseSet) kept += it }
        val movedHere = base.filter { it in localSet } != local.filter { it in baseSet }
        if (!movedHere) return kept.toList()
        val out = LinkedHashSet<String>()
        local.forEach { if (it in kept) out += it }
        out += kept
        return out.toList()
    }

    private fun readSources(raw: String?): LinkedHashMap<String, JsonObject> {
        val out = LinkedHashMap<String, JsonObject>()
        if (raw.isNullOrBlank()) return out
        val arr = liveTvJson.parseToJsonElement(raw) as? JsonArray ?: return out
        arr.forEach { e ->
            val o = e as? JsonObject ?: return@forEach
            val id = o.optString("id")
            if (id.isNotBlank()) out[id] = o
        }
        return out
    }

    private fun unionSources(firstRaw: String, secondRaw: String, removedHere: Set<String>, localRaw: String?): String {
        val first = readSources(firstRaw)
        val presentHere = readSources(localRaw).keys
        val result = ArrayList<JsonElement>()
        // A missing baseline must not resurrect an explicit removal when the remote side
        // wins settings. A source already re-added locally still wins its stale tombstone.
        first.forEach { (id, source) -> if (id !in removedHere || id in presentHere) result += source }
        for ((id, o) in readSources(secondRaw)) {
            if (id !in first && (id !in removedHere || id in presentHere)) result += o
        }
        return JsonArray(result).toString()
    }

    private fun readBase(): JsonObject? {
        if (!store.getBoolean(KEY_BASE_VALID, true)) return null
        return runCatching {
            LiveTvFiles.readSmall(LiveTvDirs.STATE, BASE_FILE)?.let { parseObject(it) }
        }.getOrNull()
    }

    private fun writeBase(data: JsonObject) {
        // A failed seal/write must not leave an older cache eligible for the next merge,
        // even if storage also refuses its deletion. Existing caches remain valid by default.
        store.putBoolean(KEY_BASE_VALID, false)
        val written = runCatching { LiveTvFiles.writeSmall(LiveTvDirs.STATE, BASE_FILE, data.toString()) }.isSuccess
        store.putBoolean(KEY_BASE_VALID, written)
        if (!written) runCatching { LiveTvFiles.delete(LiveTvDirs.STATE, BASE_FILE) }
    }

    // ------------------------------------------------------------------ Google plumbing

    private suspend fun token(): String? {
        val refresh = store.getString(KEY_REFRESH) ?: return null
        accessToken?.let { if (LiveTvClock.nowMs() < accessTokenExpiresAt - 60_000) return it }
        val res = runCatching {
            postJson(
                "https://oauth2.googleapis.com/token",
                mapOf("client_id" to clientId, "client_secret" to clientSecret, "refresh_token" to refresh, "grant_type" to "refresh_token")
            )
        }.getOrElse { e ->
            com.nuvio.app.features.livetv.model.LiveTvLoadReport.add("Google Drive: couldn't renew the sign-in (${e.message?.take(100) ?: "network error"})")
            _state.value = _state.value.copy(message = "Couldn't reach Google Drive (${e.message ?: "network error"}).")
            return null
        }
        if (res.optString("error") == "invalid_grant") {
            store.putString(KEY_REFRESH, null)
            com.nuvio.app.features.livetv.model.LiveTvLoadReport.add("Google Drive: access was removed; signed out")
            _state.value = _state.value.copy(connected = false, message = "Google Drive access was removed. Sign in again to keep syncing.")
            return null
        }
        val access = res.optString("access_token").ifBlank { return null }
        accessToken = access
        accessTokenExpiresAt = LiveTvClock.nowMs() + res.optLong("expires_in", 3600) * 1000
        return access
    }

    private suspend fun fileId(token: String): String? {
        store.getString(KEY_FILE_ID)?.let { return it }
        val q = "name='$FILE_NAME' and trashed=false".encodeURLParameter()
        val text = execute(
            "GET",
            "https://www.googleapis.com/drive/v3/files?spaces=${if (isAppDataMode) "appDataFolder" else "drive"}&q=$q&fields=files(id)",
            token
        )
        val files = parseObject(text).optJSONArray("files")
        val id = (files?.firstOrNull() as? JsonObject)?.optString("id")?.takeIf { it.isNotBlank() } ?: return null
        store.putString(KEY_FILE_ID, id)
        return id
    }

    private suspend fun postJson(url: String, fields: Map<String, String>): JsonObject {
        val r = store.postForm(url, fields)
        return runCatching { parseObject(r.body) }.getOrElse { buildJsonObject { put("error", "http_${r.code}") } }
    }

    private suspend fun execute(method: String, url: String, token: String, contentType: String? = null, body: String? = null): String {
        val r = store.request(method, url, token, contentType, body)
        if (r.code == 404) store.putString(KEY_FILE_ID, null)
        if (!r.isSuccessful) error("HTTP ${r.code}")
        return r.body
    }

    private val isAppDataMode: Boolean get() = (store.getString(KEY_MODE) ?: MODE_APPDATA) == MODE_APPDATA

    private fun googleError(json: JsonObject): String {
        val e = json.optString("error").ifBlank { json.optString("error_code") }
        val detail = json.optString("error_description").takeIf { it.isNotBlank() }?.let { " Google says: $it" }.orEmpty()
        return when (e) {
            "invalid_client" -> "Google doesn't recognize this build's sign-in client. Check the client ID and secret, and that its type is \"TVs and Limited Input devices\".$detail"
            "unauthorized_client" -> "This sign-in client isn't allowed to use device sign-in. Create one of type \"TVs and Limited Input devices\".$detail"
            "invalid_scope" -> "Google refused the Drive permission for this app.$detail"
            "org_internal" -> "This app is limited to one organization. Set the audience to External in Google Cloud.$detail"
            "rate_limit_exceeded" -> "Too many attempts. Wait a minute and try again."
            else -> "Google sign-in failed ($e).$detail"
        }
    }

    private fun emailFromIdToken(idToken: String): String? = runCatching {
        val payload = idToken.split('.').getOrNull(1) ?: return null
        val json = store.decodeBase64Url(payload) ?: return null
        parseObject(json).optString("email").ifBlank { null }
    }.getOrNull()

    private const val MODE_APPDATA = "appdata"
    private const val MODE_FILE = "file"
    private val SOURCE_LISTS = setOf("playlists", "epg_sources")
    private val SET_ITEM_KEYS = setOf("hidden_channels", "hidden_groups", "vod_hidden_categories", "favorite_groups", "hidden_playlists")
    private val ITEM_KEYS = SET_ITEM_KEYS + setOf("favorites", "custom_groups", "channel_copies")
    private val PROFILE_SUFFIX = Regex("""_p\d+$""")
    private val SCOPE_ATTEMPTS = listOf(
        "openid email https://www.googleapis.com/auth/drive.appdata" to MODE_APPDATA,
        "https://www.googleapis.com/auth/drive.appdata" to MODE_APPDATA,
        "https://www.googleapis.com/auth/drive.file" to MODE_FILE
    )
    private const val FILE_NAME = "Nuvio Live TV backup.json"
    private const val BASE_FILE = "drive_base.json"
    private const val JSON = "application/json; charset=UTF-8"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_EMAIL = "email"
    private const val KEY_AUTO = "auto_sync"
    private const val KEY_FILE_ID = "file_id"
    private const val KEY_LAST_HASH = "last_hash"
    private const val KEY_BASE_VALID = "base_valid"
    private const val KEY_LAST_SYNC = "last_sync"
    private const val KEY_APPLIED_AT = "applied_at"
    private const val KEY_LOCAL_CHANGED_AT = "local_changed_at"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_MODE = "mode"
}

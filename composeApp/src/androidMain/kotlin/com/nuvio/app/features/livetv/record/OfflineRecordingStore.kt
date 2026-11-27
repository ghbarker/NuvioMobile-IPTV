package com.nuvio.app.features.livetv.record

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/** Keeps downloads outside normal recordings so retention rules and NAS uploads cannot claim them. */
internal class OfflineRecordingStore(
    private val root: () -> File,
    private val scope: CoroutineScope,
    private val configuredFolder: () -> NetworkFolderConfig?,
    private val copyMedia: suspend (NetworkFolderConfig, String, File, (Float) -> Unit) -> Long,
) {
    private val mutableState = MutableStateFlow(OfflineRecordingState())
    val state = mutableState.asStateFlow()
    private val jobs = mutableMapOf<OfflineRecordingKey, Job>()
    private val initialization = Mutex()
    private var loaded = false

    fun start() {
        scope.launch {
            try {
                load()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A later Save can retry when local storage is available again.
            }
        }
    }

    private suspend fun load() = initialization.withLock {
        if (loaded) return@withLock
        val base = root()
        if (!base.isDirectory && !base.mkdirs()) throw IOException("Cannot open offline recordings")
        val ready = mutableMapOf<OfflineRecordingKey, LiveTvRecording>()
        val interrupted = mutableSetOf<OfflineRecordingKey>()
        for (dir in base.listFiles() ?: throw IOException("Cannot list offline recordings")) {
            when {
                dir.name.startsWith(".partial-") -> {
                    readInfo(dir)?.let { interrupted.add(it.first) }
                    dir.deleteRecursively()
                }
                dir.name.startsWith(".removed-") -> dir.deleteRecursively()
                dir.name.startsWith("saved-") && playableIndex(dir) -> {
                    val (key, recording) = readInfo(dir) ?: continue
                    if (dir.name == directoryName(key) && recording.status == RecordingStatus.DONE) {
                        ready[key] = recording.copy(fileName = PLAYLIST)
                    }
                }
            }
        }
        mutableState.update { it.copy(ready = ready, failed = (it.failed + interrupted) - ready.keys - it.progress.keys) }
        loaded = true
    }

    fun save(key: OfflineRecordingKey, recording: LiveTvRecording) {
        if (recording.status != RecordingStatus.DONE || recording.id != key.id ||
            recording.dir != NetworkLibrary.DIR_PREFIX + key.id) return
        synchronized(jobs) {
            if (key in jobs || key in state.value.ready) return
            val folder = configuredFolder()
            if (folder == null || folder.label != key.folderLabel) {
                mutableState.update { it.copy(failed = it.failed + key) }
                return
            }
            val job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    load()
                    if (key !in state.value.ready) download(key, recording, folder)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.update { it.copy(failed = it.failed + key) }
                }
            }
            jobs[key] = job
            mutableState.update { it.copy(progress = it.progress + (key to 0f), failed = it.failed - key) }
            job.invokeOnCompletion {
                synchronized(jobs) {
                    jobs.remove(key)
                    mutableState.update { it.copy(progress = it.progress - key) }
                }
            }
            job.start()
        }
    }

    private suspend fun download(key: OfflineRecordingKey, recording: LiveTvRecording, folder: NetworkFolderConfig) {
        val base = root()
        val destination = File(base, directoryName(key))
        val staging = File(base, ".partial-" + UUID.randomUUID())
        var replaced: File? = null
        if (!staging.mkdir()) throw IOException("Cannot prepare download")
        try {
            writeInfo(staging, key, recording)
            val media = File(staging, "media")
            if (!media.mkdir()) throw IOException("Cannot prepare recording files")
            val bytes = copyMedia(folder, key.id, media) { fraction ->
                mutableState.update { it.copy(progress = it.progress + (key to fraction.coerceIn(0f, 1f))) }
            }
            val job = currentCoroutineContext()
            job.ensureActive()
            if (bytes <= 0L || !playableIndex(staging)) throw IOException("Incomplete download")
            val local = recording.copy(dir = destination.absolutePath, fileName = PLAYLIST, sizeBytes = bytes)
            writeInfo(staging, key, local)
            synchronized(jobs) {
                job.ensureActive()
                // Only a broken, unpublished copy can exist here. Never replace a ready entry.
                if (destination.exists()) {
                    val old = File(base, ".removed-" + UUID.randomUUID())
                    if (!destination.renameTo(old)) throw IOException("Cannot replace incomplete download")
                    replaced = old
                }
                if (!staging.renameTo(destination)) throw IOException("Cannot finish download")
                mutableState.update { it.copy(ready = it.ready + (key to local), failed = it.failed - key) }
            }
        } finally {
            staging.deleteRecursively()
            replaced?.deleteRecursively()
        }
    }

    fun cancel(key: OfflineRecordingKey) {
        synchronized(jobs) { jobs[key]?.cancel() }
    }

    fun remove(key: OfflineRecordingKey) {
        scope.launch {
            try {
                load()
                val base = root()
                val discarded = File(base, ".removed-" + UUID.randomUUID())
                synchronized(jobs) {
                    if (key !in state.value.ready) return@launch
                    val dir = File(base, directoryName(key))
                    if (dir.exists() && !dir.renameTo(discarded)) throw IOException("Cannot remove download")
                    mutableState.update { it.copy(ready = it.ready - key, failed = it.failed - key) }
                }
                discarded.deleteRecursively()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(failed = it.failed + key) }
            }
        }
    }

    private fun writeInfo(dir: File, key: OfflineRecordingKey, recording: LiveTvRecording) {
        val info = JsonObject(recording.toJson() + ("offlineSource" to JsonPrimitive(key.folderLabel)))
        File(dir, "info.json").writeText(info.toString())
    }

    private fun playableIndex(dir: File): Boolean = File(dir, PLAYLIST).let { it.isFile && it.length() > 0 }

    private fun readInfo(dir: File): Pair<OfflineRecordingKey, LiveTvRecording>? = try {
        val info = Json.parseToJsonElement(File(dir, "info.json").readText()).jsonObject
        val source = info.getValue("offlineSource").jsonPrimitive.content
        LiveTvRecording.fromJson(info, dir.absolutePath)?.let { OfflineRecordingKey(source, it.id) to it }
    } catch (_: Exception) { null }

    private fun directoryName(key: OfflineRecordingKey): String {
        // Neither a network address nor a server-supplied ID is used as a filesystem path.
        val digest = MessageDigest.getInstance("SHA-256").digest(("${key.folderLabel}\u0000${key.id}").toByteArray())
        return "saved-" + digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val PLAYLIST = "media/index.m3u8"
    }
}

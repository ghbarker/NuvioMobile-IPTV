package com.nuvio.app.features.livetv.record

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

internal actual object NetworkLibraryPlatform {
    actual val available: Boolean = true

    @Volatile private var folder: SmbFolder? = null

    /** The connection to [config]'s folder (one kept and reused). */
    internal fun folderFor(config: NetworkFolderConfig): SmbFolder = synchronized(this) {
        folder?.takeIf { it.config == config }?.let { return it }
        runCatching { folder?.close() }
        SmbFolder(config).also { folder = it }
    }

    actual suspend fun list(config: NetworkFolderConfig): Map<String, String> = withContext(Dispatchers.IO) {
        val f = folderFor(config)
        val ids = f.listFolders(NetworkLibrary.META_DIR).filter { !it.startsWith(".") }
        val out = LinkedHashMap<String, String>()
        for (id in ids) {
            runCatching { String(f.readAll(NetworkLibrary.META_DIR + "/" + id + "/info.json"), Charsets.UTF_8) }
                .getOrNull()?.takeIf { it.isNotBlank() }?.let { out[id] = it }
        }
        out
    }

    actual fun playUrl(config: NetworkFolderConfig, id: String): String = NetworkRecordingServer.urlFor(config, id)

    actual suspend fun rename(config: NetworkFolderConfig, rec: LiveTvRecording, title: String, isCurrent: () -> Boolean): String =
        withContext(Dispatchers.IO) {
            // An edit owns its connection: changing the configured share must not redirect it.
            val job = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
            SmbFolder(config).use { NetworkRecordingRename.rename(it, rec, title) { job?.isActive != false && isCurrent() } }
        }

    /** Like the TV app: the show's joined file(s), the details folder, and the show's folder once it's empty. */
    actual suspend fun delete(config: NetworkFolderConfig, id: String): Unit = withContext(Dispatchers.IO) {
        val f = folderFor(config)
        val dir = NetworkLibrary.META_DIR + "/" + id
        val idx = runCatching { JoinFiles.parse(String(f.readAll("$dir/join.idx"), Charsets.UTF_8)) }.getOrNull()
        idx?.files?.filter { it.onNetwork }?.forEach { file ->
            f.delete(file.path)
            f.delete(file.path + ".part")
            file.path.substringBeforeLast('/', "").takeIf { it.isNotEmpty() }?.let { runCatching { f.deleteFolderIfEmpty(it) } }
        }
        f.deleteFolder(dir)
        NetworkRecordingServer.forget(id)
    }

    actual fun configChanged(config: NetworkFolderConfig?) {
        synchronized(this) {
            folder?.takeIf { it.config != config }?.let { old -> runCatching { old.close() }; folder = null }
        }
        // A Full app VPN lets the network folder's address through (home addresses only), like on the TV.
        val ctx = RecordingPlatform.context() ?: return
        val host = config?.host?.trim()?.let { h -> if (h.count { it == ':' } == 1) h.substringBefore(':') else h }
        runCatching { com.nuvio.vpn.android.VpnHomeBypass.set(ctx, listOfNotNull(host)) }
    }

    actual fun describe(e: Throwable, config: NetworkFolderConfig): String = SmbFolder.describe(e, config)

    actual suspend fun test(config: NetworkFolderConfig, onVpnWait: () -> Unit): String? = withContext(Dispatchers.IO) {
        val ctx = RecordingPlatform.context()
        // VPN in Full app mode: let this address past it first (it reconnects), like the TV.
        letPastVpn(config.host, onVpnWait)
        val result = runCatching { SmbFolder(config).use { it.test() } }
        result.exceptionOrNull()?.let { e ->
            val tip = ctx?.let { runCatching { com.nuvio.vpn.android.VpnHomeBypass.hint(it, config.host) }.getOrNull() }
            SmbFolder.describe(e, config) + (tip?.let { "\n$it" } ?: "")
        }
    }

    actual suspend fun letPastVpn(host: String, onVpnWait: () -> Unit): Unit = withContext(Dispatchers.IO) {
        val ctx = RecordingPlatform.context() ?: return@withContext
        runCatching {
            if (com.nuvio.vpn.android.VpnHomeBypass.fullAppOn(ctx)) {
                withContext(Dispatchers.Main) { onVpnWait() }
                val h = host.trim().let { h -> if (h.count { it == ':' } == 1) h.substringBefore(':') else h }
                com.nuvio.vpn.android.VpnHomeBypass.set(ctx, listOf(h))
                com.nuvio.vpn.android.VpnHomeBypass.awaitConnected(ctx, 20_000)
            }
        }
        Unit
    }

    actual fun search(): Flow<NetworkSearchState> {
        val ctx = RecordingPlatform.context() ?: return flowOf(NetworkSearchState(searching = false, noNetwork = true))
        return NetworkFolderFinder.search(ctx)
    }

    actual suspend fun shares(host: String, username: String, password: String): List<SmbShare> =
        withContext(Dispatchers.IO) { SmbDiscovery.listShares(host, username, password) }

    actual suspend fun folders(config: NetworkFolderConfig): List<String> = withContext(Dispatchers.IO) {
        SmbFolder(config).use { f -> f.listFolders("").filterNot { it.startsWith(".") }.sortedBy { it.lowercase() } }
    }

    actual suspend fun makeFolder(config: NetworkFolderConfig, name: String): Unit = withContext(Dispatchers.IO) {
        SmbFolder(config).use { it.mkdirs(name) }
        Unit
    }

    actual suspend fun publish(config: NetworkFolderConfig, rec: LiveTvRecording, localPath: String, onProgress: (Float) -> Unit): String =
        withContext(Dispatchers.IO) {
            // Copying runs at background priority so it never competes with what's on screen.
            val previous = runCatching { android.os.Process.getThreadPriority(android.os.Process.myTid()) }.getOrNull()
            runCatching { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND) }
            try {
                NetworkPublish.publish(folderFor(config), rec, java.io.File(localPath), onProgress)
            } finally {
                if (previous != null) runCatching { android.os.Process.setThreadPriority(previous) }
            }
        }

    /** A whole (small) file from the folder. */
    internal fun SmbFolder.readAll(rel: String): ByteArray {
        val size = size(rel) ?: throw NetworkFolderException("Not on the network folder")
        val out = ByteArrayOutputStream(size.toInt().coerceAtLeast(0))
        if (size > 0) read(rel, 0L, size, out)
        return out.toByteArray()
    }
}

/**
 * join.idx (the TV app's format, see its RecordingJoin.kt): where each recorded piece went in
 * the show's joined file(s). Only what the phone needs to read it.
 */
internal class JoinFiles private constructor(val files: List<JoinFile>, val pieces: Map<String, JoinPiece>) {
    data class JoinFile(val no: Int, val onNetwork: Boolean, val done: Boolean, val path: String)
    data class JoinPiece(val file: Int, val offset: Long, val length: Long)

    fun file(no: Int): JoinFile? = files.firstOrNull { it.no == no }

    companion object {
        fun parse(text: String): JoinFiles? {
            val lines = text.lines()
            if (lines.firstOrNull()?.trim() != "NUVIO-JOIN 1") return null
            val files = LinkedHashMap<Int, JoinFile>()
            val pieces = HashMap<String, JoinPiece>()
            for (line in lines.drop(1)) {
                val f = line.split('\t')
                when (f.getOrNull(0)) {
                    "F" -> if (f.size >= 5) f[1].toIntOrNull()?.let { no -> files[no] = JoinFile(no, f[2] == "N", f[3] == "1", f.drop(4).joinToString("\t")) }
                    "P" -> if (f.size >= 5) {
                        val no = f[2].toIntOrNull(); val off = f[3].toLongOrNull(); val len = f[4].toLongOrNull()
                        if (no != null && off != null && len != null) pieces[f[1]] = JoinPiece(no, off, len)
                    }
                }
            }
            return JoinFiles(files.values.toList(), pieces)
        }
    }
}

/**
 * Plays a TV's recording from the network folder in Nuvio's player, as an ordinary web link
 * (http://127.0.0.1:…/<id>/index.m3u8): the playlist comes from the folder, and each piece is
 * read from the show's joined file there (or from the folder, for a recording kept as pieces).
 * Only this phone can reach it. A recording the TV is still making plays too: its playlist and
 * index are read again every few seconds.
 */
internal object NetworkRecordingServer {
    private const val TAG = "NetworkRecordingServer"
    private const val FRESH_MS = 3_000L

    @Volatile private var server: ServerSocket? = null
    @Volatile private var config: NetworkFolderConfig? = null

    private class Cached(val at: Long, val bytes: ByteArray)
    private val texts = ConcurrentHashMap<String, Cached>()
    private class CachedIndex(val at: Long, val index: JoinFiles?)
    private val indexes = ConcurrentHashMap<String, CachedIndex>()

    fun urlFor(config: NetworkFolderConfig, id: String): String {
        this.config = config
        val port = ensure()
        return "http://127.0.0.1:$port/" + Uri.encode(id) + "/index.m3u8"
    }

    fun forget(id: String) {
        texts.keys.filter { it.startsWith("$id/") }.forEach { texts.remove(it) }
        indexes.remove(id)
    }

    @Synchronized
    private fun ensure(): Int {
        server?.takeIf { !it.isClosed }?.let { return it.localPort }
        val s = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        server = s
        thread(name = "net-rec-server", isDaemon = true) {
            while (!s.isClosed) {
                val client = runCatching { s.accept() }.getOrNull() ?: break
                thread(name = "net-rec-client", isDaemon = true) { runCatching { serve(client) }.onFailure { Log.w(TAG, "serve: ${it.message}") } }
            }
        }
        return s.localPort
    }

    private fun folder(): SmbFolder? = config?.let { NetworkLibraryPlatform.folderFor(it) }

    private fun metaDir(id: String) = NetworkLibrary.META_DIR + "/" + id

    /** A small file of recording [id], read again when older than a few seconds (it may still be recording). */
    private fun text(f: SmbFolder, id: String, name: String, fresh: Boolean): ByteArray {
        val key = "$id/$name"
        val now = System.currentTimeMillis()
        texts[key]?.takeIf { !fresh && now - it.at < FRESH_MS }?.let { return it.bytes }
        val bytes = with(NetworkLibraryPlatform) { f.readAll(metaDir(id) + "/" + name) }
        texts[key] = Cached(now, bytes)
        return bytes
    }

    private fun index(f: SmbFolder, id: String, fresh: Boolean): JoinFiles? {
        val now = System.currentTimeMillis()
        indexes[id]?.takeIf { !fresh && now - it.at < FRESH_MS }?.let { return it.index }
        val idx = runCatching { JoinFiles.parse(String(text(f, id, "join.idx", fresh), Charsets.UTF_8)) }.getOrNull()
        indexes[id] = CachedIndex(now, idx)
        return idx
    }

    /** Where piece [name] is on the folder: (path, offset, length); null if nowhere. */
    private fun locate(f: SmbFolder, id: String, name: String, fresh: Boolean): Triple<String, Long, Long>? {
        index(f, id, fresh)?.let { idx ->
            idx.pieces[name]?.let { p ->
                val file = idx.file(p.file) ?: return null
                if (file.onNetwork) return Triple(file.path, p.offset, p.length)
            }
        }
        // Kept as pieces (picture and sound apart, or fMP4): in the recording's details folder.
        val rel = metaDir(id) + "/" + name
        val size = runCatching { f.size(rel) }.getOrNull() ?: return null
        return Triple(rel, 0L, size)
    }

    private fun answer(out: OutputStream, status: String) {
        out.write("HTTP/1.1 $status\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
        out.flush()
    }

    private fun contentType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "m3u8" -> "application/vnd.apple.mpegurl"
        "ts" -> "video/mp2t"
        "m4s", "mp4" -> "video/mp4"
        "aac" -> "audio/aac"
        else -> "application/octet-stream"
    }

    private fun serve(client: Socket) = client.use { sock ->
        sock.soTimeout = 30_000
        val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
        val requestLine = reader.readLine() ?: return@use
        var rangeStart = -1L
        var rangeEnd = -1L
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Range:", ignoreCase = true)) {
                Regex("""bytes=(\d*)-(\d*)""").find(line)?.let { m ->
                    rangeStart = m.groupValues[1].toLongOrNull() ?: -1L
                    rangeEnd = m.groupValues[2].toLongOrNull() ?: -1L
                }
            }
        }
        val parts = requestLine.split(' ')
        val method = parts.getOrNull(0) ?: "GET"
        val path = URLDecoder.decode(parts.getOrNull(1)?.substringBefore('?') ?: "/", "UTF-8").trimStart('/')
        val id = path.substringBefore('/')
        val name = path.substringAfter('/', "")
        val out = sock.getOutputStream()
        // Only a recording's own files: one name, nothing outside its folder.
        if (id.isEmpty() || name.isEmpty() || name.contains('/') || name.contains("..") || id.contains("..")) { answer(out, "404 Not Found"); return@use }
        val f = folder() ?: run { answer(out, "503 Service Unavailable"); return@use }
        val head = method.equals("HEAD", ignoreCase = true)

        if (name.endsWith(".m3u8", ignoreCase = true)) {
            val text = try { String(text(f, id, name, fresh = false), Charsets.UTF_8) } catch (e: Exception) {
                Log.w(TAG, "playlist $name: ${e.message}")
                answer(out, if (e is NetworkFolderException) "404 Not Found" else "503 Service Unavailable"); return@use
            }
            // Still being recorded (no end mark): start at the beginning, not near the newest part.
            val body = (if (!text.contains("#EXT-X-ENDLIST") && !text.contains("#EXT-X-START") && !text.contains("#EXT-X-STREAM-INF"))
                text.replaceFirst("#EXTM3U", "#EXTM3U\n#EXT-X-START:TIME-OFFSET=0,PRECISE=YES") else text).toByteArray()
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: ${contentType(name)}\r\nContent-Length: ${body.size}\r\n" +
                "Cache-Control: no-cache\r\nConnection: close\r\n\r\n").toByteArray())
            if (!head) out.write(body)
            out.flush()
            return@use
        }

        // A piece: where it is (the index again if it isn't found, or a read fails: the file was just renamed).
        val first = try { locate(f, id, name, fresh = false) ?: locate(f, id, name, fresh = true) } catch (e: Exception) {
            Log.w(TAG, "piece $name: ${e.message}"); answer(out, "503 Service Unavailable"); return@use
        }
        if (first == null) { answer(out, "404 Not Found"); return@use }
        val length = first.third
        val start = if (rangeStart >= 0) rangeStart.coerceAtMost(length) else 0L
        val end = if (rangeEnd >= 0) rangeEnd.coerceAtMost(length - 1) else length - 1
        val count = (end - start + 1).coerceAtLeast(0)
        // Read before answering, so a file that's gone gives an error the player tries again.
        val bytes = ByteArrayOutputStream(count.toInt().coerceIn(0, 8 * 1024 * 1024))
        if (!head && count > 0) {
            val ok = runCatching { f.read(first.first, first.second + start, count, bytes) }.isSuccess
            if (!ok) {
                // Renamed just now (the recording finished): where it is now.
                bytes.reset()
                val again = runCatching { locate(f, id, name, fresh = true) }.getOrNull()
                if (again == null || runCatching { f.read(again.first, again.second + start, count, bytes) }.isFailure) {
                    answer(out, "503 Service Unavailable"); return@use
                }
            }
        }
        val h = StringBuilder()
        h.append(if (rangeStart >= 0) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
        h.append("Content-Type: ").append(contentType(name)).append("\r\n")
        h.append("Content-Length: ").append(count).append("\r\n")
        h.append("Accept-Ranges: bytes\r\n")
        if (rangeStart >= 0) h.append("Content-Range: bytes ").append(start).append('-').append(end).append('/').append(length).append("\r\n")
        h.append("Connection: close\r\n\r\n")
        out.write(h.toString().toByteArray())
        if (!head) bytes.writeTo(out)
        out.flush()
    }
}

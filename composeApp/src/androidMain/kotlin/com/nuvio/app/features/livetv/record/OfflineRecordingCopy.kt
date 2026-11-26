package com.nuvio.app.features.livetv.record

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream

/** Copies the TV or phone recorder's HLS files into a fresh local staging directory.
 * The caller publishes the directory with its recording metadata only after this returns. */
internal class OfflineRecordingCopy(
    private val size: (String) -> Long?,
    private val read: (String, Long, Long, OutputStream) -> Unit,
) {
    private data class Piece(val path: String, val offset: Long, val length: Long)

    suspend fun copy(id: String, stagingDir: File, onProgress: (Float) -> Unit): Long {
        require(SAFE_NAME.matches(id)) { "Invalid recording ID" }
        check(stagingDir.isDirectory && stagingDir.list()?.isEmpty() == true) { "The download needs an empty folder" }
        val context = currentCoroutineContext()
        val directory = NetworkLibrary.META_DIR + "/" + id
        val indexPath = "$directory/join.idx"
        val index = size(indexPath)?.let { length ->
            JoinFiles.parse(readText(indexPath, length)) ?: throw IOException("Unrecognized recording index")
        }

        val playlists = LinkedHashMap<String, String>()
        val assets = LinkedHashSet<String>()
        val pending = ArrayDeque<String>()
        pending.add("index.m3u8")
        while (pending.isNotEmpty()) {
            context.ensureActive()
            val name = pending.removeFirst()
            if (name in playlists) continue
            val path = "$directory/$name"
            val text = readText(path, size(path) ?: throw IOException("A recording playlist is missing"))
            val references = playlistReferences(text, root = name == "index.m3u8")
            playlists[name] = text
            for (reference in references) {
                if (reference.endsWith(".m3u8")) pending.add(reference) else assets.add(reference)
            }
        }

        val joinedSizes = HashMap<String, Long>()
        val pieces = assets.associateWith { name ->
            context.ensureActive()
            val entry = index?.pieces?.get(name)
            val file = entry?.let { index.file(it.file) ?: throw IOException("The recording index is incomplete") }
            if (file != null && file.onNetwork) {
                if (!safeSharePath(file.path)) throw IOException("Invalid recording media path")
                val length = joinedSizes.getOrPut(file.path) { size(file.path) ?: throw IOException("The recording file is missing") }
                if (!file.done || entry.length <= 0 || entry.offset < 0 || entry.offset > length - entry.length)
                    throw IOException("The recording file is incomplete")
                Piece(file.path, entry.offset, entry.length)
            } else {
                val path = "$directory/$name"
                val length = size(path)?.takeIf { it > 0 } ?: throw IOException("A recording piece is missing")
                Piece(path, 0, length)
            }
        }
        val total = pieces.values.fold(0L) { sum, piece -> Math.addExact(sum, piece.length) }
        if (total <= 0) throw IOException("The recording has no media to download")
        val free = stagingDir.usableSpace
        if (free in 1 until total) throw IOException("Not enough space on this device")
        var copied = 0L
        var lastPercent = -1
        for ((name, piece) in pieces) {
            context.ensureActive()
            val destination = File(stagingDir, name)
            destination.outputStream().use { output ->
                val checked = object : OutputStream() {
                    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
                    override fun write(b: ByteArray, off: Int, len: Int) {
                        context.ensureActive()
                        output.write(b, off, len)
                        copied += len
                        val progress = (copied.toDouble() / total).toFloat().coerceAtMost(0.99f)
                        val percent = (progress * 100).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(progress)
                        }
                    }
                }
                read(piece.path, piece.offset, piece.length, checked)
            }
            if (destination.length() != piece.length) throw IOException("The recording transfer ended early")
        }
        context.ensureActive()
        for ((name, text) in playlists) File(stagingDir, name).writeText(text)
        return copied
    }

    private fun readText(path: String, length: Long): String {
        if (length !in 1..MAX_MANIFEST_BYTES) throw IOException("Invalid recording playlist or index size")
        val bytes = ByteArrayOutputStream(length.toInt())
        read(path, 0L, length, bytes)
        if (bytes.size().toLong() != length) throw IOException("The recording transfer ended early")
        return bytes.toString(Charsets.UTF_8.name())
    }

    /** Nuvio's master and media playlists use simple sibling names for every resource.
     * Validate every reference before publishing any playlist for an offline player to open. */
    private fun playlistReferences(text: String, root: Boolean): Set<String> {
        val lines = text.lineSequence().map(String::trim).filter(String::isNotEmpty)
        if (lines.firstOrNull() != "#EXTM3U") throw IOException("Invalid recording playlist")
        val master = lines.any { it.startsWith("#EXT-X-STREAM-INF:") }
        if (master && !root) throw IOException("Invalid nested recording playlist")
        if (!master && lines.none { it == "#EXT-X-ENDLIST" }) throw IOException("The recording is still being finalized")
        val refs = LinkedHashSet<String>()
        var playable = false
        fun add(name: String, playlist: Boolean) {
            if (!SAFE_NAME.matches(name) || name.substringAfterLast('.') !in EXTENSIONS ||
                name.endsWith(".m3u8") != playlist || name == "index.m3u8") throw IOException("Unsupported recording file reference")
            refs += name
        }
        for (line in lines) {
            if (!line.startsWith('#')) {
                add(line, playlist = master)
                playable = true
                continue
            }
            val tag = line.substringBefore(':')
            if (tag in URI_TAGS) {
                val uris = URI.findAll(line).toList()
                if (uris.isEmpty() && tag == "#EXT-X-KEY" && line == "#EXT-X-KEY:METHOD=NONE") continue
                if (uris.size != 1) throw IOException("Invalid recording playlist reference")
                add(uris.single().groupValues[1], playlist = tag == "#EXT-X-MEDIA")
            } else if (line.startsWith("#EXT") && tag !in LOCAL_TAGS) {
                throw IOException("Unsupported recording playlist directive")
            }
        }
        if (!playable) throw IOException("The recording playlist contains no playable media")
        return refs
    }

    companion object {
        private const val MAX_MANIFEST_BYTES = 16L * 1024 * 1024
        private val SAFE_NAME = Regex("[A-Za-z0-9_-][A-Za-z0-9._-]{0,239}")
        private val URI = Regex("""(?:[:,])URI="([^"]+)"""")
        private val EXTENSIONS = setOf("m3u8", "ts", "m4s", "mp4", "aac", "ac3", "vtt", "bin", "key")
        private val URI_TAGS = setOf("#EXT-X-MEDIA", "#EXT-X-MAP", "#EXT-X-KEY")
        private val LOCAL_TAGS = setOf("#EXTM3U", "#EXTINF", "#EXT-X-VERSION", "#EXT-X-TARGETDURATION",
            "#EXT-X-MEDIA-SEQUENCE", "#EXT-X-DISCONTINUITY-SEQUENCE", "#EXT-X-PLAYLIST-TYPE", "#EXT-X-ENDLIST",
            "#EXT-X-DISCONTINUITY", "#EXT-X-STREAM-INF", "#EXT-X-BYTERANGE", "#EXT-X-PROGRAM-DATE-TIME",
            "#EXT-X-INDEPENDENT-SEGMENTS", "#EXT-X-START")

        private fun safeSharePath(path: String): Boolean = path.split('/').all { part ->
            part.isNotEmpty() && !part.endsWith('.') && !part.endsWith(' ') &&
                part.none { it < ' ' || it in "\\:*?\"<>|" }
        }

        suspend fun copy(config: NetworkFolderConfig, id: String, stagingDir: File, onProgress: (Float) -> Unit): Long =
            withContext(Dispatchers.IO) {
                SmbFolder(config).use { folder ->
                    // An interrupted read may already have written a prefix. Retrying into
                    // the same OutputStream would append that prefix a second time.
                    OfflineRecordingCopy(folder::size) { path, offset, count, out ->
                        folder.read(path, offset, count, out, retry = false)
                    }.copy(id, stagingDir, onProgress)
                }
            }
    }
}

package com.nuvio.app.features.livetv.record

import java.util.UUID
import java.io.ByteArrayOutputStream

/** Publishes a metadata-only edit with one replace; a failed temp write leaves info.json intact. */
internal object NetworkRecordingRename {
    fun rename(folder: SmbFolder, rec: LiveTvRecording, title: String, isCurrent: () -> Boolean): String {
        val id = rec.dir.removePrefix(NetworkLibrary.DIR_PREFIX)
        require(id == rec.id && id.isNotBlank() && id != "." && id != ".." && id.none { it == '/' || it == '\\' }) {
            RecordingRename.CHANGED
        }
        check(isCurrent()) { RecordingRename.FOLDER_CHANGED }
        val path = "${NetworkLibrary.META_DIR}/$id/info.json"
        val before = readInfo(folder, path)
        val renamed = RecordingRename.info(before, rec, title)
        val temp = "${NetworkLibrary.META_DIR}/$id/info.rename-${UUID.randomUUID()}.tmp"
        try {
            folder.writeBytes(temp, renamed.toByteArray(Charsets.UTF_8))
            check(isCurrent()) { RecordingRename.FOLDER_CHANGED }
            check(readInfo(folder, path) == before) { RecordingRename.CHANGED }
            check(isCurrent()) { RecordingRename.FOLDER_CHANGED }
            folder.rename(temp, path, replaceExisting = true)
        } finally { runCatching { folder.delete(temp) } }
        return renamed
    }

    private fun readInfo(folder: SmbFolder, path: String): String {
        val output = ByteArrayOutputStream()
        // Limit the read itself: a size query can become stale before the server sends data.
        folder.read(path, 0L, MAX_INFO_BYTES + 1L, output)
        check(output.size().toLong() in 1..MAX_INFO_BYTES) { "The recording's details couldn't be read" }
        return output.toString(Charsets.UTF_8.name())
    }

    private const val MAX_INFO_BYTES = 1024L * 1024
}

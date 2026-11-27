package com.nuvio.app.features.downloads

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl

internal val downloadHttpClient = OkHttpClient.Builder()
    .connectTimeout(60, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

internal class DownloadHttpException(val statusCode: Int) : IOException("Download failed: HTTP $statusCode")

internal suspend fun transferAndroidDownload(
    item: DownloadItem,
    directory: File,
    validator: String?,
    client: OkHttpClient = downloadHttpClient,
    onHeaders: (totalBytes: Long?, validator: String?) -> Unit,
    onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
): File {
    require(File(item.fileName).name == item.fileName && item.fileName.isNotBlank())
    check(directory.isDirectory || directory.mkdirs()) { "Cannot create downloads directory" }
    val partial = File(directory, "${item.fileName}.part")
    transferDownloadBytes(
        item, partial.takeIf(File::isFile)?.length() ?: 0L, validator, client,
        { offset -> FileOutputStream(partial, offset > 0L) }, onHeaders, onProgress,
    )
    return partial
}

internal suspend fun transferDownloadBytes(
    item: DownloadItem,
    existingBytes: Long,
    validator: String?,
    client: OkHttpClient,
    openOutput: (offset: Long) -> FileOutputStream,
    onHeaders: (totalBytes: Long?, validator: String?) -> Unit,
    onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
): Long = coroutineScope {
    val activeCall = AtomicReference<Call?>()
    val transfer = async(Dispatchers.IO) {
        try {
            val http: Call.Factory = if (item.folder != null) {
                com.nuvio.vpn.android.VpnHttpClient.wrap(playlistDownloadClient(client, item))
            } else client
            var offset = if (item.folder != null && validator == null) 0L else existingBytes
            var restarted = false

            while (true) {
                ensureActive()
                val request = Request.Builder().url(item.sourceUrl).apply {
                    item.sourceHeaders.forEach { (key, value) ->
                        if (!key.equals("Range", true) && !key.equals("If-Range", true) &&
                            !key.equals("Accept-Encoding", true)) header(key, value)
                    }
                    header("Accept-Encoding", "identity")
                    if (offset > 0L) {
                        header("Range", "bytes=$offset-")
                        validator?.let { header("If-Range", it) }
                    }
                }.build()
                val call = http.newCall(request)
                activeCall.set(call)
                ensureActive()
                call.execute().use { response ->
                    if (response.code == 416 && offset > 0L && !restarted) {
                        offset = 0L
                        restarted = true
                        return@use
                    }
                    if (!response.isSuccessful) throw DownloadHttpException(response.code)
                    val range = response.header("Content-Range")?.let(::parseDownloadContentRange)
                    val resumed = response.code == 206
                    if (resumed && (range == null || range.first != offset)) {
                        throw IOException("Server returned an invalid download range")
                    }
                    val startingBytes = if (resumed) offset else 0L
                    val body = response.body ?: throw IOException("Download response is empty")
                    if (item.folder != null) validatePlaylistResponse(response, startingBytes)
                    val contentLength = body.contentLength().takeIf { it >= 0L }
                    val totalBytes = range?.second ?: contentLength?.let { startingBytes + it }
                    val responseValidator = response.header("ETag")?.takeUnless { it.startsWith("W/") }
                        ?: response.header("Last-Modified")
                    onHeaders(totalBytes, responseValidator)
                    var downloaded = startingBytes
                    onProgress(downloaded, totalBytes)
                    body.byteStream().use { input ->
                        openOutput(startingBytes).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                ensureActive()
                                val size = input.read(buffer)
                                if (size == -1) break
                                output.write(buffer, 0, size)
                                downloaded += size
                                onProgress(downloaded, totalBytes)
                            }
                            output.fd.sync()
                        }
                    }
                    ensureActive()
                    if (totalBytes != null && downloaded != totalBytes) {
                        throw IOException("Download ended before all bytes were received")
                    }
                    return@async downloaded
                }
            }
            @Suppress("UNREACHABLE_CODE")
            0L
        } catch (error: Exception) {
            // Closing a cancelled HTTP call throws IOException from its blocking read.
            ensureActive()
            throw error
        }
    }
    try {
        transfer.await()
    } finally {
        activeCall.get()?.cancel()
    }
}

internal fun parseDownloadContentRange(value: String): Pair<Long, Long?>? {
    val match = Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)").matchEntire(value.trim()) ?: return null
    val start = match.groupValues[1].toLongOrNull() ?: return null
    val end = match.groupValues[2].toLongOrNull() ?: return null
    val total = match.groupValues[3].toLongOrNull()
    if (end < start || (total != null && end >= total)) return null
    return start to total
}

// Playlist credentials may be carried in custom headers, which OkHttp normally keeps on redirects.
private fun playlistDownloadClient(client: OkHttpClient, item: DownloadItem): OkHttpClient {
    val origin = item.sourceUrl.toHttpUrl()
    return client.newBuilder().addNetworkInterceptor { chain ->
        val request = chain.request()
        val url = request.url
        if (origin.isHttps && !url.isHttps) throw DownloadFormatException()
        val sameOrigin = origin.scheme == url.scheme && origin.host == url.host && origin.port == url.port
        val forwarded = if (sameOrigin) request else request.newBuilder().apply {
            item.sourceHeaders.keys.forEach { removeHeader(it) }
        }.build()
        chain.proceed(forwarded)
    }.build()
}

private fun validatePlaylistResponse(response: Response, offset: Long) {
    val path = response.request.url.encodedPath.lowercase()
    val type = response.header("Content-Type").orEmpty().substringBefore(';').trim().lowercase()
    if (listOf(".m3u", ".m3u8", ".mpd", ".torrent").any(path::endsWith) ||
        type.startsWith("text/") || "mpegurl" in type || "dash+xml" in type ||
        "json" in type || "xml" in type || "bittorrent" in type || type.startsWith("image/")) {
        throw DownloadFormatException()
    }
    if (offset == 0L) {
        val prefix = response.peekBody(512).bytes()
        val text = prefix.decodeToString().trimStart('\uFEFF', ' ', '\r', '\n', '\t')
        if (prefix.isEmpty() || text.startsWith("#EXT", true) || text.startsWith("<") ||
            text.startsWith("{") || text.startsWith("[") || prefix.all { it in 9..13 || it in 32..126 }) {
            throw DownloadFormatException()
        }
    }
}

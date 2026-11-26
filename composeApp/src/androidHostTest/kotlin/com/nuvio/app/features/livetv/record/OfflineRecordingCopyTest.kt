package com.nuvio.app.features.livetv.record

import com.nuvio.app.features.livetv.record.NetworkLibrary.META_DIR
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineRecordingCopyTest {
    @get:Rule val temp = TemporaryFolder()

    private val dir = "$META_DIR/rec123"
    private val source = mutableMapOf<String, ByteArray>()
    private val reader = OfflineRecordingCopy({ source[it]?.size?.toLong() }, ::read)
    private fun read(path: String, offset: Long, length: Long, output: OutputStream) {
        output.write(source.getValue(path), offset.toInt(), length.toInt())
    }
    private fun put(name: String, text: String) { source["$dir/$name"] = text.toByteArray() }
    private fun media(vararg names: String) =
        "#EXTM3U\n#EXT-X-TARGETDURATION:6\n" + names.joinToString("") { "#EXTINF:6,\n$it\n" } + "#EXT-X-ENDLIST\n"

    @Test fun joinedRecordingUsesEachIndexedRangeAndDoesNotRequireNasForReplay() = runBlocking {
        val first = byteArrayOf(1, 2, 3)
        val second = byteArrayOf(4, 5, 6, 7)
        val one = "Demo/Recording - Part 1.ts"
        val two = "Demo/Recording - Part 2.ts"
        put("index.m3u8", media("seg0.ts", "seg1.ts", "seg2.ts"))
        put("join.idx", "NUVIO-JOIN 1\nF\t1\tN\t1\t$one\nF\t2\tN\t1\t$two\n" +
            "P\tseg0.ts\t1\t1\t3\nP\tseg1.ts\t1\t4\t4\nP\tseg2.ts\t2\t0\t3\n")
        source[one] = byteArrayOf(0) + first + second
        source[two] = first.copyOf()
        val destination = temp.newFolder()
        val progress = mutableListOf<Float>()
        val before = source.mapValues { it.value.copyOf() }
        assertEquals(10L, reader.copy("rec123", destination, progress::add))
        assertTrue(progress.isNotEmpty() && progress == progress.sorted())
        for ((path, bytes) in before) assertContentEquals(bytes, source[path])
        source.clear()
        val files = File(destination, "index.m3u8").readLines().filter { it.endsWith(".ts") }
        assertEquals(listOf("seg0.ts", "seg1.ts", "seg2.ts"), files)
        assertContentEquals(first + second + first, files.fold(byteArrayOf()) { sum, n -> sum + File(destination, n).readBytes() })
    }

    @Test fun segmentedMasterCopiesSeparateAudioVideoAndInitializationFiles() = runBlocking {
        put("index.m3u8", "#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",URI=\"audio.m3u8\"\n" +
            "#EXT-X-STREAM-INF:BANDWIDTH=900000,AUDIO=\"audio\"\nvideo.m3u8\n")
        for (track in listOf("audio", "video")) {
            put("$track.m3u8", media("$track.m4s").replace("#EXTINF", "#EXT-X-MAP:URI=\"$track.mp4\"\n#EXTINF"))
            put("$track.mp4", "initial-$track")
            put("$track.m4s", "segment-$track")
        }
        val destination = temp.newFolder()
        reader.copy("rec123", destination) {}
        assertEquals(source.keys.map { it.substringAfterLast('/') }.toSet(), destination.list()!!.toSet())
        for ((key, bytes) in source) assertContentEquals(bytes, File(destination, key.substringAfterLast('/')).readBytes())
    }

    @Test fun partialMediaReadAndOutOfBoundsJoinAreNeverMarkedPlayable() = runBlocking {
        val truncated = OfflineRecordingCopy({ source[it]?.size?.toLong() }) { path, offset, length, out ->
            read(path, offset, if (path.endsWith(".ts")) length - 1 else length, out)
        }
        put("index.m3u8", media("seg0.ts"))
        put("seg0.ts", "media")
        val destination = temp.newFolder()
        assertFailsWith<IOException> { truncated.copy("rec123", destination) {} }
        assertFalse(File(destination, "index.m3u8").exists())
        put("join.idx", "NUVIO-JOIN 1\nF\t1\tN\t1\tShow/file.ts\nP\tseg0.ts\t1\t1\t5\n")
        source["Show/file.ts"] = ByteArray(5)
        assertFailsWith<IOException> { reader.copy("rec123", temp.newFolder()) {} }
        Unit
    }

    @Test fun absentAndExternalDependenciesAndUnfinishedMediaAreRejected() = runBlocking {
        val broken = listOf(media("../outside.ts"), media("https://example.invalid/seg.ts"),
            media("missing.ts"), media("seg0.ts").replace("#EXT-X-ENDLIST", ""),
            media("seg0.ts").replace("#EXTINF", "#EXT-X-MAP:URI=\"https://example.invalid/init.mp4\"\n#EXTINF"))
        put("seg0.ts", "data")
        for (playlist in broken) {
            put("index.m3u8", playlist)
            val destination = temp.newFolder()
            assertFailsWith<IOException> { reader.copy("rec123", destination) {} }
            assertFalse(File(destination, "index.m3u8").exists())
        }
        put("index.m3u8", media("seg0.ts"))
        put("join.idx", "NUVIO-JOIN 1\nF\t1\tN\t1\t../outside.ts\nP\tseg0.ts\t1\t0\t4\n")
        assertFailsWith<IOException> { reader.copy("rec123", temp.newFolder()) {} }
        assertFailsWith<IllegalArgumentException> { reader.copy("../id", temp.newFolder()) {} }
        Unit
    }

    @Test fun cancellationDuringMediaTransferKeepsThePlaylistUnpublished() = runBlocking {
        put("index.m3u8", media("seg0.ts", "seg1.ts"))
        put("seg0.ts", "first")
        put("seg1.ts", "second")
        val destination = temp.newFolder()
        val work = launch { reader.copy("rec123", destination) { cancel(CancellationException("User cancelled")) } }
        work.join()
        assertTrue(work.isCancelled)
        assertFalse(File(destination, "index.m3u8").exists())
        assertContains(source, "$dir/seg1.ts")
    }
}

package com.nuvio.app.features.downloads

import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaylistFolderDownloadTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var provider: TestDriveProvider
    private val folder = DownloadFolder(DocumentsContract.buildTreeDocumentUri(AUTHORITY, ROOT).toString(), "USB")
    private val video get() = javaClass.getResourceAsStream("/autosync/video_tx3g.mp4")!!.use { it.readBytes() }

    @Before fun mount() {
        org.robolectric.Robolectric.setupContentProvider(com.nuvio.vpn.android.VpnInitializationProvider::class.java)
        provider = TestDriveProvider()
        provider.attachInfo(context, ProviderInfo().apply {
            authority = AUTHORITY
            exported = true
            grantUriPermissions = true
            readPermission = android.Manifest.permission.MANAGE_DOCUMENTS
            writePermission = android.Manifest.permission.MANAGE_DOCUMENTS
        })
        ShadowContentResolver.registerProviderInternal(AUTHORITY, provider)
    }

    @Test fun documentOperationsWork() {
        val docs = AndroidDownloadDocuments(context.contentResolver, folder)
        val partial = docs.createPartial("source.mp4")
        assertEquals(0L, docs.size(partial))
        docs.open(partial, 0L).use { it.write(video) }
        assertEquals(video.size.toLong(), docs.size(partial))
        val final = docs.publish(partial, "source.mp4")
        assertContentEquals(video, context.contentResolver.openInputStream(final)!!.use { it.readBytes() })
    }

    @Test fun movieAndEpisodeAreReadableOfflineAfterRestart(): Unit = runBlocking {
        val scheduler = AndroidDownloadScheduler(context)
        MockWebServer().use { server ->
            for (episode in listOf(false, true)) {
                server.enqueue(MockResponse().setBody(Buffer().write(video)))
                val name = if (episode) "show-S01E02.mp4" else "movie.mp4"
                val item = downloadItem(server.url("/${name}").toString(), name).copy(
                    folder = folder, fileName = name, seasonNumber = if (episode) 1 else null,
                    episodeNumber = if (episode) 2 else null,
                )
                assertFalse(scheduler.execute(scheduler.store.begin(item)) { })
                val saved = assertNotNull(scheduler.store.get(name))
                assertEquals(DownloadStatus.Completed, saved.item.status, saved.item.errorMessage)
                // Android AtomicFile uses POSIX rename-to-replace. Robolectric calls the
                // host File.renameTo, which cannot replace an existing file on Windows.
                if (File.separatorChar == '\\') {
                    File(context.filesDir, "download-transfers").listFiles().orEmpty()
                        .filter { it.name.endsWith(".json.new") }.forEach { staged ->
                            java.nio.file.Files.move(staged.toPath(), File(staged.path.removeSuffix(".new")).toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        }
                }
                val restored = AndroidDownloadScheduler(context).restore(item)
                assertEquals(DownloadStatus.Completed, restored.status, restored.errorMessage)
                val uri = Uri.parse(assertNotNull(restored.localFileUri))
                assertContentEquals(video, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
                assertFalse(File(scheduler.directory, name).exists())
                assertFalse(File(provider.directory, "$name.part").exists())
            }
        }
    }

    @Test fun invalidBinaryIsNeverPublished(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "video/mp4").setBody(Buffer().write(ByteArray(2048))))
            val scheduler = AndroidDownloadScheduler(context)
            val item = downloadItem(server.url("/bad.mp4").toString()).copy(folder = folder)
            assertFalse(scheduler.execute(scheduler.store.begin(item)) { })
            val result = scheduler.store.get(item.fileName)!!.item
            assertEquals(DownloadStatus.Failed, result.status)
            assertNull(result.localFileUri)
            assertFalse(File(provider.directory, item.fileName).exists())
        }
    }

    @Test fun unrelatedExistingFileIsPreserved(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(Buffer().write(video)))
            val scheduler = AndroidDownloadScheduler(context)
            val item = downloadItem(server.url("/movie.mp4").toString()).copy(folder = folder)
            val existing = File(provider.directory, item.fileName).apply { writeText("original") }
            assertFalse(scheduler.execute(scheduler.store.begin(item)) { })
            assertEquals(DownloadStatus.Failed, scheduler.store.get(item.fileName)!!.item.status)
            assertEquals("original", existing.readText())
        }
    }

    @Test fun disconnectedFolderFailsWithoutWritingToInternalStorage(): Unit = runBlocking {
        val scheduler = AndroidDownloadScheduler(context)
        provider.mounted = false
        val item = downloadItem().copy(folder = folder)
        assertFalse(scheduler.execute(scheduler.store.begin(item)) { })
        val result = scheduler.store.get(item.fileName)!!.item
        assertEquals(DownloadStatus.Failed, result.status)
        assertTrue(result.errorMessage!!.contains("Reconnect"))
        assertFalse(File(scheduler.directory, item.fileName).exists())
    }

    @Test fun redirectStripsCredentialsAndRejectsManifestBody(): Unit = runBlocking {
        MockWebServer().use { playlist ->
            MockWebServer().use { cdn ->
                playlist.enqueue(MockResponse().setResponseCode(302).setHeader("Location", cdn.url("/delivery")))
                cdn.enqueue(MockResponse().setBody("#EXTM3U\n#EXTINF:5,\nsegment.ts"))
                val item = downloadItem(playlist.url("/movie.mp4").toString()).copy(
                    folder = folder, sourceHeaders = mapOf("X-Provider-Key" to "test", "Cookie" to "secret"),
                )
                val scheduler = AndroidDownloadScheduler(context)
                assertFalse(scheduler.execute(scheduler.store.begin(item)) { })
                val result = scheduler.store.get(item.fileName)!!.item
                assertEquals(DownloadStatus.Failed, result.status)
                assertNull(result.localFileUri)
                assertFalse(File(provider.directory, item.fileName).exists())
                assertEquals("test", assertNotNull(playlist.takeRequest(2, TimeUnit.SECONDS), result.errorMessage).getHeader("X-Provider-Key"))
                val redirected = assertNotNull(cdn.takeRequest(2, TimeUnit.SECONDS), result.errorMessage)
                assertNull(redirected.getHeader("X-Provider-Key"))
                assertNull(redirected.getHeader("Cookie"))
            }
        }
    }
}

private const val AUTHORITY = "com.nuvio.test.playlistdrive"
private const val ROOT = "drive"

class TestDriveProvider : DocumentsProvider() {
    val directory by lazy { File(context!!.cacheDir, "test-drive").apply { mkdirs() } }
    var mounted = true
    override fun onCreate() = true
    private fun file(id: String): File {
        if (!mounted) throw FileNotFoundException()
        return if (id == ROOT) directory else File(directory, id.substringAfter('/'))
    }
    private fun id(file: File) = "$ROOT/${file.name}"

    override fun queryRoots(projection: Array<out String>?) = MatrixCursor(projection ?: emptyArray())
    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        rows(listOf(file(documentId)).filter(File::exists), projection)
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?) =
        rows(file(parentDocumentId).listFiles().orEmpty().toList(), projection)
    private fun rows(files: List<File>, projection: Array<out String>?): Cursor {
        val columns = projection ?: arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_SIZE)
        return MatrixCursor(columns).apply {
            files.forEach { f -> addRow(columns.map {
                when (it) {
                    Document.COLUMN_DOCUMENT_ID -> if (f == directory) ROOT else id(f)
                    Document.COLUMN_DISPLAY_NAME -> f.name
                    Document.COLUMN_SIZE -> f.length()
                    else -> null
                }
            }.toTypedArray<Any?>()) }
        }
    }
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode))
    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val f = File(file(parentDocumentId), displayName)
        check(f.createNewFile())
        return id(f)
    }
    override fun renameDocument(documentId: String, displayName: String): String {
        val dest = File(directory, displayName)
        check(!dest.exists() && file(documentId).renameTo(dest))
        return id(dest)
    }
    override fun deleteDocument(documentId: String) { check(file(documentId).delete()) }
    override fun isChildDocument(parentDocumentId: String, documentId: String) =
        parentDocumentId == ROOT && documentId.startsWith("$ROOT/")
}

package com.nuvio.app.features.player

import android.content.Intent
import com.nuvio.app.features.downloads.DownloadFolder
import com.nuvio.app.features.downloads.DownloadItem
import com.nuvio.app.features.downloads.DownloadStatus
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class PlaylistDocumentPlaybackTest {
    private val uri = "content://example.documents/tree/usb/document/movie"
    private val item = DownloadItem(id = "movie", contentType = "movie", parentMetaId = "movie", parentMetaType = "movie",
        videoId = "movie", title = "Movie", streamTitle = "Movie", providerName = "Fixture", sourceUrl = "",
        localFileUri = uri, fileName = "movie.mp4", status = DownloadStatus.Completed, createdAtEpochMs = 0,
        updatedAtEpochMs = 0, folder = DownloadFolder("content://example.documents/tree/usb", "USB"))

    @Test fun onlyCompletedFolderDocumentsRequireTheLocalPlayer() {
        assertTrue(isSavedPlaylistDocument(uri, listOf(item)))
        assertFalse(isSavedPlaylistDocument("$uri-other", listOf(item)))
        assertFalse(isSavedPlaylistDocument(uri, listOf(item.copy(folder = null))))
        assertFalse(isSavedPlaylistDocument(uri, listOf(item.copy(status = DownloadStatus.Downloading))))
        val network = "https://fixture.example/movie.mp4"
        assertFalse(isSavedPlaylistDocument(network, listOf(item.copy(localFileUri = network))))
    }

    @Test fun externalPlayerReceivesReadAccessOnlyToTheDocument() {
        ExternalPlayerPlatform.initialize(RuntimeEnvironment.getApplication())
        val request = ExternalPlayerPlaybackRequest(sourceUrl = uri, title = "Movie")
        val launch = ExternalPlayerPlatform.buildIntent(request, null) as ExternalPlayerIntentResult.Success
        val intent = launch.intent as Intent
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(uri, intent.dataString)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, intent.flags and (Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION))
    }
}

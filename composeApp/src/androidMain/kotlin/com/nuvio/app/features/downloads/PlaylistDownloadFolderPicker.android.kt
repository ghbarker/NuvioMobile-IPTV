package com.nuvio.app.features.downloads

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.playlist_download_folder_denied
import nuvio.composeapp.generated.resources.playlist_download_picker_failed
import org.jetbrains.compose.resources.stringResource

private class LocalDownloadTree : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?): Intent =
        super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}

@Composable
internal actual fun rememberPlaylistDownloadFolderPicker(
    profileId: Int,
    onResult: (DownloadFolder?) -> Unit,
): PlaylistDownloadFolderPicker? {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("playlist_download_folders", Context.MODE_PRIVATE) }
    var folder by remember(profileId) {
        mutableStateOf(prefs.getString("uri_$profileId", null)?.let { uri ->
            prefs.getString("label_$profileId", null)?.let { DownloadFolder(uri, it) }
        })
    }
    val callback by rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    val denied = stringResource(Res.string.playlist_download_folder_denied)
    val pickerFailed = stringResource(Res.string.playlist_download_picker_failed)
    // Activity Result may arrive after recreation or after a profile change.
    var requestingProfile by rememberSaveable { mutableStateOf<Int?>(null) }
    val launcher = rememberLauncherForActivityResult(remember { LocalDownloadTree() }) { uri ->
        val requested = requestingProfile
        requestingProfile = null
        if (requested != profileId || profileId != ProfileRepository.activeProfileId) return@rememberLauncherForActivityResult
        if (uri == null) {
            callback(null)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val selected = withContext(Dispatchers.IO) {
                try { writableFolder(context, uri) } catch (_: Exception) { null }
            }
            if (profileId != ProfileRepository.activeProfileId) return@launch
            if (selected == null) NuvioToastController.show(denied) else {
                prefs.edit().putString("uri_$profileId", selected.treeUri)
                    .putString("label_$profileId", selected.label).apply()
                folder = selected
            }
            callback(selected)
        }
    }
    return PlaylistDownloadFolderPicker(folder) {
        requestingProfile = profileId
        try {
            launcher.launch(folder?.treeUri?.let(Uri::parse))
        } catch (_: android.content.ActivityNotFoundException) {
            requestingProfile = null
            NuvioToastController.show(pickerFailed)
            callback(null)
        }
    }
}

private fun writableFolder(context: Context, uri: Uri): DownloadFolder? {
    if (!DocumentsContract.isTreeUri(uri)) return null
    val treeId = DocumentsContract.getTreeDocumentId(uri)
    val root = DocumentsContract.buildDocumentUriUsingTree(uri, treeId)
    val resolver = context.contentResolver
    val name = resolver.query(root, arrayOf(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_FLAGS), null, null, null)?.use {
        if (!it.moveToFirst() || it.getInt(1) and Document.FLAG_DIR_SUPPORTS_CREATE == 0) return null
        it.getString(0)
    }?.takeIf { it.isNotBlank() } ?: return null
    resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    val volume = if (uri.authority == "com.android.externalstorage.documents") {
        val id = treeId.substringBefore(':')
        context.getSystemService(StorageManager::class.java)?.storageVolumes?.firstOrNull {
            (it.isPrimary && id == "primary") || it.uuid.equals(id, ignoreCase = true)
        }?.getDescription(context)
    } else null
    return DownloadFolder(uri.toString(), listOfNotNull(volume, name).distinct().joinToString(" / "))
}

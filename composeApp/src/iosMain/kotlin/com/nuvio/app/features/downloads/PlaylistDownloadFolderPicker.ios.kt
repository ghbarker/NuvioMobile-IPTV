package com.nuvio.app.features.downloads

import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberPlaylistDownloadFolderPicker(
    profileId: Int,
    onResult: (DownloadFolder?) -> Unit,
): PlaylistDownloadFolderPicker? = null

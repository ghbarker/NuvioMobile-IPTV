package com.nuvio.app.features.downloads

import androidx.compose.runtime.Composable

internal class PlaylistDownloadFolderPicker(val folder: DownloadFolder?, val choose: () -> Unit)

@Composable
internal expect fun rememberPlaylistDownloadFolderPicker(
    profileId: Int,
    onResult: (DownloadFolder?) -> Unit = {},
): PlaylistDownloadFolderPicker?

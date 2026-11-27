package com.nuvio.app.features.player

import com.nuvio.app.features.downloads.DownloadItem

/** Media3 opens the granted document directly. The native engine otherwise asks for a playback
 * proxy even while offline and passes the Android document address to its native demuxer. */
internal fun isSavedPlaylistDocument(url: String, items: List<DownloadItem>): Boolean =
    url.startsWith("content://", ignoreCase = true) &&
        items.any { it.isPlayable && it.folder != null && it.localFileUri == url }

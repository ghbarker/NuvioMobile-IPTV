package com.nuvio.app.features.livetv.ondemand

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.downloads.DownloadFolder
import com.nuvio.app.features.downloads.DownloadsRepository
import com.nuvio.app.features.downloads.rememberPlaylistDownloadFolderPicker
import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.parental.ParentalControls
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.streams.StreamBehaviorHints
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamProxyHeaders
import kotlinx.coroutines.CancellationException
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.playlist_download_protected
import nuvio.composeapp.generated.resources.playlist_download_unavailable
import org.jetbrains.compose.resources.getString

/** Keep only catalog IDs across a trip to the system picker, including activity recreation. */
@Composable
internal fun rememberPlaylistDownloadAction(
    screenId: String,
    title: String,
    poster: String?,
): ((itemUid: String, episodeId: String?) -> Unit)? {
    val profileId = ProfileRepository.activeProfileId
    var pendingUid by rememberSaveable(screenId, profileId) { mutableStateOf<String?>(null) }
    var pendingEpisode by rememberSaveable(screenId, profileId) { mutableStateOf<String?>(null) }
    val picker = rememberPlaylistDownloadFolderPicker(profileId) { if (it == null) pendingUid = null } ?: return null
    LaunchedEffect(pendingUid, pendingEpisode, picker.folder) {
        val uid = pendingUid ?: return@LaunchedEffect
        val folder = picker.folder ?: return@LaunchedEffect
        val message = try {
            val item = OnDemandRepository.itemByUid(uid)
            val episodeId = pendingEpisode
            val episode = if (item != null && episodeId != null) {
                OnDemandRepository.info(item)?.episodes?.firstOrNull { it.id == episodeId }
            } else null
            if (item == null || (episodeId != null && episode == null)) {
                getString(Res.string.playlist_download_unavailable)
            } else {
                enqueuePlaylistDownload(item, episode, folder, profileId, title, poster)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            getString(Res.string.playlist_download_unavailable)
        }
        pendingUid = null
        NuvioToastController.show(message)
    }
    return { uid, episodeId ->
        pendingEpisode = episodeId
        pendingUid = uid
        if (picker.folder == null) picker.choose()
    }
}

private suspend fun enqueuePlaylistDownload(
    item: VodItem,
    episode: VodEpisode?,
    folder: DownloadFolder,
    profileId: Int,
    title: String,
    poster: String?,
): String {
    val adult = OnDemandRepository.isAdult(item)
    val playlist = OnDemandRepository.playlist(item.playlistId)
    val category = "${item.kind.key}:${item.playlistId}:${item.categoryId}"
    val settings = LiveTvPreferences.currentSettings()
    val user = LiveTvPreferences.currentUserState()
    if (profileId != ProfileRepository.activeProfileId || playlist == null || !playlist.enabled ||
        !playlist.importVod || playlist.isStalker || item.stalker != null || episode?.stalker != null ||
        user.hidesVodCategory(category) || (item.kind == VodKind.SERIES) != (episode != null)
    ) return getString(Res.string.playlist_download_unavailable)
    // A file exported to a shared drive cannot enforce an in-app PIN.
    if (ParentalControls.enabled(settings) && ("vod:$category" in user.lockedGroups || (adult && settings.lockAdultContent))) {
        return getString(Res.string.playlist_download_protected)
    }
    val url = if (episode == null) OnDemandRepository.movieUrl(item) else episode.url
    if (url.isNullOrBlank()) return getString(Res.string.playlist_download_unavailable)
    val stream = StreamItem(
        url = url, name = item.displayTitle, addonName = playlist.name, addonId = OnDemandStreams.GROUP_ID,
        behaviorHints = StreamBehaviorHints(proxyHeaders = StreamProxyHeaders(request = OnDemandRepository.headersFor(item))),
    )
    if (adult) OnDemandPlayback.markNeverSaved(item)
    return DownloadsRepository.enqueueFromStream(
        contentType = item.kind.key, videoId = OnDemandPlayback.videoId(item, episode),
        parentMetaId = OnDemandPlayback.metaId(item, null), parentMetaType = item.kind.key,
        title = title, logo = null, poster = poster, background = null,
        seasonNumber = episode?.season, episodeNumber = episode?.episode, episodeTitle = episode?.title,
        episodeThumbnail = episode?.image, stream = stream, folder = folder,
    ).toastMessage()
}

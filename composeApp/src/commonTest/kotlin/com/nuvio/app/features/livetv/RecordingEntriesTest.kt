package com.nuvio.app.features.livetv

import com.nuvio.app.features.livetv.record.LiveTvRecording
import com.nuvio.app.features.livetv.record.NetworkLibrary
import com.nuvio.app.features.livetv.record.OfflineRecordingKey
import com.nuvio.app.features.livetv.record.OfflineRecordingState
import com.nuvio.app.features.livetv.record.RecordingStatus
import com.nuvio.app.features.livetv.ui.OfflineAction
import com.nuvio.app.features.livetv.ui.RecordingEntry
import com.nuvio.app.features.livetv.ui.recordingEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordingEntriesTest {
    private val original = LiveTvRecording(
        id = "42", dir = NetworkLibrary.DIR_PREFIX + "42", fileName = "index.m3u8",
        channelKey = "ch", channelName = "channel", channelLogo = null, title = "An episode",
        startedAtMs = 100, stopAtMs = 200, status = RecordingStatus.DONE,
    )
    private val key = OfflineRecordingKey("//nas/tv", original.id)
    private val copy = original.copy(dir = "/phone/offline/one")
    private val ready = OfflineRecordingState(ready = mapOf(key to copy))
    private val online = NetworkLibrary.State(folderLabel = key.folderLabel, recordings = listOf(original), reachable = true)

    @Test
    fun oneCardPlaysLocalWithAndWithoutTheShareAndRemovalLeavesTheOriginalPlayable() {
        val card = recordingEntries(emptyList(), online, ready.ready).single()
        assertEquals(copy, card.playable(ready.ready, networkAway = false))
        assertEquals(copy, card.playable(ready.ready, networkAway = true))
        assertEquals(OfflineAction.REMOVE, card.downloadAction(ready, online))
        assertTrue(card.canDeleteFromNetwork(ready, online))
        assertEquals(original, card.playable(emptyMap(), networkAway = false))
        assertNull(card.playable(emptyMap(), networkAway = true))
    }

    @Test
    fun disconnectedOrRemovedShareLeavesOnlyLocalActions() {
        val offline = online.copy(reachable = false)
        for (network in listOf(offline, NetworkLibrary.State(), online.copy(recordings = emptyList()))) {
            val card = recordingEntries(emptyList(), network, ready.ready).single()
            assertEquals(copy, card.playable(ready.ready, networkAway = true))
            assertEquals(OfflineAction.REMOVE, card.downloadAction(ready, network))
            assertFalse(card.canDeleteFromNetwork(ready, network))
            assertNull(card.playable(emptyMap(), networkAway = true))
        }
    }

    @Test
    fun identicalIdsOnDifferentSharesAndTheDeviceKeepDistinctCardsAndActions() {
        val device = original.copy(dir = "/phone/recordings/42")
        val otherShare = online.copy(folderLabel = "//other/tv")
        val cards = recordingEntries(listOf(device), otherShare, ready.ready)
        assertEquals(3, cards.map { it.rowKey }.toSet().size)
        val current = cards.single { it.source?.folderLabel == otherShare.folderLabel }
        val saved = cards.single { it.source == key }
        assertEquals(original, current.playable(ready.ready, networkAway = false))
        assertEquals(OfflineAction.SAVE, current.downloadAction(ready, otherShare))
        assertEquals(copy, saved.playable(ready.ready, networkAway = true))
        assertFalse(saved.canDeleteFromNetwork(ready, otherShare))
        assertNull(cards.single { it.source == null }.downloadAction(ready, otherShare))
    }

    @Test
    fun onlyCompletedRemoteItemsCanStartAndActiveDownloadsCanCancelAfterDisconnect() {
        val card = RecordingEntry(original, key)
        val failed = OfflineRecordingState(failed = setOf(key))
        val copying = OfflineRecordingState(progress = mapOf(key to .4f))
        assertEquals(OfflineAction.RETRY, card.downloadAction(failed, online))
        assertNull(card.downloadAction(failed, online.copy(reachable = false)))
        assertEquals(OfflineAction.CANCEL, card.downloadAction(copying, online.copy(reachable = false)))
        assertFalse(card.canDeleteFromNetwork(copying, online))
        for (status in listOf(RecordingStatus.RECORDING, RecordingStatus.FAILED)) {
            val unfinished = card.copy(recording = original.copy(status = status))
            assertNull(unfinished.downloadAction(OfflineRecordingState(), online))
        }
    }
}

package com.nuvio.app.features.livetv.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.features.livetv.record.LiveTvRecording
import com.nuvio.app.features.livetv.record.RecordedShow
import com.nuvio.app.features.livetv.record.RecordingStatus
import com.nuvio.app.features.livetv.record.groupRecorded
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class RecordingFoldersUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun folderCountAndTapOpenEpisodesAndHeaderBackReturnsToTheFolderCard() {
        val show = show()
        val opened = mutableStateOf(false)
        var plays = 0
        compose.setContent {
            NuvioTheme {
                if (opened.value) {
                    RecordedShowPage(show, poster = null,
                        onPlay = { plays++ }, onMenu = {}, onDeleteAll = {},
                        onClose = { opened.value = false })
                } else Box(Modifier.width(180.dp)) {
                    RecordedFolderCard(show, poster = null,
                        onClick = { opened.value = true }, onLongClick = {})
                }
            }
        }

        compose.onNodeWithText("2 episodes", substring = true).assertIsDisplayed()
        compose.onNodeWithText(show.title).performClick()
        compose.onNodeWithText("S01E01").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, plays) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertEquals(false, opened.value) }
        compose.onNodeWithText("2 episodes", substring = true).assertIsDisplayed()
        compose.onNodeWithText("S01E01").assertDoesNotExist()
    }

    @Test
    fun orderedEpisodeRowsKeepPlaybackAndLongPressIdentityForSameIdAcrossStorage() {
        val show = show()
        val local = show.episodes.first()
        val network = show.episodes.last()
        var played: LiveTvRecording? = null
        var menu: LiveTvRecording? = null
        compose.setContent {
            NuvioTheme {
                RecordedShowPage(show, poster = null,
                    onPlay = { played = it }, onMenu = { menu = it },
                    onDeleteAll = {}, onClose = {})
            }
        }

        val first = compose.onNodeWithText("S01E01")
        val second = compose.onNodeWithText("S01E02")
        first.assertIsDisplayed()
        second.assertIsDisplayed()
        assertTrue(first.fetchSemanticsNode().boundsInRoot.top < second.fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithText("Play · S01E01").performClick()
        compose.runOnIdle { assertSame(local, played) }
        second.performClick()
        compose.runOnIdle {
            assertSame(network, played)
            assertNull(menu)
        }
        first.performClick()
        compose.runOnIdle { assertSame(local, played) }
        second.performSemanticsAction(SemanticsActions.OnLongClick) { assertTrue(it()) }
        compose.runOnIdle {
            assertSame(network, menu)
            assertSame(local, played)
            assertEquals(local.id, network.id)
        }
    }

    @Test
    fun deletingAFolderRequiresConfirmationAndCancelKeepsEveryEpisode() {
        val show = show()
        val confirming = mutableStateOf(false)
        var deletes = 0
        compose.setContent {
            NuvioTheme {
                RecordedShowPage(show, poster = null,
                    onPlay = {}, onMenu = {}, onDeleteAll = { confirming.value = true }, onClose = {})
                if (confirming.value) DeleteRecordedShowDialog(show,
                    onDismiss = { confirming.value = false },
                    onConfirm = { deletes++; confirming.value = false })
            }
        }

        compose.onNodeWithText("Delete all episodes").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, deletes) }
        compose.onNodeWithText("Delete all 2 recorded episodes?").assertIsDisplayed()
        compose.onNodeWithText("Episodes in the network folder will also be removed from every TV and phone using that folder.").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertEquals(0, deletes)
            assertEquals(false, confirming.value)
            assertEquals(2, show.episodes.size)
        }
        compose.onNodeWithText("Delete all episodes").performScrollTo().performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.runOnIdle {
            assertEquals(1, deletes)
            assertEquals(false, confirming.value)
        }
    }

    private fun show(): RecordedShow {
        val local = rec("S01E01", "/recordings/same", 1)
        val network = rec("S01E02", "net:same", 2)
        return assertIs<RecordedShow>(groupRecorded(listOf(network, local)).single())
    }

    private fun rec(episode: String, dir: String, at: Long) = LiveTvRecording(
        id = "same", channelKey = "fixture", channelName = "Fixture channel", channelLogo = null,
        title = "Fixture Show", episode = episode, startedAtMs = at, stopAtMs = at + 60_000,
        status = RecordingStatus.DONE, durationSec = 60.0, sizeBytes = 1_024, dir = dir,
    )
}

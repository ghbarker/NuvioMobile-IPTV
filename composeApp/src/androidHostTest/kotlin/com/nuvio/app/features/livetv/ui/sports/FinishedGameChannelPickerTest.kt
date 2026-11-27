package com.nuvio.app.features.livetv.ui.sports

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.features.livetv.model.LiveChannel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FinishedGameChannelPickerTest {
    @get:Rule val compose = createComposeRule()
    private val main = channel("Main broadcast", "main")
    private val alternate = channel("Alternate broadcast", "alternate")
    private var played: LiveChannel? = null

    @Test fun choosingAnAlternateTunesThatChannelEvenWithNoReplay() {
        compose.setContent {
            NuvioTheme { FinishedGameChannelPicker(listOf(main, alternate), listOf(main, alternate), false, { channel, _ -> played = channel }, {}) }
        }
        compose.onNodeWithText("Watch live channel").assertIsDisplayed()
        compose.runOnIdle { assertNull(played) }
        compose.onNodeWithText(alternate.name).performClick()
        compose.runOnIdle { assertEquals(alternate, played) }
    }

    @Test fun aFinishedGameWithoutAMatchCanTuneAnUnassignedPlaylistChannel() {
        compose.setContent {
            NuvioTheme { FinishedGameChannelPicker(emptyList(), listOf(alternate), false, { channel, _ -> played = channel }, {}) }
        }
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasText(alternate.name)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(alternate.name).performClick()
        compose.runOnIdle { assertEquals(alternate, played) }
    }

    @Test fun anEmptyLiveLookupDoesNotOfferPlayback() {
        var searching by mutableStateOf(true)
        compose.setContent {
            NuvioTheme { FinishedGameChannelPicker(emptyList(), emptyList(), searching, { channel, _ -> played = channel }, {}) }
        }
        compose.onNodeWithText("No live channels found for this game.").assertDoesNotExist()
        compose.runOnIdle { searching = false }
        compose.onNodeWithText("No live channels found for this game.").assertIsDisplayed()
        compose.runOnIdle { assertNull(played) }
    }

    private fun channel(name: String, key: String) = LiveChannel(
        key, "source", "Fixture", name, null, null, null, "group", "Sports", 1, "https://example.invalid/$key.m3u8",
    )
}

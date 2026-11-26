package com.nuvio.app.features.livetv

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.features.livetv.sports.GameState
import com.nuvio.app.features.livetv.sports.SportsEvent
import com.nuvio.app.features.livetv.ui.sports.FixChannelDialog
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FindGameChannelsDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyChannelListCanSearchAgainAndReturnToTheGameOnSuccess() {
        val event = SportsEvent(
            id = "test-card", leagueId = "ufc", startMs = 1, hasTime = true,
            state = GameState.LIVE, title = "UFC Test Card", subtitle = null, status = null,
            away = null, home = null,
        )
        val firstSearch = CompletableDeferred<Int>()
        val requested = mutableListOf<String>()
        var dismissed = false
        compose.setContent {
            NuvioTheme {
                FixChannelDialog(event, emptyList(), onDismiss = { dismissed = true }) { selected ->
                    requested += selected.id
                    if (requested.size == 1) firstSearch.await() else 2
                }
            }
        }
        compose.onNodeWithText("Find Channels").performClick()
        compose.onNodeWithText("Finding channels…").assertIsNotEnabled()
        compose.runOnIdle { firstSearch.complete(0) }
        compose.onNodeWithText("No channels matched this game.", substring = true).assertExists()
        compose.runOnIdle { assertFalse(dismissed) }
        compose.onNodeWithText("Find Channels").performClick()
        compose.runOnIdle {
            assertEquals(listOf(event.id, event.id), requested)
            assertTrue(dismissed)
        }
    }
}

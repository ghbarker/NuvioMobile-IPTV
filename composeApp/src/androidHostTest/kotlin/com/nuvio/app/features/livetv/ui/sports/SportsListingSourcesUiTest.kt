package com.nuvio.app.features.livetv.ui.sports

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.features.livetv.sports.EventSource
import com.nuvio.app.features.livetv.sports.GameState
import com.nuvio.app.features.livetv.sports.SportsEvent
import com.nuvio.app.features.livetv.sports.SportsFollows
import com.nuvio.app.features.livetv.sports.SportsListingSource
import com.nuvio.app.features.livetv.sports.SportsRow
import com.nuvio.app.features.livetv.sports.SportsRows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SportsListingSourcesUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tappingTheActualChipsChangesTheActualLiveAndUpcomingRows() {
        val now = 1_800_000_000_000L
        val league = "guide/motogp"
        val guide = (1..12).map { i -> SportsEvent("guide-$i", league, now, true, GameState.LIVE,
            "Guide race $i", null, null, null, null, source = EventSource.GUIDE) }
        val scheduled = SportsEvent("schedule", league, now, true, GameState.LIVE,
            "Portugal Sprint Race", null, null, null, null, source = EventSource.FIXTURES)
        compose.setContent {
            NuvioTheme(darkTheme = true) {
                var follows by remember { mutableStateOf(SportsFollows(pins = listOf(league))) }
                Column {
                    SportsListingSourceChoices(follows.listingSource(league), onSelect = {
                        follows = follows.withListingSource(league, it)
                    })
                    val row = SportsRows.build(guide + scheduled, follows, now) { it.leagueId == league }
                        .rows.first { it.kind == SportsRow.Kind.UPCOMING }
                    Text("Visible cards: ${row.events.size}")
                    row.events.forEach { Text(it.title) }
                }
            }
        }
        compose.onNodeWithText("Both").assertIsSelected()
        compose.onNodeWithText("Visible cards: 13").assertExists()
        compose.onNodeWithText("Scheduled events").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Visible cards: 1").assertExists()
        compose.onNodeWithText("Portugal Sprint Race").assertExists()
        compose.onAllNodesWithText("Guide race", substring = true).assertCountEquals(0)
        compose.onNodeWithText("TV guide listings").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Visible cards: 12").assertExists()
        compose.onAllNodesWithText("Portugal Sprint Race").assertCountEquals(0)
        compose.onNodeWithText("Both").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Visible cards: 13").assertExists()
    }
}

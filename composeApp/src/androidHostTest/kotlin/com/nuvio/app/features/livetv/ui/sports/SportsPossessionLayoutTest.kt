package com.nuvio.app.features.livetv.ui.sports

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.features.livetv.sports.GameState
import com.nuvio.app.features.livetv.sports.Side
import com.nuvio.app.features.livetv.sports.SportsEvent
import com.nuvio.app.features.livetv.sports.SportsFollows
import com.nuvio.app.features.livetv.sports.TeamInfo
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SportsPossessionLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun possessionSitsBetweenTheTeamAndItsScoreInBothCards() {
        val league = "football/nfl"
        val away = TeamInfo(league, "1", "Packers", "Green Bay Packers", "GB", null, null, null, null)
        val home = TeamInfo(league, "2", "Rams", "Los Angeles Rams", "LA", null, null, null, null)
        val awaySide = Side(away, "17", null, winner = false, home = false)
        val homeSide = Side(home, "10", null, winner = false, home = true)
        val event = SportsEvent(
            id = "fixture", leagueId = league, startMs = 0L, hasTime = false,
            state = GameState.LIVE, title = "Packers at Rams", subtitle = null, status = null,
            away = awaySide, home = homeSide,
        )
        val compact = mutableStateOf(false)
        val hide = mutableStateOf(false)
        val possession = mutableStateOf(away)
        compose.setContent {
            NuvioTheme {
                val live = event.copy(
                    away = awaySide.copy(onOffense = possession.value == away),
                    home = homeSide.copy(onOffense = possession.value == home),
                )
                if (compact.value) RowCard(live, SportsFollows(), 0L, false, hide.value, onClick = {})
                else GameCard(live, SportsFollows(), 0L, false, hide.value, channels = 2, showLeague = false, onClick = {})
            }
        }
        for (compactCard in listOf(false, true)) {
            compose.runOnIdle { compact.value = compactCard }
            for ((team, scoreText) in listOf(away to "17", home to "10")) {
                compose.runOnIdle { possession.value = team }
                val icon = compose.onNodeWithContentDescription("${team.name} has possession", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val label = compose.onNodeWithText(team.name, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val score = compose.onNodeWithText(scoreText, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertTrue("The marker must follow the team name", icon.left >= label.right)
                assertTrue("The marker must precede its score", icon.right <= score.left)
                assertTrue("The marker must share its score's row", icon.center.y in score.top..score.bottom)
                if (!compactCard) {
                    val well = compose.onNodeWithTag("sports_score_box", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    assertTrue("The marker must sit outside the score box", icon.right < well.left)
                    assertTrue("The score must remain inside the box", score.left >= well.left && score.right <= well.right)
                }
                val other = if (team == away) home else away
                compose.onNodeWithContentDescription("${other.name} has possession", useUnmergedTree = true).assertDoesNotExist()
            }
        }
        compose.runOnIdle { hide.value = true }
        compose.onNodeWithContentDescription("Rams has possession", useUnmergedTree = true).assertDoesNotExist()
    }
}

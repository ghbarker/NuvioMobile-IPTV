package com.nuvio.app.features.livetv.sports

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.livetv.ui.sports.GuideKind
import com.nuvio.app.features.livetv.ui.sports.GuideLine
import com.nuvio.app.features.livetv.ui.sports.SportsChannelRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-port-mdpi")
class SportsChannelRowQualityUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun eachCopyShowsWhereItsQualityCameFromAndTunesTheSelectedChannel() {
        val matches = listOf("TNT Sports 4 (720p25)", "TNT Sports 4 (1080p50)", "TNT Sports 4 (1080p25)")
            .mapIndexed { i, name ->
                val ch = LiveChannel("channel-$i", "tnt", "Playlist", name, null, null, null, "sports", "Sports", i, "")
                ChannelMatch(ch, 80, "Guide", null, null, false)
            }
        val rows = sportsGameChannelRows(matches, mapOf("channel-1" to "1920×1080 · 50 fps", "channel-2" to "1920×1080"), true)
        var selected = ""
        compose.setContent {
            NuvioTheme {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rows.forEach { row ->
                        SportsChannelRow(row, GuideLine(GuideKind.NOW, "Showing it now", null),
                            onPlay = { selected = it.channel.key }, onLongPress = {})
                    }
                }
            }
        }
        compose.onNodeWithText("1920×1080 · 50 fps", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("1080p25 reported", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Reported by provider", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("TNT Sports 4 (720p25)").performClick()
        compose.runOnIdle { assertEquals("channel-0", selected) }
    }
}

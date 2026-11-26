package com.nuvio.app.features.livetv.sports

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.livetv.model.BackupOrder
import com.nuvio.app.features.livetv.model.LiveTvSettings
import com.nuvio.app.features.livetv.ui.GameSourceOrderButton
import com.nuvio.app.features.livetv.ui.sports.GuideKind
import com.nuvio.app.features.livetv.ui.sports.GuideLine
import com.nuvio.app.features.livetv.ui.sports.SportsChannelRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-port-mdpi")
class SportsChannelRowQualityUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sourceOrderCanBePreviewedAppliedAndResetOnTheGamePage() {
        val playlists = linkedMapOf("main" to "Main IPTV", "backup" to "Backup IPTV")
        val addons = listOf("Sports Stream")
        val matches = listOf(
            LiveChannel("main1", "main", "Main IPTV", "Main feed FHD", null, null, null, "g", "Sports", 1, ""),
            LiveChannel("backup1", "backup", "Backup IPTV", "Backup feed UHD", null, null, null, "g", "Sports", 2, ""),
            LiveChannel("addon1", SportsAddonSource.SOURCE_ID, "Sports Stream", "Sports feed HD", null, null, null, "g", "Sports", 3, ""),
        ).map { ChannelMatch(it, 80, "Guide", null, null, false) }
        var settings by mutableStateOf(LiveTvSettings(backupGroupBy = BackupOrder.QUALITY))
        var selected = ""
        compose.setContent {
            NuvioTheme {
                Column {
                    GameSourceOrderButton(settings, playlists, addons) { settings = it(settings) }
                    val order = BackupOrder.gameSources(settings, playlists.keys.toList(), addons)
                    sportsGameChannelRows(matches, emptyMap(), true, order).forEach { row ->
                        SportsChannelRow(row, null, onPlay = { selected = it.channel.key }, onLongPress = {})
                    }
                }
            }
        }
        fun open() = compose.onNodeWithText("Sort sources").performClick()
        fun moveUp() = compose.onNodeWithContentDescription("Move Sports Stream up").performClick()
        fun top(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top
        open()
        moveUp()
        compose.onNodeWithText("Close").performClick()
        compose.runOnIdle { assertEquals("", settings.backupSourceOrder) }
        open()
        moveUp()
        moveUp()
        compose.onNodeWithText("Use this order").performClick()
        assertTrue(top("Sports feed HD") < top("Main feed FHD"))
        assertTrue(top("Main feed FHD") < top("Backup feed UHD"))
        compose.onNodeWithText("Sports feed HD").performClick()
        compose.runOnIdle { assertEquals("addon1", selected) }
        open()
        compose.onNodeWithText("Reset source order").performClick()
        assertTrue(top("Backup feed UHD") < top("Sports feed HD"))
    }

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

package com.nuvio.app.features.livetv

import com.nuvio.app.features.addons.AddonCatalog
import com.nuvio.app.features.addons.AddonManifest
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.livetv.sports.GameState
import com.nuvio.app.features.livetv.sports.SportsAddonSource
import com.nuvio.app.features.livetv.sports.SportsEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SportsOvertimeAddonTest {
    private val hour = 3_600_000L
    private val game = SportsEvent("game", "football/nfl", 1_800_000_000_000L, true, GameState.POST, "Final", null, "Final", null, null)
    private val catalog = AddonCatalog("sport", "live-events", "Live events")
    private val addon = ManagedAddon("https://example.invalid/manifest.json",
        AddonManifest("fixture", "Fixture", "", "1", resources = emptyList(), types = listOf("sport"), catalogs = listOf(catalog), transportUrl = "https://example.invalid"))

    @Test fun recentFinalAndLongScheduledEventsCanLookForLiveAddonStreams() {
        fun possible(event: SportsEvent, hours: Int) =
            SportsAddonSource.canLookForOvertime(event, game.startMs + hours * hour, listOf(addon))
        assertTrue(possible(game, 4))
        assertTrue(possible(game.copy(endMs = game.startMs + 10 * hour), 11))
        assertFalse(possible(game, 24))
        assertFalse(possible(game.copy(state = GameState.OFF), 4))
        assertFalse(possible(game.copy(hasTime = false), 4))
    }

    @Test fun replayOnlyOrDisabledAddonsDoNotOfferALiveLookup() {
        val replay = addon.copy(manifest = addon.manifest!!.copy(catalogs = listOf(catalog.copy(id = "replays", name = "Game replays"))))
        assertFalse(SportsAddonSource.canLookForOvertime(game, game.startMs + hour, listOf(addon.copy(enabled = false), replay)))
    }
}

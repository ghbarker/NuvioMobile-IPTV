package com.nuvio.app.features.livetv.multiview

import com.nuvio.app.features.livetv.model.LiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MultiviewSessionTest {
    @Test
    fun layoutsHaveTheRequestedRowsWithoutDuplicatePanes() {
        assertEquals(listOf(listOf(0, 1)), MultiviewLayout.DUAL.rows)
        assertEquals(listOf(listOf(0, 1), listOf(2)), MultiviewLayout.TRIPLE.rows)
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), MultiviewLayout.QUAD.rows)
        MultiviewLayout.entries.forEach { assertEquals((0 until it.paneCount).toList(), it.rows.flatten()) }
    }

    @Test
    fun selectingAndClearingOtherPanesNeverStealsAudio() {
        val initial = MultiviewSelection().assign(0, channel("one")).assign(1, channel("two"))
        assertNull(initial.focusedPane)
        val focused = initial.focus(0)
        assertEquals(0, focused.assign(1, channel("replacement")).focusedPane)
        assertEquals(0, focused.assign(1, null).focusedPane)
        assertNull(focused.assign(0, null).focusedPane)
        assertEquals(focused, focused.focus(3))
    }

    @Test
    fun reducingLayoutMutesHiddenFocusAndRetainsAssignmentsForLater() {
        val quad = MultiviewSelection(layout = MultiviewLayout.QUAD).assign(3, channel("four")).focus(3)
        val dual = quad.withLayout(MultiviewLayout.DUAL)
        assertNull(dual.focusedPane)
        assertEquals("four", dual.channels[3]?.key)
        assertNull(dual.withLayout(MultiviewLayout.QUAD).focusedPane)
        assertEquals(dual, dual.assign(3, channel("disallowed-hidden-change")))
    }

    @Test
    fun parentalOrVisibilityChangesClearAllDisallowedPanesIncludingHiddenOnes() {
        val selected = MultiviewSelection(layout = MultiviewLayout.QUAD)
            .assign(0, channel("visible"))
            .assign(1, channel("locked"))
            .assign(3, channel("hidden"))
            .focus(1)
            .withLayout(MultiviewLayout.DUAL)
        val refreshed = channel("visible").copy(name = "Updated channel")
        val permitted = selected.retainAllowed(mapOf(refreshed.key to refreshed))
        assertEquals(refreshed, permitted.channels[0])
        assertNull(permitted.channels[1])
        assertNull(permitted.channels[3])
        assertNull(permitted.focusedPane)
    }

    @Test
    fun profileSessionsDoNotShareAssignmentsOrAudio() {
        val first = MultiviewSession(profileId = 1)
        first.assign(0, channel("one"))
        first.focus(0)
        val nextProfile = MultiviewSession(profileId = 2)
        assertTrue(first.isForProfile(1))
        assertFalse(first.isForProfile(2))
        assertFalse(first.isForProfile(null))
        assertTrue(nextProfile.isForProfile(2))
        assertTrue(nextProfile.selection.value.channels.all { it == null })
        assertNull(nextProfile.selection.value.focusedPane)
        assertEquals("one", first.selection.value.channels[0]?.key)
    }

    @Test
    fun everyAudioHandoffMutesAllRegisteredPlayersBeforeUnmutingTarget() {
        val calls = mutableListOf<String>()
        val players = List(4) { FakePlayer(it, calls) }
        val coordinator = MultiviewAudioCoordinator()
        players.forEachIndexed { index, player -> coordinator.register(index, player) }
        assertTrue(players.all { it.muted })
        coordinator.focus(0)
        calls.clear()
        coordinator.focus(3)
        assertEquals(listOf("0:true", "1:true", "2:true", "3:true", "3:false"), calls)
        assertEquals(listOf(3), players.filterNot { it.muted }.map { it.index })
        // A native focus loss may silence a player without changing the selected pane.
        calls.clear()
        coordinator.focus(3)
        assertEquals(listOf("0:true", "1:true", "2:true", "3:true", "3:false"), calls)
        coordinator.muteAll()
        assertTrue(players.all { it.muted })
    }

    @Test
    fun registrationReplacementAndRemovalNeverLeaveTwoPlayersAudible() {
        val calls = mutableListOf<String>()
        val players = mutableListOf<FakePlayer>()
        fun player(index: Int) = FakePlayer(index, calls) {
            assertTrue(players.count { !it.muted } <= 1, "Multiple audible players during $calls")
        }.also { players += it }
        val first = player(0)
        val second = player(1)
        val replacement = player(1)
        val coordinator = MultiviewAudioCoordinator()
        coordinator.focus(0)
        coordinator.register(0, first)
        coordinator.register(1, second)
        coordinator.focus(1)
        coordinator.register(1, replacement)
        assertTrue(second.muted)
        assertTrue(!replacement.muted)
        // A late disposal callback from the replaced owner must not unregister its replacement.
        coordinator.unregister(1, second)
        assertTrue(!replacement.muted)
        coordinator.unregister(1, replacement)
        assertTrue(players.all { it.muted })
        coordinator.muteAll()
    }

    private class FakePlayer(
        val index: Int,
        private val calls: MutableList<String>,
        private val changed: () -> Unit = {},
    ) : MultiviewPlayer {
        override val state = MutableStateFlow(MultiviewTileState())
        var muted = true
            private set
        override fun play(channel: LiveChannel) = Unit
        override fun setMuted(muted: Boolean) {
            this.muted = muted
            calls += "$index:$muted"
            changed()
        }
        override fun setPaused(paused: Boolean) = Unit
        override fun release() = Unit
    }

    private fun channel(key: String) = LiveChannel(
        key = key,
        sourceId = "fixture",
        sourceName = "Fixture",
        name = key,
        tvgId = null,
        tvgName = null,
        logo = null,
        groupId = "fixture:group",
        group = "Channels",
        number = 1,
        url = "https://example.invalid/live.m3u8",
    )
}

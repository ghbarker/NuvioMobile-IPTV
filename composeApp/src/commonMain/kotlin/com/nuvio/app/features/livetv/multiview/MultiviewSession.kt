package com.nuvio.app.features.livetv.multiview

import com.nuvio.app.features.livetv.model.LiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class MultiviewLayout(val paneCount: Int, val title: String) {
    DUAL(2, "Dual"), TRIPLE(3, "Triple"), QUAD(4, "Quad");

    /** Rows are shared by rendering and tests; Dual always remains side by side. */
    val rows: List<List<Int>>
        get() = when (this) {
            DUAL -> listOf(listOf(0, 1))
            TRIPLE -> listOf(listOf(0, 1), listOf(2))
            QUAD -> listOf(listOf(0, 1), listOf(2, 3))
        }
}

data class MultiviewSelection(
    val layout: MultiviewLayout = MultiviewLayout.DUAL,
    val channels: List<LiveChannel?> = List(4) { null },
    /** Overlay controls never change this. Only a tap on a populated video chooses audio. */
    val focusedPane: Int? = null,
) {
    fun withLayout(next: MultiviewLayout) = copy(
        layout = next,
        focusedPane = focusedPane?.takeIf { it < next.paneCount },
    )

    fun assign(pane: Int, channel: LiveChannel?): MultiviewSelection {
        if (pane !in 0 until layout.paneCount) return this
        return copy(
            channels = channels.mapIndexed { index, old -> if (index == pane) channel else old },
            focusedPane = focusedPane?.takeUnless { it == pane && channel == null },
        )
    }

    fun focus(pane: Int): MultiviewSelection =
        if (pane in 0 until layout.paneCount && channels[pane] != null) copy(focusedPane = pane) else this

    fun retainAllowed(allowed: Map<String, LiveChannel>): MultiviewSelection {
        val refreshed = channels.map { old -> old?.let { allowed[it.key] } }
        return copy(channels = refreshed, focusedPane = focusedPane?.takeIf { refreshed[it] != null })
    }
}

/** In-memory only, recreated for every profile. No stream URLs enter routes or saved state. */
class MultiviewSession(val profileId: Int? = null) {
    private val mutableSelection = MutableStateFlow(MultiviewSelection())
    val selection: StateFlow<MultiviewSelection> = mutableSelection.asStateFlow()

    fun isForProfile(currentProfileId: Int?): Boolean = profileId == currentProfileId

    fun setLayout(layout: MultiviewLayout) = mutableSelection.update { it.withLayout(layout) }
    fun assign(pane: Int, channel: LiveChannel?) = mutableSelection.update { it.assign(pane, channel) }
    fun focus(pane: Int) = mutableSelection.update { it.focus(pane) }
    fun retainAllowed(channels: Map<String, LiveChannel>) = mutableSelection.update { it.retainAllowed(channels) }
}

/** All audio handoffs mute every registered pane before enabling the selected one. */
internal class MultiviewAudioCoordinator {
    private val players = LinkedHashMap<Int, MultiviewPlayer>()
    private var focusedPane: Int? = null

    fun register(pane: Int, player: MultiviewPlayer) {
        player.setMuted(true)
        players.put(pane, player)?.takeUnless { it === player }?.setMuted(true)
        reconcile()
    }

    fun unregister(pane: Int, player: MultiviewPlayer) {
        player.setMuted(true)
        if (players[pane] === player) players.remove(pane)
        reconcile()
    }

    fun focus(pane: Int?) {
        focusedPane = pane
        reconcile()
    }

    fun muteAll() {
        focusedPane = null
        players.values.forEach { it.setMuted(true) }
    }

    private fun reconcile() {
        players.values.forEach { it.setMuted(true) }
        focusedPane?.let { players[it]?.setMuted(false) }
    }
}

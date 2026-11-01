package com.nuvio.app.features.livetv.ui

import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.data.LiveTvRepository
import com.nuvio.app.features.livetv.model.ChannelGroup
import com.nuvio.app.features.livetv.model.ChannelSort
import com.nuvio.app.features.livetv.model.CustomGroup
import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.livetv.model.LiveTvSettings
import com.nuvio.app.features.livetv.model.LiveUserState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One group in the channel list, with its channels in display order. */
data class LiveGroupSection(
    val group: ChannelGroup,
    val channels: List<LiveChannel>,
)

/** The channel list as the Live TV tab shows it: groups in order, favorites first. */
data class LiveTvListState(
    val sections: List<LiveGroupSection> = emptyList(),
    /** Every channel that isn't hidden, sorted, for search and the guide. */
    val visible: List<LiveChannel> = emptyList(),
    val favoriteKeys: Set<String> = emptySet(),
    val customGroups: List<CustomGroup> = emptyList(),
    val hasSources: Boolean = false,
)

/**
 * Builds the grouped channel list from the repository's channels and your preferences, the TV
 * app's rules: hidden channels and groups left out, your own groups first, then the playlist's
 * groups in your order (or by name), channels sorted as the Channel Sort setting says, with
 * your "Reorder channels" order on top.
 */
internal object LiveTvViewState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val list: StateFlow<LiveTvListState> = combine(
        LiveTvRepository.displayChannels,
        LiveTvPreferences.userState,
        LiveTvPreferences.settings,
        LiveTvPreferences.playlists,
    ) { channels, user, s, playlists ->
        build(channels, user, s, playlists.any { it.liveEnabled })
    }.stateIn(scope, SharingStarted.Eagerly, LiveTvListState())

    fun build(channels: List<LiveChannel>, user: LiveUserState, s: LiveTvSettings, hasSources: Boolean): LiveTvListState {
        val visible = channels.filter { it.key !in user.hiddenChannels && it.groupId !in user.hiddenGroups }
        val byKey = visible.associateBy { it.key }
        val favorites = user.favorites.mapNotNull { byKey[it] }
        val recent = user.recent.mapNotNull { byKey[it] }

        fun sorted(list: List<LiveChannel>): List<LiveChannel> = when (s.channelSort) {
            ChannelSort.PLAYLIST -> list
            ChannelSort.NUMBER -> list.sortedBy { it.number }
            ChannelSort.NAME -> list.sortedBy { it.name.lowercase() }
        }

        fun ordered(groupId: String, list: List<LiveChannel>): List<LiveChannel> {
            val order = user.channelOrder[groupId] ?: return sorted(list)
            val here = list.associateBy { it.key }
            val placed = order.mapNotNull { here[it] }
            val placedKeys = placed.mapTo(HashSet()) { it.key }
            return placed + sorted(list.filter { it.key !in placedKeys })
        }

        val sections = ArrayList<LiveGroupSection>()
        if (s.showFavoritesGroup && favorites.isNotEmpty()) {
            sections += LiveGroupSection(ChannelGroup(ChannelGroup.FAVORITES, "Favorites", favorites.size, special = true), sorted(favorites))
        }
        if (s.showRecentGroup && recent.isNotEmpty()) {
            sections += LiveGroupSection(ChannelGroup(ChannelGroup.RECENT, "Recently watched", recent.size, special = true), recent)
        }

        val regular = ArrayList<LiveGroupSection>()
        val custom = user.customGroups.filter { it.id !in user.hiddenGroups }
        custom.forEach { g ->
            val list = sorted(g.channelKeys.mapNotNull { byKey[it] })
            regular += LiveGroupSection(ChannelGroup(g.id, g.name, list.size), list)
        }
        val playlistGroups = LinkedHashMap<String, Pair<String, ArrayList<LiveChannel>>>()
        visible.forEach { c ->
            val cur = playlistGroups.getOrPut(c.groupId) { c.group to ArrayList() }
            cur.second += c
        }
        val groupSource = HashMap<String, String>()
        visible.forEach { c -> if (c.groupId !in groupSource) groupSource[c.groupId] = c.sourceId }
        playlistGroups.forEach { (id, v) ->
            val own = v.second
            val ownKeys = own.mapTo(HashSet()) { it.key }
            val copies = user.channelCopies[id].orEmpty().mapNotNull { byKey[it] }.filter { it.key !in ownKeys }
            val list = ordered(id, own + copies).distinctBy { it.key }
            regular += LiveGroupSection(ChannelGroup(id, v.first, list.size, sourceId = groupSource[id]), list)
        }
        val orderIndex = user.groupOrder.withIndex().associate { (i, id) -> id to i }
        val playlistIndex = HashMap<String, Int>().also { m -> visible.forEach { c -> if (c.sourceId !in m) m[c.sourceId] = m.size } }
        val orderedRegular = when (s.groupSort) {
            "playlist" -> regular
            "name" -> regular.sortedWith(
                compareBy<LiveGroupSection>(
                    { g -> if (g.group.sourceId == null) -1 else if (s.groupPlaylistHeadings) playlistIndex[g.group.sourceId] ?: Int.MAX_VALUE else 0 },
                    { g -> g.group.title.lowercase() }
                )
            )
            else -> regular.withIndex()
                .sortedWith(compareBy<IndexedValue<LiveGroupSection>>({ orderIndex[it.value.group.id] ?: Int.MAX_VALUE }, { it.index }))
                .map { it.value }
        }
        sections += orderedRegular
        val numbered = if (s.sequentialNumbers) {
            sections.map { sec -> sec.copy(channels = sec.channels.mapIndexed { i, c -> c.copy(number = i + 1) }) }
        } else sections
        return LiveTvListState(
            sections = numbered,
            visible = sorted(visible),
            favoriteKeys = user.favorites.toHashSet(),
            customGroups = custom,
            hasSources = hasSources,
        )
    }

    /** Channels whose name or number matches [query], from the visible list. */
    fun search(visible: List<LiveChannel>, query: String): List<LiveChannel> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return visible.filter { it.name.contains(q, ignoreCase = true) || it.number.toString() == q }
    }
}

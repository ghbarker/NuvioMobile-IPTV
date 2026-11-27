package com.nuvio.app.features.livetv.home

import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.LiveHomeRowDefinition
import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.model.ChannelGroup
import com.nuvio.app.features.livetv.ondemand.OnDemandGroups
import com.nuvio.app.features.livetv.ondemand.OnDemandRepository
import com.nuvio.app.features.livetv.ondemand.VodKind
import com.nuvio.app.features.livetv.sports.JSONArray
import com.nuvio.app.features.livetv.sports.JSONObject
import com.nuvio.app.features.livetv.sports.Sport
import com.nuvio.app.features.livetv.sports.SportsCatalog
import com.nuvio.app.features.livetv.ui.LiveTvViewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.home_rows_channels_title
import nuvio.composeapp.generated.resources.home_rows_favorites_title
import nuvio.composeapp.generated.resources.home_rows_options_channels_subtitle
import nuvio.composeapp.generated.resources.home_rows_options_sports_subtitle
import nuvio.composeapp.generated.resources.home_rows_options_vod_subtitle
import nuvio.composeapp.generated.resources.home_rows_recent_title
import nuvio.composeapp.generated.resources.home_rows_sports_one_title
import nuvio.composeapp.generated.resources.home_rows_sports_title
import nuvio.composeapp.generated.resources.home_rows_type_vod
import nuvio.composeapp.generated.resources.home_rows_vod_recent_movies_title
import nuvio.composeapp.generated.resources.home_rows_vod_recent_series_title
import nuvio.composeapp.generated.resources.home_rows_vod_continue_title
import org.jetbrains.compose.resources.getString
import kotlin.random.Random

/**
 * A Live TV, Sports or On Demand row on Home, added from Settings › Homescreen (Add a row).
 * Each row sits in Home's own row list under [key], so it moves, turns off and on and is renamed
 * like an addon catalog. Kept per profile, in the TV app's format (the same key and JSON).
 */
data class HomeLiveRow(
    val id: String,
    /** [KIND_CHANNELS], [KIND_SPORTS] or [KIND_VOD]. */
    val kind: String,
    /**
     * Channels: the group (Favorites, Recently watched, a playlist group or one of your own).
     * Sports: "" for every sport, "sport:<SPORT>" or "league:<league id>".
     * On Demand: "recent:movie", "recent:series" or "group:<category uid>".
     */
    val source: String,
    /** Your name for the row; blank = the group's or league's own name. */
    val title: String = "",
    /** Channels: "yours" (the group's order), "number" or "ending" (ending soonest first). */
    val sort: String = "yours",
    val limit: Int = ALL,
    /** Channels: what's on next under what's on now. */
    val showNext: Boolean = false,
    /** Channels: leave out channels with no real listing ("No information"). */
    val hidePlaceholders: Boolean = false,
    /** Channels: a tap opens the guide (instead of full screen). */
    val opensGuide: Boolean = false,
    /** Sports: a tap plays the game's best channel (instead of opening the game page). */
    val playsGame: Boolean = false,
    /** Sports: fill the row with games starting soon (always after the live ones). */
    val startingSoon: Boolean = true,
    /** [SHAPE_AUTO], [SHAPE_PORTRAIT] or [SHAPE_LANDSCAPE]. On the phone, auto is landscape. */
    val shape: String = SHAPE_AUTO,
    /** The row it sits under on Home ("" = the top), so a new device puts it in the same place. */
    val after: String = "",
    /** On Demand: the category's own name when it was added (shown until you rename the row). */
    val label: String = "",
) {
    val key: String get() = KEY_PREFIX + id
    val portrait: Boolean get() = shape == SHAPE_PORTRAIT

    companion object {
        const val KEY_PREFIX = "livetv_"
        const val KIND_CHANNELS = "channels"
        const val KIND_SPORTS = "sports"
        const val KIND_VOD = "ondemand"
        const val VOD_RECENT_MOVIES = "recent:movie"
        const val VOD_RECENT_SERIES = "recent:series"
        /** The On Demand titles you started and haven't finished, last watched first (same id as the TV). */
        const val VOD_CONTINUE = "continue"
        const val VOD_GROUP_PREFIX = "group:"
        const val SHAPE_AUTO = "auto"
        const val SHAPE_PORTRAIT = "portrait"
        const val SHAPE_LANDSCAPE = "landscape"
        const val ALL = 0

        fun isKey(key: String): Boolean = key.startsWith(KEY_PREFIX)
        fun idOf(key: String): String? = key.takeIf(::isKey)?.removePrefix(KEY_PREFIX)

        fun channels(group: String) = HomeLiveRow(newId(), KIND_CHANNELS, group)
        fun sports(source: String = "") = HomeLiveRow(newId(), KIND_SPORTS, source)
        fun vod(source: String, label: String = "") = HomeLiveRow(newId(), KIND_VOD, source, label = label)

        private fun newId(): String =
            com.nuvio.app.features.livetv.model.LiveTvClock.nowMs().toString(36) + Random.nextInt(1296).toString(36)

        fun decode(json: String?): List<HomeLiveRow> {
            if (json.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val kind = o.optString("kind").takeIf { it == KIND_CHANNELS || it == KIND_SPORTS || it == KIND_VOD }
                        ?: return@mapNotNull null
                    HomeLiveRow(
                        id = id,
                        kind = kind,
                        source = o.optString("source"),
                        title = o.optString("title"),
                        sort = o.optString("sort", "yours"),
                        limit = ALL,
                        showNext = o.optBoolean("showNext", false),
                        hidePlaceholders = o.optBoolean("hidePlaceholders", false),
                        opensGuide = o.optBoolean("opensGuide", false),
                        playsGame = o.optBoolean("playsGame", false),
                        startingSoon = o.optBoolean("startingSoon", true),
                        shape = o.optString("shape", SHAPE_AUTO),
                        after = o.optString("after"),
                        label = o.optString("label"),
                    )
                }.distinctBy { it.id }
            }.getOrDefault(emptyList())
        }

        fun encode(rows: List<HomeLiveRow>): String = JSONArray().apply {
            rows.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id)
                    put("kind", r.kind)
                    put("source", r.source)
                    put("title", r.title)
                    put("sort", r.sort)
                    put("limit", r.limit)
                    put("showNext", r.showNext)
                    put("hidePlaceholders", r.hidePlaceholders)
                    put("opensGuide", r.opensGuide)
                    put("playsGame", r.playsGame)
                    put("startingSoon", r.startingSoon)
                    put("shape", r.shape)
                    put("after", r.after)
                    put("label", r.label)
                })
            }
        }.toString()
    }
}

/**
 * This profile's Home rows, their names, and changes to them. The rows are handed to Home's row
 * list ([HomeCatalogSettingsRepository]) so they show in Settings › Homescreen with the catalogs.
 */
@OptIn(FlowPreview::class)
internal object HomeLiveRows {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs = LiveTvPreferences

    val rows: StateFlow<List<HomeLiveRow>> = prefs.homeRowsJson
        .map { HomeLiveRow.decode(it) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** The rows Home shows: none while Live TV is switched off. */
    val shownRows: StateFlow<List<HomeLiveRow>> = combine(rows, prefs.settings) { r, s -> if (s.showInSidebar) r else emptyList() }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    // Only catalog publication or a row source change reads group names. Presentation changes
    // use this small snapshot; they never reload titles or repeat metadata enrichment.
    private val vodNames: StateFlow<Map<String, String>> = combine(
        rows.map { list -> list.filter { it.kind == HomeLiveRow.KIND_VOD && it.source.startsWith(HomeLiveRow.VOD_GROUP_PREFIX) }
            .map { it.source.removePrefix(HomeLiveRow.VOD_GROUP_PREFIX) }.toSet() }.distinctUntilChanged(),
        OnDemandRepository.version,
    ) { ids, _ ->
        if (ids.isEmpty()) emptyMap()
        else VodKind.entries.filter { kind -> ids.any { it.startsWith("${kind.key}:") } }
            .flatMap { OnDemandRepository.categories(it) }.filter { it.uid in ids }.associate { it.uid to it.name }
    }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** Each row's name as shown on Home and in Settings › Homescreen, by row id. */
    val names: StateFlow<Map<String, String>> = combine(
        rows,
        prefs.userState.map { Triple(it.customGroups, it.groupNames, it.vodGroupNames) }.distinctUntilChanged(),
        LiveTvViewState.list.map { l -> l.sections.associate { it.group.id to (it.group.title to it.group.sourceId) } }.distinctUntilChanged(),
        combine(prefs.playlists.map { l -> l.associate { it.id to it.name } }.distinctUntilChanged(), vodNames) { playlists, vod -> playlists to vod },
        prefs.settings.map { Triple(it.homeRowPlaylistNames, it.nameRemovals, it.cleanVodGroupNames) }.distinctUntilChanged(),
    ) { list, (custom, renamed, vodRenamed), groups, (playlistNames, vod), (withPlaylist, removals, cleanVod) ->
        list.associate { row -> row.id to (row.title.ifBlank { defaultName(row, custom, renamed, vodRenamed, groups, playlistNames, vod, withPlaylist, removals, cleanVod) }) }
    }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private suspend fun defaultName(
        row: HomeLiveRow,
        custom: List<com.nuvio.app.features.livetv.model.CustomGroup>,
        renamed: Map<String, String>,
        vodRenamed: Map<String, String>,
        groups: Map<String, Pair<String, String?>>,
        playlistNames: Map<String, String>,
        vodNames: Map<String, String>,
        withPlaylist: Boolean,
        removals: String,
        cleanVod: Boolean,
    ): String = when (row.kind) {
        HomeLiveRow.KIND_SPORTS -> sportsTitle(row.source)
        HomeLiveRow.KIND_VOD -> when (row.source) {
            HomeLiveRow.VOD_RECENT_MOVIES -> getString(Res.string.home_rows_vod_recent_movies_title)
            HomeLiveRow.VOD_RECENT_SERIES -> getString(Res.string.home_rows_vod_recent_series_title)
            HomeLiveRow.VOD_CONTINUE -> getString(Res.string.home_rows_vod_continue_title)
            else -> {
                val uid = row.source.removePrefix(HomeLiveRow.VOD_GROUP_PREFIX)
                val raw = vodNames[uid] ?: row.label.ifBlank { getString(Res.string.home_rows_type_vod) }
                val name = OnDemandGroups.displayName(raw, vodRenamed[uid], removals, cleanVod)
                // The category's uid is "<movie|series>:<playlist id>:<category id>".
                val playlist = uid.split(':', limit = 3).getOrNull(1)?.let { playlistNames[it] }?.takeIf { it.isNotBlank() && withPlaylist }
                if (playlist != null) "$name - $playlist" else name
            }
        }
        else -> when (row.source) {
            ChannelGroup.FAVORITES -> getString(Res.string.home_rows_favorites_title)
            ChannelGroup.RECENT -> getString(Res.string.home_rows_recent_title)
            else -> custom.firstOrNull { it.id == row.source }?.name
                ?: (renamed[row.source]?.takeIf { it.isNotBlank() } ?: groups[row.source]?.first)?.let { name ->
                    val playlist = groups[row.source]?.second?.let { playlistNames[it] }?.takeIf { it.isNotBlank() && withPlaylist }
                    if (playlist != null) "$name - $playlist" else name
                }
                ?: row.source.substringAfter("::").ifBlank { getString(Res.string.home_rows_channels_title) }
        }
    }

    suspend fun sportsTitle(source: String): String = when {
        source.startsWith("sport:") -> Sport.entries.firstOrNull { it.name == source.removePrefix("sport:") }
            ?.let { getString(Res.string.home_rows_sports_one_title, it.label) } ?: getString(Res.string.home_rows_sports_title)
        source.startsWith("league:") -> SportsCatalog.league(source.removePrefix("league:"))
            ?.let { getString(Res.string.home_rows_sports_one_title, it.name) } ?: getString(Res.string.home_rows_sports_title)
        else -> getString(Res.string.home_rows_sports_title)
    }

    private val _emptyIds = MutableStateFlow<Set<String>>(emptySet())

    /** Rows with nothing to show right now (by row id): Home leaves them out, Settings says so. */
    val emptyRowIds: StateFlow<Set<String>> = _emptyIds

    fun setEmptyRows(ids: Set<String>) { _emptyIds.value = ids }

    private var started = false

    /** Each row's place the last time Home's order was looked at (row id → the row above it). */
    private var seenAnchors: Map<String, String> = emptyMap()

    /** Hands the rows to Home's row list and keeps each row's place remembered (once, at app start). */
    fun start() {
        if (started) return
        started = true
        HomeLiveRowsData.start()
        scope.launch {
            combine(shownRows, names) { list, n -> list to n }
                .distinctUntilChanged()
                .collect { (list, n) ->
                    val defs = list.map { r ->
                        LiveHomeRowDefinition(
                            key = r.key,
                            title = n[r.id].orEmpty(),
                            kindLabel = getString(
                                when (r.kind) {
                                    HomeLiveRow.KIND_SPORTS -> Res.string.home_rows_options_sports_subtitle
                                    HomeLiveRow.KIND_VOD -> Res.string.home_rows_options_vod_subtitle
                                    else -> Res.string.home_rows_options_channels_subtitle
                                },
                            ),
                            after = r.after,
                        )
                    }
                    kotlinx.coroutines.withContext(Dispatchers.Main) { HomeCatalogSettingsRepository.syncLiveRows(defs) }
                }
        }
        // Remember the row above each live row, so the place carries over to another device.
        scope.launch {
            combine(HomeCatalogSettingsRepository.uiState.map { s -> s.items.map { it.key } }.distinctUntilChanged(), rows) { order, list -> order to list }
                .debounce(1_500)
                .collect { (order, list) ->
                    if (list.isEmpty() || order.isEmpty()) return@collect
                    // Only rows moved on this phone: the TVs' Home has other rows, so a row can sit under
                    // a different one there, and saving every difference would make them keep
                    // overwriting each other's place.
                    val anchors = list.mapNotNull { r ->
                        val i = order.indexOf(r.key)
                        if (i < 0) null else r.id to order.subList(0, i).lastOrNull { !HomeLiveRow.isKey(it) }.orEmpty()
                    }.toMap()
                    val seen = seenAnchors
                    seenAnchors = anchors
                    val moved = anchors.filter { (id, at) -> seen[id]?.let { it != at } == true }
                    if (list.any { r -> moved[r.id]?.let { it != r.after } == true }) {
                        edit { rows -> rows.map { r -> moved[r.id]?.let { r.copy(after = it) } ?: r } }
                    }
                }
        }
    }

    fun add(row: HomeLiveRow) {
        // A new row goes to the top of Home.
        edit { it + row.copy(after = "") }
        HomeCatalogSettingsRepository.placeAtTopWhenAdded(row.key)
    }

    fun update(row: HomeLiveRow) = edit { list -> list.map { if (it.id == row.id) row else it } }

    fun remove(id: String) = edit { list -> list.filterNot { it.id == id } }

    fun row(id: String): HomeLiveRow? = rows.value.firstOrNull { it.id == id }

    private fun edit(transform: (List<HomeLiveRow>) -> List<HomeLiveRow>) {
        prefs.editHomeRows { HomeLiveRow.encode(transform(HomeLiveRow.decode(it))) }
    }
}

package com.nuvio.app.features.livetv.ondemand

import com.nuvio.app.features.livetv.stalker.StalkerGateway
import com.nuvio.stalker.StalkerStreamRef
import com.nuvio.stalker.StalkerSeries
import com.nuvio.stalker.StalkerSeriesVariant
import com.nuvio.stalker.StalkerException
import com.nuvio.stalker.StalkerError
import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.livetv.data.LiveTvDirs
import com.nuvio.app.features.livetv.data.LiveTvFiles
import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.data.LiveTvRepository
import com.nuvio.app.features.livetv.data.M3uParser
import com.nuvio.app.features.livetv.data.XtreamClient
import com.nuvio.app.features.livetv.data.liveTvJson
import com.nuvio.app.features.livetv.data.optInt
import com.nuvio.app.features.livetv.data.optJSONArray
import com.nuvio.app.features.livetv.data.optJSONObject
import com.nuvio.app.features.livetv.data.optString
import com.nuvio.app.features.livetv.model.LiveTvClock
import com.nuvio.app.features.livetv.model.PlaylistSource
import com.nuvio.app.features.livetv.parental.ParentalControls
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.features.tmdb.buildTmdbUrl
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Imports movies and series from playlists that have "Import movies & series" on (Xtream logins
 * through the API; M3U playlists' movie and episode entries by group), keeps them in
 * [OnDemandStore], and turns them into playable links. The TV app's OnDemandRepository.
 */
internal object OnDemandRepository {
    private val prefs = LiveTvPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val xtream = XtreamClient()

    private val _hasContent = MutableStateFlow(false)
    /** True when at least one provider's movies or series are imported (shows the On Demand button). */
    val hasContent: StateFlow<Boolean> = _hasContent.asStateFlow()

    private val _version = MutableStateFlow(0)
    /** Goes up after every import, so screens reload. */
    val version: StateFlow<Int> = _version.asStateFlow()

    private val _status = MutableStateFlow(OnDemandStatus())
    val status: StateFlow<OnDemandStatus> = _status.asStateFlow()

    /** Each playlist's last import problem (by id), so one login's success doesn't hide another's failure. */
    private val problemByPlaylist = HashMap<String, String>()
    private val infoCache = HashMap<String, VodInfo>()
    private var started = false

    /** Playlists whose movies and series are imported. */
    fun importing(list: List<PlaylistSource>): List<PlaylistSource> = list.filter { it.enabled && it.importVod }

    fun start() {
        if (started) return
        started = true
        scope.launch {
            _hasContent.value = runCatching { withContext(Dispatchers.Default) { OnDemandStore.hasAny() } }.getOrDefault(false)
            runCatching { VodTitles.knownPrefixes = OnDemandStore.loadPrefixes() }
            // The heavier work waits a little, so it never competes with the app's own start-up.
            delay(START_DELAY_MS)
            runCatching { OnDemandStore.compact() }
            runCatching {
                importing(prefs.currentPlaylists()).forEach { pl -> if (!OnDemandStore.hasLearnedPrefixes(pl.id)) VodTitles.knownPrefixes = OnDemandStore.learnPrefixes(pl.id) }
            }
            refresh(force = false)
        }
        // "Hide Non-English Titles": switched on, titles labelled as another language are set
        // aside at once and the rest are checked in the background; either way, lists reload.
        scope.launch {
            var first = true
            prefs.settings.map { it.vodHideNonEnglish }.distinctUntilChanged().collect { on ->
                hideForeign = on
                runCatching { OnDemandStore.setHideForeign(on) }
                if (first) {
                    first = false
                    if (on) { delay(START_DELAY_MS); startLanguageCheck() }
                    return@collect
                }
                if (on) {
                    runCatching { withContext(Dispatchers.Default) { OnDemandStore.markForeignAll() } }
                    startLanguageCheck()
                } else OnDemandLanguages.stopSweep()
                _version.value++
            }
        }
        // Adult titles never stay in Continue watching (also ones saved by older versions).
        scope.launch {
            delay(START_DELAY_MS)
            val checked = HashMap<String, Boolean>()
            WatchProgressRepository.uiState.collect { runCatching { removeAdultProgress(checked) } }
        }
        // A playlist with movies and series that arrives or is switched on later (settings, a
        // restore from Google Drive): import it straight away. One that's gone is dropped.
        scope.launch {
            prefs.playlists
                .map { list -> importing(list).map { it.id }.toSet() }
                .distinctUntilChanged()
                .drop(1)
                .collect { refreshSoon() }
        }
    }

    /** Imports new providers and drops ones that were turned off (no re-download of the rest). */
    fun refreshSoon() {
        scope.launch { delay(1_500); refresh(force = false) }
    }

    fun refreshNow() {
        scope.launch { refresh(force = true) }
    }

    /** Config changes invalidate metadata and make the next catalog import immediately due. */
    fun sourceChanged(playlistId: String) {
        infoCache.keys.removeAll { it.substringAfter(':').substringBefore(':') == playlistId }
        prefs.setOnDemandImportTime(playlistId, 0L)
        scope.launch {
            mutex.withLock {
                runCatching { withContext(Dispatchers.Default) {
                    OnDemandStore.removeExcept(importing(prefs.currentPlaylists()).map { it.id }.toSet() - playlistId)
                    _hasContent.value = OnDemandStore.hasAny()
                    _version.value++
                } }
            }
            refresh(force = false)
        }
    }

    /** "Update now" on one playlist: re-imports just that provider's movies and series. */
    fun refreshNow(playlistId: String) {
        scope.launch { delay(500); refresh(force = true, onlyId = playlistId) }
    }

    /**
     * Imports providers that are due (or all, when [force]); drops ones that were turned off.
     * With [onlyId], only that provider is imported (the others are kept as they are).
     */
    suspend fun refresh(force: Boolean, onlyId: String? = null) = mutex.withLock {
        val playlists = importing(prefs.currentPlaylists())
        val keep = playlists.map { it.id }.toSet()
        val dropped = runCatching { withContext(Dispatchers.Default) { OnDemandStore.removeExcept(keep) } }.getOrDefault(emptySet())
        // A provider switched off forgets when it was last imported, so switching it back on
        // imports it again straight away.
        dropped.forEach { prefs.setOnDemandImportTime(it, 0L) }
        val stamps = prefs.onDemandImportTimes()
        val now = LiveTvClock.nowMs()
        for (pl in playlists) {
            if (onlyId != null && pl.id != onlyId) continue
            val last = stamps[pl.id] ?: 0L
            if (!force && now - last < REFRESH_MS) continue
            // The daily re-import of a catalog that's already there happens without a progress line
            // (people took it for the app reloading everything); a first import still shows it.
            quietImport = !force && last > 0L
            _status.value = OnDemandStatus(true, "Importing movies and series from ${pl.name}…", silent = quietImport)
            var problem: String? = null
            val t0 = LiveTvClock.nowMs()
            if (pl.isStalker) {
                runCatching { importStalker(pl, VodKind.MOVIE) }
                    .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; problem = "Couldn't import movies from ${pl.name}: ${com.nuvio.app.features.livetv.stalker.StalkerGateway.safeUpdateError(it)}" }
                runCatching { importStalker(pl, VodKind.SERIES) }
                    .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; problem = "Couldn't import series from ${pl.name}: ${com.nuvio.app.features.livetv.stalker.StalkerGateway.safeUpdateError(it)}" }
            } else if (pl.isXtream) {
                // Movies and series import separately, so a problem with one keeps the other.
                runCatching { importXtream(pl, VodKind.MOVIE) }
                    .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; problem = "Couldn't import movies from ${pl.name}: ${it.message ?: "unknown error"}" }
                runCatching { importXtream(pl, VodKind.SERIES) }
                    .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; problem = "Couldn't import series from ${pl.name}: ${it.message ?: "unknown error"}" }
            } else {
                if (!pl.isM3u) problem = "Couldn't read movies and series from ${pl.name}: unsupported source"
                else {
                    // Movies and series import separately, so a problem with one keeps the other.
                    runCatching { importM3uSource(pl, VodKind.MOVIE) }
                        .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; problem = "Couldn't read movies from ${pl.name}: ${it.message ?: "unknown error"}" }
                    runCatching { importM3uSource(pl, VodKind.SERIES) }
                        .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; problem = "Couldn't read series from ${pl.name}: ${it.message ?: "unknown error"}" }
                }
            }
            val p = problem
            com.nuvio.app.features.livetv.model.LiveTvLoadReport.add(
                "On Demand \"${pl.name}\": imported in ${(LiveTvClock.nowMs() - t0) / 1000} s" + (p?.let { "; $it" } ?: "")
            )
            if (p != null) problemByPlaylist[pl.id] = p else problemByPlaylist.remove(pl.id)
            // Success: next import in a day. Failure: try again in an hour, not on every start.
            prefs.setOnDemandImportTime(pl.id, if (p == null) LiveTvClock.nowMs() else LiveTvClock.nowMs() - REFRESH_MS + 60 * 60 * 1000L)
            runCatching { VodTitles.knownPrefixes = withContext(Dispatchers.Default) { OnDemandStore.learnPrefixes(pl.id) } }
            // Titles in another language, marked again (an import writes changed titles afresh).
            if (hideForeign) runCatching { withContext(Dispatchers.Default) { OnDemandStore.markForeign(pl.id) } }
            _version.value++
        }
        if (hideForeign) startLanguageCheck()
        _hasContent.value = runCatching { withContext(Dispatchers.Default) { OnDemandStore.hasAny() } }.getOrDefault(_hasContent.value)
        problemByPlaylist.keys.retainAll(keep)
        quietImport = false
        _status.value = OnDemandStatus(false, problemByPlaylist.values.joinToString("\n").ifBlank { null })
    }

    /** The import running now is a routine re-import (see [OnDemandStatus.silent]). */
    @kotlin.concurrent.Volatile private var quietImport = false

    private suspend fun importStalker(pl: PlaylistSource, kind: VodKind) {
        val client = StalkerGateway.client(pl)
        val identity = StalkerGateway.sourceIdentity(pl)
        val latest = VodTitles.latestYear()
        if (kind == VodKind.MOVIE) {
            val categoryNames = try { client.vodCategories() } catch (error: StalkerException) {
                if (error.error != StalkerError.UNSUPPORTED) throw error
                emptyList()
            }.associateTo(LinkedHashMap()) { it.id to it.name }
            val raw = client.movies().filterNot { it.isSeries }
            raw.forEach { categoryNames.putIfAbsent(it.categoryId.orEmpty(), "Uncategorized") }
            val categories = categoryNames.toList()
            val items = raw.asSequence().map { movie ->
                VodItem(pl.id, kind, movie.id, movie.title, movie.poster, movie.categoryId.orEmpty(),
                    ext = null, tmdbId = null, year = VodTitles.yearOf(movie.year)?.takeIf { it <= latest }
                        ?: VodTitles.releaseYearInName(movie.title, latest), rating = movie.rating, addedSec = 0,
                    genre = movie.genre, stalker = movie.stream.copy(sourceIdentity = identity), plot = movie.plot, durationSecs = movie.durationSeconds)
            }
            withContext(Dispatchers.Default) { OnDemandStore.replace(pl.id, kind, categories, items, publication = { commit -> prefs.withCurrentPlaylist(pl, commit) }) }
            com.nuvio.app.features.livetv.model.LiveTvLoadReport.add("On Demand \"${pl.name}\": ${raw.size} movies, ${categories.size} categories from the portal")
        } else {
            val categoryNames = LinkedHashMap<String, String>()
            try { client.seriesCategories().forEach { categoryNames["CLASSIC:${it.id}"] = it.name } }
            catch (error: StalkerException) { if (error.error != StalkerError.UNSUPPORTED) throw error }
            try {
                client.vodCategories().forEach { categoryNames["VOD:${it.id}"] = it.name }
            } catch (error: StalkerException) {
                if (error.error != StalkerError.UNSUPPORTED) throw error
            }
            val raw = client.series()
            raw.forEach { categoryNames.putIfAbsent("${it.variant.name}:${it.categoryId.orEmpty()}", "Uncategorized") }
            val categories = categoryNames.toList()
            val items = raw.asSequence().map { series ->
                // Classic-series and VOD-series namespaces can reuse the same portal ID.
                VodItem(pl.id, kind, "${series.variant.name}:${series.id}", series.title, series.poster,
                    "${series.variant.name}:${series.categoryId.orEmpty()}", ext = null, tmdbId = null,
                    year = VodTitles.yearOf(series.year)?.takeIf { it <= latest } ?: VodTitles.releaseYearInName(series.title, latest),
                    rating = series.rating, addedSec = 0, genre = series.genre, stalker = series.stream.copy(sourceIdentity = identity),
                    stalkerSeriesVariant = series.variant.name, plot = series.plot)
            }
            withContext(Dispatchers.Default) { OnDemandStore.replace(pl.id, kind, categories, items, publication = { commit -> prefs.withCurrentPlaylist(pl, commit) }) }
            com.nuvio.app.features.livetv.model.LiveTvLoadReport.add("On Demand \"${pl.name}\": ${raw.size} series, ${categories.size} categories from the portal")
        }
        infoCache.keys.removeAll { it.substringAfter(':').substringBefore(':') == pl.id }
    }

    private suspend fun importXtream(pl: PlaylistSource, kind: VodKind): Int {
        val series = kind == VodKind.SERIES
        val what = if (series) "series" else "movies"
        val categories = xtream.vodCategories(pl, series)
        _status.value = OnDemandStatus(true, "Importing $what from ${pl.name}…", silent = quietImport)
        val latest = VodTitles.latestYear()
        val raw = xtream.vodItems(pl, series)
        _status.value = OnDemandStatus(true, "Storing ${raw.size} $what from ${pl.name}…", silent = quietImport)
        val items = raw.asSequence().map { x ->
            VodItem(
                playlistId = pl.id,
                kind = kind,
                id = x.id,
                name = x.name,
                icon = x.icon,
                categoryId = x.categoryId,
                ext = x.containerExtension,
                tmdbId = x.tmdbId,
                // The provider's year when it's believable, else the one in the name.
                year = x.year?.toIntOrNull()?.takeIf { it <= latest } ?: VodTitles.releaseYearInName(x.name, latest),
                rating = x.rating,
                addedSec = x.addedSec,
                genre = x.genre,
            )
        }
        val saved = withContext(Dispatchers.Default) { OnDemandStore.replace(pl.id, kind, categories, items) }
        com.nuvio.app.features.livetv.model.LiveTvLoadReport.add("On Demand \"${pl.name}\": $saved $what, ${categories.size} categories from the provider")
        return saved
    }

    /**
     * An M3U playlist's movies or series. With a login the provider accepts, they come from the
     * provider like an Xtream login's (fuller details); otherwise, or when that doesn't work,
     * from the playlist's own movie and episode entries.
     */
    private suspend fun importM3uSource(pl: PlaylistSource, kind: VodKind) {
        val what = if (kind == VodKind.MOVIE) "movies" else "series"
        if (pl.m3uLogin != null) {
            val fromProvider = runCatching { importXtream(pl.withVodLogin(), kind) }
            fromProvider.exceptionOrNull()?.let { if (it is kotlinx.coroutines.CancellationException) throw it }
            if (fromProvider.isSuccess && (fromProvider.getOrNull() ?: 0) > 0) {
                if (kind == VodKind.SERIES) runCatching { LiveTvFiles.delete(LiveTvDirs.PLAYLISTS, episodesFile(pl.id)) }
                return
            }
            // Nothing from the provider and nothing in the playlist itself: report why.
            if (!pl.vodInPlaylist) fromProvider.getOrThrow()
            com.nuvio.app.features.livetv.model.LiveTvLoadReport.add(
                "On Demand \"${pl.name}\": the provider gave no $what" +
                    (fromProvider.exceptionOrNull()?.let { " (${it.message ?: it::class.simpleName})" } ?: "") +
                    "; reading them from the playlist"
            )
            infoCache.keys.removeAll { it.substringAfter(':').substringBefore(':') == pl.id }
        }
        importFromPlaylist(pl, kind, what)
    }

    private fun episodesFile(id: String) = "$id.episodes"

    /** "S01E02", "S1 E2", "S01.E02". */
    private val EPISODE_TAG = Regex("""\bS(\d{1,3})\s*[._-]?\s*E(\d{1,4})\b""", RegexOption.IGNORE_CASE)
    /** "1x02". */
    private val EPISODE_X_TAG = Regex("""\b(\d{1,2})x(\d{1,3})\b""", RegexOption.IGNORE_CASE)
    private val SERIES_LINK = Regex("""/series/""", RegexOption.IGNORE_CASE)
    private val MOVIE_LINK = Regex("""/movies?/""", RegexOption.IGNORE_CASE)
    private val STREAM_ID = Regex("""/(\d+)\.[A-Za-z0-9]+(?:\?.*)?$""")

    /** An episode entry: its show, season and episode ("Show S01E02", "Show 1x02", or a /series/ link). */
    private class EpisodeName(val show: String, val season: Int, val episode: Int?)

    private fun episodeName(name: String, url: String): EpisodeName? {
        // Xtream-style links say which it is: /movie/ is always a movie (even with "S01E01" in
        // its name); "1x02" only counts on a /series/ link ("10x10" is a film's name).
        if (MOVIE_LINK.containsMatchIn(url)) return null
        val seriesLink = SERIES_LINK.containsMatchIn(url)
        val m = EPISODE_TAG.find(name) ?: if (seriesLink) EPISODE_X_TAG.find(name) else null
        if (m != null) {
            val show = name.substring(0, m.range.first).trim().trimEnd('-', '|', ':', '.', '_', '(', '[').trim()
            return EpisodeName(show.ifEmpty { name }, m.groupValues[1].toIntOrNull() ?: 1, m.groupValues[2].toIntOrNull())
        }
        if (seriesLink) return EpisodeName(name, 1, null)
        return null
    }

    /**
     * Movies or series from the playlist's own entries: categories from their groups, posters
     * from their logos, and episodes grouped into series and seasons by their names.
     */
    private suspend fun importFromPlaylist(pl: PlaylistSource, kind: VodKind, what: String) = withContext(Dispatchers.Default) {
        val file = "${pl.id}.m3u"
        if (!LiveTvFiles.exists(LiveTvDirs.PLAYLISTS, file)) error("the playlist hasn't been downloaded yet")
        // Only movie and episode entries are read (the channels are left out while parsing).
        val parsed = LiveTvFiles.readChunks(LiveTvDirs.PLAYLISTS, file) { chunks ->
            M3uParser.parse(LiveTvRepository.linesOf(chunks)) { url -> !LiveTvRepository.isVodEntry(url) }
        } ?: error("the playlist couldn't be read")
        val latest = VodTitles.latestYear()
        _status.value = OnDemandStatus(true, "Importing $what from ${pl.name}…", silent = quietImport)
        fun extOf(url: String) = url.substringBefore('?').substringAfterLast('.', "").takeIf { it.length in 2..5 && it.all { c -> c.isLetterOrDigit() } }?.lowercase()
        fun groupOf(e: com.nuvio.app.features.livetv.data.M3uEntry) = e.group?.trim()?.ifBlank { null } ?: "Uncategorized"
        val categories = LinkedHashMap<String, String>()
        if (kind == VodKind.MOVIE) {
            val items = ArrayList<VodItem>()
            var count = 0
            for (e in parsed.entries) {
                if (!LiveTvRepository.isVodEntry(e.url) || episodeName(e.name, e.url) != null) continue
                val group = groupOf(e)
                val catId = stableHash(group)
                categories.putIfAbsent(catId, group)
                items += VodItem(
                    playlistId = pl.id, kind = VodKind.MOVIE, id = stableHash(e.url), name = e.name,
                    icon = e.logo?.takeIf { it.startsWith("http") }, categoryId = catId, ext = extOf(e.url), tmdbId = null,
                    year = VodTitles.releaseYearInName(e.name, latest), rating = null,
                    // Playlist order stands in for "added".
                    addedSec = (count++).toLong(), url = e.url,
                )
            }
            _status.value = OnDemandStatus(true, "Storing ${items.size} movies from ${pl.name}…", silent = quietImport)
            OnDemandStore.replace(pl.id, VodKind.MOVIE, categories.entries.map { it.key to it.value }, items.asSequence())
            com.nuvio.app.features.livetv.model.LiveTvLoadReport.add("On Demand \"${pl.name}\": ${items.size} movies from the playlist, ${categories.size} categories")
            return@withContext
        }
        // Series: each show is kept once (its first poster); the episodes go in a file of their own.
        val shows = LinkedHashMap<String, VodItem>()
        val nextEpisode = HashMap<String, Int>()
        val lines = ArrayList<String>()
        fun clean(v: String) = v.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
        var order = 0L
        for (e in parsed.entries) {
            if (!LiveTvRepository.isVodEntry(e.url)) continue
            val ep = episodeName(e.name, e.url) ?: continue
            val group = groupOf(e)
            val catId = stableHash(group)
            val id = M3U_ID + stableHash(VodTitles.normalize(ep.show).ifEmpty { ep.show.lowercase() } + "|" + group)
            if (id !in shows) {
                categories.putIfAbsent(catId, group)
                shows[id] = VodItem(
                    playlistId = pl.id, kind = VodKind.SERIES, id = id, name = ep.show,
                    icon = e.logo?.takeIf { it.startsWith("http") }, categoryId = catId, ext = null, tmdbId = null,
                    year = VodTitles.releaseYearInName(ep.show, latest), rating = null, addedSec = order++,
                )
            }
            val seasonKey = "$id#${ep.season}"
            val number = ep.episode ?: ((nextEpisode[seasonKey] ?: 0) + 1)
            nextEpisode[seasonKey] = maxOf(nextEpisode[seasonKey] ?: 0, number)
            lines += listOf(id, ep.season, number, clean(e.name), clean(e.url), clean(e.logo?.takeIf { it.startsWith("http") }.orEmpty())).joinToString("\t")
        }
        LiveTvFiles.writeText(LiveTvDirs.PLAYLISTS, episodesFile(pl.id), lines.joinToString("\n"))
        _status.value = OnDemandStatus(true, "Storing ${shows.size} series from ${pl.name}…", silent = quietImport)
        OnDemandStore.replace(pl.id, VodKind.SERIES, categories.entries.map { it.key to it.value }, shows.values.asSequence())
        com.nuvio.app.features.livetv.model.LiveTvLoadReport.add("On Demand \"${pl.name}\": ${shows.size} series (${lines.size} episodes) from the playlist, ${categories.size} categories")
        infoCache.keys.removeAll { it.substringAfter(':').substringBefore(':') == pl.id }
    }

    /** A series read from an M3U playlist's own entries: its episodes come from the saved episode file. */
    private fun m3uInfo(item: VodItem, pl: PlaylistSource): VodInfo {
        val rows = ArrayList<VodEpisode>()
        val prefix = item.id + "\t"
        runCatching {
            LiveTvFiles.readChunks(LiveTvDirs.PLAYLISTS, episodesFile(pl.id)) { chunks ->
                for (line in LiveTvRepository.linesOf(chunks)) {
                    if (!line.startsWith(prefix)) continue
                    val f = line.split('\t')
                    if (f.size < 5) continue
                    val url = f[4]
                    rows += VodEpisode(
                        season = f[1].toIntOrNull() ?: 1, episode = f[2].toIntOrNull() ?: (rows.size + 1),
                        id = STREAM_ID.find(url)?.groupValues?.get(1) ?: stableHash(url),
                        ext = url.substringBefore('?').substringAfterLast('.', "").takeIf { it.length in 2..5 && it.all { c -> c.isLetterOrDigit() } }?.lowercase() ?: "mp4",
                        title = f[3], plot = null, image = f.getOrNull(5)?.takeIf { it.startsWith("http") }, url = url,
                    )
                }
            }
        }
        return VodInfo(
            plot = null, cover = item.icon, backdrop = null, genre = null, releaseDate = item.year?.toString(), durationText = null,
            episodes = rows.sortedWith(compareBy({ it.season }, { it.episode })),
        )
    }

    private fun stableHash(s: String): String {
        var h = -3750763034362895579L
        for (ch in s) {
            h = h xor ch.code.toLong()
            h *= 1099511628211L
        }
        return h.toULong().toString(16)
    }

    // ------------------------------------------------------------------ reading

    suspend fun categories(kind: VodKind): List<VodCategory> = withContext(Dispatchers.Default) {
        runCatching { OnDemandStore.categories(kind) }.getOrDefault(emptyList())
    }

    /**
     * A page of titles. With "Hide Non-English Titles" on, titles in another language are never
     * in it: those already known are left out by the catalog itself, and titles on the page not
     * checked yet are checked first (a short wait, once per title) and the page read again.
     */
    suspend fun items(kind: VodKind, category: VodCategory?, hidden: Set<String>, limit: Int, offset: Int, sort: VodSort, merge: Boolean): List<VodItem> =
        withContext(Dispatchers.Default) {
            fun read() = runCatching { OnDemandStore.items(kind, category, hidden, limit, offset, sort, merge) }.getOrDefault(emptyList())
            var page = read()
            if (hideForeign && page.isNotEmpty()) {
                repeat(2) {
                    if (!OnDemandLanguages.check(page, PAGE_CHECK_MS)) return@withContext page
                    page = read()
                }
            }
            page
        }

    suspend fun search(kind: VodKind, text: String, hidden: Set<String>, limit: Int = 200, sort: VodSort = VodSort.DEFAULT, merge: Boolean = true): List<VodItem> =
        withContext(Dispatchers.Default) {
            val found = runCatching { OnDemandStore.search(kind, text, hidden, limit, sort, merge) }.getOrDefault(emptyList())
            if (hideForeign && found.isNotEmpty() && OnDemandLanguages.check(found.take(SEARCH_CHECK), PAGE_CHECK_MS)) {
                runCatching { OnDemandStore.search(kind, text, hidden, limit, sort, merge) }.getOrDefault(found)
            } else found
        }

    /** "Hide Non-English Titles" is on (kept up to date from the settings). */
    @kotlin.concurrent.Volatile private var hideForeign = false

    private fun startLanguageCheck() {
        OnDemandLanguages.startSweep(enabled = { hideForeign }, onMarked = { _version.value++ })
    }

    /** One version of a title you can pick: where it's from and what it is. */
    data class Version(val item: VodItem, val label: String, val detail: String)

    /** Every copy of [item] (other qualities, other categories, other providers), best first. */
    suspend fun versions(item: VodItem): List<Version> = withContext(Dispatchers.Default) {
        val all = runCatching { OnDemandStore.versions(item) }.getOrDefault(listOf(item)).ifEmpty { listOf(item) }
        val playlists = prefs.currentPlaylists().associateBy { it.id }
        val catNames = HashMap<String, Map<String, String>>()
        all.map { v ->
            val cats = catNames.getOrPut(v.playlistId) { runCatching { OnDemandStore.categoryNames(v.playlistId, v.kind) }.getOrDefault(emptyMap()) }
            val quality = VodTitles.qualityOf(v.name)
            val label = listOfNotNull(quality, v.ext?.uppercase()).joinToString(" · ").ifBlank { "Standard" }
            val detail = listOfNotNull(
                playlists[v.playlistId]?.name.takeIf { playlists.size > 1 },
                cats[v.categoryId],
                v.year?.toString()
            ).joinToString(" · ")
            Version(v, label, detail)
        }.sortedByDescending { qualityRank(it.label) }
    }

    private fun qualityRank(label: String) = when {
        label.startsWith("4K") -> 4
        label.startsWith("Full HD") -> 3
        label.startsWith("HD") -> 2
        label.startsWith("SD") -> 1
        else -> 0
    }

    /** One playable copy of a title from a provider, for Nuvio's stream list. */
    data class OnDemandSource(
        val label: String,
        val url: String,
        val headers: Map<String, String>,
        val title: String,
        val year: Int?,
        val providerName: String,
        val ext: String,
        val episodeTag: String? = null,
        /** The provider's full title, tags included ("4K-D+ - …"): where the quality is read from. */
        val providerTitle: String = "",
        /** A fresh portal link is requested only when this source is actually selected. */
        val stalkerSourceId: String? = null,
        val stalkerStream: StalkerStreamRef? = null,
        val downloadItemUid: String? = null,
        val downloadEpisodeId: String? = null,
    )

    /**
     * Finds a title in your providers' catalogs: by TMDB id when there is one, otherwise by name
     * (and year when known). For series, [season]/[episode] pick the episode. Titles in hidden or
     * locked groups are left out.
     */
    suspend fun sourcesFor(type: String, tmdbId: String?, title: String, year: Int?, season: Int?, episode: Int?): List<OnDemandSource> =
        withContext(Dispatchers.Default) {
            val kind = when (type.lowercase()) {
                "movie" -> VodKind.MOVIE
                "series", "tv" -> VodKind.SERIES
                else -> return@withContext emptyList()
            }
            if (!_hasContent.value) return@withContext emptyList()
            val candidates = LinkedHashMap<String, VodItem>()
            tmdbId?.takeIf { it.isNotBlank() }?.let { id ->
                runCatching { OnDemandStore.byTmdb(kind, id) }.getOrDefault(emptyList()).forEach { candidates[it.uid] = it }
            }
            // Same name (and year within one), as on the TV: catches titles whose provider TMDB id is missing or wrong.
            if (title.isNotBlank()) {
                runCatching { OnDemandStore.byTitle(kind, title) }.getOrDefault(emptyList())
                    .filter { year == null || it.year == null || kotlin.math.abs(it.year - year) <= 1 }
                    .forEach { candidates.putIfAbsent(it.uid, it) }
            }
            val core = VodTitles.coreKey(title)
            if (candidates.isEmpty() && title.isNotBlank() && core.length >= 3) {
                val found = runCatching { OnDemandStore.search(kind, VodTitles.displayTitle(title), emptySet(), 80, VodSort.DEFAULT, false) }
                    .getOrDefault(emptyList())
                    .filter { VodTitles.coreKey(it.name) == core }
                val withYears = found.map { it to (it.year ?: VodTitles.yearInTitle(it.name)) }
                val picked = when {
                    year == null -> withYears.map { it.first }
                    withYears.any { it.second != null && kotlin.math.abs(it.second!! - year) <= 1 } ->
                        withYears.filter { it.second != null && kotlin.math.abs(it.second!! - year) <= 1 }.map { it.first }
                    else -> withYears.filter { it.second == null }.map { it.first }
                }
                picked.forEach { candidates.putIfAbsent(it.uid, it) }
            }
            val user = prefs.currentUserState()
            val settings = prefs.currentSettings()
            val allowed = candidates.values.filter { item ->
                val source = playlist(item.playlistId) ?: return@filter false
                if (!source.enabled || !source.importVod) return@filter false
                if (source.isStalker && item.stalker?.sourceIdentity != StalkerGateway.sourceIdentity(source)) return@filter false
                val uid = "${item.kind.key}:${item.playlistId}:${item.categoryId}"
                if (user.hidesVodCategory(uid)) return@filter false
                val name = runCatching { OnDemandStore.categoryNames(item.playlistId, item.kind)[item.categoryId] }.getOrNull().orEmpty()
                !com.nuvio.app.features.livetv.parental.ParentalControls.isLocked("vod:$uid", name, settings, user.lockedGroups)
            }
            val out = ArrayList<OnDemandSource>()
            // Highest quality first (4K, Full HD, HD, SD, from the provider's name for each copy;
            // the full name, since the shown title drops tags like "4K-D+ - "). Copies with no
            // hint keep their order, last. Sorted before the limit so a 4K copy isn't cut.
            for (item in allowed.sortedByDescending { qualityRank(VodTitles.qualityOf(it.name).orEmpty()) }.take(6)) {
                val pl = playlist(item.playlistId)?.takeIf { it.enabled && it.importVod } ?: continue
                val headers = headersFor(item)
                if (kind == VodKind.MOVIE) {
                    val url = movieUrl(item) ?: continue
                    val ext = if (pl.isStalker) item.ext.orEmpty() else item.ext ?: "mp4"
                    out += OnDemandSource(pl.name + if (ext.isNotBlank()) " · ${ext.uppercase()}" else "", url, headers,
                        item.displayTitle, item.year, pl.name, ext, providerTitle = item.name,
                        stalkerSourceId = pl.id.takeIf { pl.isStalker }, stalkerStream = item.stalker,
                        downloadItemUid = item.uid.takeUnless { pl.isStalker })
                } else if (season != null && episode != null) {
                    val episodes = runCatching { info(item) }
                        .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()?.episodes.orEmpty()
                    val ep = episodes.firstOrNull { it.season == season && it.episode == episode }
                        ?: episodes.takeIf { list -> list.map { it.season }.distinct().size == 1 }?.firstOrNull { it.episode == episode }
                        ?: continue
                    out += OnDemandSource(
                        "${pl.name} · S${season}E${episode}" + if (ep.ext.isNotBlank()) " · ${ep.ext.uppercase()}" else "", ep.url, headers, item.displayTitle, item.year, pl.name, ep.ext,
                        episodeTag = "S" + season.toString().padStart(2, '0') + "E" + episode.toString().padStart(2, '0'),
                        providerTitle = item.name,
                        stalkerSourceId = pl.id.takeIf { pl.isStalker }, stalkerStream = ep.stalker,
                        downloadItemUid = item.uid.takeUnless { pl.isStalker }, downloadEpisodeId = ep.id,
                    )
                }
            }
            out
        }

    /** Adult titles (by their own name or their category's name), which never go in Continue watching. */
    suspend fun isAdult(item: VodItem): Boolean {
        if (ParentalControls.isAdult(item.name)) return true
        val cat = withContext(Dispatchers.Default) {
            runCatching { OnDemandStore.categoryNames(item.playlistId, item.kind)[item.categoryId] }.getOrNull()
        }
        return cat != null && ParentalControls.isAdult(cat)
    }

    /** Takes adult On Demand titles out of Continue watching (ones saved before they were kept out). */
    private suspend fun removeAdultProgress(checked: MutableMap<String, Boolean>) {
        val prefix = "${OnDemandPlayback.PROVIDER_ID}:"
        WatchProgressRepository.continueWatching()
            .filter { it.videoId.startsWith(prefix) }
            .forEach { entry ->
                val raw = entry.videoId.removePrefix(prefix)
                val suffix = if (entry.seasonNumber != null && entry.episodeNumber != null) ":${entry.seasonNumber}:${entry.episodeNumber}" else ""
                val uid = if (raw.startsWith("series:") && suffix.isNotEmpty()) raw.removeSuffix(suffix) else raw
                val adult = checked.getOrPut(uid) { itemByUid(uid)?.let { isAdult(it) } ?: false }
                if (adult) {
                    WatchProgressRepository.removeProgress(entry.parentMetaId, entry.seasonNumber, entry.episodeNumber)
                }
            }
    }

    /** A title by its uid ("movie:<playlist>:<id>"). */
    suspend fun itemByUid(uid: String): VodItem? = withContext(Dispatchers.Default) {
        val parts = uid.split(':', limit = 3)
        if (parts.size != 3) return@withContext null
        runCatching { OnDemandStore.item(parts[1], VodKind.of(parts[0]), parts[2]) }.getOrNull()
    }

    fun playlist(id: String): PlaylistSource? = prefs.currentPlaylists().firstOrNull { it.id == id }?.withVodLogin()

    /** The headers to play a title with (the playlist's user agent, when it has one). */
    fun headersFor(item: VodItem): Map<String, String> {
        val pl = playlist(item.playlistId)
        val ua = pl?.userAgent?.takeIf { it.isNotBlank() } ?: XtreamClient.DEFAULT_UA
        return mapOf("User-Agent" to ua)
    }

    /** Movie link: the M3U entry's own, or Xtream's /movie/user/pass/<id>.<ext>. */
    fun movieUrl(item: VodItem): String? {
        val pl = playlist(item.playlistId) ?: return null
        if (pl.isStalker) return item.stalker?.let { StalkerGateway.locator(pl.id, it) }
        item.url?.let { return it }
        if (!pl.isXtream) return null
        return "${pl.xtreamBase()}/movie/${enc(pl.xtreamUsername)}/${enc(pl.xtreamPassword)}/${item.id}.${item.ext ?: "mp4"}"
    }

    private fun enc(s: String) = s.trim().encodeURLParameter()

    /** Provider details for a movie or series (series include every episode). Cached. */
    suspend fun info(item: VodItem): VodInfo? = withContext(Dispatchers.Default) {
        infoCache[item.uid]?.let { return@withContext it }
        val pl = playlist(item.playlistId) ?: return@withContext null
        if (pl.isStalker) return@withContext stalkerInfo(pl, item).also { infoCache[item.uid] = it }
        if (item.id.startsWith(M3U_ID)) return@withContext m3uInfo(item, pl).also { infoCache[item.uid] = it }
        if (item.url != null || !pl.isXtream) return@withContext null
        val json = runCatching { xtream.vodInfo(pl, item.kind == VodKind.SERIES, item.id) }.getOrNull() ?: return@withContext null
        val info = json.optJSONObject("info") ?: JsonObject(emptyMap())
        val episodes = ArrayList<VodEpisode>()
        json.optJSONObject("episodes")?.let { bySeason ->
            bySeason.keys.forEach { seasonKey ->
                val arr = bySeason[seasonKey] as? JsonArray ?: return@forEach
                arr.forEachIndexed { i, el ->
                    val e = el as? JsonObject ?: return@forEachIndexed
                    val id = e.optString("id").ifBlank { return@forEachIndexed }
                    val ext = e.optString("container_extension").ifBlank { "mp4" }
                    val season = e.optString("season").toIntOrNull() ?: seasonKey.toIntOrNull() ?: 1
                    val num = e.optString("episode_num").toIntOrNull() ?: (i + 1)
                    val einfo = e.optJSONObject("info")
                    episodes += VodEpisode(
                        season = season,
                        episode = num,
                        id = id,
                        ext = ext,
                        title = e.optString("title").ifBlank { "Episode $num" },
                        plot = einfo?.optString("plot")?.takeIf { it.isNotBlank() },
                        image = einfo?.optString("movie_image")?.takeIf { it.startsWith("http") },
                        url = "${pl.xtreamBase()}/series/${enc(pl.xtreamUsername)}/${enc(pl.xtreamPassword)}/$id.$ext",
                        durationSecs = einfo?.optInt("duration_secs")?.takeIf { it > 0 },
                    )
                }
            }
        }
        val result = VodInfo(
            plot = info.optString("plot").ifBlank { info.optString("description") }.takeIf { it.isNotBlank() },
            cover = (info.optString("movie_image").ifBlank { info.optString("cover") }).takeIf { it.startsWith("http") },
            backdrop = info.optJSONArray("backdrop_path")?.firstOrNull()?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }?.takeIf { it.startsWith("http") },
            genre = info.optString("genre").takeIf { it.isNotBlank() },
            releaseDate = (info.optString("releasedate").ifBlank { info.optString("releaseDate") }).takeIf { it.isNotBlank() },
            durationText = info.optString("duration").takeIf { it.isNotBlank() },
            tmdbId = (info.optString("tmdb_id").ifBlank { info.optString("tmdb") }).takeIf { it.isNotBlank() && it != "0" },
            episodes = episodes.sortedWith(compareBy({ it.season }, { it.episode })),
        )
        infoCache[item.uid] = result
        result
    }

    private suspend fun stalkerInfo(pl: PlaylistSource, item: VodItem): VodInfo {
        val ref = item.stalker ?: throw StalkerException(StalkerError.INVALID_CONFIG)
        val episodes = ArrayList<VodEpisode>()
        val identity = StalkerGateway.sourceIdentity(pl)
        if (ref.sourceIdentity != identity) throw StalkerException(StalkerError.STALE_SESSION)
        if (item.kind == VodKind.SERIES) {
            val variant = StalkerSeriesVariant.entries.firstOrNull { it.name == item.stalkerSeriesVariant }
                ?: throw StalkerException(StalkerError.INVALID_CONFIG)
            val client = StalkerGateway.client(pl)
            val series = StalkerSeries(ref.providerId, item.name, ref, variant = variant, categoryId = item.categoryId.substringAfter(':'),
                poster = item.icon, plot = item.plot, genre = item.genre, rating = item.rating)
            // Series structure is queried only when details are opened, never during catalog import.
            client.seasons(series).forEachIndexed { index, season ->
                client.episodes(season).forEachIndexed { episodeIndex, episode ->
                    episodes += VodEpisode(season = episode.seasonNumber ?: season.number ?: index + 1,
                        episode = episode.number ?: episodeIndex + 1,
                        id = "${variant.name}:${season.id.encodeURLParameter()}:${episode.id.encodeURLParameter()}", ext = "",
                        title = episode.title, plot = episode.plot, image = episode.poster,
                        url = StalkerGateway.locator(pl.id, episode.stream),
                        durationSecs = episode.durationSeconds?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(), stalker = episode.stream.copy(sourceIdentity = identity))
                }
            }
        }
        return VodInfo(item.plot, item.icon, null, item.genre, item.year?.toString(),
            item.durationSecs?.let { "${it / 60} min" }, episodes = episodes.sortedWith(compareBy({ it.season }, { it.episode })))
    }

    // ------------------------------------------------------------------ posters (TMDB)

    private val matchCache = HashMap<String, VodMatch>()
    private val matchLimit = Semaphore(4)
    private const val MATCH_RETRY_MS = 7L * 24 * 3_600_000L

    /**
     * A poster and proper title for [item] from TMDB, by the provider's TMDB id when it has one,
     * otherwise by the cleaned name and year. Remembered between app starts; a miss is looked up
     * again after a week. Null when TMDB is off (no key) or the lookup failed.
     */
    suspend fun match(item: VodItem): VodMatch? {
        // Picked with Fix match (here or on a TV): that title.
        com.nuvio.app.features.livetv.data.TitleMatchFixes.get(com.nuvio.app.features.livetv.data.TitleMatchFixes.vodKey(item.uid))?.let { p ->
            return VodMatch(poster = p.poster.orEmpty(), title = p.name.ifBlank { null }, tmdbId = p.tmdbId, checkedMs = LiveTvClock.nowMs())
        }
        matchCache[item.uid]?.let { return it }
        val cached = withContext(Dispatchers.Default) { runCatching { OnDemandStore.cachedMatch(item.uid) }.getOrNull() }
        val now = LiveTvClock.nowMs()
        if (cached != null && (cached.found || now - cached.checkedMs < MATCH_RETRY_MS)) {
            matchCache[item.uid] = cached
            return cached
        }
        val apiKey = TmdbSettingsRepository.effectiveApiKey()
        if (apiKey.isBlank()) return null
        val found = matchLimit.withPermit { runCatching { lookup(item, apiKey, now) }.getOrNull() } ?: return null
        matchCache[item.uid] = found
        withContext(Dispatchers.Default) { runCatching { OnDemandStore.cacheMatch(item.uid, found) } }
        return found
    }

    private suspend fun lookup(item: VodItem, apiKey: String, now: Long): VodMatch {
        val path = if (item.kind == VodKind.SERIES) "tv" else "movie"
        val titleKey = if (item.kind == VodKind.SERIES) "name" else "title"
        item.tmdbId?.toIntOrNull()?.takeIf { it > 0 }?.let { id ->
            val o = parse(httpGetText(buildTmdbUrl("$path/$id", apiKey)))
            if (o != null && o.optInt("id") == id) {
                OnDemandLanguages.remember(item, o.optString("original_language"))
                return VodMatch(poster = image(o.optString("poster_path")), title = o.optString(titleKey).ifBlank { null }, tmdbId = id.toString(), checkedMs = now)
            }
        }
        val title = item.displayTitle.ifBlank { item.name }
        val year = item.year ?: VodTitles.yearInTitle(item.name)
        val query = LinkedHashMap<String, String>()
        query["query"] = title.encodeURLParameter()
        query["include_adult"] = "false"
        if (year != null) query[if (item.kind == VodKind.SERIES) "first_air_date_year" else "year"] = year.toString()
        var results = parse(httpGetText(buildTmdbUrl("search/$path", apiKey, query)))?.optJSONArray("results").orEmpty()
        if (results.isEmpty() && year != null) {
            query.remove("year"); query.remove("first_air_date_year")
            results = parse(httpGetText(buildTmdbUrl("search/$path", apiKey, query)))?.optJSONArray("results").orEmpty()
        }
        // "Troll 2 2025": a year written after the name with just a space; tried without it.
        if (results.isEmpty()) VodTitles.withoutTrailingYear(title)?.let { shorter ->
            query["query"] = shorter.encodeURLParameter()
            results = parse(httpGetText(buildTmdbUrl("search/$path", apiKey, query)))?.optJSONArray("results").orEmpty()
        }
        val wanted = VodTitles.normalize(title)
        val best = results.mapNotNull { it as? JsonObject }
            .firstOrNull { r ->
                val name = r.optString(titleKey).ifBlank { r.optString("original_$titleKey") }
                val ry = VodTitles.yearOf(r.optString(if (item.kind == VodKind.SERIES) "first_air_date" else "release_date"))
                VodTitles.normalize(name) == wanted && (year == null || ry == null || kotlin.math.abs(ry - year) <= 1)
            } ?: results.mapNotNull { it as? JsonObject }.firstOrNull()
        if (best != null && VodTitles.normalize(best.optString(titleKey).ifBlank { best.optString("original_$titleKey") }) == wanted) {
            OnDemandLanguages.remember(item, best.optString("original_language"))
        }
        return if (best == null) VodMatch("", null, null, now)
        else VodMatch(poster = image(best.optString("poster_path")), title = best.optString(titleKey).ifBlank { null }, tmdbId = best.optInt("id").takeIf { it > 0 }?.toString(), checkedMs = now)
    }

    private fun parse(text: String): JsonObject? = runCatching { liveTvJson.parseToJsonElement(text) as? JsonObject }.getOrNull()

    private fun image(path: String): String = if (path.startsWith("/")) "https://image.tmdb.org/t/p/w342$path" else ""

    private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this?.toList() ?: emptyList()

    /** Series read from an M3U playlist's own entries: their id starts with this, then a key of the show. */
    private const val M3U_ID = "m3u:"
    private const val START_DELAY_MS = 20_000L
    /** How long a list waits for its unchecked titles' languages before showing ("Hide Non-English Titles"). */
    private const val PAGE_CHECK_MS = 2_500L
    /** Search results checked before showing (the best matches; the rest are checked in the background). */
    private const val SEARCH_CHECK = 40
    /** Re-import each provider once a day. */
    private const val REFRESH_MS = 24L * 60 * 60 * 1000
}

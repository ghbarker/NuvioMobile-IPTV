package com.nuvio.app.features.livetv.home

import nuvio.composeapp.generated.resources.home_rows_vod_continue_hint
import nuvio.composeapp.generated.resources.home_rows_vod_continue_title
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.model.ChannelGroup
import com.nuvio.app.features.livetv.ondemand.OnDemandGroups
import com.nuvio.app.features.livetv.ondemand.OnDemandRepository
import com.nuvio.app.features.livetv.ondemand.VodKind
import com.nuvio.app.features.livetv.parental.ParentalControls
import com.nuvio.app.features.livetv.sports.SportsCatalog
import com.nuvio.app.features.livetv.sports.SportsRepository
import com.nuvio.app.features.livetv.ui.LiveTvViewState
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.home_rows_add_subtitle
import nuvio.composeapp.generated.resources.home_rows_add_title
import nuvio.composeapp.generated.resources.home_rows_added
import nuvio.composeapp.generated.resources.home_rows_edit
import nuvio.composeapp.generated.resources.home_rows_leagues_heading
import nuvio.composeapp.generated.resources.home_rows_nothing_to_pick
import nuvio.composeapp.generated.resources.home_rows_option_channels
import nuvio.composeapp.generated.resources.home_rows_option_hide_empty
import nuvio.composeapp.generated.resources.home_rows_option_hide_empty_hint
import nuvio.composeapp.generated.resources.home_rows_option_name
import nuvio.composeapp.generated.resources.home_rows_option_name_hint
import nuvio.composeapp.generated.resources.home_rows_option_next
import nuvio.composeapp.generated.resources.home_rows_option_remove
import nuvio.composeapp.generated.resources.home_rows_option_shape
import nuvio.composeapp.generated.resources.home_rows_option_soon
import nuvio.composeapp.generated.resources.home_rows_option_soon_hint
import nuvio.composeapp.generated.resources.home_rows_option_sort
import nuvio.composeapp.generated.resources.home_rows_option_sports
import nuvio.composeapp.generated.resources.home_rows_option_tap
import nuvio.composeapp.generated.resources.home_rows_option_vod
import nuvio.composeapp.generated.resources.home_rows_options_channels_subtitle
import nuvio.composeapp.generated.resources.home_rows_options_sports_subtitle
import nuvio.composeapp.generated.resources.home_rows_options_vod_subtitle
import nuvio.composeapp.generated.resources.home_rows_pick_favorites
import nuvio.composeapp.generated.resources.home_rows_pick_favorites_hint
import nuvio.composeapp.generated.resources.home_rows_pick_my_group
import nuvio.composeapp.generated.resources.home_rows_pick_my_group_hint
import nuvio.composeapp.generated.resources.home_rows_pick_my_group_title
import nuvio.composeapp.generated.resources.home_rows_pick_playlist_group
import nuvio.composeapp.generated.resources.home_rows_pick_playlist_group_hint
import nuvio.composeapp.generated.resources.home_rows_pick_playlist_group_title
import nuvio.composeapp.generated.resources.home_rows_pick_recent
import nuvio.composeapp.generated.resources.home_rows_pick_recent_hint
import nuvio.composeapp.generated.resources.home_rows_pick_sports_all
import nuvio.composeapp.generated.resources.home_rows_pick_sports_all_hint
import nuvio.composeapp.generated.resources.home_rows_pick_sports_one
import nuvio.composeapp.generated.resources.home_rows_pick_sports_one_hint
import nuvio.composeapp.generated.resources.home_rows_pick_sports_one_title
import nuvio.composeapp.generated.resources.home_rows_pick_vod_group
import nuvio.composeapp.generated.resources.home_rows_pick_vod_group_hint
import nuvio.composeapp.generated.resources.home_rows_pick_vod_group_title
import nuvio.composeapp.generated.resources.home_rows_pick_vod_none
import nuvio.composeapp.generated.resources.home_rows_pick_vod_recent
import nuvio.composeapp.generated.resources.home_rows_pick_vod_recent_hint
import nuvio.composeapp.generated.resources.home_rows_playlist_names
import nuvio.composeapp.generated.resources.home_rows_playlist_names_hint
import nuvio.composeapp.generated.resources.home_rows_section_live
import nuvio.composeapp.generated.resources.home_rows_section_sports
import nuvio.composeapp.generated.resources.home_rows_section_vod
import nuvio.composeapp.generated.resources.home_rows_shape_landscape
import nuvio.composeapp.generated.resources.home_rows_shape_portrait
import nuvio.composeapp.generated.resources.home_rows_sort_ending
import nuvio.composeapp.generated.resources.home_rows_sort_number
import nuvio.composeapp.generated.resources.home_rows_sort_yours
import nuvio.composeapp.generated.resources.home_rows_sports_heading
import nuvio.composeapp.generated.resources.home_rows_tap_full
import nuvio.composeapp.generated.resources.home_rows_tap_guide
import nuvio.composeapp.generated.resources.home_rows_tap_page
import nuvio.composeapp.generated.resources.home_rows_tap_play
import nuvio.composeapp.generated.resources.home_rows_vod_movies
import nuvio.composeapp.generated.resources.home_rows_vod_series
import nuvio.composeapp.generated.resources.home_rows_web_back
import nuvio.composeapp.generated.resources.home_rows_web_done
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** One thing a row can show, in a pick list. */
private data class RowChoice(val source: String, val title: String, val detail: String? = null, val label: String = "", val heading: Boolean = false)

/** The lists that open from Add a row (and from a row's options, to change what it shows). */
private enum class Picker { PLAYLIST_GROUP, MY_GROUP, SPORTS, VOD_GROUP, VOD_RECENT }

/** Add a row: Live TV, Sports or On Demand. A pick adds the row to the top of Home. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeRowAddSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val tokens = MaterialTheme.nuvio
    var picker by remember { mutableStateOf<Picker?>(null) }
    fun add(row: HomeLiveRow) {
        HomeLiveRows.add(row)
        scope.launch {
            NuvioToastController.show(getString(Res.string.home_rows_added))
            dismissNuvioBottomSheet(sheetState) { onDismiss() }
        }
    }
    NuvioModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val open = picker
        if (open != null) {
            RowPicker(open, onBack = { picker = null }) { choice ->
                add(
                    when (open) {
                        Picker.SPORTS -> HomeLiveRow.sports(choice.source)
                        Picker.VOD_GROUP, Picker.VOD_RECENT -> HomeLiveRow.vod(choice.source, choice.label)
                        else -> HomeLiveRow.channels(choice.source)
                    },
                )
            }
            return@NuvioModalBottomSheet
        }
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(Res.string.home_rows_add_title), style = MaterialTheme.typography.titleLarge, color = tokens.colors.textPrimary,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = NuvioTokens.Space.s16, end = NuvioTokens.Space.s16, top = NuvioTokens.Space.s8))
            Text(stringResource(Res.string.home_rows_add_subtitle), style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted,
                modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s4))
            SheetHeading(stringResource(Res.string.home_rows_section_live))
            ChoiceRow(stringResource(Res.string.home_rows_pick_favorites), stringResource(Res.string.home_rows_pick_favorites_hint), more = false) {
                add(HomeLiveRow.channels(ChannelGroup.FAVORITES))
            }
            ChoiceRow(stringResource(Res.string.home_rows_pick_recent), stringResource(Res.string.home_rows_pick_recent_hint), more = false) {
                add(HomeLiveRow.channels(ChannelGroup.RECENT))
            }
            ChoiceRow(stringResource(Res.string.home_rows_pick_playlist_group), stringResource(Res.string.home_rows_pick_playlist_group_hint), more = true) {
                picker = Picker.PLAYLIST_GROUP
            }
            ChoiceRow(stringResource(Res.string.home_rows_pick_my_group), stringResource(Res.string.home_rows_pick_my_group_hint), more = true) {
                picker = Picker.MY_GROUP
            }
            SheetHeading(stringResource(Res.string.home_rows_section_sports))
            ChoiceRow(stringResource(Res.string.home_rows_pick_sports_all), stringResource(Res.string.home_rows_pick_sports_all_hint), more = false) {
                add(HomeLiveRow.sports())
            }
            ChoiceRow(stringResource(Res.string.home_rows_pick_sports_one), stringResource(Res.string.home_rows_pick_sports_one_hint), more = true) {
                picker = Picker.SPORTS
            }
            SheetHeading(stringResource(Res.string.home_rows_section_vod))
            ChoiceRow(stringResource(Res.string.home_rows_pick_vod_group), stringResource(Res.string.home_rows_pick_vod_group_hint), more = true) {
                picker = Picker.VOD_GROUP
            }
            ChoiceRow(stringResource(Res.string.home_rows_pick_vod_recent), stringResource(Res.string.home_rows_pick_vod_recent_hint), more = true) {
                picker = Picker.VOD_RECENT
            }
            ChoiceRow(stringResource(Res.string.home_rows_vod_continue_title), stringResource(Res.string.home_rows_vod_continue_hint), more = false) {
                add(HomeLiveRow.vod(HomeLiveRow.VOD_CONTINUE, ""))
            }
            Spacer(Modifier.height(NuvioTokens.Space.s16))
        }
    }
}

/** A row's options: its name, what it shows and how. Done saves; Remove takes it off Home. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeRowEditSheet(row: HomeLiveRow, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val tokens = MaterialTheme.nuvio
    val names by HomeLiveRows.names.collectAsStateWithLifecycle()
    val settings by LiveTvPreferences.settings.collectAsStateWithLifecycle()
    var draft by remember(row.id) { mutableStateOf(row) }
    var picker by remember { mutableStateOf<Picker?>(null) }
    var sourceName by remember(row.id) { mutableStateOf<String?>(null) }
    fun close(then: () -> Unit = {}) { scope.launch { dismissNuvioBottomSheet(sheetState) { onDismiss(); then() } } }
    NuvioModalBottomSheet(onDismissRequest = { HomeLiveRows.update(draft); onDismiss() }, sheetState = sheetState) {
        val open = picker
        if (open != null) {
            RowPicker(open, onBack = { picker = null }) { choice ->
                draft = draft.copy(source = choice.source, label = choice.label.ifBlank { draft.label })
                sourceName = choice.title
                picker = null
            }
            return@NuvioModalBottomSheet
        }
        Column(Modifier.fillMaxWidth().heightIn(max = 680.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(Res.string.home_rows_edit) + " · " + (names[row.id] ?: ""), style = MaterialTheme.typography.titleLarge,
                color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = NuvioTokens.Space.s16, end = NuvioTokens.Space.s16, top = NuvioTokens.Space.s8))
            Text(
                stringResource(
                    when (row.kind) {
                        HomeLiveRow.KIND_SPORTS -> Res.string.home_rows_options_sports_subtitle
                        HomeLiveRow.KIND_VOD -> Res.string.home_rows_options_vod_subtitle
                        else -> Res.string.home_rows_options_channels_subtitle
                    },
                ),
                style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted,
                modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s4),
            )
            Text(stringResource(Res.string.home_rows_option_name), style = MaterialTheme.typography.labelMedium, color = tokens.colors.textMuted,
                modifier = Modifier.padding(start = NuvioTokens.Space.s16, end = NuvioTokens.Space.s16, top = NuvioTokens.Space.s8))
            NuvioInputField(
                value = draft.title,
                onValueChange = { draft = draft.copy(title = it) },
                placeholder = stringResource(Res.string.home_rows_option_name_hint),
                modifier = Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s4),
            )
            when (row.kind) {
                HomeLiveRow.KIND_CHANNELS -> {
                    val from = sourceName ?: names[row.id]?.takeIf { draft.source == row.source } ?: draft.source.substringAfter("::")
                    ChoiceRow(stringResource(Res.string.home_rows_option_channels), from, more = true) {
                        picker = if (draft.source.startsWith(ChannelGroup.CUSTOM_PREFIX)) Picker.MY_GROUP else Picker.PLAYLIST_GROUP
                    }
                    OptionChips(
                        stringResource(Res.string.home_rows_option_sort),
                        listOf("yours" to stringResource(Res.string.home_rows_sort_yours), "number" to stringResource(Res.string.home_rows_sort_number),
                            "ending" to stringResource(Res.string.home_rows_sort_ending)),
                        draft.sort,
                    ) { draft = draft.copy(sort = it) }
                    OptionChips(
                        stringResource(Res.string.home_rows_option_tap),
                        listOf("full" to stringResource(Res.string.home_rows_tap_full), "guide" to stringResource(Res.string.home_rows_tap_guide)),
                        if (draft.opensGuide) "guide" else "full",
                    ) { draft = draft.copy(opensGuide = it == "guide") }
                    ShapeChips(draft) { draft = it }
                    SwitchRow(stringResource(Res.string.home_rows_option_next), null, draft.showNext) { draft = draft.copy(showNext = it) }
                    SwitchRow(stringResource(Res.string.home_rows_option_hide_empty), stringResource(Res.string.home_rows_option_hide_empty_hint), draft.hidePlaceholders) {
                        draft = draft.copy(hidePlaceholders = it)
                    }
                    PlaylistNamesSwitch(settings.homeRowPlaylistNames)
                }
                HomeLiveRow.KIND_SPORTS -> {
                    val from = sourceName ?: names[row.id]?.takeIf { draft.source == row.source && row.title.isBlank() } ?: produceSportsName(draft.source)
                    ChoiceRow(stringResource(Res.string.home_rows_option_sports), from, more = true) { picker = Picker.SPORTS }
                    OptionChips(
                        stringResource(Res.string.home_rows_option_tap),
                        listOf("page" to stringResource(Res.string.home_rows_tap_page), "play" to stringResource(Res.string.home_rows_tap_play)),
                        if (draft.playsGame) "play" else "page",
                    ) { draft = draft.copy(playsGame = it == "play") }
                    ShapeChips(draft) { draft = it }
                    SwitchRow(stringResource(Res.string.home_rows_option_soon), stringResource(Res.string.home_rows_option_soon_hint), draft.startingSoon) {
                        draft = draft.copy(startingSoon = it)
                    }
                }
                else -> {
                    val from = sourceName ?: draft.label.ifBlank { names[row.id].orEmpty() }
                    ChoiceRow(stringResource(Res.string.home_rows_option_vod), from, more = true) {
                        picker = if (draft.source.startsWith(HomeLiveRow.VOD_GROUP_PREFIX)) Picker.VOD_GROUP else Picker.VOD_RECENT
                    }
                    PlaylistNamesSwitch(settings.homeRowPlaylistNames)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s12),
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10),
            ) {
                TextButton(onClick = { close { HomeLiveRows.remove(row.id) } }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.home_rows_option_remove), color = tokens.colors.danger)
                }
                TextButton(onClick = { val d = draft; close { HomeLiveRows.update(d) } }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.home_rows_web_done), color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun produceSportsName(source: String): String {
    val name by produceState("", source) { value = HomeLiveRows.sportsTitle(source) }
    return name
}

@Composable
private fun PlaylistNamesSwitch(on: Boolean) {
    SwitchRow(stringResource(Res.string.home_rows_playlist_names), stringResource(Res.string.home_rows_playlist_names_hint), on) { v ->
        LiveTvPreferences.updateSettings { it.copy(homeRowPlaylistNames = v) }
    }
}

@Composable
private fun ShapeChips(row: HomeLiveRow, onChange: (HomeLiveRow) -> Unit) {
    OptionChips(
        stringResource(Res.string.home_rows_option_shape),
        listOf(HomeLiveRow.SHAPE_LANDSCAPE to stringResource(Res.string.home_rows_shape_landscape),
            HomeLiveRow.SHAPE_PORTRAIT to stringResource(Res.string.home_rows_shape_portrait)),
        if (row.portrait) HomeLiveRow.SHAPE_PORTRAIT else HomeLiveRow.SHAPE_LANDSCAPE,
    ) { onChange(row.copy(shape = it)) }
}

// ------------------------------------------------------------------ pick lists

@Composable
private fun RowPicker(picker: Picker, onBack: () -> Unit, onPick: (RowChoice) -> Unit) {
    val tokens = MaterialTheme.nuvio
    val list by LiveTvViewState.list.collectAsStateWithLifecycle()
    val playlists by LiveTvPreferences.playlists.collectAsStateWithLifecycle()
    val user by LiveTvPreferences.userState.collectAsStateWithLifecycle()
    val settings by LiveTvPreferences.settings.collectAsStateWithLifecycle()
    val sportsSettings by SportsRepository.settings.collectAsStateWithLifecycle()
    val version by OnDemandRepository.version.collectAsStateWithLifecycle()
    val moviesLabel = stringResource(Res.string.home_rows_vod_movies)
    val seriesLabel = stringResource(Res.string.home_rows_vod_series)
    val continueLabel = stringResource(Res.string.home_rows_vod_continue_title)
    val sportsHeading = stringResource(Res.string.home_rows_sports_heading)
    val leaguesHeading = stringResource(Res.string.home_rows_leagues_heading)
    val title = stringResource(
        when (picker) {
            Picker.PLAYLIST_GROUP -> Res.string.home_rows_pick_playlist_group_title
            Picker.MY_GROUP -> Res.string.home_rows_pick_my_group_title
            Picker.SPORTS -> Res.string.home_rows_pick_sports_one_title
            Picker.VOD_GROUP -> Res.string.home_rows_pick_vod_group_title
            Picker.VOD_RECENT -> Res.string.home_rows_pick_vod_recent
        },
    )
    val names = remember(playlists) { playlists.associate { it.id to it.name } }
    val vodChoices by produceState(emptyList<RowChoice>(), picker, version, user, settings, moviesLabel, seriesLabel) {
        if (picker != Picker.VOD_GROUP) return@produceState
        val multi = playlists.size > 1
        value = listOf(VodKind.MOVIE to moviesLabel, VodKind.SERIES to seriesLabel).flatMap { (kind, kindLabel) ->
            OnDemandGroups.visible(OnDemandRepository.categories(kind), user)
                .filterNot { c -> ParentalControls.isLocked("vod:${c.uid}", c.name, settings, user.lockedGroups) }
                .map { c ->
                    RowChoice(
                        source = HomeLiveRow.VOD_GROUP_PREFIX + c.uid,
                        title = OnDemandGroups.name(c, user, settings),
                        detail = listOfNotNull(kindLabel, names[c.playlistId]?.takeIf { multi }, c.count.toString()).joinToString(" · "),
                        // Persist the provider name, so disabling cleanup restores it on Home.
                        label = c.name,
                    )
                }
        }
    }
    val choices: List<RowChoice> = when (picker) {
        Picker.PLAYLIST_GROUP -> list.sections
            .filter { !it.group.special && !it.group.locked && !it.group.id.startsWith(ChannelGroup.CUSTOM_PREFIX) }
            .map { s -> RowChoice(s.group.id, s.group.title, listOfNotNull(s.group.sourceId?.let { names[it] }, s.channels.size.toString()).joinToString(" · ")) }
        Picker.MY_GROUP -> list.sections
            .filter { it.group.id.startsWith(ChannelGroup.CUSTOM_PREFIX) && !it.group.locked }
            .map { s -> RowChoice(s.group.id, s.group.title, s.channels.size.toString()) }
        Picker.SPORTS -> {
            val leagues = SportsCatalog.ordered(sportsSettings).filter { SportsCatalog.shown(it, sportsSettings) }
            val sports = leagues.map { it.sport }.distinct()
            listOf(RowChoice("", sportsHeading, heading = true)) +
                sports.map { RowChoice("sport:${it.name}", "${it.icon}  ${it.label}") } +
                listOf(RowChoice("", leaguesHeading, heading = true)) +
                leagues.map { RowChoice("league:${it.id}", "${it.sport.icon}  ${it.name}") }
        }
        Picker.VOD_GROUP -> vodChoices
        Picker.VOD_RECENT -> listOf(
            RowChoice(HomeLiveRow.VOD_RECENT_MOVIES, moviesLabel),
            RowChoice(HomeLiveRow.VOD_RECENT_SERIES, seriesLabel),
            RowChoice(HomeLiveRow.VOD_CONTINUE, continueLabel),
        )
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s4), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.home_rows_web_back), tint = tokens.colors.textPrimary)
            }
            Text(title, style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold)
        }
        if (choices.isEmpty()) {
            Text(
                stringResource(if (picker == Picker.VOD_GROUP) Res.string.home_rows_pick_vod_none else Res.string.home_rows_nothing_to_pick),
                style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted,
                modifier = Modifier.padding(NuvioTokens.Space.s16),
            )
        } else {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                items(choices, key = { if (picker == Picker.VOD_GROUP) it.source else it.source + "|" + it.title }) { c ->
                    if (c.heading) SheetHeading(c.title)
                    else ChoiceRow(c.title, c.detail, more = false) { onPick(c) }
                }
            }
        }
        Spacer(Modifier.height(NuvioTokens.Space.s12))
    }
}

// ------------------------------------------------------------------ small pieces

@Composable
private fun SheetHeading(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.nuvio.colors.textMuted,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = NuvioTokens.Space.s16, end = NuvioTokens.Space.s16, top = NuvioTokens.Space.s14, bottom = NuvioTokens.Space.s4),
    )
}

@Composable
private fun ChoiceRow(title: String, detail: String?, more: Boolean, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick)
            .padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s10),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            detail?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(
            if (more) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Filled.Add,
            contentDescription = null, tint = tokens.colors.textMuted, modifier = Modifier.size(NuvioTokens.Icon.md),
        )
    }
}

@Composable
private fun OptionChips(title: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val tokens = MaterialTheme.nuvio
    Column(Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s8),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
            options.forEach { (key, label) -> Chip(label = label, selected = key == selected, onClick = { onSelect(key) }) }
        }
    }
}

@Composable
private fun SwitchRow(title: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val tokens = MaterialTheme.nuvio
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }
            .padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s10),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted) }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = tokens.colors.onAccent,
                checkedTrackColor = tokens.colors.accent,
                uncheckedThumbColor = tokens.colors.textMuted,
                uncheckedTrackColor = tokens.colors.borderDefault,
            ),
        )
    }
}

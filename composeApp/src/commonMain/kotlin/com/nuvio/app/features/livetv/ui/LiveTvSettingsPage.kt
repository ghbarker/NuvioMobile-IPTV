package com.nuvio.app.features.livetv.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.nuvio.stalker.StalkerConfig
import io.ktor.http.Url
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.format.formatLocalDateTime
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogOption
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.NuvioSectionLabel
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.isIos
import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.data.LiveTvRepository
import com.nuvio.app.features.livetv.data.LiveTvDns
import com.nuvio.app.features.livetv.data.DnsChoice
import com.nuvio.app.features.livetv.data.CivilTime
import com.nuvio.app.features.livetv.model.LiveTvClock
import com.nuvio.app.features.livetv.data.BackupFileException
import com.nuvio.app.features.livetv.data.BackupFileInfo
import com.nuvio.app.features.livetv.data.exportBackupFileText
import com.nuvio.app.features.livetv.data.importBackupFile
import com.nuvio.app.features.livetv.data.parseBackupFile
import com.nuvio.app.features.livetv.model.EpgSource
import com.nuvio.app.features.livetv.model.PlaylistAccount
import com.nuvio.app.features.livetv.model.PlaylistSource
import com.nuvio.app.features.livetv.ondemand.OnDemandRepository
import com.nuvio.app.features.livetv.ondemand.VodSort
import com.nuvio.app.features.livetv.record.LiveTvRecorder
import com.nuvio.app.features.livetv.record.FoundComputer
import com.nuvio.app.features.livetv.record.NetworkFolderConfig
import com.nuvio.app.features.livetv.record.NetworkSearchState
import com.nuvio.app.features.livetv.record.SmbShare
import com.nuvio.app.features.livetv.record.NetworkLibrary
import com.nuvio.app.features.livetv.record.NetworkLibraryPlatform
import com.nuvio.app.features.livetv.record.NetworkUploads
import com.nuvio.app.features.livetv.record.RecordingPlatform
import com.nuvio.app.features.livetv.record.RecordingRules
import com.nuvio.app.features.livetv.sports.SportsRepository
import com.nuvio.app.features.livetv.sports.SportsScoreDelay
import com.nuvio.app.features.livetv.ui.sports.GameAlertsRow
import com.nuvio.app.features.livetv.ui.sports.GameAlertsSheet
import com.nuvio.app.features.livetv.parental.ParentalControls
import com.nuvio.app.features.livetv.ondemand.VodCategory
import com.nuvio.app.features.livetv.ondemand.OnDemandGroups
import com.nuvio.app.features.livetv.ondemand.VodKind
import com.nuvio.app.features.livetv.model.ChannelGroup
import androidx.compose.runtime.produceState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationPlatform
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nuvio.app.features.livetv.sync.LiveTvDriveSync
import com.nuvio.app.features.livetv.sync.SignInPrompt
import com.nuvio.app.features.settings.SettingsGroup
import com.nuvio.app.features.settings.SettingsGroupDivider
import com.nuvio.app.features.settings.SettingsNavigationRow
import com.nuvio.app.features.settings.SettingsSection
import com.nuvio.app.features.settings.SettingsSwitchRow
import com.nuvio.app.features.livetv.calendar.CalendarRepository
import nuvio.composeapp.generated.resources.livetv_stalker_playback_limits
import nuvio.composeapp.generated.resources.livetv_add_stalker
import nuvio.composeapp.generated.resources.livetv_add_stalker_description
import nuvio.composeapp.generated.resources.livetv_stalker_portal
import nuvio.composeapp.generated.resources.livetv_stalker_mac
import nuvio.composeapp.generated.resources.livetv_stalker_load_guide
import nuvio.composeapp.generated.resources.livetv_stalker_device_options
import nuvio.composeapp.generated.resources.livetv_stalker_device_options_description
import nuvio.composeapp.generated.resources.livetv_stalker_serial
import nuvio.composeapp.generated.resources.livetv_stalker_device_id
import nuvio.composeapp.generated.resources.livetv_stalker_device_id2
import nuvio.composeapp.generated.resources.livetv_stalker_signature
import nuvio.composeapp.generated.resources.livetv_stalker_timezone
import nuvio.composeapp.generated.resources.livetv_stalker_language
import nuvio.composeapp.generated.resources.livetv_stalker_optional_login
import nuvio.composeapp.generated.resources.livetv_stalker_device_binding
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.livetv_section_help
import nuvio.composeapp.generated.resources.livetv_report_title
import nuvio.composeapp.generated.resources.livetv_report_description
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.action_delete
import nuvio.composeapp.generated.resources.action_edit
import nuvio.composeapp.generated.resources.action_save
import nuvio.composeapp.generated.resources.livetv_add_epg
import nuvio.composeapp.generated.resources.livetv_add_epg_description
import nuvio.composeapp.generated.resources.livetv_add_m3u
import nuvio.composeapp.generated.resources.livetv_add_m3u_description
import nuvio.composeapp.generated.resources.livetv_add_xtream
import nuvio.composeapp.generated.resources.livetv_add_xtream_description
import nuvio.composeapp.generated.resources.livetv_xtream_include_channels
import nuvio.composeapp.generated.resources.livetv_xtream_include_vod
import nuvio.composeapp.generated.resources.livetv_xtream_movies_only
import nuvio.composeapp.generated.resources.livetv_xtream_nothing_included
import nuvio.composeapp.generated.resources.livetv_backup_now
import nuvio.composeapp.generated.resources.livetv_backup_now_description
import nuvio.composeapp.generated.resources.livetv_catchup_prefer_hls
import nuvio.composeapp.generated.resources.livetv_catchup_prefer_hls_description
import nuvio.composeapp.generated.resources.livetv_catchup_shift
import nuvio.composeapp.generated.resources.livetv_catchup_shift_description
import nuvio.composeapp.generated.resources.livetv_catchup_shift_none
import nuvio.composeapp.generated.resources.livetv_catchup_zone
import nuvio.composeapp.generated.resources.livetv_catchup_zone_automatic
import nuvio.composeapp.generated.resources.livetv_catchup_zone_description
import nuvio.composeapp.generated.resources.livetv_catchup_zone_found
import nuvio.composeapp.generated.resources.livetv_catchup_zone_not_found
import nuvio.composeapp.generated.resources.livetv_epg_shift
import nuvio.composeapp.generated.resources.livetv_epg_shift_description
import nuvio.composeapp.generated.resources.livetv_field_guide_link
import nuvio.composeapp.generated.resources.livetv_guides
import com.nuvio.app.features.profiles.NuvioProfile
import com.nuvio.app.features.profiles.ProfileRepository
import nuvio.composeapp.generated.resources.livetv_show_on_profiles
import nuvio.composeapp.generated.resources.livetv_show_on_profiles_all
import nuvio.composeapp.generated.resources.livetv_show_on_profiles_count
import nuvio.composeapp.generated.resources.livetv_show_on_profiles_description
import nuvio.composeapp.generated.resources.livetv_show_on_profiles_value
import nuvio.composeapp.generated.resources.livetv_show_on_this_profile
import nuvio.composeapp.generated.resources.livetv_ui_playlist_profile_hidden
import nuvio.composeapp.generated.resources.livetv_guides_all
import nuvio.composeapp.generated.resources.livetv_guides_description
import nuvio.composeapp.generated.resources.livetv_guides_none
import nuvio.composeapp.generated.resources.livetv_guides_some
import nuvio.composeapp.generated.resources.livetv_guide_shift
import nuvio.composeapp.generated.resources.livetv_guide_shift_description
import nuvio.composeapp.generated.resources.livetv_time_shifts_note
import nuvio.composeapp.generated.resources.livetv_clean_group_names
import nuvio.composeapp.generated.resources.livetv_clean_group_names_description
import nuvio.composeapp.generated.resources.livetv_clean_vod_group_names
import nuvio.composeapp.generated.resources.livetv_clean_vod_group_names_description
import nuvio.composeapp.generated.resources.livetv_hide_stray_entries
import nuvio.composeapp.generated.resources.livetv_hide_stray_entries_description
import nuvio.composeapp.generated.resources.livetv_section_catchup
import nuvio.composeapp.generated.resources.livetv_days_ahead
import nuvio.composeapp.generated.resources.livetv_days_ahead_description
import nuvio.composeapp.generated.resources.livetv_hours_back
import nuvio.composeapp.generated.resources.livetv_hours_back_description
import nuvio.composeapp.generated.resources.livetv_delete_source_message
import nuvio.composeapp.generated.resources.livetv_disable
import nuvio.composeapp.generated.resources.livetv_drive_account
import nuvio.composeapp.generated.resources.livetv_drive_auto_sync
import nuvio.composeapp.generated.resources.livetv_drive_auto_sync_description
import nuvio.composeapp.generated.resources.livetv_drive_body
import nuvio.composeapp.generated.resources.livetv_drive_code_instructions
import nuvio.composeapp.generated.resources.livetv_drive_code_waiting
import nuvio.composeapp.generated.resources.livetv_drive_sign_in
import nuvio.composeapp.generated.resources.livetv_drive_sign_in_description
import nuvio.composeapp.generated.resources.livetv_drive_sign_out
import nuvio.composeapp.generated.resources.livetv_drive_sign_out_description
import nuvio.composeapp.generated.resources.livetv_drive_unavailable
import nuvio.composeapp.generated.resources.livetv_enable
import nuvio.composeapp.generated.resources.livetv_epg_refresh
import nuvio.composeapp.generated.resources.livetv_epg_refresh_description
import nuvio.composeapp.generated.resources.livetv_show_update_banners
import nuvio.composeapp.generated.resources.livetv_show_update_banners_description
import nuvio.composeapp.generated.resources.livetv_dns
import nuvio.composeapp.generated.resources.livetv_dns_description
import nuvio.composeapp.generated.resources.livetv_dns_dialog_message
import nuvio.composeapp.generated.resources.livetv_dns_system
import nuvio.composeapp.generated.resources.livetv_dns_cloudflare
import nuvio.composeapp.generated.resources.livetv_dns_google
import nuvio.composeapp.generated.resources.livetv_field_name
import nuvio.composeapp.generated.resources.livetv_field_password
import nuvio.composeapp.generated.resources.livetv_field_server
import nuvio.composeapp.generated.resources.livetv_field_url
import nuvio.composeapp.generated.resources.livetv_field_user_agent
import nuvio.composeapp.generated.resources.livetv_field_username
import nuvio.composeapp.generated.resources.livetv_field_xmltv_url
import nuvio.composeapp.generated.resources.livetv_load_embedded_epg
import nuvio.composeapp.generated.resources.livetv_never_updated
import nuvio.composeapp.generated.resources.livetv_playlist_refresh
import nuvio.composeapp.generated.resources.livetv_playlist_refresh_description
import nuvio.composeapp.generated.resources.livetv_remember_audio
import nuvio.composeapp.generated.resources.livetv_remember_audio_description
import nuvio.composeapp.generated.resources.livetv_restore
import nuvio.composeapp.generated.resources.livetv_restore_description
import nuvio.composeapp.generated.resources.livetv_restore_message
import nuvio.composeapp.generated.resources.livetv_section_backup
import nuvio.composeapp.generated.resources.livetv_backup_file_body
import nuvio.composeapp.generated.resources.livetv_import_file
import nuvio.composeapp.generated.resources.livetv_import_file_description
import nuvio.composeapp.generated.resources.livetv_export_file
import nuvio.composeapp.generated.resources.livetv_export_file_description
import nuvio.composeapp.generated.resources.livetv_import_confirm_title
import nuvio.composeapp.generated.resources.livetv_import_confirm_message
import nuvio.composeapp.generated.resources.livetv_import_confirm_action
import nuvio.composeapp.generated.resources.livetv_import_unknown_device
import nuvio.composeapp.generated.resources.livetv_import_unknown_date
import nuvio.composeapp.generated.resources.livetv_import_done
import nuvio.composeapp.generated.resources.livetv_export_done
import nuvio.composeapp.generated.resources.livetv_file_error_not_backup
import nuvio.composeapp.generated.resources.livetv_file_error_newer
import nuvio.composeapp.generated.resources.livetv_file_error_no_sources
import nuvio.composeapp.generated.resources.livetv_file_error_unavailable
import nuvio.composeapp.generated.resources.livetv_file_error_read
import nuvio.composeapp.generated.resources.livetv_file_error_write
import nuvio.composeapp.generated.resources.livetv_file_error_import
import nuvio.composeapp.generated.resources.livetv_drive_subsection
import nuvio.composeapp.generated.resources.livetv_section_general
import nuvio.composeapp.generated.resources.livetv_section_on_demand
import nuvio.composeapp.generated.resources.livetv_section_recordings
import nuvio.composeapp.generated.resources.livetv_rec_exact_alarms
import nuvio.composeapp.generated.resources.livetv_rec_exact_alarms_description
import nuvio.composeapp.generated.resources.livetv_rec_keep_all
import nuvio.composeapp.generated.resources.livetv_rec_keep_last
import nuvio.composeapp.generated.resources.livetv_rec_notifications
import nuvio.composeapp.generated.resources.livetv_rec_notifications_description
import nuvio.composeapp.generated.resources.livetv_rec_padding_post
import nuvio.composeapp.generated.resources.livetv_rec_padding_post_description
import nuvio.composeapp.generated.resources.livetv_rec_padding_pre
import nuvio.composeapp.generated.resources.livetv_rec_padding_pre_description
import nuvio.composeapp.generated.resources.livetv_rec_series_keep
import nuvio.composeapp.generated.resources.livetv_rec_series_keep_description
import nuvio.composeapp.generated.resources.livetv_rec_space_rule
import nuvio.composeapp.generated.resources.livetv_rec_space_rule_delete
import nuvio.composeapp.generated.resources.livetv_rec_space_rule_description
import nuvio.composeapp.generated.resources.livetv_rec_space_rule_stop
import nuvio.composeapp.generated.resources.livetv_rec_storage
import nuvio.composeapp.generated.resources.livetv_rec_storage_description
import nuvio.composeapp.generated.resources.livetv_rec_storage_network_description
import nuvio.composeapp.generated.resources.livetv_rec_storage_choice_message
import nuvio.composeapp.generated.resources.livetv_rec_this_device
import nuvio.composeapp.generated.resources.livetv_rec_network_folder
import nuvio.composeapp.generated.resources.livetv_rec_network_folder_description
import nuvio.composeapp.generated.resources.livetv_rec_network_not_set_up
import nuvio.composeapp.generated.resources.livetv_network_title
import nuvio.composeapp.generated.resources.livetv_network_subtitle
import nuvio.composeapp.generated.resources.livetv_network_address
import nuvio.composeapp.generated.resources.livetv_network_username
import nuvio.composeapp.generated.resources.livetv_network_password
import nuvio.composeapp.generated.resources.livetv_network_tips
import nuvio.composeapp.generated.resources.livetv_network_test_save
import nuvio.composeapp.generated.resources.livetv_network_save_anyway
import nuvio.composeapp.generated.resources.livetv_network_remove
import nuvio.composeapp.generated.resources.livetv_network_checking
import nuvio.composeapp.generated.resources.livetv_network_checking_vpn
import nuvio.composeapp.generated.resources.livetv_network_bad_address
import nuvio.composeapp.generated.resources.livetv_network_saved
import nuvio.composeapp.generated.resources.livetv_network_removed
import nuvio.composeapp.generated.resources.livetv_network_find
import nuvio.composeapp.generated.resources.livetv_network_find_row_subtitle
import nuvio.composeapp.generated.resources.livetv_network_find_subtitle
import nuvio.composeapp.generated.resources.livetv_network_find_type_instead
import nuvio.composeapp.generated.resources.livetv_network_find_searching
import nuvio.composeapp.generated.resources.livetv_network_find_pick
import nuvio.composeapp.generated.resources.livetv_network_find_none
import nuvio.composeapp.generated.resources.livetv_network_find_no_network
import nuvio.composeapp.generated.resources.livetv_network_find_vpn
import nuvio.composeapp.generated.resources.livetv_network_find_again
import nuvio.composeapp.generated.resources.livetv_network_find_back
import nuvio.composeapp.generated.resources.livetv_network_find_login_subtitle
import nuvio.composeapp.generated.resources.livetv_network_find_login_tip
import nuvio.composeapp.generated.resources.livetv_network_find_show_shares
import nuvio.composeapp.generated.resources.livetv_network_find_signing_in
import nuvio.composeapp.generated.resources.livetv_network_find_shares_subtitle
import nuvio.composeapp.generated.resources.livetv_network_find_no_shares
import nuvio.composeapp.generated.resources.livetv_network_find_folders_subtitle
import nuvio.composeapp.generated.resources.livetv_network_find_use_folder
import nuvio.composeapp.generated.resources.livetv_network_find_new_folder
import nuvio.composeapp.generated.resources.livetv_network_find_new_folder_name
import nuvio.composeapp.generated.resources.livetv_network_find_new_folder_in
import nuvio.composeapp.generated.resources.livetv_network_find_create
import nuvio.composeapp.generated.resources.livetv_network_find_bad_folder_name
import nuvio.composeapp.generated.resources.livetv_network_find_loading_folders
import nuvio.composeapp.generated.resources.livetv_vod_default_sort
import nuvio.composeapp.generated.resources.livetv_vod_default_sort_description
import nuvio.composeapp.generated.resources.livetv_vod_autoplay
import nuvio.composeapp.generated.resources.livetv_vod_autoplay_description
import nuvio.composeapp.generated.resources.livetv_vod_pos_after_first
import nuvio.composeapp.generated.resources.livetv_vod_pos_first
import nuvio.composeapp.generated.resources.livetv_vod_pos_last
import nuvio.composeapp.generated.resources.livetv_vod_tab_position
import nuvio.composeapp.generated.resources.livetv_vod_tab_position_description
import nuvio.composeapp.generated.resources.livetv_vod_import
import nuvio.composeapp.generated.resources.livetv_vod_import_description
import nuvio.composeapp.generated.resources.livetv_vod_merge_duplicates
import nuvio.composeapp.generated.resources.livetv_vod_hide_non_english
import nuvio.composeapp.generated.resources.livetv_vod_hide_non_english_description
import nuvio.composeapp.generated.resources.livetv_vod_merge_duplicates_description
import nuvio.composeapp.generated.resources.livetv_vod_in_streams
import nuvio.composeapp.generated.resources.livetv_vod_in_streams_description
import nuvio.composeapp.generated.resources.livetv_vod_update_now
import nuvio.composeapp.generated.resources.livetv_vod_update_now_description
import nuvio.composeapp.generated.resources.livetv_section_guides
import nuvio.composeapp.generated.resources.livetv_section_playlists
import nuvio.composeapp.generated.resources.livetv_section_watching
import nuvio.composeapp.generated.resources.livetv_backup_order_title
import nuvio.composeapp.generated.resources.livetv_backup_order_row_subtitle
import nuvio.composeapp.generated.resources.livetv_section_channel_display
import nuvio.composeapp.generated.resources.livetv_phone_logos
import nuvio.composeapp.generated.resources.livetv_phone_logos_description
import nuvio.composeapp.generated.resources.livetv_phone_names
import nuvio.composeapp.generated.resources.livetv_phone_names_description
import nuvio.composeapp.generated.resources.livetv_phone_numbers
import nuvio.composeapp.generated.resources.livetv_phone_numbers_description
import nuvio.composeapp.generated.resources.livetv_now_line
import nuvio.composeapp.generated.resources.livetv_now_line_description
import nuvio.composeapp.generated.resources.livetv_show_tab
import nuvio.composeapp.generated.resources.livetv_sports
import nuvio.composeapp.generated.resources.livetv_parental
import nuvio.composeapp.generated.resources.livetv_section_reminders
import nuvio.composeapp.generated.resources.livetv_reminders_list
import nuvio.composeapp.generated.resources.livetv_reminders_list_description
import nuvio.composeapp.generated.resources.livetv_reminders_none
import nuvio.composeapp.generated.resources.livetv_reminder_lead
import nuvio.composeapp.generated.resources.livetv_reminder_lead_description
import nuvio.composeapp.generated.resources.livetv_reminder_lead_at_start
import nuvio.composeapp.generated.resources.livetv_reminder_lead_minutes
import com.nuvio.app.features.livetv.reminders.LiveTvReminders
import androidx.compose.material.icons.filled.Notifications
import nuvio.composeapp.generated.resources.livetv_parental_pin
import nuvio.composeapp.generated.resources.livetv_parental_pin_set
import nuvio.composeapp.generated.resources.livetv_parental_pin_not_set
import nuvio.composeapp.generated.resources.livetv_parental_pin_description
import nuvio.composeapp.generated.resources.livetv_parental_set_pin
import nuvio.composeapp.generated.resources.livetv_parental_change_pin
import nuvio.composeapp.generated.resources.livetv_parental_remove_pin
import nuvio.composeapp.generated.resources.livetv_parental_new_pin
import nuvio.composeapp.generated.resources.livetv_parental_current_pin
import nuvio.composeapp.generated.resources.livetv_parental_lock_adult
import nuvio.composeapp.generated.resources.livetv_parental_lock_adult_description
import nuvio.composeapp.generated.resources.livetv_parental_lock_logins
import nuvio.composeapp.generated.resources.livetv_parental_lock_logins_description
import nuvio.composeapp.generated.resources.livetv_parental_logins_pin
import nuvio.composeapp.generated.resources.livetv_parental_locked_groups
import nuvio.composeapp.generated.resources.livetv_parental_locked_groups_description
import nuvio.composeapp.generated.resources.livetv_parental_locked_count
import nuvio.composeapp.generated.resources.livetv_parental_channel_groups
import nuvio.composeapp.generated.resources.livetv_parental_vod_groups
import nuvio.composeapp.generated.resources.action_close
import nuvio.composeapp.generated.resources.action_save
import nuvio.composeapp.generated.resources.livetv_sports_show
import nuvio.composeapp.generated.resources.livetv_sports_show_description
import nuvio.composeapp.generated.resources.livetv_sports_best_channel_first
import nuvio.composeapp.generated.resources.livetv_sports_best_channel_first_description
import nuvio.composeapp.generated.resources.livetv_sports_hide_scores
import nuvio.composeapp.generated.resources.livetv_sports_score_delay
import nuvio.composeapp.generated.resources.livetv_sports_score_delay_description
import nuvio.composeapp.generated.resources.livetv_sports_score_delay_off
import nuvio.composeapp.generated.resources.livetv_sports_score_delay_seconds
import nuvio.composeapp.generated.resources.livetv_sports_score_delay_one_minute
import nuvio.composeapp.generated.resources.livetv_sports_score_delay_minutes
import nuvio.composeapp.generated.resources.livetv_sports_hide_scores_description
import nuvio.composeapp.generated.resources.livetv_sports_score_chip
import nuvio.composeapp.generated.resources.livetv_sports_score_chip_description
import nuvio.composeapp.generated.resources.livetv_sports_show_condensed
import nuvio.composeapp.generated.resources.livetv_sports_show_condensed_description
import nuvio.composeapp.generated.resources.livetv_sports_show_highlights
import nuvio.composeapp.generated.resources.livetv_sports_show_highlights_description
import nuvio.composeapp.generated.resources.livetv_show_tab_description
import nuvio.composeapp.generated.resources.livetv_stream_format
import nuvio.composeapp.generated.resources.livetv_stream_format_description
import nuvio.composeapp.generated.resources.livetv_switch_backup
import nuvio.composeapp.generated.resources.livetv_switch_backup_description
import nuvio.composeapp.generated.resources.livetv_adaptive_buffer
import nuvio.composeapp.generated.resources.livetv_adaptive_buffer_description
import nuvio.composeapp.generated.resources.livetv_update_all
import nuvio.composeapp.generated.resources.livetv_update_all_description
import nuvio.composeapp.generated.resources.livetv_update_now
import nuvio.composeapp.generated.resources.livetv_updated_at
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import nuvio.composeapp.generated.resources.livetv_ui_cal_games
import nuvio.composeapp.generated.resources.livetv_ui_cal_games_desc
import nuvio.composeapp.generated.resources.livetv_ui_cal_hide_spoilers
import nuvio.composeapp.generated.resources.livetv_ui_cal_hide_spoilers_desc
import nuvio.composeapp.generated.resources.livetv_ui_cal_new_cards
import nuvio.composeapp.generated.resources.livetv_ui_cal_new_cards_desc
import nuvio.composeapp.generated.resources.livetv_ui_cal_nothing_saved
import nuvio.composeapp.generated.resources.livetv_ui_cal_nothing_saved_desc
import nuvio.composeapp.generated.resources.livetv_ui_cal_saved_channels
import nuvio.composeapp.generated.resources.livetv_ui_cal_saved_network
import nuvio.composeapp.generated.resources.livetv_ui_cal_saved_show
import nuvio.composeapp.generated.resources.livetv_ui_cal_upcoming_row
import nuvio.composeapp.generated.resources.livetv_ui_cal_upcoming_row_desc
import nuvio.composeapp.generated.resources.livetv_ui_calendar
import nuvio.composeapp.generated.resources.livetv_ui_channel_count
import nuvio.composeapp.generated.resources.livetv_ui_connected
import nuvio.composeapp.generated.resources.livetv_ui_connections
import nuvio.composeapp.generated.resources.livetv_ui_days
import nuvio.composeapp.generated.resources.livetv_ui_expired
import nuvio.composeapp.generated.resources.livetv_ui_expires_in
import nuvio.composeapp.generated.resources.livetv_ui_expires_today
import nuvio.composeapp.generated.resources.livetv_ui_hours
import nuvio.composeapp.generated.resources.livetv_ui_minutes
import nuvio.composeapp.generated.resources.livetv_ui_movies
import nuvio.composeapp.generated.resources.livetv_ui_netfolder_failed
import nuvio.composeapp.generated.resources.livetv_ui_none
import nuvio.composeapp.generated.resources.livetv_ui_off
import nuvio.composeapp.generated.resources.livetv_ui_profile_n
import nuvio.composeapp.generated.resources.livetv_ui_series
import nuvio.composeapp.generated.resources.livetv_ui_zone_central_eu
import nuvio.composeapp.generated.resources.livetv_ui_zone_eastern_eu
import nuvio.composeapp.generated.resources.livetv_ui_zone_gulf
import nuvio.composeapp.generated.resources.livetv_ui_zone_india
import nuvio.composeapp.generated.resources.livetv_ui_zone_moscow
import nuvio.composeapp.generated.resources.livetv_ui_zone_turkey
import nuvio.composeapp.generated.resources.livetv_ui_zone_uk
import nuvio.composeapp.generated.resources.livetv_ui_zone_us_central
import nuvio.composeapp.generated.resources.livetv_ui_zone_us_eastern
import nuvio.composeapp.generated.resources.livetv_ui_zone_us_mountain
import nuvio.composeapp.generated.resources.livetv_ui_zone_us_pacific
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.StringResource

private val REFRESH_HOURS = listOf(3, 6, 12, 24, 48, 72, 168)
private val FUTURE_DAYS = listOf(1, 2, 3, 5, 7, 10, 14)
private val PAST_HOURS = listOf(1, 3, 6, 12, 24, 48, 72, 168, 336)

@Composable
private fun pastHoursLabel(h: Int): String = when {
    h < 24 -> pluralStringResource(Res.plurals.livetv_ui_hours, h, h)
    else -> pluralStringResource(Res.plurals.livetv_ui_days, h / 24, h / 24)
}
/** Time shifts in minutes, half an hour apart, a day either way (the TV app's choices). */
private val EPG_OFFSETS = (-48..48).map { it * 30 }

/** Time zones offered for a playlist's catch-up (the common ones IPTV servers run on), as on the TV app. */
private val CATCHUP_ZONES: List<Pair<String, StringResource?>> = listOf(
    "UTC" to null,
    "Europe/London" to Res.string.livetv_ui_zone_uk,
    "Europe/Amsterdam" to Res.string.livetv_ui_zone_central_eu,
    "Europe/Athens" to Res.string.livetv_ui_zone_eastern_eu,
    "Europe/Istanbul" to Res.string.livetv_ui_zone_turkey,
    "Europe/Moscow" to Res.string.livetv_ui_zone_moscow,
    "Asia/Dubai" to Res.string.livetv_ui_zone_gulf,
    "Asia/Kolkata" to Res.string.livetv_ui_zone_india,
    "America/New_York" to Res.string.livetv_ui_zone_us_eastern,
    "America/Chicago" to Res.string.livetv_ui_zone_us_central,
    "America/Denver" to Res.string.livetv_ui_zone_us_mountain,
    "America/Los_Angeles" to Res.string.livetv_ui_zone_us_pacific,
)

/** A zone id in words: one of the list's names, else as found (e.g. "GMT+01:00"). */
@Composable
private fun zoneName(id: String): String = CATCHUP_ZONES.firstOrNull { it.first == id }?.let { z -> z.second?.let { stringResource(it) } ?: z.first } ?: id

/** The time on that zone's clock now, to match against the provider's. */
private fun zoneClock(id: String): String {
    val t = CivilTime.at(LiveTvClock.nowMs(), id)
    return "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
}

@Composable
private fun catchupZoneLabel(p: PlaylistSource): String = when {
    p.catchupTimezone.isNotBlank() -> zoneName(p.catchupTimezone)
    p.serverTimezone.isNotBlank() -> stringResource(Res.string.livetv_catchup_zone_found, zoneName(p.serverTimezone))
    else -> stringResource(Res.string.livetv_catchup_zone_automatic)
}
private val PADDING_MINUTES = listOf(0, 1, 2, 3, 5, 10, 15, 30)
private val KEEP_COUNTS = listOf(0, 1, 2, 3, 5, 10, 20)
private val REMINDER_LEADS = listOf(0, 1, 2, 5, 10, 15, 30)

/** "+1 h", "−1 h 30 min" or [none] for 0. */
internal fun offsetLabel(min: Int, none: String): String {
    if (min == 0) return none
    val sign = if (min > 0) "+" else "−"
    val a = kotlin.math.abs(min)
    return if (a % 60 == 0) "$sign${a / 60} h" else "$sign${a / 60} h ${a % 60} min"
}

@Composable
internal fun hoursLabel(h: Int): String = when {
    h >= 24 && h % 24 == 0 -> pluralStringResource(Res.plurals.livetv_ui_days, h / 24, h / 24)
    else -> pluralStringResource(Res.plurals.livetv_ui_hours, h, h)
}

private sealed interface LiveDialog {
    data object AddM3u : LiveDialog
    data object Dns : LiveDialog
    data object AddXtream : LiveDialog
    data object AddStalker : LiveDialog
    data object AddEpg : LiveDialog
    data class PlaylistActions(val source: PlaylistSource) : LiveDialog
    data class EditPlaylist(val source: PlaylistSource) : LiveDialog
    data class DeletePlaylist(val source: PlaylistSource) : LiveDialog
    data class EpgActions(val source: EpgSource) : LiveDialog
    data class EditEpg(val source: EpgSource) : LiveDialog
    data class DeleteEpg(val source: EpgSource) : LiveDialog
    data object PlaylistRefresh : LiveDialog
    data object EpgRefresh : LiveDialog
    data object FutureDays : LiveDialog
    data object PastHours : LiveDialog
    data object StreamFormat : LiveDialog
    data object BackupOrder : LiveDialog
    data class CatchupZone(val source: PlaylistSource) : LiveDialog
    data class PlaylistGuideShift(val source: PlaylistSource) : LiveDialog
    data class PlaylistCatchupShift(val source: PlaylistSource) : LiveDialog
    data class PlaylistGuides(val source: PlaylistSource) : LiveDialog
    data class PlaylistProfiles(val source: PlaylistSource) : LiveDialog
    data class EpgSourceShift(val source: EpgSource) : LiveDialog
    data object VodDefaultSort : LiveDialog
    data object VodTabPosition : LiveDialog
    data object RecordPre : LiveDialog
    data object RecordPost : LiveDialog
    data object RecordKeep : LiveDialog
    data object RecordSpaceRule : LiveDialog
    data object ConfirmRestore : LiveDialog
    /** A backup file was picked and read: confirm before it replaces this device's setup. */
    data class ConfirmImportFile(val text: String, val info: BackupFileInfo) : LiveDialog
    data object ConfirmSignOut : LiveDialog
    data object StartUpReport : LiveDialog
    data object ParentalMenu : LiveDialog
    data object ParentalSetPin : LiveDialog
    /** Asks the current PIN, then [then]. */
    data class ParentalAskPin(val then: LiveDialog?) : LiveDialog
    data object ParentalRemovePin : LiveDialog
    /** The PIN before playlist logins are shown or handed out; [then] runs once it's right. */
    data class LoginPin(val then: () -> Unit) : LiveDialog
    data object NetworkFolder : LiveDialog
    data object RecordStorage : LiveDialog
    data object ParentalGroups : LiveDialog
    data object Reminders : LiveDialog
    data object ReminderLead : LiveDialog
    data object ScoreDelay : LiveDialog
}

/** The Live TV page in Settings: playlists, guides, watching options, Google Drive backup. */
internal fun LazyListScope.liveTvSettingsContent(isTablet: Boolean, onlyRecordings: Boolean = false) {
    item(key = "livetv_settings") {
        LiveTvSettingsBody(isTablet, onlyRecordings)
    }
}

/** Recordings settings on their own, opened from the gear on the Recordings page. */
@Composable
internal fun LiveTvRecordingSettingsScreen(onBack: () -> Unit) {
    com.nuvio.app.core.ui.NuvioScreen {
        stickyHeader {
            com.nuvio.app.core.ui.NuvioScreenHeader(
                title = stringResource(Res.string.livetv_section_recordings),
                onBack = onBack,
            )
        }
        liveTvSettingsContent(isTablet = false, onlyRecordings = true)
    }
}

/** [onlyRecordings]: just the Recordings section (the page the Recordings gear opens). */
@Composable
private fun LiveTvSettingsBody(isTablet: Boolean, onlyRecordings: Boolean = false) {
    val tokens = MaterialTheme.nuvio
    val playlists by LiveTvPreferences.playlists.collectAsStateWithLifecycle()
    val epgs by LiveTvPreferences.epgSources.collectAsStateWithLifecycle()
    val settings by LiveTvPreferences.settings.collectAsStateWithLifecycle()
    val accounts by LiveTvPreferences.playlistAccounts.collectAsStateWithLifecycle()
    val status by remember { LiveTvRepository.ensureLoaded(); LiveTvRepository.status }.collectAsStateWithLifecycle()
    val drive by remember { LiveTvDriveSync.start(); LiveTvDriveSync.state }.collectAsStateWithLifecycle()
    val vodStatus by remember { OnDemandRepository.start(); OnDemandRepository.status }.collectAsStateWithLifecycle()
    val sports by SportsRepository.settings.collectAsStateWithLifecycle()
    val networkFolderJson by LiveTvPreferences.networkFolderJson.collectAsStateWithLifecycle(LiveTvPreferences.currentNetworkFolderJson())
    val userState by LiveTvPreferences.userState.collectAsStateWithLifecycle()
    val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
    val profiles = profileState.profiles
    val reminderLead by LiveTvPreferences.reminderLeadMinutes.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<LiveDialog?>(null) }
    /** Runs [go] now, or after the PIN when playlist logins are locked. */
    fun withLogins(go: () -> Unit) {
        if (ParentalControls.loginsLocked(settings)) dialog = LiveDialog.LoginPin(go) else go()
    }
    var dnsChoice by remember { mutableStateOf(LiveTvDns.current()) }
    val scope = rememberCoroutineScope()
    var exactAlarms by remember { mutableStateOf(RecordingPlatform.canScheduleExact()) }
    var notificationsOn by remember { mutableStateOf(RecordingPlatform.notificationsAllowed()) }
    var showGameAlerts by remember { mutableStateOf(false) }
    fun update(transform: (com.nuvio.app.features.livetv.model.LiveTvSettings) -> com.nuvio.app.features.livetv.model.LiveTvSettings) =
        LiveTvPreferences.updateSettings(transform)

    // Backup files: what the last import or export said, and whether one is under way.
    var fileMessage by remember { mutableStateOf<String?>(null) }
    var fileBusy by remember { mutableStateOf(false) }
    val pickImportFile = rememberLiveTvBackupImporter { result ->
        result.fold(
            onSuccess = { text ->
                if (text != null) parseBackupFile(text).fold(
                    onSuccess = { info -> fileMessage = null; dialog = LiveDialog.ConfirmImportFile(text, info) },
                    onFailure = { e -> scope.launch { fileMessage = backupFileErrorText(e, reading = true) } },
                )
            },
            onFailure = { e -> scope.launch { fileMessage = backupFileErrorText(e, reading = true) } },
        )
    }
    val pickExportFile = rememberLiveTvBackupExporter(text = { exportBackupFileText() }) { result ->
        scope.launch {
            fileMessage = result.fold(
                onSuccess = { written -> if (written) getString(Res.string.livetv_export_done) else fileMessage },
                onFailure = { e -> backupFileErrorText(e, reading = false) },
            )
        }
    }
    fun importFile(text: String) {
        fileBusy = true
        fileMessage = null
        scope.launch {
            val result = withContext(Dispatchers.Default) { importBackupFile(text) }
            fileMessage = result.fold(
                onSuccess = { summary ->
                    LiveTvDriveSync.backUpAfterImport()
                    getString(Res.string.livetv_import_done, summary.playlistCount, summary.epgCount)
                },
                onFailure = { e -> backupFileErrorText(e, reading = true) },
            )
            fileBusy = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.listGap)) {
        if (!onlyRecordings) {
        SettingsSection(title = stringResource(Res.string.livetv_section_playlists), isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                playlists.forEachIndexed { i, pl ->
                    if (i > 0) SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = pl.name,
                        description = playlistStatus(pl, accounts[pl.id], hiddenHere = pl.id in userState.hiddenPlaylists),
                        icon = if (pl.isXtream || pl.isStalker) Icons.Filled.Login else Icons.Filled.ListAlt,
                        isTablet = isTablet,
                        onClick = { dialog = LiveDialog.PlaylistActions(pl) },
                    )
                }
                if (playlists.isNotEmpty()) SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_add_m3u),
                    description = stringResource(Res.string.livetv_add_m3u_description),
                    icon = Icons.Filled.Add,
                    isTablet = isTablet,
                    onClick = { dialog = LiveDialog.AddM3u },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_add_xtream),
                    description = stringResource(Res.string.livetv_add_xtream_description),
                    icon = Icons.Filled.Add,
                    isTablet = isTablet,
                    onClick = { dialog = LiveDialog.AddXtream },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_add_stalker),
                    description = stringResource(Res.string.livetv_add_stalker_description),
                    icon = Icons.Filled.Add,
                    isTablet = isTablet,
                    onClick = { dialog = LiveDialog.AddStalker },
                )
            }
        }

        SettingsSection(title = stringResource(Res.string.livetv_section_guides), isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                epgs.forEachIndexed { i, src ->
                    if (i > 0) SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = src.name,
                        description = epgStatus(src),
                        icon = Icons.Filled.Schedule,
                        isTablet = isTablet,
                        onClick = { dialog = LiveDialog.EpgActions(src) },
                    )
                }
                if (epgs.isNotEmpty()) SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_add_epg),
                    description = stringResource(Res.string.livetv_add_epg_description),
                    icon = Icons.Filled.Add,
                    isTablet = isTablet,
                    onClick = { dialog = LiveDialog.AddEpg },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_days_ahead),
                    description = stringResource(Res.string.livetv_days_ahead_description),
                    isTablet = isTablet,
                    trailingContent = { ValueLabel(pluralStringResource(Res.plurals.livetv_ui_days, settings.epgFutureDays, settings.epgFutureDays)) },
                    onClick = { dialog = LiveDialog.FutureDays },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_hours_back),
                    description = stringResource(Res.string.livetv_hours_back_description),
                    isTablet = isTablet,
                    trailingContent = { ValueLabel(pastHoursLabel(settings.epgPastHours)) },
                    onClick = { dialog = LiveDialog.PastHours },
                )
            }
        }

        SettingsSection(title = stringResource(Res.string.livetv_section_general), isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_update_all),
                    description = if (status.loading) status.message else stringResource(Res.string.livetv_update_all_description),
                    isTablet = isTablet,
                    enabled = !status.loading,
                    trailingContent = { if (status.loading) NuvioLoadingIndicator(size = NuvioTokens.Space.s20) },
                    onClick = { LiveTvRepository.refreshAll(force = true) },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_playlist_refresh),
                    description = stringResource(Res.string.livetv_playlist_refresh_description),
                    isTablet = isTablet,
                    trailingContent = { ValueLabel(hoursLabel(settings.playlistRefreshHours)) },
                    onClick = { dialog = LiveDialog.PlaylistRefresh },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_epg_refresh),
                    description = stringResource(Res.string.livetv_epg_refresh_description),
                    isTablet = isTablet,
                    trailingContent = { ValueLabel(hoursLabel(settings.epgRefreshHours)) },
                    onClick = { dialog = LiveDialog.EpgRefresh },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_show_tab),
                    description = stringResource(Res.string.livetv_show_tab_description),
                    checked = settings.showInSidebar,
                    isTablet = isTablet,
                    onCheckedChange = { on -> update { it.copy(showInSidebar = on) } },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_show_update_banners),
                    description = stringResource(Res.string.livetv_show_update_banners_description),
                    checked = settings.showImportProgress,
                    isTablet = isTablet,
                    onCheckedChange = { on -> update { it.copy(showImportProgress = on) } },
                )
                if (LiveTvDns.supported) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.livetv_dns),
                        description = stringResource(Res.string.livetv_dns_description),
                        isTablet = isTablet,
                        trailingContent = { ValueLabel(dnsLabel(dnsChoice)) },
                        onClick = { dialog = LiveDialog.Dns },
                    )
                }
            }
        }

        SettingsSection(title = stringResource(Res.string.livetv_section_channel_display), isTablet = isTablet) {
            // Turning one off is ignored when it's the last one on, so a channel always shows something.
            val onCount = listOf(settings.phoneChannelLogos, settings.phoneChannelNames, settings.phoneChannelNumbers).count { it }
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_phone_logos),
                    description = stringResource(Res.string.livetv_phone_logos_description),
                    checked = settings.phoneChannelLogos,
                    enabled = !(settings.phoneChannelLogos && onCount == 1),
                    isTablet = isTablet,
                    onCheckedChange = { on -> update { if (!on && !it.phoneChannelNames && !it.phoneChannelNumbers) it else it.copy(phoneChannelLogos = on) } },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_phone_names),
                    description = stringResource(Res.string.livetv_phone_names_description),
                    checked = settings.phoneChannelNames,
                    enabled = !(settings.phoneChannelNames && onCount == 1),
                    isTablet = isTablet,
                    onCheckedChange = { on -> update { if (!on && !it.phoneChannelLogos && !it.phoneChannelNumbers) it else it.copy(phoneChannelNames = on) } },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_phone_numbers),
                    description = stringResource(Res.string.livetv_phone_numbers_description),
                    checked = settings.phoneChannelNumbers,
                    enabled = !(settings.phoneChannelNumbers && onCount == 1),
                    isTablet = isTablet,
                    onCheckedChange = { on -> update { if (!on && !it.phoneChannelLogos && !it.phoneChannelNames) it else it.copy(phoneChannelNumbers = on) } },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_now_line),
                    description = stringResource(Res.string.livetv_now_line_description),
                    checked = settings.showNowLine,
                    isTablet = isTablet,
                    onCheckedChange = { on -> update { it.copy(showNowLine = on) } },
                )
                SettingsGroupDivider(isTablet = isTablet)
                // Channel Name Cleanup's words (set on the TV app, synced) taken off group names too.
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_clean_group_names),
                    description = stringResource(Res.string.livetv_clean_group_names_description),
                    checked = settings.cleanGroupNames,
                    isTablet = isTablet,
                    onCheckedChange = { on -> update { it.copy(cleanGroupNames = on) } },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_clean_vod_group_names),
                    description = stringResource(Res.string.livetv_clean_vod_group_names_description),
                    checked = settings.cleanVodGroupNames,
                    isTablet = isTablet,
                    onCheckedChange = { on -> update { it.copy(cleanVodGroupNames = on) } },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_hide_stray_entries),
                    description = stringResource(Res.string.livetv_hide_stray_entries_description),
                    checked = settings.hideStrayEntries,
                    isTablet = isTablet,
                    onCheckedChange = { on ->
                        update { it.copy(hideStrayEntries = on) }
                        LiveTvRepository.rebuildChannelList()
                    },
                )
            }
        }

        SettingsSection(title = stringResource(Res.string.livetv_section_watching), isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                if (!isIos) {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.livetv_adaptive_buffer),
                        description = stringResource(Res.string.livetv_adaptive_buffer_description),
                        checked = settings.adaptiveLiveBufferEnabled,
                        isTablet = isTablet,
                        onCheckedChange = { on -> update { it.copy(adaptiveLiveBufferEnabled = on) } },
                    )
                    SettingsGroupDivider(isTablet = isTablet)
                }
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_stream_format),
                    description = stringResource(Res.string.livetv_stream_format_description),
                    isTablet = isTablet,
                    trailingContent = { ValueLabel(streamFormatLabel(playlists.firstOrNull { it.isXtream }?.streamFormat ?: "auto")) },
                    onClick = { dialog = LiveDialog.StreamFormat },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_switch_backup),
                    description = stringResource(Res.string.livetv_switch_backup_description),
                    checked = settings.autoBackupChannel,
                    isTablet = isTablet,
                    onCheckedChange = { on -> update { it.copy(autoBackupChannel = on) } },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_backup_order_title),
                    description = stringResource(Res.string.livetv_backup_order_row_subtitle),
                    isTablet = isTablet,
                    trailingContent = { ValueLabel(backupGroupByLabel(com.nuvio.app.features.livetv.model.BackupOrder.groupBy(settings))) },
                    onClick = { dialog = LiveDialog.BackupOrder },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_remember_audio),
                    description = stringResource(Res.string.livetv_remember_audio_description),
                    checked = true,
                    enabled = false,
                    isTablet = isTablet,
                    onCheckedChange = {},
                )
            }
        }

        SettingsSection(title = stringResource(Res.string.livetv_section_catchup), isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_catchup_prefer_hls),
                    description = stringResource(Res.string.livetv_catchup_prefer_hls_description),
                    checked = settings.catchupPreferHls,
                    isTablet = isTablet,
                    onCheckedChange = { on -> update { it.copy(catchupPreferHls = on) } },
                )
                SettingsGroupDivider(isTablet = isTablet)
                // Catch-up and guide time shifts are set on each playlist and guide (their menus).
                Text(
                    stringResource(Res.string.livetv_time_shifts_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textSecondary,
                    modifier = Modifier.padding(NuvioTokens.Space.s16),
                )
            }
        }

        SettingsSection(title = stringResource(Res.string.livetv_section_on_demand), isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                playlists.forEachIndexed { i, pl ->
                    if (i > 0) SettingsGroupDivider(isTablet = isTablet)
                    SettingsSwitchRow(
                        title = stringResource(Res.string.livetv_vod_import) + " · " + pl.name,
                        description = stringResource(Res.string.livetv_vod_import_description),
                        checked = pl.importVod,
                        enabled = pl.enabled,
                        isTablet = isTablet,
                        onCheckedChange = { on -> LiveTvPreferences.updatePlaylists { l -> l.map { if (it.id == pl.id) it.copy(importVod = on, vodSwitchSet = true) else it } } },
                    )
                }
                if (playlists.isNotEmpty()) SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_vod_update_now),
                    description = vodStatus.message ?: stringResource(Res.string.livetv_vod_update_now_description),
                    isTablet = isTablet,
                    enabled = !vodStatus.loading && playlists.any { it.enabled && it.importVod },
                    trailingContent = { if (vodStatus.loading) NuvioLoadingIndicator(size = NuvioTokens.Space.s20) },
                    onClick = { OnDemandRepository.refreshNow() },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_vod_default_sort),
                    description = stringResource(Res.string.livetv_vod_default_sort_description),
                    isTablet = isTablet,
                    trailingContent = { ValueLabel((VodSort.parse(settings.vodDefaultSort) ?: VodSort.DEFAULT).label) },
                    onClick = { dialog = LiveDialog.VodDefaultSort },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_vod_merge_duplicates),
                    description = stringResource(Res.string.livetv_vod_merge_duplicates_description),
                    checked = settings.vodMergeDuplicates,
                    isTablet = isTablet,
                    onCheckedChange = { on -> update { it.copy(vodMergeDuplicates = on) } },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_vod_hide_non_english),
                    description = stringResource(Res.string.livetv_vod_hide_non_english_description),
                    checked = settings.vodHideNonEnglish,
                    isTablet = isTablet,
                    onCheckedChange = { on -> update { it.copy(vodHideNonEnglish = on) } },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_vod_in_streams),
                    description = stringResource(Res.string.livetv_vod_in_streams_description),
                    checked = settings.onDemandInStreams,
                    isTablet = isTablet,
                    onCheckedChange = { on -> update { it.copy(onDemandInStreams = on) } },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_vod_tab_position),
                    description = stringResource(Res.string.livetv_vod_tab_position_description),
                    isTablet = isTablet,
                    trailingContent = { ValueLabel(vodTabPositionLabel(settings.onDemandStreamsPosition)) },
                    onClick = { dialog = LiveDialog.VodTabPosition },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_vod_autoplay),
                    description = stringResource(Res.string.livetv_vod_autoplay_description),
                    checked = settings.onDemandAutoPlay,
                    isTablet = isTablet,
                    onCheckedChange = { on -> update { it.copy(onDemandAutoPlay = on) } },
                )
            }
        }

        SettingsSection(title = stringResource(Res.string.livetv_sports), isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_sports_show),
                    description = stringResource(Res.string.livetv_sports_show_description),
                    checked = sports.showInSidebar,
                    isTablet = isTablet,
                    onCheckedChange = { on -> SportsRepository.updateSettings { it.copy(showInSidebar = on) } },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_sports_best_channel_first),
                    description = stringResource(Res.string.livetv_sports_best_channel_first_description),
                    checked = sports.bestChannelFirst,
                    isTablet = isTablet,
                    onCheckedChange = { on -> SportsRepository.updateSettings { it.copy(bestChannelFirst = on) } },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_sports_hide_scores),
                    description = stringResource(Res.string.livetv_sports_hide_scores_description),
                    checked = sports.hideScores,
                    isTablet = isTablet,
                    onCheckedChange = { on -> SportsRepository.updateSettings { it.copy(hideScores = on) } },
                )
                if (!sports.hideScores) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.livetv_sports_score_delay),
                        description = stringResource(Res.string.livetv_sports_score_delay_description),
                        isTablet = isTablet,
                        trailingContent = { ValueLabel(scoreDelayLabel(sports.scoreDelaySeconds)) },
                        onClick = { dialog = LiveDialog.ScoreDelay },
                    )
                }
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_sports_score_chip),
                    description = stringResource(Res.string.livetv_sports_score_chip_description),
                    checked = sports.scoreOnInfoBar,
                    isTablet = isTablet,
                    onCheckedChange = { on -> SportsRepository.updateSettings { it.copy(scoreOnInfoBar = on) } },
                )
                SettingsGroupDivider(isTablet = isTablet)
                GameAlertsRow(isTablet = isTablet, onClick = { showGameAlerts = true })
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_sports_show_highlights),
                    description = stringResource(Res.string.livetv_sports_show_highlights_description),
                    checked = sports.showHighlights,
                    isTablet = isTablet,
                    onCheckedChange = { on -> SportsRepository.updateSettings { it.copy(showHighlights = on) } },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_sports_show_condensed),
                    description = stringResource(Res.string.livetv_sports_show_condensed_description),
                    checked = sports.showCondensed,
                    isTablet = isTablet,
                    onCheckedChange = { on -> SportsRepository.updateSettings { it.copy(showCondensed = on) } },
                )
            }
        }
        if (showGameAlerts) GameAlertsSheet(onDismiss = { showGameAlerts = false })

        SettingsSection(title = stringResource(Res.string.livetv_parental), isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                val pinSet = ParentalControls.enabled(settings)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_parental_pin),
                    description = stringResource(Res.string.livetv_parental_pin_description),
                    icon = Icons.Filled.Lock,
                    isTablet = isTablet,
                    trailingContent = { ValueLabel(stringResource(if (pinSet) Res.string.livetv_parental_pin_set else Res.string.livetv_parental_pin_not_set)) },
                    onClick = { dialog = if (pinSet) LiveDialog.ParentalMenu else LiveDialog.ParentalSetPin },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_parental_lock_adult),
                    description = stringResource(Res.string.livetv_parental_lock_adult_description),
                    checked = settings.lockAdultContent,
                    enabled = pinSet,
                    isTablet = isTablet,
                    onCheckedChange = { on -> update { it.copy(lockAdultContent = on) }; ParentalControls.relock() },
                )
                SettingsGroupDivider(isTablet = isTablet)
                // Turning it on is free; turning it off needs the PIN (unless already entered).
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_parental_lock_logins),
                    description = stringResource(Res.string.livetv_parental_lock_logins_description),
                    checked = settings.lockLogins,
                    enabled = pinSet,
                    isTablet = isTablet,
                    onCheckedChange = { on -> if (on) update { it.copy(lockLogins = true) } else withLogins { update { it.copy(lockLogins = false) } } },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_parental_locked_groups),
                    description = stringResource(Res.string.livetv_parental_locked_groups_description),
                    isTablet = isTablet,
                    enabled = pinSet,
                    trailingContent = { ValueLabel(stringResource(Res.string.livetv_parental_locked_count, userState.lockedGroups.size)) },
                    onClick = { dialog = LiveDialog.ParentalAskPin(LiveDialog.ParentalGroups) },
                )
            }
        }

        SettingsSection(title = stringResource(Res.string.livetv_ui_calendar), isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_ui_cal_upcoming_row),
                    description = stringResource(Res.string.livetv_ui_cal_upcoming_row_desc),
                    checked = settings.calendarUpcomingRow, isTablet = isTablet,
                    onCheckedChange = { on -> update { it.copy(calendarUpcomingRow = on) } },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_ui_cal_new_cards),
                    description = stringResource(Res.string.livetv_ui_cal_new_cards_desc),
                    checked = settings.calendarNewEpisodeCards, isTablet = isTablet,
                    onCheckedChange = { on -> update { it.copy(calendarNewEpisodeCards = on) } },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_ui_cal_hide_spoilers),
                    description = stringResource(Res.string.livetv_ui_cal_hide_spoilers_desc),
                    checked = settings.calendarHideSpoilers, isTablet = isTablet,
                    onCheckedChange = { on -> update { it.copy(calendarHideSpoilers = on) } },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.livetv_ui_cal_games),
                    description = stringResource(Res.string.livetv_ui_cal_games_desc),
                    checked = settings.calendarSports, isTablet = isTablet,
                    onCheckedChange = { on -> update { it.copy(calendarSports = on) } },
                )
            }
        }

        run {
            val picks by CalendarRepository.channelPicks.collectAsStateWithLifecycle()
            val channelCount by LiveTvRepository.channels.collectAsStateWithLifecycle()
            val savedList = remember(picks, channelCount) { CalendarRepository.savedChannels() }
            SettingsSection(title = stringResource(Res.string.livetv_ui_cal_saved_channels), isTablet = isTablet) {
                SettingsGroup(isTablet = isTablet) {
                    if (savedList.isEmpty()) {
                        SettingsNavigationRow(
                            title = stringResource(Res.string.livetv_ui_cal_nothing_saved),
                            description = stringResource(Res.string.livetv_ui_cal_nothing_saved_desc),
                            isTablet = isTablet, onClick = {},
                        )
                    }
                    savedList.forEachIndexed { i, sc ->
                        if (i > 0) SettingsGroupDivider(isTablet = isTablet)
                        SettingsNavigationRow(
                            title = sc.label,
                            description = stringResource(if (sc.forShow) Res.string.livetv_ui_cal_saved_show else Res.string.livetv_ui_cal_saved_network, sc.channel),
                            isTablet = isTablet,
                            onClick = { CalendarRepository.forgetSaved(sc.pickKey) },
                        )
                    }
                }
            }
        }

        SettingsSection(title = stringResource(Res.string.livetv_section_reminders), isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_reminders_list),
                    description = stringResource(Res.string.livetv_reminders_list_description),
                    icon = Icons.Filled.Notifications,
                    isTablet = isTablet,
                    trailingContent = { ValueLabel(userState.reminders.size.toString()) },
                    onClick = { dialog = LiveDialog.Reminders },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_reminder_lead),
                    description = stringResource(Res.string.livetv_reminder_lead_description),
                    isTablet = isTablet,
                    trailingContent = { ValueLabel(leadLabel(reminderLead)) },
                    onClick = { dialog = LiveDialog.ReminderLead },
                )
            }
        }
        }

        SettingsSection(title = stringResource(Res.string.livetv_section_recordings), isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_rec_padding_pre),
                    description = stringResource(Res.string.livetv_rec_padding_pre_description),
                    isTablet = isTablet,
                    trailingContent = { ValueLabel(minutesLabel(settings.recordPreMinutes)) },
                    onClick = { dialog = LiveDialog.RecordPre },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_rec_padding_post),
                    description = stringResource(Res.string.livetv_rec_padding_post_description),
                    isTablet = isTablet,
                    trailingContent = { ValueLabel(minutesLabel(settings.recordPostMinutes)) },
                    onClick = { dialog = LiveDialog.RecordPost },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_rec_series_keep),
                    description = stringResource(Res.string.livetv_rec_series_keep_description),
                    isTablet = isTablet,
                    trailingContent = { ValueLabel(if (settings.recordSeriesKeep > 0) stringResource(Res.string.livetv_rec_keep_last, settings.recordSeriesKeep) else stringResource(Res.string.livetv_rec_keep_all)) },
                    onClick = { dialog = LiveDialog.RecordKeep },
                )
                SettingsGroupDivider(isTablet = isTablet)
                // Where new recordings go: this phone, or the network folder (recorded here, then copied there).
                val toNetwork = NetworkLibraryPlatform.available && settings.recordingStorage == NetworkUploads.STORAGE_ID &&
                    NetworkFolderConfig.fromJson(networkFolderJson) != null
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_rec_storage),
                    description = stringResource(if (toNetwork) Res.string.livetv_rec_storage_network_description else Res.string.livetv_rec_storage_description),
                    isTablet = isTablet,
                    trailingContent = {
                        ValueLabel(
                            if (toNetwork) stringResource(Res.string.livetv_rec_network_folder)
                            else stringResource(Res.string.livetv_rec_this_device) + " · " + RecordingRules.sizeLabel(LiveTvRecorder.freeBytes())
                        )
                    },
                    onClick = { if (NetworkLibraryPlatform.available) dialog = LiveDialog.RecordStorage },
                )
                // The network folder (its address kept on this phone only): set up or changed here.
                if (NetworkLibraryPlatform.available) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.livetv_rec_network_folder),
                        description = stringResource(Res.string.livetv_rec_network_folder_description),
                        isTablet = isTablet,
                        trailingContent = {
                            ValueLabel(NetworkFolderConfig.fromJson(networkFolderJson)?.label ?: stringResource(Res.string.livetv_rec_network_not_set_up))
                        },
                        onClick = { withLogins { dialog = LiveDialog.NetworkFolder } },
                    )
                }
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_rec_space_rule),
                    description = stringResource(Res.string.livetv_rec_space_rule_description),
                    isTablet = isTablet,
                    trailingContent = { ValueLabel(stringResource(if (settings.recordingSpaceRule == "DELETE_OLDEST") Res.string.livetv_rec_space_rule_delete else Res.string.livetv_rec_space_rule_stop)) },
                    onClick = { dialog = LiveDialog.RecordSpaceRule },
                )
                if (!exactAlarms) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.livetv_rec_exact_alarms),
                        description = stringResource(Res.string.livetv_rec_exact_alarms_description),
                        isTablet = isTablet,
                        onClick = { RecordingPlatform.requestExactAlarms(); exactAlarms = RecordingPlatform.canScheduleExact() },
                    )
                }
                if (!notificationsOn) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.livetv_rec_notifications),
                        description = stringResource(Res.string.livetv_rec_notifications_description),
                        isTablet = isTablet,
                        onClick = { scope.launch { notificationsOn = EpisodeReleaseNotificationPlatform.requestAuthorization() } },
                    )
                }
            }
        }

        if (!onlyRecordings) {
        SettingsSection(title = stringResource(Res.string.livetv_section_backup), isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                Text(
                    text = stringResource(Res.string.livetv_backup_file_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textMuted,
                    modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s12),
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_import_file),
                    description = stringResource(Res.string.livetv_import_file_description),
                    icon = Icons.Filled.FileDownload,
                    isTablet = isTablet,
                    enabled = !fileBusy && !drive.busy,
                    trailingContent = { if (fileBusy) NuvioLoadingIndicator(size = NuvioTokens.Space.s20) },
                    onClick = { pickImportFile() },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_export_file),
                    description = stringResource(Res.string.livetv_export_file_description),
                    icon = Icons.Filled.FileUpload,
                    isTablet = isTablet,
                    enabled = !fileBusy,
                    onClick = { withLogins { pickExportFile() } },
                )
                fileMessage?.let {
                    SettingsGroupDivider(isTablet = isTablet)
                    Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textSecondary, modifier = Modifier.padding(NuvioTokens.Space.s16))
                }
            }
            NuvioSectionLabel(
                text = stringResource(Res.string.livetv_drive_subsection),
                modifier = Modifier.padding(horizontal = NuvioTokens.Radius.card).padding(top = NuvioTokens.Space.s16, bottom = NuvioTokens.Space.s10),
            )
            SettingsGroup(isTablet = isTablet) {
                when {
                    !drive.available -> Text(
                        text = stringResource(Res.string.livetv_drive_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.colors.textMuted,
                        modifier = Modifier.padding(NuvioTokens.Space.s16),
                    )
                    !drive.connected -> {
                        Text(
                            text = stringResource(Res.string.livetv_drive_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.colors.textMuted,
                            modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s12),
                        )
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsNavigationRow(
                            title = stringResource(Res.string.livetv_drive_sign_in),
                            description = stringResource(Res.string.livetv_drive_sign_in_description),
                            icon = Icons.Filled.Backup,
                            isTablet = isTablet,
                            enabled = !drive.busy,
                            onClick = { LiveTvDriveSync.beginSignIn() },
                        )
                    }
                    else -> {
                        SettingsNavigationRow(
                            title = stringResource(Res.string.livetv_drive_account),
                            description = (drive.email ?: stringResource(Res.string.livetv_ui_connected)) + (drive.lastSyncMs.takeIf { it > 0 }?.let { " · " + formatLocalDateTime(it) } ?: ""),
                            icon = Icons.Filled.Backup,
                            isTablet = isTablet,
                            onClick = {},
                        )
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsSwitchRow(
                            title = stringResource(Res.string.livetv_drive_auto_sync),
                            description = stringResource(Res.string.livetv_drive_auto_sync_description),
                            checked = drive.autoSync,
                            isTablet = isTablet,
                            onCheckedChange = LiveTvDriveSync::setAutoSync,
                        )
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsNavigationRow(
                            title = stringResource(Res.string.livetv_backup_now),
                            description = stringResource(Res.string.livetv_backup_now_description),
                            isTablet = isTablet,
                            enabled = !drive.busy,
                            trailingContent = { if (drive.busy) NuvioLoadingIndicator(size = NuvioTokens.Space.s20) },
                            onClick = { LiveTvDriveSync.backUpNow() },
                        )
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsNavigationRow(
                            title = stringResource(Res.string.livetv_restore),
                            description = stringResource(Res.string.livetv_restore_description),
                            isTablet = isTablet,
                            enabled = !drive.busy,
                            onClick = { dialog = LiveDialog.ConfirmRestore },
                        )
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsNavigationRow(
                            title = stringResource(Res.string.livetv_drive_sign_out),
                            description = stringResource(Res.string.livetv_drive_sign_out_description),
                            isTablet = isTablet,
                            onClick = { dialog = LiveDialog.ConfirmSignOut },
                        )
                    }
                }
                drive.message?.let {
                    SettingsGroupDivider(isTablet = isTablet)
                    Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textSecondary, modifier = Modifier.padding(NuvioTokens.Space.s16))
                }
            }
            LiveTvCloudSyncSection(isTablet = isTablet)
        }

        SettingsSection(title = stringResource(Res.string.livetv_section_help), isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.livetv_report_title),
                    description = stringResource(Res.string.livetv_report_description),
                    icon = Icons.Filled.Info,
                    isTablet = isTablet,
                    onClick = { dialog = LiveDialog.StartUpReport },
                )
            }
        }
        }
    }

    drive.signIn?.let { prompt -> DriveSignInDialog(prompt, onCancel = { LiveTvDriveSync.cancelSignIn() }) }

    val close = { dialog = null }
    when (val d = dialog) {
        null -> Unit
        LiveDialog.AddM3u -> M3uForm(null, onDismiss = close)
        LiveDialog.AddXtream -> XtreamForm(null, onDismiss = close)
        LiveDialog.AddStalker -> StalkerForm(null, onDismiss = close)
        LiveDialog.AddEpg -> EpgForm(null, onDismiss = close)
        LiveDialog.StartUpReport -> StartUpReportDialog(onDismiss = close)
        is LiveDialog.EditPlaylist -> when {
            d.source.isStalker -> StalkerForm(d.source, onDismiss = close)
            d.source.isXtream -> XtreamForm(d.source, onDismiss = close)
            else -> M3uForm(d.source, onDismiss = close)
        }
        is LiveDialog.EditEpg -> EpgForm(d.source, onDismiss = close)
        LiveDialog.NetworkFolder -> NetworkFolderForm(onDismiss = close)
        LiveDialog.RecordStorage -> {
            val folderSetUp = NetworkFolderConfig.fromJson(networkFolderJson) != null
            ChoiceDialog(
                title = stringResource(Res.string.livetv_rec_storage),
                options = listOf(
                    "" to stringResource(Res.string.livetv_rec_this_device),
                    NetworkUploads.STORAGE_ID to stringResource(Res.string.livetv_rec_network_folder) +
                        (if (folderSetUp) "" else " (" + stringResource(Res.string.livetv_rec_network_not_set_up).lowercase() + ")"),
                ),
                selected = if (settings.recordingStorage == NetworkUploads.STORAGE_ID && folderSetUp) NetworkUploads.STORAGE_ID else "",
                onDismiss = close,
                message = stringResource(Res.string.livetv_rec_storage_choice_message),
            ) { v ->
                update { it.copy(recordingStorage = v) }
                // Not set up yet: straight to setting it up (once this list has closed).
                if (v == NetworkUploads.STORAGE_ID && !folderSetUp) scope.launch { withLogins { dialog = LiveDialog.NetworkFolder } }
            }
        }
        is LiveDialog.PlaylistActions -> ActionsDialog(
            title = d.source.name,
            subtitle = playlistStatus(d.source, accounts[d.source.id], hiddenHere = d.source.id in userState.hiddenPlaylists),
            onDismiss = close,
            actions = listOfNotNull(
                stringResource(Res.string.action_edit) to { withLogins { dialog = LiveDialog.EditPlaylist(d.source) } },
                stringResource(Res.string.livetv_update_now) to {
                    LiveTvRepository.updatePlaylistNow(d.source.id)
                    // A login with TV channels off only has On Demand to update.
                    if ((d.source.isXtream || d.source.isStalker) && d.source.importVod && d.source.enabled && !d.source.liveEnabled) OnDemandRepository.refreshNow(d.source.id)
                    close()
                },
                // Portal archives use provider programme IDs; their date parsing uses the portal configuration's time zone.
                if (d.source.isStalker) null else
                    stringResource(Res.string.livetv_catchup_zone) + ": " + catchupZoneLabel(d.source) to { dialog = LiveDialog.CatchupZone(d.source) },
            ) + (if (d.source.liveEnabled) listOfNotNull(
                // Time shifts for this playlist: its built-in guide, and its catch-up.
                stringResource(Res.string.livetv_guide_shift) + ": " + offsetLabel(d.source.guideShiftMinutes, stringResource(Res.string.livetv_catchup_shift_none)) to {
                    dialog = LiveDialog.PlaylistGuideShift(d.source)
                },
                if (d.source.isStalker) null else
                    stringResource(Res.string.livetv_catchup_shift) + ": " + offsetLabel(d.source.catchupShiftMinutes, stringResource(Res.string.livetv_catchup_shift_none)) to {
                        dialog = LiveDialog.PlaylistCatchupShift(d.source)
                    },
                // Which guides this playlist's channels may use (only when there is a choice).
                guideChoices(d.source, playlists, epgs).let { choices ->
                    if (choices.isEmpty()) null
                    else {
                        val on = choices.count { it.first !in d.source.guidesOff }
                        stringResource(Res.string.livetv_guides) + ": " +
                            (if (on == choices.size) stringResource(Res.string.livetv_guides_all) else stringResource(Res.string.livetv_guides_some, on, choices.size)) to {
                                dialog = LiveDialog.PlaylistGuides(d.source)
                            }
                    }
                },
            ) else emptyList()) + listOfNotNull(
                // Which Nuvio profiles see this playlist (only with two or more profiles). With one
                // profile, a playlist still hidden on it (set when there were more, or from sync)
                // gets a way to show it again.
                if (profiles.size < 2 && d.source.id in userState.hiddenPlaylists) {
                    stringResource(Res.string.livetv_show_on_this_profile) to {
                        LiveTvPreferences.showPlaylistOnThisProfile(d.source.id); close()
                    }
                } else profiles.takeIf { it.size >= 2 }?.let { list ->
                    val shownOn = list.count { d.source.id !in LiveTvPreferences.hiddenPlaylistsOf(it.profileIndex) }
                    stringResource(
                        Res.string.livetv_show_on_profiles_value,
                        if (shownOn == list.size) stringResource(Res.string.livetv_show_on_profiles_all)
                        else stringResource(Res.string.livetv_show_on_profiles_count, shownOn, list.size),
                    ) to { dialog = LiveDialog.PlaylistProfiles(d.source) }
                },
            ) + listOf(
                stringResource(if (d.source.enabled) Res.string.livetv_disable else Res.string.livetv_enable) to {
                    LiveTvRepository.setPlaylistEnabled(d.source.id, !d.source.enabled); close()
                },
                stringResource(Res.string.action_delete) to { dialog = LiveDialog.DeletePlaylist(d.source) },
            ),
        )
        is LiveDialog.CatchupZone -> {
            val automatic = d.source.serverTimezone.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.livetv_catchup_zone_found, zoneName(it)) }
                ?: (stringResource(Res.string.livetv_catchup_zone_automatic) + " · " + stringResource(Res.string.livetv_catchup_zone_not_found))
            ChoiceDialog(
                title = stringResource(Res.string.livetv_catchup_zone),
                message = stringResource(Res.string.livetv_catchup_zone_description, d.source.name),
                options = listOf("" to automatic) + CATCHUP_ZONES.map { (id, _) -> id to "${zoneName(id)} · ${zoneClock(id)}" },
                selected = d.source.catchupTimezone,
                onDismiss = close,
            ) { v -> LiveTvRepository.setPlaylistCatchupZone(d.source.id, v) }
        }
        is LiveDialog.PlaylistGuides -> PlaylistGuidesDialog(d.source.id, onDismiss = close)
        is LiveDialog.PlaylistProfiles -> PlaylistProfilesDialog(d.source, profiles, onDismiss = close)
        is LiveDialog.PlaylistGuideShift -> {
            val none = stringResource(Res.string.livetv_catchup_shift_none)
            ChoiceDialog(
                title = stringResource(Res.string.livetv_guide_shift) + ": " + d.source.name,
                message = stringResource(Res.string.livetv_guide_shift_description),
                options = EPG_OFFSETS.map { it to offsetLabel(it, none) },
                selected = d.source.guideShiftMinutes,
                onDismiss = close,
            ) { v -> LiveTvRepository.setPlaylistGuideShift(d.source.id, v) }
        }
        is LiveDialog.PlaylistCatchupShift -> {
            val none = stringResource(Res.string.livetv_catchup_shift_none)
            ChoiceDialog(
                title = stringResource(Res.string.livetv_catchup_shift) + ": " + d.source.name,
                message = stringResource(Res.string.livetv_catchup_shift_description),
                options = EPG_OFFSETS.map { it to offsetLabel(it, none) },
                selected = d.source.catchupShiftMinutes,
                onDismiss = close,
            ) { v -> LiveTvRepository.setPlaylistCatchupShift(d.source.id, v) }
        }
        is LiveDialog.EpgSourceShift -> {
            val none = stringResource(Res.string.livetv_catchup_shift_none)
            ChoiceDialog(
                title = stringResource(Res.string.livetv_epg_shift) + ": " + d.source.name,
                message = stringResource(Res.string.livetv_epg_shift_description),
                options = EPG_OFFSETS.map { it to offsetLabel(it, none) },
                selected = d.source.shiftMinutes,
                onDismiss = close,
            ) { v -> LiveTvRepository.setEpgShift(d.source.id, v) }
        }
        is LiveDialog.EpgActions -> ActionsDialog(
            title = d.source.name,
            subtitle = epgStatus(d.source),
            onDismiss = close,
            actions = listOf(
                stringResource(Res.string.action_edit) to { withLogins { dialog = LiveDialog.EditEpg(d.source) } },
                stringResource(Res.string.livetv_update_now) to { LiveTvRepository.updateEpg(d.source); close() },
                stringResource(Res.string.livetv_epg_shift) + ": " + offsetLabel(d.source.shiftMinutes, stringResource(Res.string.livetv_catchup_shift_none)) to {
                    dialog = LiveDialog.EpgSourceShift(d.source)
                },
                stringResource(if (d.source.enabled) Res.string.livetv_disable else Res.string.livetv_enable) to {
                    LiveTvRepository.setEpgEnabled(d.source.id, !d.source.enabled); close()
                },
                stringResource(Res.string.action_delete) to { dialog = LiveDialog.DeleteEpg(d.source) },
            ),
        )
        is LiveDialog.DeletePlaylist -> ConfirmDialog(
            title = stringResource(Res.string.action_delete) + " \"${d.source.name}\"?",
            message = stringResource(Res.string.livetv_delete_source_message),
            confirm = stringResource(Res.string.action_delete),
            destructive = true,
            onDismiss = close,
        ) { LiveTvRepository.removePlaylist(d.source.id) }
        is LiveDialog.DeleteEpg -> ConfirmDialog(
            title = stringResource(Res.string.action_delete) + " \"${d.source.name}\"?",
            message = stringResource(Res.string.livetv_delete_source_message),
            confirm = stringResource(Res.string.action_delete),
            destructive = true,
            onDismiss = close,
        ) { LiveTvRepository.removeEpg(d.source.id) }
        LiveDialog.Dns -> ChoiceDialog(
            title = stringResource(Res.string.livetv_dns),
            options = DnsChoice.entries.map { it to dnsLabel(it) },
            selected = dnsChoice,
            onDismiss = close,
            message = stringResource(Res.string.livetv_dns_dialog_message),
        ) { v -> LiveTvDns.set(v); dnsChoice = v }
        LiveDialog.PlaylistRefresh -> ChoiceDialog(
            title = stringResource(Res.string.livetv_playlist_refresh),
            options = REFRESH_HOURS.map { it to hoursLabel(it) },
            selected = settings.playlistRefreshHours,
            onDismiss = close,
        ) { v -> update { it.copy(playlistRefreshHours = v) } }
        LiveDialog.EpgRefresh -> ChoiceDialog(
            title = stringResource(Res.string.livetv_epg_refresh),
            options = REFRESH_HOURS.map { it to hoursLabel(it) },
            selected = settings.epgRefreshHours,
            onDismiss = close,
        ) { v -> update { it.copy(epgRefreshHours = v) } }
        LiveDialog.FutureDays -> ChoiceDialog(
            title = stringResource(Res.string.livetv_days_ahead),
            options = FUTURE_DAYS.map { it to pluralStringResource(Res.plurals.livetv_ui_days, it, it) },
            selected = settings.epgFutureDays,
            onDismiss = close,
        ) { v -> update { it.copy(epgFutureDays = v) }; LiveTvRepository.refreshEpgOnly() }
        LiveDialog.PastHours -> ChoiceDialog(
            title = stringResource(Res.string.livetv_hours_back),
            options = (PAST_HOURS + settings.epgPastHours).distinct().sorted().map { it to pastHoursLabel(it) },
            selected = settings.epgPastHours,
            onDismiss = close,
        ) { v -> update { it.copy(epgPastHours = v) }; LiveTvRepository.refreshEpgOnly() }
        LiveDialog.BackupOrder -> BackupOrderDialog(onDismiss = close)
        LiveDialog.StreamFormat -> ChoiceDialog(
            title = stringResource(Res.string.livetv_stream_format),
            options = listOf("auto" to "Auto", "ts" to "MPEG-TS", "m3u8" to "HLS (m3u8)"),
            selected = playlists.firstOrNull { it.isXtream }?.streamFormat ?: "auto",
            onDismiss = close,
        ) { v ->
            playlists.filter { it.isXtream && it.streamFormat != v }.forEach { LiveTvRepository.updatePlaylist(it.copy(streamFormat = v)) }
        }
        LiveDialog.VodTabPosition -> ChoiceDialog(
            title = stringResource(Res.string.livetv_vod_tab_position),
            options = listOf("FIRST", "AFTER_FIRST", "LAST").map { it to vodTabPositionLabel(it) },
            selected = settings.onDemandStreamsPosition,
            onDismiss = close,
        ) { v -> update { it.copy(onDemandStreamsPosition = v) } }
        LiveDialog.VodDefaultSort -> ChoiceDialog(
            title = stringResource(Res.string.livetv_vod_default_sort),
            options = VodSort.entries.map { it to it.label },
            selected = VodSort.parse(settings.vodDefaultSort) ?: VodSort.DEFAULT,
            onDismiss = close,
        ) { v -> update { it.copy(vodDefaultSort = v.name) } }
        LiveDialog.RecordPre -> ChoiceDialog(
            title = stringResource(Res.string.livetv_rec_padding_pre),
            options = PADDING_MINUTES.map { it to minutesLabel(it) },
            selected = settings.recordPreMinutes,
            onDismiss = close,
        ) { v -> update { it.copy(recordPreMinutes = v) } }
        LiveDialog.RecordPost -> ChoiceDialog(
            title = stringResource(Res.string.livetv_rec_padding_post),
            options = PADDING_MINUTES.map { it to minutesLabel(it) },
            selected = settings.recordPostMinutes,
            onDismiss = close,
        ) { v -> update { it.copy(recordPostMinutes = v) } }
        LiveDialog.RecordKeep -> {
            val all = stringResource(Res.string.livetv_rec_keep_all)
            ChoiceDialog(
                title = stringResource(Res.string.livetv_rec_series_keep),
                options = KEEP_COUNTS.map { it to if (it == 0) all else it.toString() },
                selected = settings.recordSeriesKeep,
                onDismiss = close,
            ) { v -> update { it.copy(recordSeriesKeep = v) } }
        }
        LiveDialog.RecordSpaceRule -> ChoiceDialog(
            title = stringResource(Res.string.livetv_rec_space_rule),
            options = listOf("STOP" to stringResource(Res.string.livetv_rec_space_rule_stop), "DELETE_OLDEST" to stringResource(Res.string.livetv_rec_space_rule_delete)),
            selected = settings.recordingSpaceRule,
            onDismiss = close,
        ) { v -> update { it.copy(recordingSpaceRule = v) } }
        LiveDialog.ConfirmRestore -> ConfirmDialog(
            title = stringResource(Res.string.livetv_restore) + "?",
            message = stringResource(Res.string.livetv_restore_message),
            confirm = stringResource(Res.string.livetv_restore),
            onDismiss = close,
        ) { LiveTvDriveSync.restoreNow() }
        is LiveDialog.ConfirmImportFile -> ConfirmDialog(
            title = stringResource(Res.string.livetv_import_confirm_title),
            message = stringResource(
                Res.string.livetv_import_confirm_message,
                d.info.device.ifBlank { stringResource(Res.string.livetv_import_unknown_device) },
                if (d.info.savedAtMs > 0) formatLocalDateTime(d.info.savedAtMs) else stringResource(Res.string.livetv_import_unknown_date),
                d.info.playlistCount,
                d.info.epgCount,
            ),
            confirm = stringResource(Res.string.livetv_import_confirm_action),
            onDismiss = close,
        ) { importFile(d.text) }
        LiveDialog.ConfirmSignOut -> ConfirmDialog(
            title = stringResource(Res.string.livetv_drive_sign_out) + "?",
            message = stringResource(Res.string.livetv_drive_sign_out_description),
            confirm = stringResource(Res.string.livetv_drive_sign_out),
            onDismiss = close,
        ) { LiveTvDriveSync.disconnect() }
        LiveDialog.ParentalMenu -> ActionsDialog(
            title = stringResource(Res.string.livetv_parental_pin),
            subtitle = stringResource(Res.string.livetv_parental_pin_set),
            onDismiss = close,
            actions = listOf(
                stringResource(Res.string.livetv_parental_change_pin) to { dialog = LiveDialog.ParentalAskPin(LiveDialog.ParentalSetPin) },
                stringResource(Res.string.livetv_parental_remove_pin) to { dialog = LiveDialog.ParentalAskPin(LiveDialog.ParentalRemovePin) },
            ),
        )
        is LiveDialog.ParentalAskPin -> ParentalPinDialog(
            title = stringResource(Res.string.livetv_parental_current_pin),
            // A right PIN moves on to the next step; only a cancel closes everything.
            onDismiss = { if (dialog is LiveDialog.ParentalAskPin) dialog = null },
            confirmLabel = stringResource(Res.string.action_save),
            onPin = { pin -> ParentalControls.checkPin(pin, settings).also { ok -> if (ok) dialog = d.then } },
        )
        is LiveDialog.LoginPin -> ParentalPinDialog(
            title = stringResource(Res.string.livetv_parental_logins_pin),
            // A right PIN moves on to what was asked for; only a cancel closes everything.
            onDismiss = { if (dialog is LiveDialog.LoginPin) dialog = null },
            onPin = { pin -> ParentalControls.unlockLogins(pin, settings).also { ok -> if (ok) { dialog = null; d.then() } } },
        )
        LiveDialog.ParentalSetPin -> ParentalPinDialog(
            title = stringResource(Res.string.livetv_parental_new_pin),
            message = stringResource(Res.string.livetv_parental_pin_description),
            onDismiss = close,
            confirmLabel = stringResource(Res.string.action_save),
            onPin = { pin -> ParentalControls.setPin(pin); true },
        )
        LiveDialog.ParentalRemovePin -> LaunchedEffect(Unit) { ParentalControls.setPin(""); close() }
        LiveDialog.ParentalGroups -> LockedGroupsDialog(onDismiss = close)
        LiveDialog.Reminders -> DialogSurface(onDismissRequest = close, title = stringResource(Res.string.livetv_reminders_list), message = stringResource(Res.string.livetv_reminders_list_description)) {
            if (userState.reminders.isEmpty()) {
                Text(stringResource(Res.string.livetv_reminders_none), style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted)
            }
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4)) {
                items(userState.reminders, key = { it.channelKey + "|" + it.startMs }) { r ->
                    DialogOption(
                        text = r.title,
                        description = r.channelName + " · " + formatLocalDateTime(r.startMs),
                        selected = true,
                        onClick = { LiveTvPreferences.removeReminder(r.channelKey, r.startMs) },
                    )
                }
            }
            DialogButtons { DialogButton(text = stringResource(Res.string.action_close), style = DialogButtonStyle.Primary, onClick = close) }
        }
        LiveDialog.ReminderLead -> ChoiceDialog(
            title = stringResource(Res.string.livetv_reminder_lead),
            options = REMINDER_LEADS.map { it to leadLabel(it) },
            selected = reminderLead,
            onDismiss = close,
        ) { v -> LiveTvPreferences.setReminderLeadMinutes(v) }
        LiveDialog.ScoreDelay -> ChoiceDialog(
            title = stringResource(Res.string.livetv_sports_score_delay),
            options = SportsScoreDelay.CHOICES.map { it to scoreDelayLabel(it) },
            selected = sports.scoreDelaySeconds,
            onDismiss = close,
        ) { v -> SportsRepository.updateSettings { it.copy(scoreDelaySeconds = v) } }
    }
}

@Composable
private fun dnsLabel(value: DnsChoice): String = when (value) {
    DnsChoice.SYSTEM -> stringResource(Res.string.livetv_dns_system)
    DnsChoice.CLOUDFLARE -> stringResource(Res.string.livetv_dns_cloudflare)
    DnsChoice.GOOGLE -> stringResource(Res.string.livetv_dns_google)
}

@Composable
private fun ValueLabel(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.nuvio.colors.accent)
}

@Composable
private fun minutesLabel(m: Int) = if (m == 0) stringResource(Res.string.livetv_ui_none) else pluralStringResource(Res.plurals.livetv_ui_minutes, m, m)

@Composable
private fun scoreDelayLabel(seconds: Int): String = when {
    seconds <= 0 -> stringResource(Res.string.livetv_sports_score_delay_off)
    seconds < 60 -> stringResource(Res.string.livetv_sports_score_delay_seconds, seconds)
    seconds == 60 -> stringResource(Res.string.livetv_sports_score_delay_one_minute)
    else -> stringResource(Res.string.livetv_sports_score_delay_minutes, seconds / 60)
}

@Composable
private fun leadLabel(m: Int): String = if (m == 0) stringResource(Res.string.livetv_reminder_lead_at_start) else stringResource(Res.string.livetv_reminder_lead_minutes, m)

private fun streamFormatLabel(f: String) = when (f) { "ts" -> "MPEG-TS"; "m3u8" -> "HLS (m3u8)"; else -> "Auto" }

@Composable
private fun playlistStatus(pl: PlaylistSource, account: PlaylistAccount?, hiddenHere: Boolean = false): String {
    val parts = ArrayList<String>()
    if (!pl.enabled) parts += stringResource(Res.string.livetv_ui_off)
    if (hiddenHere) parts += stringResource(Res.string.livetv_ui_playlist_profile_hidden)
    if ((pl.isXtream || pl.isStalker) && !pl.importLive) {
        // TV channels switched off for this login: nothing to update or count on the Live TV side.
        parts += stringResource(if (pl.importVod) Res.string.livetv_xtream_movies_only else Res.string.livetv_xtream_nothing_included)
    } else {
        if (pl.lastError != null) parts += pl.lastError
        else if (pl.lastUpdatedMs > 0) parts += stringResource(Res.string.livetv_updated_at, formatLocalDateTime(pl.lastUpdatedMs))
        else parts += stringResource(Res.string.livetv_never_updated)
        if (pl.channelCount > 0) parts += stringResource(Res.string.livetv_ui_channel_count, pl.channelCount)
    }
    account?.let { a ->
        a.daysLeftLabel()?.let {
            val d = (a.daysLeft() ?: 0L).toInt()
            parts += when (it) {
                "expired" -> stringResource(Res.string.livetv_ui_expired)
                "today" -> stringResource(Res.string.livetv_ui_expires_today)
                else -> stringResource(Res.string.livetv_ui_expires_in, pluralStringResource(Res.plurals.livetv_ui_days, d, d))
            }
        }
        if (a.status.isNotBlank() && !a.status.equals("Active", true)) parts += a.status
        if (a.maxConnections > 0) parts += stringResource(Res.string.livetv_ui_connections, a.activeConnections, a.maxConnections)
    }
    return parts.joinToString(" · ")
}

@Composable
private fun epgStatus(src: EpgSource): String {
    val parts = ArrayList<String>()
    if (!src.enabled) parts += stringResource(Res.string.livetv_ui_off)
    if (src.lastError != null) parts += src.lastError
    else if (src.lastUpdatedMs > 0) parts += stringResource(Res.string.livetv_updated_at, formatLocalDateTime(src.lastUpdatedMs))
    else parts += stringResource(Res.string.livetv_never_updated)
    return parts.joinToString(" · ")
}

// ---------------------------------------------------------------- dialogs

@Composable
private fun ActionsDialog(title: String, subtitle: String?, actions: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    DialogSurface(onDismissRequest = onDismiss, title = title, message = subtitle) {
        Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6)) {
            actions.forEach { (label, act) -> DialogOption(text = label, selected = false, onClick = act) }
        }
        DialogButtons { DialogButton(text = stringResource(Res.string.action_cancel), onClick = onDismiss) }
    }
}

@Composable
private fun ConfirmDialog(title: String, message: String, confirm: String, destructive: Boolean = false, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    DialogSurface(onDismissRequest = onDismiss, title = title, message = message) {
        DialogButtons {
            DialogButton(text = stringResource(Res.string.action_cancel), onClick = onDismiss)
            DialogButton(text = confirm, style = if (destructive) DialogButtonStyle.Destructive else DialogButtonStyle.Primary, onClick = { onConfirm(); onDismiss() })
        }
    }
}

@Composable
private fun vodTabPositionLabel(v: String): String = stringResource(
    when (v) {
        "FIRST" -> Res.string.livetv_vod_pos_first
        "AFTER_FIRST" -> Res.string.livetv_vod_pos_after_first
        else -> Res.string.livetv_vod_pos_last
    },
)

@Composable
private fun <T> ChoiceDialog(title: String, options: List<Pair<T, String>>, selected: T, onDismiss: () -> Unit, message: String? = null, onPick: (T) -> Unit) {
    // Long lists (catch-up time shift has 49 choices) scroll, and open at the current choice.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (options.indexOfFirst { it.first == selected } - 2).coerceAtLeast(0),
    )
    DialogSurface(onDismissRequest = onDismiss, title = title, message = message) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
            modifier = Modifier.weight(1f, fill = false).heightIn(max = 420.dp),
        ) {
            items(options) { (v, label) -> DialogOption(text = label, selected = v == selected, onClick = { onPick(v); onDismiss() }) }
        }
        DialogButtons { DialogButton(text = stringResource(Res.string.action_cancel), onClick = onDismiss) }
    }
}

/**
 * The guides a playlist can switch on or off: every guide added on its own, and the built-in
 * guides of other playlists (key, name). Empty when there's nothing to choose from.
 */
private fun guideChoices(pl: PlaylistSource, playlists: List<PlaylistSource>, epgs: List<EpgSource>): List<Pair<String, String>> {
    val own = LiveTvRepository.playlistsWithOwnGuide(playlists)
    return epgs.filter { it.url.isNotBlank() }.map { it.id to it.name } +
        playlists.filter { it.id != pl.id && it.liveEnabled && it.useEmbeddedEpg && it.id in own }
            .map { PlaylistSource.builtInGuideKey(it.id) to it.name + " (built-in)" }
}

/** "Show on Profiles": which Nuvio profiles get this playlist's channels, On Demand and Sports. */
@Composable
private fun PlaylistProfilesDialog(pl: PlaylistSource, profiles: List<NuvioProfile>, onDismiss: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    // Read again after each change (matched profiles change together).
    var version by remember { mutableStateOf(0) }
    val hidden = remember(version, profiles) { profiles.associate { it.profileIndex to (pl.id in LiveTvPreferences.hiddenPlaylistsOf(it.profileIndex)) } }
    DialogSurface(onDismissRequest = onDismiss, title = stringResource(Res.string.livetv_show_on_profiles) + ": " + pl.name) {
        Text(stringResource(Res.string.livetv_show_on_profiles_description, pl.name), style = MaterialTheme.typography.bodySmall, color = tokens.colors.textSecondary)
        Column(modifier = Modifier.weight(1f, fill = false).heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            profiles.forEach { prof ->
                SettingsSwitchRow(
                    title = prof.name.ifBlank { stringResource(Res.string.livetv_ui_profile_n, prof.profileIndex) }, checked = hidden[prof.profileIndex] != true, isTablet = false, horizontalPadding = 0.dp,
                    onCheckedChange = { on -> LiveTvPreferences.setPlaylistShownOn(pl.id, prof.profileIndex, on); version++ },
                )
            }
        }
        DialogButtons { DialogButton(text = stringResource(Res.string.action_close), onClick = onDismiss) }
    }
}

@Composable
private fun PlaylistGuidesDialog(playlistId: String, onDismiss: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val playlists by LiveTvPreferences.playlists.collectAsStateWithLifecycle()
    val epgs by LiveTvPreferences.epgSources.collectAsStateWithLifecycle()
    val pl = playlists.firstOrNull { it.id == playlistId } ?: return
    val choices = guideChoices(pl, playlists, epgs)
    DialogSurface(onDismissRequest = onDismiss, title = stringResource(Res.string.livetv_guides) + ": " + pl.name) {
        if (choices.isEmpty()) {
            Text(stringResource(Res.string.livetv_guides_none), style = MaterialTheme.typography.bodySmall, color = tokens.colors.textSecondary)
        } else {
            Text(stringResource(Res.string.livetv_guides_description, pl.name), style = MaterialTheme.typography.bodySmall, color = tokens.colors.textSecondary)
            Column(modifier = Modifier.weight(1f, fill = false).heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                choices.forEach { (key, label) ->
                    SettingsSwitchRow(
                        title = label, checked = key !in pl.guidesOff, isTablet = false, horizontalPadding = 0.dp,
                        onCheckedChange = { on -> LiveTvRepository.setPlaylistGuide(pl.id, key, on) },
                    )
                }
            }
        }
        DialogButtons { DialogButton(text = stringResource(Res.string.action_close), onClick = onDismiss) }
    }
}

@Composable
private fun M3uForm(existing: PlaylistSource?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var url by remember { mutableStateOf(existing?.url.orEmpty()) }
    var ua by remember { mutableStateOf(existing?.userAgent.orEmpty()) }
    var embedded by remember { mutableStateOf(existing?.useEmbeddedEpg ?: true) }
    var includeVod by remember { mutableStateOf(existing?.importVod ?: true) }
    var guideLink by remember { mutableStateOf("") }
    DialogSurface(onDismissRequest = onDismiss, title = stringResource(if (existing == null) Res.string.livetv_add_m3u else Res.string.action_edit)) {
        NuvioInputField(value = name, onValueChange = { name = it }, placeholder = stringResource(Res.string.livetv_field_name))
        NuvioInputField(value = url, onValueChange = { url = it }, placeholder = stringResource(Res.string.livetv_field_url))
        NuvioInputField(value = ua, onValueChange = { ua = it }, placeholder = stringResource(Res.string.livetv_field_user_agent))
        if (existing == null) NuvioInputField(value = guideLink, onValueChange = { guideLink = it }, placeholder = stringResource(Res.string.livetv_field_guide_link))
        SettingsSwitchRow(title = stringResource(Res.string.livetv_load_embedded_epg), checked = embedded, isTablet = false, horizontalPadding = 0.dp, onCheckedChange = { embedded = it })
        SettingsSwitchRow(title = stringResource(Res.string.livetv_xtream_include_vod), checked = includeVod, isTablet = false, horizontalPadding = 0.dp, onCheckedChange = { includeVod = it })
        DialogButtons {
            DialogButton(text = stringResource(Res.string.action_cancel), onClick = onDismiss)
            DialogButton(
                text = stringResource(Res.string.action_save),
                style = DialogButtonStyle.Primary,
                enabled = url.isNotBlank(),
                onClick = {
                    if (existing == null) LiveTvRepository.addPlaylist(name, url, ua, guideLink, importVod = includeVod)
                    else LiveTvRepository.updatePlaylist(
                        existing.copy(
                            name = name.ifBlank { "Playlist" }, url = url.trim(), userAgent = ua.trim(), useEmbeddedEpg = embedded,
                            importVod = includeVod, vodSwitchSet = existing.vodSwitchSet || includeVod != existing.importVod
                        )
                    )
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun XtreamForm(existing: PlaylistSource?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var server by remember { mutableStateOf(existing?.xtreamServer.orEmpty()) }
    var user by remember { mutableStateOf(existing?.xtreamUsername.orEmpty()) }
    var pass by remember { mutableStateOf(existing?.xtreamPassword.orEmpty()) }
    var ua by remember { mutableStateOf(existing?.userAgent.orEmpty()) }
    var embedded by remember { mutableStateOf(existing?.useEmbeddedEpg ?: true) }
    var includeLive by remember { mutableStateOf(existing?.importLive ?: true) }
    var includeVod by remember { mutableStateOf(existing?.importVod ?: true) }
    var guideLink by remember { mutableStateOf("") }
    DialogSurface(onDismissRequest = onDismiss, title = stringResource(if (existing == null) Res.string.livetv_add_xtream else Res.string.action_edit)) {
        NuvioInputField(value = name, onValueChange = { name = it }, placeholder = stringResource(Res.string.livetv_field_name))
        NuvioInputField(value = server, onValueChange = { server = it }, placeholder = stringResource(Res.string.livetv_field_server))
        NuvioInputField(value = user, onValueChange = { user = it }, placeholder = stringResource(Res.string.livetv_field_username))
        if (ParentalControls.hideLogins()) StalkerSecretField(pass, { pass = it }, stringResource(Res.string.livetv_field_password))
        else NuvioInputField(value = pass, onValueChange = { pass = it }, placeholder = stringResource(Res.string.livetv_field_password))
        NuvioInputField(value = ua, onValueChange = { ua = it }, placeholder = stringResource(Res.string.livetv_field_user_agent))
        if (existing == null) NuvioInputField(value = guideLink, onValueChange = { guideLink = it }, placeholder = stringResource(Res.string.livetv_field_guide_link))
        SettingsSwitchRow(title = stringResource(Res.string.livetv_load_embedded_epg), checked = embedded, isTablet = false, horizontalPadding = 0.dp, onCheckedChange = { embedded = it })
        SettingsSwitchRow(title = stringResource(Res.string.livetv_xtream_include_channels), checked = includeLive, isTablet = false, horizontalPadding = 0.dp, onCheckedChange = { includeLive = it })
        SettingsSwitchRow(title = stringResource(Res.string.livetv_xtream_include_vod), checked = includeVod, isTablet = false, horizontalPadding = 0.dp, onCheckedChange = { includeVod = it })
        DialogButtons {
            DialogButton(text = stringResource(Res.string.action_cancel), onClick = onDismiss)
            DialogButton(
                text = stringResource(Res.string.action_save),
                style = DialogButtonStyle.Primary,
                enabled = server.isNotBlank() && (PlaylistSource.credentialsFromLink(server) != null || (user.isNotBlank() && pass.isNotBlank())),
                onClick = {
                    if (existing == null) LiveTvRepository.addXtream(name, server, user, pass, importVod = includeVod, importLive = includeLive, epgUrl = guideLink)
                    else LiveTvRepository.updatePlaylist(
                        existing.copy(name = name.ifBlank { "Xtream" }, xtreamServer = server, xtreamUsername = user, xtreamPassword = pass, userAgent = ua.trim(), useEmbeddedEpg = embedded, importVod = includeVod, importLive = includeLive)
                    )
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun StalkerForm(existing: PlaylistSource?, onDismiss: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val original = existing?.stalker
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var portal by remember { mutableStateOf(original?.portalUrl.orEmpty()) }
    var mac by remember { mutableStateOf(original?.mac.orEmpty()) }
    var user by remember { mutableStateOf(original?.username.orEmpty()) }
    var password by remember { mutableStateOf(original?.password.orEmpty()) }
    var serial by remember { mutableStateOf(original?.serialNumber.orEmpty()) }
    var device by remember { mutableStateOf(original?.deviceId.orEmpty()) }
    var device2 by remember { mutableStateOf(original?.deviceId2.orEmpty()) }
    var signature by remember { mutableStateOf(original?.signature.orEmpty()) }
    var timezone by remember { mutableStateOf(original?.timezone ?: "UTC") }
    var language by remember { mutableStateOf(original?.language ?: "en") }
    val ua = existing?.userAgent.orEmpty()
    var embedded by remember { mutableStateOf(existing?.useEmbeddedEpg ?: true) }
    var includeLive by remember { mutableStateOf(existing?.importLive ?: true) }
    var includeVod by remember { mutableStateOf(existing?.importVod ?: true) }
    var advanced by remember { mutableStateOf(false) }
    var guideLink by remember { mutableStateOf("") }
    val normalizedMac = mac.trim().replace('-', ':').uppercase()
    val validPortal = runCatching { Url(portal.trim()).let { it.protocol.name in setOf("http", "https") && it.host.isNotBlank() && it.user.isNullOrEmpty() && it.password.isNullOrEmpty() } }.getOrDefault(false)
    val validMac = Regex("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}").matches(normalizedMac)
    DialogSurface(onDismissRequest = onDismiss, title = stringResource(if (existing == null) Res.string.livetv_add_stalker else Res.string.action_edit)) {
        Column(modifier = Modifier.weight(1f, fill = false).heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.listGap)) {
            NuvioInputField(name, { name = it }, stringResource(Res.string.livetv_field_name))
            NuvioInputField(portal, { portal = it }, stringResource(Res.string.livetv_stalker_portal))
            NuvioInputField(mac, { mac = it }, stringResource(Res.string.livetv_stalker_mac))
            Text(stringResource(Res.string.livetv_stalker_device_binding), style = MaterialTheme.typography.bodySmall, color = tokens.colors.textSecondary)
            Text(stringResource(Res.string.livetv_stalker_playback_limits), style = MaterialTheme.typography.bodySmall, color = tokens.colors.textSecondary)
            if (existing == null) NuvioInputField(guideLink, { guideLink = it }, stringResource(Res.string.livetv_field_guide_link))
            SettingsSwitchRow(title = stringResource(Res.string.livetv_stalker_load_guide), checked = embedded, isTablet = false, horizontalPadding = 0.dp, onCheckedChange = { embedded = it })
            SettingsSwitchRow(title = stringResource(Res.string.livetv_xtream_include_channels), checked = includeLive, isTablet = false, horizontalPadding = 0.dp, onCheckedChange = { includeLive = it })
            SettingsSwitchRow(title = stringResource(Res.string.livetv_xtream_include_vod), checked = includeVod, isTablet = false, horizontalPadding = 0.dp, onCheckedChange = { includeVod = it })
            SettingsNavigationRow(title = stringResource(Res.string.livetv_stalker_device_options),
                description = stringResource(Res.string.livetv_stalker_device_options_description), isTablet = false,
                onClick = { advanced = !advanced })
            if (advanced) {
                Text(stringResource(Res.string.livetv_stalker_optional_login), style = MaterialTheme.typography.bodySmall)
                NuvioInputField(user, { user = it }, stringResource(Res.string.livetv_field_username))
                StalkerSecretField(password, { password = it }, stringResource(Res.string.livetv_field_password))
                NuvioInputField(serial, { serial = it }, stringResource(Res.string.livetv_stalker_serial))
                StalkerSecretField(device, { device = it }, stringResource(Res.string.livetv_stalker_device_id))
                StalkerSecretField(device2, { device2 = it }, stringResource(Res.string.livetv_stalker_device_id2))
                StalkerSecretField(signature, { signature = it }, stringResource(Res.string.livetv_stalker_signature))
                NuvioInputField(timezone, { timezone = it }, stringResource(Res.string.livetv_stalker_timezone))
                NuvioInputField(language, { language = it }, stringResource(Res.string.livetv_stalker_language))
            }
        }
        DialogButtons {
            DialogButton(text = stringResource(Res.string.action_cancel), onClick = onDismiss)
            DialogButton(text = stringResource(Res.string.action_save), style = DialogButtonStyle.Primary,
                enabled = validPortal && validMac && (includeLive || includeVod), onClick = {
                    val config = StalkerConfig(portalUrl = portal.trim(), mac = normalizedMac,
                        username = user.trim(), password = password, serialNumber = serial.trim(), deviceId = device.trim(),
                        deviceId2 = device2.trim(), signature = signature.trim(), timezone = timezone.trim().ifBlank { "UTC" },
                        language = language.trim().ifBlank { "en" })
                    if (existing == null) LiveTvRepository.addStalker(name, config, ua, includeVod, includeLive, embedded, guideLink)
                    else LiveTvRepository.updatePlaylist(existing.copy(name = name.ifBlank { "Stalker" }, providerKind = "stalker",
                        stalker = config, url = "", userAgent = ua.trim(), importVod = includeVod, importLive = includeLive,
                        useEmbeddedEpg = embedded))
                    OnDemandRepository.refreshSoon()
                    onDismiss()
                })
        }
    }
}

/** How a network folder address is typed (shown as the example). */
private const val NETWORK_ADDRESS_EXAMPLE = "\\\\192.168.1.20\\Recordings"

/** Where the network folder set-up is: the form, or a step of Find on network. */
private enum class FolderStep { FORM, SEARCH, LOGIN, SHARES, FOLDERS }

/**
 * Sets up the network folder (the one the TVs record to): address, username, password.
 * "Test and save" connects and saves a small test file first; if that fails it says why, and
 * "Save anyway" keeps it (a computer that's asleep right now). Saved here, it goes to the TVs
 * with Live TV sync. The password always shows as dots, like on the TV.
 *
 * "Find on network" fills it in instead, like on the TV: pick a computer or NAS from the ones
 * found, sign in, pick one of its shared folders and a folder inside it, and it's tested and saved.
 */
@Composable
private fun NetworkFolderForm(onDismiss: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val existing = remember { NetworkFolderConfig.fromJson(LiveTvPreferences.currentNetworkFolderJson()) }
    var address by remember { mutableStateOf(existing?.address.orEmpty()) }
    var username by remember { mutableStateOf(existing?.username.orEmpty()) }
    var password by remember { mutableStateOf(existing?.password.orEmpty()) }
    var status by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val checkingText = stringResource(Res.string.livetv_network_checking)
    val checkingVpnText = stringResource(Res.string.livetv_network_checking_vpn)
    val badAddressText = stringResource(Res.string.livetv_network_bad_address, NETWORK_ADDRESS_EXAMPLE)
    val netFailedText = stringResource(Res.string.livetv_ui_netfolder_failed)
    val savedText = stringResource(Res.string.livetv_network_saved)
    val removedText = stringResource(Res.string.livetv_network_removed)
    val signingInText = stringResource(Res.string.livetv_network_find_signing_in)
    val badFolderNameText = stringResource(Res.string.livetv_network_find_bad_folder_name)

    // Find on network
    var step by remember { mutableStateOf(FolderStep.FORM) }
    var searchRun by remember { mutableStateOf(0) }
    var search by remember { mutableStateOf(NetworkSearchState()) }
    var computer by remember { mutableStateOf<FoundComputer?>(null) }
    var shares by remember { mutableStateOf<List<SmbShare>>(emptyList()) }
    var share by remember { mutableStateOf<String?>(null) }
    var browsePath by remember { mutableStateOf("") }
    var folders by remember { mutableStateOf<List<String>?>(null) }
    var foldersRun by remember { mutableStateOf(0) }
    var creatingFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    fun configOrNull(): NetworkFolderConfig? {
        val (host, share, path) = NetworkFolderConfig.parseAddress(address) ?: return null
        return NetworkFolderConfig(host = host, share = share, path = path, username = username.trim(), password = password,
            domain = existing?.takeIf { it.host == host }?.domain.orEmpty())
    }
    fun save(c: NetworkFolderConfig) {
        NetworkLibrary.save(c)
        com.nuvio.app.core.ui.NuvioToastController.show(savedText)
        onDismiss()
    }
    fun edited() { failed = false; status = null }

    fun testAndSave() {
        val c = configOrNull()
        if (c == null) { failed = false; status = badAddressText; return }
        checking = true
        failed = false
        status = checkingText
        scope.launch {
            val problem = runCatching { NetworkLibraryPlatform.test(c) { status = checkingVpnText } }
                .getOrElse { it.message ?: netFailedText }
            checking = false
            if (problem == null) save(c)
            else { failed = true; status = problem }
        }
    }

    fun browsingConfig(path: String = browsePath): NetworkFolderConfig? {
        val c = computer ?: return null
        val s = share ?: return null
        return NetworkFolderConfig(host = c.host, share = s, path = path, username = username.trim(), password = password)
    }

    fun signIn() {
        val c = computer ?: return
        checking = true
        failed = false
        status = signingInText
        scope.launch {
            val result = runCatching {
                NetworkLibraryPlatform.letPastVpn(c.host) { status = checkingVpnText }
                NetworkLibraryPlatform.shares(c.host, username, password)
            }
            checking = false
            result.onSuccess {
                shares = it
                edited()
                step = FolderStep.SHARES
            }.onFailure { e ->
                failed = true
                status = NetworkLibraryPlatform.describe(e, NetworkFolderConfig(host = c.host, share = ""))
            }
        }
    }

    fun openFolder(path: String) {
        browsePath = path
        folders = null
        creatingFolder = false
        edited()
        foldersRun++
    }

    LaunchedEffect(step, searchRun) {
        if (step != FolderStep.SEARCH) return@LaunchedEffect
        search = NetworkSearchState()
        NetworkLibraryPlatform.search().collect { search = it }
    }

    LaunchedEffect(step, foldersRun) {
        if (step != FolderStep.FOLDERS) return@LaunchedEffect
        val config = browsingConfig() ?: return@LaunchedEffect
        runCatching { NetworkLibraryPlatform.folders(config) }
            .onSuccess { folders = it }
            .onFailure { e ->
                folders = emptyList()
                failed = true
                status = NetworkLibraryPlatform.describe(e, config)
            }
    }

    fun back() {
        if (checking) return
        edited()
        when (step) {
            FolderStep.FORM -> onDismiss()
            FolderStep.SEARCH -> step = FolderStep.FORM
            FolderStep.LOGIN -> step = FolderStep.SEARCH
            FolderStep.SHARES -> step = FolderStep.LOGIN
            FolderStep.FOLDERS -> when {
                creatingFolder -> creatingFolder = false
                browsePath.isEmpty() -> step = FolderStep.SHARES
                else -> openFolder(browsePath.substringBeforeLast('/', ""))
            }
        }
    }

    // Closed without saving: the VPN goes back to the saved folder's address.
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { NetworkLibrary.restoreVpn() } }

    val computerLabel = computer?.let { c -> c.name?.let { "$it (${c.host})" } ?: c.host }.orEmpty()
    val backText = stringResource(Res.string.livetv_network_find_back)

    @Composable
    fun FindRow(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, description: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
        DialogOption(
            text = text,
            selected = false,
            onClick = onClick,
            description = description,
            enabled = enabled,
            role = androidx.compose.ui.semantics.Role.Button,
            leading = {
                androidx.compose.material3.Icon(icon, contentDescription = null, tint = tokens.colors.textSecondary)
            },
        )
    }

    @Composable
    fun Note(text: String) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted)
    }

    DialogSurface(
        onDismissRequest = { back() },
        title = stringResource(Res.string.livetv_network_title),
        message = when (step) {
            FolderStep.FORM -> stringResource(Res.string.livetv_network_subtitle)
            FolderStep.SEARCH -> stringResource(Res.string.livetv_network_find_subtitle)
            FolderStep.LOGIN -> stringResource(Res.string.livetv_network_find_login_subtitle, computerLabel)
            FolderStep.SHARES -> stringResource(Res.string.livetv_network_find_shares_subtitle, computerLabel)
            FolderStep.FOLDERS -> stringResource(Res.string.livetv_network_find_folders_subtitle)
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
        ) {
            when (step) {
                FolderStep.FORM -> {
                    FindRow(
                        text = stringResource(Res.string.livetv_network_find),
                        icon = androidx.compose.material.icons.Icons.Default.Search,
                        description = stringResource(Res.string.livetv_network_find_row_subtitle),
                        enabled = !checking,
                    ) { edited(); searchRun++; step = FolderStep.SEARCH }
                    NuvioInputField(value = address, onValueChange = { address = it; edited() },
                        placeholder = stringResource(Res.string.livetv_network_address, NETWORK_ADDRESS_EXAMPLE))
                    NuvioInputField(value = username, onValueChange = { username = it; edited() },
                        placeholder = stringResource(Res.string.livetv_network_username))
                    StalkerSecretField(password, { password = it; edited() }, stringResource(Res.string.livetv_network_password))
                    Note(stringResource(Res.string.livetv_network_tips))
                }

                FolderStep.SEARCH -> {
                    FindRow(
                        text = stringResource(Res.string.livetv_network_find_type_instead),
                        icon = androidx.compose.material.icons.Icons.Default.Edit,
                    ) { step = FolderStep.FORM }
                    Note(
                        when {
                            search.noNetwork -> stringResource(Res.string.livetv_network_find_no_network)
                            search.searching -> stringResource(Res.string.livetv_network_find_searching)
                            search.found.isEmpty() -> stringResource(Res.string.livetv_network_find_none)
                            else -> stringResource(Res.string.livetv_network_find_pick)
                        },
                    )
                    if (search.vpnInTheWay) Note(stringResource(Res.string.livetv_network_find_vpn))
                    for (found in search.found) {
                        FindRow(
                            text = found.name ?: found.host,
                            icon = androidx.compose.material.icons.Icons.Default.Dns,
                            description = if (found.name != null) found.host else null,
                        ) {
                            // Signing in to the same computer again: keep the saved login.
                            if (existing != null && existing.host.equals(found.host, ignoreCase = true) && computer?.host != found.host) {
                                username = existing.username
                                password = existing.password
                            }
                            computer = found
                            edited()
                            step = FolderStep.LOGIN
                        }
                    }
                }

                FolderStep.LOGIN -> {
                    NuvioInputField(value = username, onValueChange = { username = it; edited() },
                        placeholder = stringResource(Res.string.livetv_network_username))
                    StalkerSecretField(password, { password = it; edited() }, stringResource(Res.string.livetv_network_password))
                    Note(stringResource(Res.string.livetv_network_find_login_tip))
                }

                FolderStep.SHARES -> {
                    if (shares.isEmpty()) {
                        Note(stringResource(Res.string.livetv_network_find_no_shares))
                        FindRow(
                            text = stringResource(Res.string.livetv_network_find_type_instead),
                            icon = androidx.compose.material.icons.Icons.Default.Edit,
                        ) {
                            computer?.let { address = "\\\\" + it.host + "\\" }
                            step = FolderStep.FORM
                        }
                    }
                    for (s in shares) {
                        FindRow(
                            text = s.name,
                            icon = androidx.compose.material.icons.Icons.Default.FolderShared,
                            description = s.remark.ifBlank { null },
                        ) {
                            share = s.name
                            step = FolderStep.FOLDERS
                            openFolder("")
                        }
                    }
                }

                FolderStep.FOLDERS -> {
                    val here = browsingConfig()?.label.orEmpty()
                    if (creatingFolder) {
                        NuvioInputField(value = newFolderName, onValueChange = { newFolderName = it; edited() },
                            placeholder = stringResource(Res.string.livetv_network_find_new_folder_name))
                        Note(stringResource(Res.string.livetv_network_find_new_folder_in, here))
                    } else {
                        FindRow(
                            text = stringResource(Res.string.livetv_network_find_use_folder),
                            icon = androidx.compose.material.icons.Icons.Default.Check,
                            description = here,
                            enabled = !checking,
                        ) {
                            val config = browsingConfig() ?: return@FindRow
                            address = config.address
                            step = FolderStep.FORM
                            testAndSave()
                        }
                        FindRow(
                            text = stringResource(Res.string.livetv_network_find_new_folder),
                            icon = androidx.compose.material.icons.Icons.Default.CreateNewFolder,
                            enabled = !checking && folders != null,
                        ) { newFolderName = ""; edited(); creatingFolder = true }
                        val list = folders
                        if (list == null) Note(stringResource(Res.string.livetv_network_find_loading_folders))
                        else for (name in list) {
                            FindRow(text = name, icon = androidx.compose.material.icons.Icons.Default.Folder) {
                                openFolder(if (browsePath.isEmpty()) name else "$browsePath/$name")
                            }
                        }
                    }
                }
            }
        }
        status?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = if (failed) tokens.colors.danger else tokens.colors.textMuted)
        }
        DialogButtons {
            when (step) {
                FolderStep.FORM -> {
                    DialogButton(text = stringResource(Res.string.action_cancel), onClick = onDismiss, enabled = !checking)
                    if (existing != null) DialogButton(
                        text = stringResource(Res.string.livetv_network_remove),
                        style = DialogButtonStyle.Destructive,
                        enabled = !checking,
                        onClick = {
                            NetworkLibrary.save(null)
                            com.nuvio.app.core.ui.NuvioToastController.show(removedText)
                            onDismiss()
                        },
                    )
                    if (failed) DialogButton(
                        text = stringResource(Res.string.livetv_network_save_anyway),
                        enabled = !checking,
                        onClick = { configOrNull()?.let { save(it) } },
                    )
                    DialogButton(
                        text = stringResource(Res.string.livetv_network_test_save),
                        style = DialogButtonStyle.Primary,
                        loading = checking,
                        onClick = { testAndSave() },
                    )
                }
                FolderStep.SEARCH -> {
                    DialogButton(text = backText, onClick = { back() })
                    DialogButton(
                        text = stringResource(Res.string.livetv_network_find_again),
                        style = DialogButtonStyle.Primary,
                        enabled = !search.searching,
                        onClick = { searchRun++ },
                    )
                }
                FolderStep.LOGIN -> {
                    DialogButton(text = backText, onClick = { back() }, enabled = !checking)
                    DialogButton(
                        text = stringResource(Res.string.livetv_network_find_show_shares),
                        style = DialogButtonStyle.Primary,
                        loading = checking,
                        onClick = { signIn() },
                    )
                }
                FolderStep.SHARES -> {
                    DialogButton(text = backText, onClick = { back() })
                }
                FolderStep.FOLDERS -> {
                    DialogButton(
                        text = if (creatingFolder) stringResource(Res.string.action_cancel) else backText,
                        onClick = { back() },
                        enabled = !checking,
                    )
                    if (creatingFolder) DialogButton(
                        text = stringResource(Res.string.livetv_network_find_create),
                        style = DialogButtonStyle.Primary,
                        loading = checking,
                        onClick = create@{
                            val name = newFolderName.trim().trim('/', '\\')
                            if (name.isEmpty() || name.any { it in "\\/:*?\"<>|" } || name == "." || name == "..") {
                                failed = true
                                status = badFolderNameText
                                return@create
                            }
                            val config = browsingConfig() ?: return@create
                            checking = true
                            scope.launch {
                                val result = runCatching { NetworkLibraryPlatform.makeFolder(config, name) }
                                checking = false
                                result.onSuccess {
                                    openFolder(if (browsePath.isEmpty()) name else "$browsePath/$name")
                                }.onFailure { e ->
                                    failed = true
                                    status = NetworkLibraryPlatform.describe(e, config)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StalkerSecretField(value: String, onChange: (String) -> Unit, placeholder: String) {
    val tokens = MaterialTheme.nuvio
    OutlinedTextField(value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(), singleLine = true,
        shape = tokens.shapes.compactCard, visualTransformation = PasswordVisualTransformation(),
        placeholder = { Text(placeholder, color = tokens.colors.textMuted) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = tokens.colors.textPrimary),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = tokens.colors.borderFocus,
            unfocusedBorderColor = tokens.colors.borderDefault, focusedContainerColor = tokens.colors.surfaceCard,
            unfocusedContainerColor = tokens.colors.surfaceCard, cursorColor = tokens.colors.accent))
}

@Composable
private fun EpgForm(existing: EpgSource?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var url by remember { mutableStateOf(existing?.url.orEmpty()) }
    var ua by remember { mutableStateOf(existing?.userAgent.orEmpty()) }
    DialogSurface(onDismissRequest = onDismiss, title = stringResource(if (existing == null) Res.string.livetv_add_epg else Res.string.action_edit)) {
        NuvioInputField(value = name, onValueChange = { name = it }, placeholder = stringResource(Res.string.livetv_field_name))
        NuvioInputField(value = url, onValueChange = { url = it }, placeholder = stringResource(Res.string.livetv_field_xmltv_url))
        NuvioInputField(value = ua, onValueChange = { ua = it }, placeholder = stringResource(Res.string.livetv_field_user_agent))
        DialogButtons {
            DialogButton(text = stringResource(Res.string.action_cancel), onClick = onDismiss)
            DialogButton(
                text = stringResource(Res.string.action_save),
                style = DialogButtonStyle.Primary,
                enabled = url.isNotBlank(),
                onClick = {
                    if (existing == null) LiveTvRepository.addEpg(name, url, ua)
                    else LiveTvRepository.updateEpg(existing.copy(name = name.ifBlank { "EPG" }, url = url.trim(), userAgent = ua.trim()))
                    onDismiss()
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DriveSignInDialog(prompt: SignInPrompt, onCancel: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val shortUrl = prompt.url.removePrefix("https://").removePrefix("http://")
    DialogSurface(onDismissRequest = onCancel, title = stringResource(Res.string.livetv_drive_sign_in)) {
        Text(
            text = stringResource(Res.string.livetv_drive_code_instructions, shortUrl),
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = prompt.userCode,
            style = MaterialTheme.typography.displaySmall,
            color = tokens.colors.accent,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8, Alignment.CenterHorizontally)) {
            Chip(label = shortUrl, selected = true, onClick = { runCatching { uriHandler.openUri(prompt.qrUrl) } })
        }
        Text(
            text = stringResource(Res.string.livetv_drive_code_waiting),
            style = MaterialTheme.typography.bodySmall,
            color = tokens.colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        DialogButtons { DialogButton(text = stringResource(Res.string.action_cancel), onClick = onCancel) }
    }
}

/** Which channel groups and On Demand categories need the PIN. */
@Composable
private fun LockedGroupsDialog(onDismiss: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val state by LiveTvViewState.list.collectAsStateWithLifecycle()
    val user by LiveTvPreferences.userState.collectAsStateWithLifecycle()
    val settings by LiveTvPreferences.settings.collectAsStateWithLifecycle()
    val movies by produceState(emptyList<VodCategory>()) { value = runCatching { OnDemandRepository.categories(VodKind.MOVIE) }.getOrDefault(emptyList()) }
    val series by produceState(emptyList<VodCategory>()) { value = runCatching { OnDemandRepository.categories(VodKind.SERIES) }.getOrDefault(emptyList()) }
    val groups = remember(state) { state.sections.map { it.group }.filter { !it.special && it.id != ChannelGroup.ALL } }
    DialogSurface(onDismissRequest = onDismiss, title = stringResource(Res.string.livetv_parental_locked_groups), message = stringResource(Res.string.livetv_parental_locked_groups_description)) {
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4)) {
            item { Text(stringResource(Res.string.livetv_parental_channel_groups), style = MaterialTheme.typography.labelMedium, color = tokens.colors.textMuted) }
            items(groups, key = { "g:" + it.id }) { g ->
                val locked = g.id in user.lockedGroups
                DialogOption(text = g.title, selected = locked, onClick = { LiveTvPreferences.setGroupLocked(g.id, !locked); if (locked) ParentalControls.relock() })
            }
            val vod = movies + series
            if (vod.isNotEmpty()) item { Text(stringResource(Res.string.livetv_parental_vod_groups), style = MaterialTheme.typography.labelMedium, color = tokens.colors.textMuted, modifier = Modifier.padding(top = NuvioTokens.Space.s8)) }
            items(vod, key = { "v:" + it.uid }) { c ->
                val id = "vod:" + c.uid
                val locked = id in user.lockedGroups
                DialogOption(text = OnDemandGroups.name(c, user, settings), description = stringResource(if (c.kind == VodKind.MOVIE) Res.string.livetv_ui_movies else Res.string.livetv_ui_series), selected = locked, onClick = { LiveTvPreferences.setGroupLocked(id, !locked); if (locked) ParentalControls.relock() })
            }
        }
        DialogButtons { DialogButton(text = stringResource(Res.string.action_close), style = DialogButtonStyle.Primary, onClick = onDismiss) }
    }
}

/** A backup-file failure in plain words. [reading]: the file was being read or imported, not written. */
private suspend fun backupFileErrorText(e: Throwable, reading: Boolean): String = when {
    e is BackupFilePickerUnavailable -> getString(Res.string.livetv_file_error_unavailable)
    e is BackupFileException -> when (e.reason) {
        BackupFileException.Reason.NOT_JSON, BackupFileException.Reason.NOT_BACKUP -> getString(Res.string.livetv_file_error_not_backup)
        BackupFileException.Reason.NEWER_FORMAT -> getString(Res.string.livetv_file_error_newer)
        BackupFileException.Reason.NO_SOURCES -> getString(Res.string.livetv_file_error_no_sources)
    }
    !reading -> getString(Res.string.livetv_file_error_write)
    e is IllegalArgumentException || e is IllegalStateException -> e.message?.takeIf { it.isNotBlank() }
        ?.let { getString(Res.string.livetv_file_error_import, it) } ?: getString(Res.string.livetv_file_error_read)
    else -> getString(Res.string.livetv_file_error_read)
}

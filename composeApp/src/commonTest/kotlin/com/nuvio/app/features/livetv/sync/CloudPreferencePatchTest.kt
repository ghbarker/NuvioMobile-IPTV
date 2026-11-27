package com.nuvio.app.features.livetv.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloudPreferencePatchTest {
    private fun data(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test
    fun settingsAloneAreUsableAndEmptyProviderRecordsDoNotClearExistingSetup() {
        val edit = data("""{"seek_thumbs_enabled":{"t":"b","v":false},"vpn_profiles_v1":{"t":"str","v":"{}"},"playlists":{"t":"str","v":"[]"},"usenet_sources_v1_p2":{"t":"str","v":"{}"}}""")
        val current = data("""{"playlists":{"t":"str","v":"existing playlist"},"vpn_profiles_v1":{"t":"str","v":"existing VPN"},"usenet_sources_v1_p2":{"t":"str","v":"existing source"},"epg_offset_minutes":{"t":"i","v":15}}""")
        val choices = CloudPreferencePatch.onlyChoices(edit)
        assertTrue(CloudPreferencePatch.hasChoices(choices, false))
        assertEquals(listOf("seek_thumbs_enabled"), choices.keys.toList())
        val restored = CloudPreferencePatch.overlay(current, edit)
        for (name in current.keys) assertEquals(current[name], restored[name])
        assertEquals(choices["seek_thumbs_enabled"], restored["seek_thumbs_enabled"])
    }

    @Test
    fun configuredSharedFolderSurvivesASettingsOnlyRoundTripAndAnEmptyFolderDoesNotClearIt() {
        val withFolder = data("""{"network_folder":{"t":"str","v":"{\"host\":\"fixture.invalid\",\"share\":\"recordings\"}"}}""")
        val blank = data("""{"network_folder":{"t":"str","v":""}}""")
        assertTrue(CloudPreferencePatch.hasChoices(withFolder, false))
        assertFalse(CloudPreferencePatch.hasChoices(blank, false))
        val saved = CloudPreferencePatch.onlyChoices(withFolder)
        assertEquals(withFolder.toString(), CloudPreferencePatch.overlay(saved, blank).toString())
    }

    @Test
    fun cleanInstallMarkersAndMalformedEntriesDoNotTriggerFirstTimeConflict() {
        val defaults = data("""{"group_playlist_headings":{"t":"b","v":true},"mobile_setting_v1_update_channel":{"t":"str","v":"stable"},"profile_seeded_p2":{"t":"b","v":true},"mobile_setting_v1_layout_group_playlist_headings":{"t":"b","v":true},"seek_thumbs_enabled":{"t":"b","v":"wrong type"}}""")
        assertFalse(CloudPreferencePatch.hasChoices(defaults, false))
        assertTrue(CloudPreferencePatch.hasChoices(data("""{"group_playlist_headings":{"t":"b","v":false}}"""), false))
    }
}

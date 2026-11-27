package com.nuvio.app.features.livetv

import com.nuvio.app.features.livetv.home.HomeLiveRow
import com.nuvio.app.features.livetv.model.LiveTvSettings
import com.nuvio.app.features.livetv.model.LiveUserState
import com.nuvio.app.features.livetv.ondemand.OnDemandGroups
import com.nuvio.app.features.livetv.ondemand.VodCategory
import com.nuvio.app.features.livetv.ondemand.VodKind
import com.nuvio.app.features.livetv.parental.ParentalControls
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnDemandGroupCleanupTest {
    private val movie = VodCategory("provider", VodKind.MOVIE, "1", "| UK | Movies HD", 4)
    private val series = VodCategory("provider", VodKind.SERIES, "1", "| UK | Series HD", 3)
    private val settings = LiveTvSettings(nameRemovals = "UK, HD", cleanVodGroupNames = true)

    @Test fun cleanupIsOptInAndSeparateFromLiveGroupCleanup() {
        assertFalse(LiveTvSettings().cleanVodGroupNames)
        assertEquals(movie.name, OnDemandGroups.name(movie, LiveUserState(), settings.copy(cleanVodGroupNames = false, cleanGroupNames = true)))
        assertEquals("Movies", OnDemandGroups.name(movie, LiveUserState(), settings.copy(cleanGroupNames = false)))
        assertEquals("Series", OnDemandGroups.name(series, LiveUserState(), settings))
    }

    @Test fun changingWordsAndDisablingRestoreRawNamesWithoutMutatingCategories() {
        val user = LiveUserState()
        assertEquals("Movies", OnDemandGroups.name(movie, user, settings))
        assertEquals("Movies HD", OnDemandGroups.name(movie, user, settings.copy(nameRemovals = "UK")))
        assertEquals(movie.name, OnDemandGroups.name(movie, user, settings.copy(cleanVodGroupNames = false)))
        assertEquals("| UK | Movies HD", movie.name)
        assertEquals("movie:provider:1", movie.uid)
    }

    @Test fun explicitRenamesWinEvenWhenEqualToCurrentAutomaticName() {
        val user = LiveUserState(vodGroupNames = mapOf(movie.uid to "Movies", series.uid to "My UK Series HD"))
        assertEquals("Movies", OnDemandGroups.name(movie, user, settings))
        assertEquals("Movies", OnDemandGroups.name(movie, user, settings.copy(nameRemovals = "Movies")))
        assertEquals("Movies", OnDemandGroups.name(movie, user, settings.copy(cleanVodGroupNames = false)))
        assertEquals("My UK Series HD", OnDemandGroups.name(series, user, settings))
    }

    @Test fun emptyResultFallsBackAndWordsInsideNamesAreProtected() {
        assertEquals("HD", OnDemandGroups.displayName("HD", null, "HD", true))
        assertEquals("USA Network", OnDemandGroups.displayName("USA Network", null, "US", true))
        assertEquals("Movies", OnDemandGroups.displayName("| UK | Movies HD", "  ", "UK, HD", true))
    }

    @Test fun identicalDisplayNamesKeepStableIdentityOrderAndVisibility() {
        val other = movie.copy(id = "2", name = "Movies HD")
        val user = LiveUserState(vodGroupOrder = listOf(other.uid, movie.uid))
        val visible = OnDemandGroups.visible(listOf(movie, other, series), user)
        assertEquals(listOf(other.uid, movie.uid, series.uid), visible.map { it.uid })
        assertEquals(listOf("Movies", "Movies", "Series"), visible.map { OnDemandGroups.name(it, user, settings) })
        assertEquals(listOf(other.name, movie.name, series.name), visible.map { it.name })
        assertEquals(listOf(other, series), OnDemandGroups.visible(listOf(movie, other, series), user.copy(vodHiddenCategories = setOf(movie.uid))))
    }

    @Test fun parentalRulesStillSeeRawProviderNameDespiteCleanupAndRename() {
        val adult = movie.copy(name = "Adult | Movies HD")
        val user = LiveUserState(vodGroupNames = mapOf(adult.uid to "Movies"))
        val s = settings.copy(nameRemovals = "Adult, HD", parentalPin = "1234", lockAdultContent = true)
        val raw = OnDemandGroups.visible(listOf(adult), user).single()
        assertEquals("Movies", OnDemandGroups.name(raw, user, s))
        assertTrue(ParentalControls.isLocked("vod:${raw.uid}", raw.name, s, user.lockedGroups))
    }

    @Test fun homeRowsRetainRawLabelAcrossSerializationAndCleanupChanges() {
        val row = HomeLiveRow.vod(HomeLiveRow.VOD_GROUP_PREFIX + movie.uid, movie.name)
        val restored = HomeLiveRow.decode(HomeLiveRow.encode(listOf(row))).single()
        assertEquals(movie.name, restored.label)
        assertEquals("Movies", OnDemandGroups.displayName(restored.label, null, settings.nameRemovals, true))
        assertEquals(movie.name, OnDemandGroups.displayName(restored.label, null, settings.nameRemovals, false))
        assertEquals(movie.name, restored.label)
    }
}

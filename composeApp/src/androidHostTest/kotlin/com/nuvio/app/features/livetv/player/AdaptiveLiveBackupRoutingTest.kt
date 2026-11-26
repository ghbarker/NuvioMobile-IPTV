package com.nuvio.app.features.livetv.player

import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.data.LiveTvRepository
import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.livetv.model.LiveTvSettings
import com.nuvio.app.features.livetv.model.LiveUserState
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AdaptiveLiveBackupRoutingTest {
    private var settings = LiveTvSettings(adaptiveLiveBufferEnabled = true, autoBackupChannel = true)
    private val inventory = MutableStateFlow<List<LiveChannel>>(emptyList())
    private val primary = channel("original")

    @Before fun setup() {
        mockkObject(LiveTvPreferences, LiveTvRepository)
        every { LiveTvPreferences.currentSettings() } answers { settings }
        every { LiveTvPreferences.currentUserState() } returns LiveUserState(lockedGroups = setOf("locked"))
        every { LiveTvPreferences.currentPlaylists() } returns emptyList()
        justRun { LiveTvPreferences.recordWatched(any(), any()) }
        every { LiveTvRepository.displayChannels } returns inventory
    }

    @After fun cleanup() { unmockkObject(LiveTvRepository, LiveTvPreferences) }

    @Test fun orderedHandoffSkipsLockedBrokenAndDuplicateStreamsAndHardErrorContinuesTheChain() = runTest {
        settings = settings.copy(parentalPin = "1234", backupSourceOrder = "locked\nbroken\nalias\nfirst\nsecond")
        val alias = channel("alias").copy(url = primary.url)
        val locked = channel("locked").copy(groupId = "locked")
        val broken = channel("broken").copy(url = "stalker://unresolved")
        val first = channel("first")
        val second = channel("second")
        inventory.value = listOf(primary, second, alias, locked, broken, first)
        LiveTvPlayback.switchTo(primary)
        val pick = LiveTvPlayback.backupForStalledChannel(primary.url, primary.key) { true }
        assertEquals(first.key, pick?.channel?.key)
        assertEquals(primary.key, pick?.switchedFrom?.key)
        assertNull(LiveTvPlayback.backupForStalledChannel(primary.url, primary.key) { true })
        // Both the vanished locator and the original source's alias remain consumed if the first backup fails.
        inventory.value = listOf(primary, locked, alias, broken, first, second)
        assertEquals(second.key, LiveTvPlayback.fallbackFor(first.url)?.channel?.key)
        assertNull(LiveTvPlayback.backupForStalledChannel(second.url, second.key) { true })
    }

    @Test fun disabledStaleOrExhaustedBackupDoesNotChangeTheWorkingChannel() = runTest {
        val next = channel("next")
        inventory.value = listOf(primary, next)
        LiveTvPlayback.switchTo(primary)
        settings = settings.copy(autoBackupChannel = false)
        assertNull(LiveTvPlayback.backupForStalledChannel(primary.url, primary.key) { true })
        settings = settings.copy(autoBackupChannel = true, adaptiveLiveBufferEnabled = false)
        assertNull(LiveTvPlayback.backupForStalledChannel(primary.url, primary.key) { true })
        settings = settings.copy(adaptiveLiveBufferEnabled = true)
        var checks = 0
        assertNull(LiveTvPlayback.backupForStalledChannel(primary.url, primary.key) { ++checks <= 2 })
        assertEquals(primary.key, LiveTvPlayback.currentKey)
        inventory.value = listOf(primary, next.copy(url = primary.url), channel("other").copy(name = "Movie Network"))
        assertNull(LiveTvPlayback.backupForStalledChannel(primary.url, primary.key) { true })
        assertNull(LiveTvPlayback.backupForStalledChannel(primary.url, "wrong key") { true })
        assertEquals(primary.key, LiveTvPlayback.currentKey)
    }

    private fun channel(id: String) = LiveChannel(
        key = id, sourceId = id, sourceName = id, name = "Example Sports HD",
        tvgId = null, tvgName = null, logo = null, groupId = "sports", group = "Sports",
        number = 1, url = "https://example.invalid/live/$id.ts",
    )
}

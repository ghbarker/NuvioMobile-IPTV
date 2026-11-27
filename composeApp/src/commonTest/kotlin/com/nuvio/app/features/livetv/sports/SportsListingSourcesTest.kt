package com.nuvio.app.features.livetv.sports

import com.nuvio.app.features.livetv.data.LiveTvBackup
import com.nuvio.app.features.livetv.data.LiveTvKeys
import com.nuvio.app.features.livetv.data.LiveTvPreferencesStore
import com.nuvio.app.features.livetv.data.PrefValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SportsListingSourcesTest {
    private val moto = "guide/motogp"

    @Test fun legacyFollowsKeepTheCombinedListAndTheirOriginalJsonShape() {
        val original = SportsFollows(pins = listOf(moto), words = listOf("Rider"),
            reminders = listOf(SportsReminder("race", "Sprint", 1000)), setUp = true, order = listOf("l:$moto"))
        val raw = original.toJson()
        assertFalse(JSONObject(raw).has("listingSources"))
        val restored = SportsFollows.fromJson(raw)
        assertEquals(original, restored)
        assertEquals(SportsListingSource.BOTH, restored.listingSource(moto))
        assertEquals(raw, restored.toJson())
        assertEquals(listOf(moto), SportsFollows.fromJson("""{"leagues":["$moto"]}""").pins)
    }

    @Test fun sourceChoicesRoundTripWithoutRewritingLeagueOrFollowIdentities() {
        val follows = SportsFollows(pins = listOf(moto), order = listOf("l:$moto"))
            .withListingSource(moto, SportsListingSource.SCHEDULE)
            .withListingSource("future/competition", SportsListingSource.GUIDE)
        val restored = SportsFollows.fromJson(follows.toJson())
        assertEquals(follows, restored)
        assertEquals("SCHEDULE", JSONObject(follows.toJson()).getJSONObject("listingSources").getString(moto))
        assertEquals(listOf("l:$moto"), SportsRows.rowKeys(restored))
        val reset = follows.withListingSource(moto, SportsListingSource.BOTH)
            .withListingSource("future/competition", SportsListingSource.BOTH)
        assertTrue(reset.listingSources.isEmpty())
        assertFalse(JSONObject(reset.toJson()).has("listingSources"))
        assertSame(follows, follows.withListingSource(" ", SportsListingSource.GUIDE))
    }

    @Test fun malformedSourceValuesDoNotDiscardOtherFollowData() {
        val raw = """{"pins":["$moto"],"words":["Rider"],"listingSources":{"$moto":"SCHEDULE","new/id":"GUIDE","blank":"UNKNOWN","number":1,"object":{},"":"GUIDE"}}"""
        val restored = SportsFollows.fromJson(raw)
        assertEquals(listOf(moto), restored.pins)
        assertEquals(listOf("Rider"), restored.words)
        assertEquals(mapOf(moto to SportsListingSource.SCHEDULE, "new/id" to SportsListingSource.GUIDE), restored.listingSources)
        for (bad in listOf("[]", "7", "null", "\"GUIDE\"")) {
            val invalid = SportsFollows.fromJson("""{"pins":["$moto"],"listingSources":$bad}""")
            assertEquals(listOf(moto), invalid.pins)
            assertEquals(SportsListingSource.BOTH, invalid.listingSource(moto))
        }
    }

    @Test fun portableBackupRetainsRootAndProfileSpecificSourceChoices() {
        val root = SportsFollows(pins = listOf(moto)).withListingSource(moto, SportsListingSource.SCHEDULE)
        val child = root.withListingSource(moto, SportsListingSource.GUIDE)
        val raw = mapOf(
            LiveTvKeys.sportsMine to PrefValue.Str(root.toJson()),
            "${LiveTvKeys.sportsMine}_p3" to PrefValue.Str(child.toJson()),
        )
        val exported = LiveTvBackup.exportForSync(raw)
        val imported = LiveTvBackup.importInto(emptyMap(), exported).data
        assertEquals(raw, imported)
        assertEquals(root, SportsFollows.fromJson((imported.getValue(LiveTvKeys.sportsMine) as PrefValue.Str).value))
        assertEquals(child, SportsFollows.fromJson((imported.getValue("${LiveTvKeys.sportsMine}_p3") as PrefValue.Str).value))
    }

    @Test fun editingAnotherProfilesFollowsKeepsBothSourceChoices() = runTest {
        val store = object : LiveTvPreferencesStore() {
            init { installPersistence(load = { null }, save = {}) }
        }
        store.editSportsMine { SportsFollows(pins = listOf(moto)).withListingSource(moto, SportsListingSource.SCHEDULE).toJson() }
        store.onProfileChanged(3)
        store.editSportsMine { SportsFollows.fromJson(it).withListingSource(moto, SportsListingSource.GUIDE).toJson() }
        store.editSportsMine { SportsFollows.fromJson(it).copy(words = listOf("Rider")).toJson() }
        assertEquals(SportsListingSource.GUIDE, SportsFollows.fromJson(store.sportsMineJson.first()).listingSource(moto))
        store.onProfileChanged(1)
        val root = SportsFollows.fromJson(store.sportsMineJson.first())
        assertEquals(SportsListingSource.SCHEDULE, root.listingSource(moto))
        assertTrue(root.words.isEmpty())
    }
}

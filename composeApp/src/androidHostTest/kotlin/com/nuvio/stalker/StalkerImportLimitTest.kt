package com.nuvio.stalker

import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StalkerImportLimitTest {
    private val config = StalkerConfig("https://portal.example.test/c/", "02:00:00:00:00:01")
    private val limits = StalkerLimits(maxPages = 2, maxItems = 8, maxResponseChars = 256)

    @Test fun oversizedBulkTransportFallsBackToTheCompletePagedCatalogue(): Unit = runBlocking {
        val portal = Portal { action, page ->
            when (action) {
                "get_all_channels" -> throw StalkerException(StalkerError.TOO_LARGE)
                "get_ordered_list" -> responsePage(if (page == 1) listOf("one", "two") else listOf("three"), total = 3, size = 2)
                else -> error("Unexpected synthetic operation")
            }
        }
        assertEquals(listOf("one", "two", "three"), portal.client().liveChannels().map { it.id })
        assertEquals(1, portal.bulkCalls)
        assertEquals(listOf(1, 2), portal.pages)
        assertEquals(1, portal.handshakes)
    }

    @Test fun oversizedBulkProtocolBodyFallsBackWithoutRaisingTheResponseLimit(): Unit = runBlocking {
        val portal = Portal { action, page ->
            when (action) {
                "get_all_channels" -> oversized()
                "get_ordered_list" -> responsePage(if (page == 1) listOf("one", "two") else listOf("three"), total = 3, size = 2)
                else -> error("Unexpected synthetic operation")
            }
        }
        assertEquals(3, portal.client().liveChannels().size)
        assertEquals(listOf(1, 2), portal.pages)
        assertEquals(1, portal.handshakes)
    }

    @Test fun anOversizedPageStillFailsAfterBulkFallback(): Unit = runBlocking {
        for (transportFailure in listOf(false, true)) {
            val portal = Portal { action, page ->
                when {
                    action == "get_all_channels" -> throw StalkerException(StalkerError.TOO_LARGE)
                    page == 1 -> responsePage(listOf("one", "two"), total = 3, size = 2)
                    transportFailure -> throw StalkerException(StalkerError.TOO_LARGE)
                    else -> oversized()
                }
            }
            assertEquals(StalkerError.TOO_LARGE, assertFailsWith<StalkerException> { portal.client().liveChannels() }.error)
            assertEquals(listOf(1, 2), portal.pages)
            assertEquals(1, portal.bulkCalls)
        }
    }

    @Test fun aKnownCatalogueBeyondThePageBudgetFailsBeforeFurtherRequests(): Unit = runBlocking {
        val portal = Portal { action, _ ->
            if (action == "get_all_channels") throw StalkerException(StalkerError.UNSUPPORTED)
            responsePage(listOf("one", "two"), total = 3, size = 2)
        }
        assertEquals(StalkerError.TOO_LARGE, assertFailsWith<StalkerException> {
            portal.client(limits.copy(maxPages = 1)).liveChannels()
        }.error)
        assertEquals(listOf(1), portal.pages)
    }

    @Test fun fullPagesWithoutACompletionSignalCannotPublishAtTheBudget(): Unit = runBlocking {
        for (size in listOf(null, 2)) {
            val portal = Portal { action, page ->
                if (action == "get_all_channels") throw StalkerException(StalkerError.UNSUPPORTED)
                responsePage(if (page == 1) listOf("one", "two") else listOf("three", "four"), size = size)
            }
            assertEquals(StalkerError.TOO_LARGE, assertFailsWith<StalkerException> { portal.client().liveChannels() }.error)
            assertEquals(listOf(1, 2), portal.pages)
        }
    }

    @Test fun aKnownCatalogueCompleteOnTheLastAllowedPageSucceeds(): Unit = runBlocking {
        for (budget in listOf(1, 2)) {
            val portal = Portal { action, page ->
                if (action == "get_all_channels") throw StalkerException(StalkerError.UNSUPPORTED)
                responsePage(if (page == 1) listOf("one", "two") else listOf("three", "four"), total = budget * 2, size = 2)
            }
            assertEquals(budget * 2, portal.client(limits.copy(maxPages = budget)).liveChannels().size)
            assertEquals((1..budget).toList(), portal.pages)
        }
    }

    @Test fun anUnknownCatalogueWithAShortLastAllowedPageSucceeds(): Unit = runBlocking {
        val portal = Portal { action, page ->
            if (action == "get_all_channels") throw StalkerException(StalkerError.UNSUPPORTED)
            responsePage(if (page == 1) listOf("one", "two") else listOf("three"), size = 2)
        }
        assertEquals(3, portal.client().liveChannels().size)
        assertEquals(listOf(1, 2), portal.pages)
    }

    @Test fun anUnknownCatalogueWithAnEmptyLastAllowedPageSucceeds(): Unit = runBlocking {
        val portal = Portal { action, page ->
            if (action == "get_all_channels") throw StalkerException(StalkerError.UNSUPPORTED)
            responsePage(if (page == 1) listOf("one", "two") else emptyList())
        }
        assertEquals(2, portal.client().liveChannels().size)
        assertEquals(listOf(1, 2), portal.pages)
    }

    @Test fun totalItemLimitsStillFailInsteadOfPublishingPartialResults(): Unit = runBlocking {
        for (knownTotal in listOf(false, true)) {
            val portal = Portal { action, page ->
                if (action == "get_all_channels") throw StalkerException(StalkerError.TOO_LARGE)
                responsePage(if (page == 1) listOf("one", "two") else listOf("three"), total = if (knownTotal) 3 else null, size = 2)
            }
            assertEquals(StalkerError.TOO_LARGE, assertFailsWith<StalkerException> {
                portal.client(limits.copy(maxItems = 2)).liveChannels()
            }.error)
            assertEquals(if (knownTotal) listOf(1) else listOf(1, 2), portal.pages)
        }
    }

    @Test fun duplicateOrIncompletePagesStillFailAfterBulkFallback(): Unit = runBlocking {
        for (duplicate in listOf(false, true)) {
            val portal = Portal { action, page ->
                if (action == "get_all_channels") throw StalkerException(StalkerError.TOO_LARGE)
                responsePage(if (page == 1) listOf("one", "two") else if (duplicate) listOf("one") else emptyList(), total = 3, size = 2)
            }
            assertEquals(StalkerError.PAGINATION_FAILED, assertFailsWith<StalkerException> { portal.client().liveChannels() }.error)
            assertEquals(listOf(1, 2), portal.pages)
        }
    }

    @Test fun cancellationDoesNotStartPagination(): Unit = runBlocking {
        val portal = Portal { action, _ ->
            assertEquals("get_all_channels", action)
            throw CancellationException("Synthetic cancellation")
        }
        assertFailsWith<CancellationException> { portal.client().liveChannels() }
        assertTrue(portal.pages.isEmpty())
        assertEquals(1, portal.bulkCalls)
    }

    @Test fun cancellationDuringFallbackDoesNotReturnAPartialCatalogue(): Unit = runBlocking {
        val portal = Portal { action, page ->
            when {
                action == "get_all_channels" -> throw StalkerException(StalkerError.TOO_LARGE)
                page == 1 -> responsePage(listOf("one", "two"), total = 3, size = 2)
                else -> throw CancellationException("Synthetic cancellation")
            }
        }
        assertFailsWith<CancellationException> { portal.client().liveChannels() }
        assertEquals(listOf(1, 2), portal.pages)
        assertEquals(1, portal.bulkCalls)
    }

    @Test fun authenticationRefusalDoesNotStartPagination(): Unit = runBlocking {
        val portal = Portal { action, _ ->
            assertEquals("get_all_channels", action)
            StalkerHttpResponse(401, "")
        }
        assertEquals(StalkerError.AUTHENTICATION_FAILED, assertFailsWith<StalkerException> { portal.client().liveChannels() }.error)
        assertTrue(portal.pages.isEmpty())
        assertEquals(2, portal.bulkCalls)
        assertEquals(2, portal.handshakes)
    }

    @Test fun bulkItemCountLimitDoesNotBecomeAPaginationFallback(): Unit = runBlocking {
        val portal = Portal { action, _ ->
            assertEquals("get_all_channels", action)
            responsePage(listOf("one", "two", "three"))
        }
        assertEquals(StalkerError.TOO_LARGE, assertFailsWith<StalkerException> {
            portal.client(limits.copy(maxItems = 2)).liveChannels()
        }.error)
        assertTrue(portal.pages.isEmpty())
        assertEquals(1, portal.bulkCalls)
    }

    private fun responsePage(ids: List<String>, total: Int? = null, size: Int? = null): StalkerHttpResponse {
        val rows = ids.joinToString(",") { "{\"id\":\"$it\",\"cmd\":\"ffrt4://$it\"}" }
        val metadata = buildString {
            total?.let { append(",\"total_items\":$it") }
            size?.let { append(",\"max_page_items\":$it") }
        }
        return StalkerHttpResponse(200, "{\"js\":{\"data\":[$rows]$metadata}}")
    }

    private fun oversized() = StalkerHttpResponse(200, "{\"js\":{\"data\":[],\"padding\":\"${"x".repeat(limits.maxResponseChars)}\"}}")

    private inner class Portal(private val reply: (action: String, page: Int?) -> StalkerHttpResponse) : StalkerTransport {
        var handshakes = 0
        var bulkCalls = 0
        val pages = mutableListOf<Int>()

        override suspend fun get(url: String, headers: Map<String, String>): StalkerHttpResponse {
            val request = Url(url)
            return when (val action = request.parameters["action"].orEmpty()) {
                "handshake" -> { handshakes++; StalkerHttpResponse(200, "{\"js\":{\"token\":\"fixture-token\"}}") }
                "get_profile" -> StalkerHttpResponse(200, "{\"js\":{\"id\":\"fixture-account\",\"status\":0}}")
                "get_all_channels" -> { bulkCalls++; reply(action, null) }
                "get_ordered_list" -> {
                    val page = request.parameters["p"]!!.toInt()
                    pages += page
                    reply(action, page)
                }
                else -> error("Unexpected synthetic operation")
            }
        }

        fun client(boundaries: StalkerLimits = limits) = StalkerClient(config, this, { 0L }, limits = boundaries)
    }
}

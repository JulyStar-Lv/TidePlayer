package io.github.julystar.musicapp.core.data

import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.StoredCredential
import io.github.julystar.musicapp.source.api.OpenListBrowsePage
import io.github.julystar.musicapp.source.api.OpenListBrowsePageResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OpenListBrowseClientTest {
    @Test
    fun browse401ReloginsOnceAndKeepsAccountsIsolated() = runTest {
        val accountA = SourceAccountId("storage:51")
        val accountB = SourceAccountId("storage:52")
        val auth = FakeAuthTransport()
        val browse = FakeBrowseTransport().also { it.unauthorizedFirst = true }
        val materials = OpenListAccountMaterialReader { account ->
            OpenListAccountMaterial(
                account,
                if (account == accountA) "https://a.example" else "https://b.example",
                StoredCredential("user", "password", false),
                null,
            )
        }
        val client = OpenListSessionBrowseClient(
            materials,
            OpenListSessionManager(materials, auth),
            browse,
        )

        assertEquals(OpenListBrowsePageResult.Success(OpenListBrowsePage(emptyList(), 0)), client.listPage(accountA, "/", 1, 10))
        assertEquals(2, auth.loginCalls)
        assertEquals(listOf("https://a.example" to "token-a-1", "https://a.example" to "token-a-2"), browse.requests)

        assertEquals(OpenListBrowsePageResult.Success(OpenListBrowsePage(emptyList(), 0)), client.listPage(accountB, "/", 1, 10))
        assertEquals(listOf("https://b.example" to "token-b-3"), browse.requests.drop(2))
    }

    @Test
    fun second401IsUnauthorizedAndDoesNotStartThirdRequest() = runTest {
        val account = SourceAccountId("storage:53")
        val auth = FakeAuthTransport()
        val browse = FakeBrowseTransport().also { it.alwaysUnauthorized = true }
        val materials = OpenListAccountMaterialReader {
            OpenListAccountMaterial(account, "https://a.example", StoredCredential("u", "p", false), null)
        }
        val client = OpenListSessionBrowseClient(materials, OpenListSessionManager(materials, auth), browse)

        assertEquals(
            OpenListBrowsePageResult.Failure(io.github.julystar.musicapp.source.api.SourceListFailureReason.Unauthorized),
            client.listPage(account, "/", 1, 10),
        )
        assertEquals(2, browse.requests.size)
        assertEquals(2, auth.loginCalls)
    }

    @Test
    fun browseCancellationPropagates() = runTest {
        val account = SourceAccountId("storage:54")
        val auth = FakeAuthTransport()
        val browse = FakeBrowseTransport().also { it.cancel = true }
        val materials = OpenListAccountMaterialReader {
            OpenListAccountMaterial(account, "https://a.example", StoredCredential("u", "p", false), null)
        }
        val client = OpenListSessionBrowseClient(materials, OpenListSessionManager(materials, auth), browse)

        assertFailsWith<CancellationException> { client.listPage(account, "/", 1, 10) }
    }

    private class FakeAuthTransport : OpenListAuthTransport {
        var loginCalls = 0
        override suspend fun login(endpoint: String, username: String, password: String, otpCode: String): String {
            loginCalls++
            return if (endpoint.startsWith("https://a")) "token-a-$loginCalls" else "token-b-$loginCalls"
        }

        override suspend fun validateSession(endpoint: String, token: String) = Unit
    }

    private class FakeBrowseTransport : OpenListBrowseTransport {
        var unauthorizedFirst = false
        var alwaysUnauthorized = false
        var cancel = false
        val requests = mutableListOf<Pair<String, String>>()
        private var calls = 0

        override suspend fun listPage(
            endpoint: String,
            token: String,
            path: String,
            page: Int,
            pageSize: Int,
        ): OpenListBrowsePage {
            if (cancel) throw CancellationException("cancel")
            calls++
            requests += endpoint to token
            if (alwaysUnauthorized || (unauthorizedFirst && calls == 1)) {
                throw OpenListAuthTransportException(OpenListAuthTransportFailureReason.Unauthorized)
            }
            return OpenListBrowsePage(emptyList(), 0)
        }
    }
}

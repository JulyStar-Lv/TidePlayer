package io.github.julystar.musicapp.core.data

import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.NeedsReauthenticationException
import io.github.julystar.musicapp.core.domain.model.StoredCredential
import io.github.julystar.musicapp.source.api.OpenListSourceConfiguration
import io.github.julystar.musicapp.source.api.SourceAuthFailureReason
import io.github.julystar.musicapp.source.api.SourceAuthResult
import io.github.julystar.musicapp.source.storage.LegacyPlaybackSession
import io.github.julystar.musicapp.source.storage.OpenListPlaybackSessionCreator
import io.github.julystar.musicapp.source.storage.SessionManagerOpenListPlaybackSessionFactory
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OpenListSessionManagerTest {
    private val account = SourceAccountId("storage:41")

    @Test
    fun passwordLoginValidatesAndCachesOnlyInMemory() = runTest {
        val transport = FakeTransport()
        val manager = manager(transport)
        assertEquals(
            SourceAuthResult.Success,
            manager.authenticate(
                OpenListSourceConfiguration(
                    accountId = account,
                    alias = "OpenList",
                    address = "https://openlist.example",
                    username = "alice",
                    password = "password",
                    isGuest = false,
                ),
            ),
        )
        assertEquals(1, transport.loginCalls)

        manager.authorized(account) { token ->
            assertEquals("token-1", token)
        }
        assertEquals(1, transport.loginCalls)
        val resetManager = manager(transport)
        resetManager.authorized(account) { token -> assertEquals("token-2", token) }
        assertEquals(2, transport.loginCalls)
    }

    @Test
    fun otpChallengeIsTypedAndNeverPrinted() = runTest {
        val transport = FakeTransport().also { it.loginFailure = OpenListAuthTransportFailureReason.OtpRequired }
        val result = manager(transport).authenticate(
            OpenListSourceConfiguration(
                accountId = account,
                alias = "OpenList",
                address = "https://openlist.example",
                username = "alice",
                password = "password",
                isGuest = false,
            ),
        )
        assertEquals(SourceAuthResult.Failure(SourceAuthFailureReason.OtpRequired), result)
        assertTrue("password" !in result.toString())
    }

    @Test
    fun unauthorizedRetriesPasswordOnceButGuestDoesNotLoop() = runTest {
        val transport = FakeTransport()
        val manager = manager(transport)
        manager.authenticate(
            OpenListSourceConfiguration(
                accountId = account,
                alias = "OpenList",
                address = "https://openlist.example",
                username = "alice",
                password = "password",
                isGuest = false,
            ),
        )
        var calls = 0
        manager.authorized(account) {
            calls++
            if (calls == 1) throw OpenListAuthTransportException(OpenListAuthTransportFailureReason.Unauthorized)
        }
        assertEquals(2, calls)
        assertEquals(2, transport.loginCalls)

        val guest = SourceAccountId("storage:42")
        val guestManager = OpenListSessionManager(
            OpenListAccountMaterialReader {
                OpenListAccountMaterial(guest, "https://openlist.example", StoredCredential("", "", true), null)
            },
            transport,
        )
        guestManager.authenticate(
            OpenListSourceConfiguration(
                accountId = guest,
                alias = "Guest",
                address = "https://openlist.example",
                username = "",
                password = "",
                isGuest = true,
            ),
        )
        assertFailsWith<NeedsReauthenticationException> {
            guestManager.authorized(guest) {
                throw OpenListAuthTransportException(OpenListAuthTransportFailureReason.Unauthorized)
            }
        }
    }

    @Test
    fun secondUnauthorizedIsBoundedAndOtpOnReloginNeedsReauthentication() = runTest {
        val transport = FakeTransport()
        val manager = manager(transport)
        manager.authenticate(
            OpenListSourceConfiguration(accountId = account, alias = "x", address = "https://x", username = "a", password = "p", isGuest = false),
        )
        assertFailsWith<NeedsReauthenticationException> {
            manager.authorized(account) {
                throw OpenListAuthTransportException(OpenListAuthTransportFailureReason.Unauthorized)
            }
        }
        assertEquals(2, transport.loginCalls)

        val otpTransport = FakeTransport()
        val otpManager = manager(otpTransport)
        otpManager.authenticate(
            OpenListSourceConfiguration(accountId = account, alias = "x", address = "https://x", username = "a", password = "p", isGuest = false),
        )
        otpTransport.loginFailure = OpenListAuthTransportFailureReason.OtpRequired
        assertFailsWith<NeedsReauthenticationException> {
            otpManager.authorized(account) {
                throw OpenListAuthTransportException(OpenListAuthTransportFailureReason.Unauthorized)
            }
        }
    }

    @Test
    fun cancellationPropagates() = runTest {
        val transport = FakeTransport()
        transport.cancel = true
        val manager = manager(transport)
        assertFailsWith<CancellationException> {
            manager.authenticate(
                OpenListSourceConfiguration(accountId = account, alias = "x", address = "https://x", username = "a", password = "p", isGuest = false),
            )
        }
    }

    @Test
    fun blankLoginTokenIsRejectedWithoutSessionValidation() = runTest {
        val transport = FakeTransport().also { it.blankToken = true }
        val result = manager(transport).authenticate(
            OpenListSourceConfiguration(accountId = account, alias = "x", address = "https://x", username = "a", password = "p", isGuest = false),
        )
        assertEquals(SourceAuthResult.Failure(SourceAuthFailureReason.Unavailable), result)
        assertEquals(0, transport.validateCalls)
    }

    @Test
    fun failedInteractiveAuthenticationKeepsPreviouslyValidatedSession() = runTest {
        val transport = FakeTransport()
        val manager = manager(transport)
        val configuration = OpenListSourceConfiguration(
            accountId = account,
            alias = "x",
            address = "https://x",
            username = "a",
            password = "p",
            isGuest = false,
        )
        assertEquals(SourceAuthResult.Success, manager.authenticate(configuration))
        transport.loginFailure = OpenListAuthTransportFailureReason.Unauthorized
        assertEquals(SourceAuthResult.Failure(SourceAuthFailureReason.Unauthorized), manager.authenticate(configuration))
        var received = ""
        manager.authorized(account) { received = it }
        assertEquals("token-1", received)
        assertEquals(2, transport.loginCalls)
    }

    @Test
    fun persistedOtpRequiresReauthenticationAfterProcessReset() = runTest {
        val transport = FakeTransport()
        val manager = OpenListSessionManager(
            OpenListAccountMaterialReader {
                OpenListAccountMaterial(
                    account,
                    "https://x",
                    StoredCredential("alice", "password", false),
                    "{\"requiresOtp\":true}",
                )
            },
            transport,
        )
        assertFailsWith<NeedsReauthenticationException> {
            manager.authorized(account) { error("must not authorize") }
        }
        assertEquals(0, transport.loginCalls)
        assertTrue("password" !in OpenListAccountMaterial(account, "https://x?token=secret", StoredCredential("a", "password", false), null).toString())
        assertTrue("https://x?token=secret" !in OpenListAccountMaterial(account, "https://x?token=secret", StoredCredential("a", "password", false), null).toString())
    }

    @Test
    fun validatedAuthorizedReauthenticatesOnceWhenCachedValidationIsUnauthorized() = runTest {
        val transport = FakeTransport()
        val manager = manager(transport)
        val configuration = OpenListSourceConfiguration(
            accountId = account,
            alias = "x",
            address = "https://x",
            username = "a",
            password = "p",
            isGuest = false,
        )
        assertEquals(SourceAuthResult.Success, manager.authenticate(configuration))
        transport.validationFailures = 1

        var token = ""
        manager.validatedAuthorized(account) { _, currentToken -> token = currentToken }

        assertEquals("token-2", token)
        assertEquals(2, transport.loginCalls)
        assertEquals(4, transport.validateCalls)
    }

    @Test
    fun validatedAuthorizedSecondUnauthorizedIsBoundedAndGuestDoesNotRelogin() = runTest {
        val transport = FakeTransport()
        val manager = manager(transport)
        assertEquals(
            SourceAuthResult.Success,
            manager.authenticate(
                OpenListSourceConfiguration(
                    accountId = account,
                    alias = "x",
                    address = "https://x",
                    username = "a",
                    password = "p",
                    isGuest = false,
                ),
            ),
        )
        transport.validationFailures = 2
        assertFailsWith<NeedsReauthenticationException> {
            manager.validatedAuthorized(account) { _, _ -> error("must not run") }
        }
        assertEquals(2, transport.loginCalls)

        val guest = SourceAccountId("storage:guest")
        val guestTransport = FakeTransport()
        val guestManager = OpenListSessionManager(
            OpenListAccountMaterialReader {
                OpenListAccountMaterial(guest, "https://guest", StoredCredential("", "", true), null)
            },
            guestTransport,
        )
        assertEquals(
            SourceAuthResult.Success,
            guestManager.authenticate(
                OpenListSourceConfiguration(
                    accountId = guest,
                    alias = "guest",
                    address = "https://guest",
                    username = "",
                    password = "",
                    isGuest = true,
                ),
            ),
        )
        guestTransport.validationFailures = 1
        assertFailsWith<NeedsReauthenticationException> {
            guestManager.validatedAuthorized(guest) { _, _ -> error("must not run") }
        }
        assertEquals(0, guestTransport.loginCalls)
    }

    @Test
    fun playbackFactoryUsesValidatedEndpointTokenAndExactCanonicalPath() = runTest {
        val transport = FakeTransport()
        val manager = manager(transport)
        assertEquals(
            SourceAuthResult.Success,
            manager.authenticate(
                OpenListSourceConfiguration(
                    accountId = account,
                    alias = "x",
                    address = "https://openlist.example",
                    username = "alice",
                    password = "password",
                    isGuest = false,
                ),
            ),
        )
        val rawPath = "/音乐/type3 %25 #? \\"
        var creatorCalls = 0
        val expectedSession = object : LegacyPlaybackSession {
            override val url = "http://127.0.0.1:1234/stream.bin"
            override val mimeType = "audio/flac"
            override fun shutdown() = Unit
        }
        val factory = SessionManagerOpenListPlaybackSessionFactory(
            sessionManager = manager,
            creator = OpenListPlaybackSessionCreator { endpoint, token, path ->
                creatorCalls += 1
                assertEquals("https://openlist.example", endpoint)
                assertEquals("token-1", token)
                assertEquals(rawPath, path)
                expectedSession
            },
        )

        assertTrue(factory.create(account, rawPath) === expectedSession)
        assertEquals(1, creatorCalls)
        assertEquals(1, transport.loginCalls)
        assertEquals(2, transport.validateCalls)
    }

    private fun manager(transport: FakeTransport) = OpenListSessionManager(
        OpenListAccountMaterialReader {
            OpenListAccountMaterial(
                account,
                "https://openlist.example",
                StoredCredential("alice", "password", false),
                null,
            )
        },
        transport,
    )

    private class FakeTransport : OpenListAuthTransport {
        var loginCalls = 0
        var loginFailure: OpenListAuthTransportFailureReason? = null
        var cancel = false
        var blankToken = false
        var validateCalls = 0
        var validationFailures = 0

        override suspend fun login(endpoint: String, username: String, password: String, otpCode: String): String {
            loginCalls++
            if (cancel) throw CancellationException("cancel")
            loginFailure?.let { throw OpenListAuthTransportException(it) }
            return if (blankToken) "" else "token-$loginCalls"
        }

        override suspend fun validateSession(endpoint: String, token: String) {
            validateCalls++
            if (validationFailures > 0) {
                validationFailures--
                throw OpenListAuthTransportException(OpenListAuthTransportFailureReason.Unauthorized)
            }
        }
    }
}

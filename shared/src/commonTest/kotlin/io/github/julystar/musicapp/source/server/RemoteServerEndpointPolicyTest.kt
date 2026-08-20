package io.github.julystar.musicapp.source.server

import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import uniffi.app_backend.RemoteMusicException

class RemoteServerEndpointPolicyTest {
    @Test
    fun timeoutAndConnectivityFallbackExactlyOnceAndReturnSuccessfulEndpoint() {
        listOf<RemoteMusicException>(
            RemoteMusicException.Timeout(),
            RemoteMusicException.Connectivity(),
        ).forEach { primaryFailure ->
            val endpoints = mutableListOf<String>()
            val result = RemoteServerEndpointPolicy.execute(
                "https://primary.example",
                "https://secondary.example/",
            ) { endpoint ->
                endpoints += endpoint
                if (endpoint.contains("primary")) throw primaryFailure
                "success"
            }
            assertEquals("success", result.value)
            assertEquals("https://secondary.example", result.endpoint)
            assertEquals(listOf("https://primary.example", "https://secondary.example"), endpoints)
        }
    }

    @Test
    fun typedNonConnectivityFailuresAndCancellationNeverFallback() {
        val failures = listOf<Throwable>(
            RemoteMusicException.Unauthorized(),
            RemoteMusicException.PermissionDenied(),
            RemoteMusicException.NotFound(),
            RemoteMusicException.HttpFailure(),
            RemoteMusicException.InvalidResponse(),
            RemoteMusicException.ProtocolFailure(),
            RemoteMusicException.Unavailable(),
            CancellationException("cancelled"),
        )
        failures.forEach { failure ->
            val endpoints = mutableListOf<String>()
            val caught = assertFailsWith<Throwable> {
                RemoteServerEndpointPolicy.execute(
                    "https://primary.example",
                    "https://secondary.example",
                ) { endpoint ->
                    endpoints += endpoint
                    throw failure
                }
            }
            assertSame(failure, caught)
            assertEquals(listOf("https://primary.example"), endpoints)
        }
    }

    @Test
    fun invalidSameOrMissingSecondaryDoesNotRetry() {
        listOf(
            null,
            "",
            "https://primary.example/",
            "https://user:password@secondary.example",
            "https://secondary.example/?token=secret",
            "ftp://secondary.example",
        ).forEach { secondary ->
            var calls = 0
            assertFailsWith<RemoteMusicException.Timeout> {
                RemoteServerEndpointPolicy.execute("https://primary.example", secondary) {
                    calls++
                    throw RemoteMusicException.Timeout()
                }
            }
            assertEquals(1, calls)
        }
    }

    @Test
    fun secondaryFailureTerminatesWithoutThirdAttempt() {
        val secondaryFailure = RemoteMusicException.HttpFailure()
        val endpoints = mutableListOf<String>()
        val caught = assertFailsWith<RemoteMusicException.HttpFailure> {
            RemoteServerEndpointPolicy.execute(
                "https://primary.example",
                "https://secondary.example",
            ) { endpoint ->
                endpoints += endpoint
                if (endpoint.contains("primary")) throw RemoteMusicException.Connectivity()
                throw secondaryFailure
            }
        }
        assertSame(secondaryFailure, caught)
        assertEquals(listOf("https://primary.example", "https://secondary.example"), endpoints)
    }
}

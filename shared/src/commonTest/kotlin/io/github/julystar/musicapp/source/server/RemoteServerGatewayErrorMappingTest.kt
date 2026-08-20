package io.github.julystar.musicapp.source.server

import io.github.julystar.musicapp.source.api.SourceAuthFailureReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import uniffi.app_backend.RemoteMusicException

class RemoteServerGatewayErrorMappingTest {
    @Test
    fun typedRemoteErrorsMapToAuthReasonsWithoutMessageParsing() {
        val cases = listOf(
            RemoteMusicException.InvalidAddress() to SourceAuthFailureReason.InvalidAddress,
            RemoteMusicException.Timeout() to SourceAuthFailureReason.Timeout,
            RemoteMusicException.Connectivity() to SourceAuthFailureReason.Unavailable,
            RemoteMusicException.Unauthorized() to SourceAuthFailureReason.Unauthorized,
            RemoteMusicException.PermissionDenied() to SourceAuthFailureReason.PermissionDenied,
            RemoteMusicException.NotFound() to SourceAuthFailureReason.NotFound,
            RemoteMusicException.HttpFailure() to SourceAuthFailureReason.Unavailable,
            RemoteMusicException.InvalidResponse() to SourceAuthFailureReason.Unavailable,
            RemoteMusicException.ProtocolFailure() to SourceAuthFailureReason.Unavailable,
            RemoteMusicException.Unavailable() to SourceAuthFailureReason.Unavailable,
        )

        cases.forEach { (error, expected) ->
            assertEquals(expected, error.toRemoteAuthFailureReason())
            assertFalse(error.toString().contains("fixed-password"))
            assertFalse(error.toString().contains("fixed-server-message"))
        }
        assertEquals(
            SourceAuthFailureReason.Unavailable,
            Throwable("401 fixed-server-message").toRemoteAuthFailureReason(),
        )
    }
}

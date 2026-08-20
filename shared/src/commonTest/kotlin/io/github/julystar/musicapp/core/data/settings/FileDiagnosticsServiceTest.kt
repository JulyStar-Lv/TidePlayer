package io.github.julystar.musicapp.core.data.settings

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileDiagnosticsServiceTest {
    @Test
    fun redactsCredentialsTokensAndAuthorizationValues() {
        val input = "https://user:secret@example.test/dav?access_token=token-value " +
            "refresh_token=refresh-value Authorization: Bearer bearer-value " +
            "api_key=api-value Cookie: sid=cookie-value"

        val redacted = input.redactSensitiveData()

        assertFalse(redacted.contains("user:secret"))
        assertFalse(redacted.contains("token-value"))
        assertFalse(redacted.contains("refresh-value"))
        assertFalse(redacted.contains("bearer-value"))
        assertFalse(redacted.contains("api-value"))
        assertFalse(redacted.contains("cookie-value"))
        assertTrue(redacted.contains("https://***:***@example.test"))
        assertTrue(redacted.contains("<REDACTED_QUERY>"))
        assertTrue(redacted.contains("refresh_token=***"))
    }

    @Test
    fun redactsJsonHeadersMixedCaseAndOtpVariantsButKeepsOrdinaryCode() {
        val cases = listOf(
            "{\"accessToken\":\"fixture-one\",\"refresh_token\":\"fixture-two\"," +
                "\"otpCode\":\"fixture-three\"}",
            "api-key: fixture-four one-time-password=fixture-five",
            "Authorization: Custom fixture-six\nX-Emby-Token: fixture-seven",
            "Cookie: sid=fixture-eight\nSet-Cookie: session=fixture-nine",
            "at call (File.kt:4): PASSWORD=fixture-ten Access_Token:fixture-eleven",
            "https://user:fixture-twelve@example.test/path",
            "https://example.test/path?ordinary=value&token=fixture-thirteen",
            "prefix Authorization: Bearer fixture-fourteen suffix",
            "prefix Authorization=Basic fixture-fifteen suffix",
            "Authorization=fixture-sixteen",
            """{"password":"prefix\"fixture-seventeen"}""",
            """{"webdav_password":"fixture-eighteen"}""",
            "smbPassword=fixture-nineteen",
            "plugin_config_secret: fixture-twenty",
        )

        cases.forEachIndexed { index, input ->
            val redacted = input.redactSensitiveData()
            assertFalse("fixture-" in redacted, "redaction case $index failed")
        }

        assertTrue("code=42" in "code=42 status: ok".redactSensitiveData())
    }

    @Test
    fun redactsLoopbackPlaybackCapabilityFromStackLikeText() {
        val playbackUrl =
            "http://127.0.0.1:45678/media/fixture-capability-token/stream.flac"
        val redacted = "player failed at $playbackUrl\n  at prepare(Player.kt:42)"
            .redactSensitiveData()

        assertFalse("fixture-capability-token" in redacted)
        assertFalse(playbackUrl in redacted)
        assertTrue("http://127.0.0.1:45678/<REDACTED_PLAYBACK_PATH>" in redacted)
    }

    @Test
    fun diagnosticsFailureMessageIsRedactedBeforeUiExposure() {
        val message = IllegalStateException(
            "Authorization: Bearer fixture-export-failure-sensitive",
        ).redactedDiagnosticsFailureMessage()

        assertFalse("fixture-export-failure-sensitive" in message)
        assertTrue("***" in message)
    }
}

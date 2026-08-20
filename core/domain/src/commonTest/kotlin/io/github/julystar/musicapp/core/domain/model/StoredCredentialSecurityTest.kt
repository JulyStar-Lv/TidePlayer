package io.github.julystar.musicapp.core.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoredCredentialSecurityTest {
    @Test
    fun toStringRedactsSecret() {
        val rendered = StoredCredential("listener", TEST_SECRET, false).toString()

        assertFalse(TEST_SECRET in rendered)
        assertTrue("secret=<redacted>" in rendered)
    }

    @Test
    fun oneDriveDriveResultRedactsRefreshedToken() {
        val rendered = OneDriveDriveListResult(
            drives = listOf(OneDriveDriveInfo("drive", "Music")),
            refreshedToken = TEST_SECRET,
        ).toString()

        assertFalse(TEST_SECRET in rendered)
        assertTrue("refreshedToken=<redacted>" in rendered)
    }

    private companion object {
        const val TEST_SECRET = "credential-fixture-sensitive-value"
    }
}

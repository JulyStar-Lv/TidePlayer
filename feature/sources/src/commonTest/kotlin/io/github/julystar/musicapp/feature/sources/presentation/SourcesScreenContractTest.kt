package io.github.julystar.musicapp.feature.sources.presentation

import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.StorageAccountInfo
import io.github.julystar.musicapp.source.api.BuiltInSourceIds
import kotlinx.collections.immutable.toPersistentList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class SourcesScreenContractTest {
    @Test
    fun addSourceRemainsInRenderItemsForEmptySingleAndMultipleSourceLists() {
        val cases = listOf(
            emptyList(),
            listOf("WebDAV"),
            listOf("WebDAV", "SMB", "OneDrive"),
            listOf("WebDAV", "SMB", "Navidrome", "OpenSubsonic", "Emby", "OpenList"),
        )

        cases.forEachIndexed { caseIndex, sourceTypes ->
            val sources = sourceTypes.mapIndexed { sourceIndex, sourceType ->
                source(sourceType, caseIndex, sourceIndex)
            }
            val rendered = SourcesState(sources.toPersistentList()).renderItems()

            assertEquals(sources.size + 1, rendered.size, "render item count for case $caseIndex")
            assertIs<SourcesRenderItem.AddSource>(rendered.first(), "first render item for case $caseIndex")
            assertEquals(
                sources.map(SourceAccountUi::id),
                rendered.filterIsInstance<SourcesRenderItem.Source>().map { it.account.id },
                "source order for case $caseIndex",
            )
            assertEquals(
                1,
                rendered.count { it == SourcesRenderItem.AddSource },
                "Add Source count for case $caseIndex",
            )
        }
    }

    @Test
    fun sourceCardEndpointKeepsOnlySafeSchemeHostAndOptionalPort() {
        assertEquals(
            "https://music.example:8443",
            sanitizeSourceCardEndpoint(
                "HTTPS://user:pass@Music.Example:8443/library?access_token=query-secret#jwt-fragment",
            ),
        )
        assertEquals(
            "https://music.example",
            sanitizeSourceCardEndpoint("https://music.example/path?token=query-secret"),
        )
        assertEquals(
            "https://music.example",
            sanitizeSourceCardEndpoint("https://music.example/#Authorization=Bearer-fragment-secret"),
        )
        assertEquals(
            "http://[2001:db8::1]:8080",
            sanitizeSourceCardEndpoint("http://user:pass@[2001:DB8::1]:8080/music?token=secret"),
        )
    }

    @Test
    fun sourceCardEndpointFailsClosedForMalformedOrUnsafeValues() {
        listOf(
            "not a url",
            "javascript://example.test/steal",
            "https:///missing-host",
            "https://[2001:db8::1",
            "https://2001:db8::1/path",
            "https://example.test:0/path",
            "https://example.test:65536/path",
            "https://token%40example.test/path",
        ).forEach { value ->
            assertNull(sanitizeSourceCardEndpoint(value), value)
        }
    }

    @Test
    fun productionMapperNeverCarriesRawEndpointOrForbiddenDisplayValues() {
        val rawEndpoint =
            "https://listener:password-secret@Music.Example:8443/library" +
                "?access_token=token-secret&Cookie=cookie-secret&otp=otp-secret&jwt=jwt-secret" +
                "#Authorization=Bearer-secret"
        val mapped = StorageAccountInfo(
            accountId = SourceAccountId("storage:17"),
            sourceId = BuiltInSourceIds.OpenList,
            isLocal = false,
            isOneDrive = false,
            title = rawEndpoint,
            subtitle = rawEndpoint,
            musicCount = 0,
        ).toSourceAccountUi(isSyncing = false)

        assertEquals("OpenList", mapped.title)
        assertEquals("https://music.example:8443", mapped.safeEndpoint)
        listOf(
            "listener",
            "password-secret",
            "access_token",
            "token-secret",
            "Cookie",
            "cookie-secret",
            "otp",
            "otp-secret",
            "jwt",
            "Authorization",
            "jwt-secret",
            "Bearer-secret",
            "/library",
        ).forEach { forbidden ->
            assertFalse(forbidden in mapped.toString(), forbidden)
        }

        val malformed = StorageAccountInfo(
            accountId = SourceAccountId("storage:18"),
            sourceId = BuiltInSourceIds.Navidrome,
            isLocal = false,
            isOneDrive = false,
            title = "Bearer token-secret without a URL",
            subtitle = "Bearer token-secret without a URL",
            musicCount = 0,
        ).toSourceAccountUi(isSyncing = false)
        assertEquals("Navidrome", malformed.title)
        assertNull(malformed.safeEndpoint)
        assertFalse("token-secret" in malformed.toString())
    }

    private fun source(sourceType: String, caseIndex: Int, sourceIndex: Int) = SourceAccountUi(
        id = SourceAccountId("account-$caseIndex-$sourceIndex"),
        title = sourceType,
        safeEndpoint = null,
        sourceType = sourceType,
        musicCount = 0,
    )
}

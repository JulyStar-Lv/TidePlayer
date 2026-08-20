package io.github.julystar.musicapp.feature.sources.presentation

import io.github.julystar.musicapp.core.domain.model.SourceEditorDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceEditorContractTest {
    @Test
    fun selectorContainsEightSourcesAndLocalIsUiOnly() {
        assertEquals(
            listOf(
                "Local",
                "WebDav",
                "Smb",
                "OneDrive",
                "Navidrome",
                "OpenSubsonic",
                "Emby",
                "OpenList",
            ),
            sourceSelectorOptions.map { it.name },
        )
        assertEquals(null, SourceSelectorOption.Local.editorType)
        assertEquals(SourceEditorType.OpenList, SourceSelectorOption.OpenList.editorType)
        assertFalse(SourceEditorType.entries.any { it.name == "Local" })
    }

    @Test
    fun providerFieldsExposeOnlySafeRequiredInputs() {
        val advanced = setOf(
            SourceEditorField.SecondaryAddress,
            SourceEditorField.StreamBitRate,
            SourceEditorField.DownloadBitRate,
            SourceEditorField.CoverArtSize,
            SourceEditorField.RemoteWrite,
        )
        assertTrue(sourceEditorVisibleFields(SourceEditorType.Navidrome).containsAll(advanced))
        assertTrue(sourceEditorVisibleFields(SourceEditorType.OpenSubsonic).containsAll(advanced))
        assertEquals(listOf(0, 128, 192, 256, 320), sourceEditorBitRateChoices)
        assertEquals(listOf(256, 512, 768, 1024), sourceEditorCoverArtSizeChoices)

        val emby = sourceEditorVisibleFields(SourceEditorType.Emby, isCreated = false)
        assertTrue(SourceEditorField.Password in emby)
        assertTrue(SourceEditorField.ServerName in emby)
        assertTrue(SourceEditorField.ConnectedAccount in emby)
        assertFalse(SourceEditorField.StreamBitRate in emby)

        val openListGuest = sourceEditorVisibleFields(
            SourceEditorType.OpenList,
            isGuest = true,
            showOtp = true,
        )
        assertFalse(SourceEditorField.Username in openListGuest)
        assertFalse(SourceEditorField.Password in openListGuest)
        assertFalse(SourceEditorField.Otp in openListGuest)

        val openListOtp = sourceEditorVisibleFields(
            SourceEditorType.OpenList,
            showOtp = true,
            isCreated = false,
        )
        assertTrue(SourceEditorField.Otp in openListOtp)
        assertTrue(SourceEditorField.LibraryRoot in openListOtp)
        assertTrue(SourceEditorField.entries.none { field ->
            field.name.contains("cookie", ignoreCase = true) ||
                field.name.contains("token", ignoreCase = true) ||
                field.name.contains("header", ignoreCase = true) ||
                field.name.contains("authorization", ignoreCase = true) ||
                field.name.contains("useragent", ignoreCase = true)
        })
    }

    @Test
    fun dedicatedStatesMapAdvancedAndReadonlyValuesWithoutSecrets() {
        val state = sourceEditorState(
            draft = SourceEditorDraft(
                storageType = SourceEditorType.OpenSubsonic,
                alias = "OpenSub",
                address = "https://primary.example",
                username = "listener",
                secret = "password-secret",
                externalAccountId = "user-17",
                secondaryBaseUrl = "https://secondary.example",
                streamMaxBitRate = 192,
                downloadMaxBitRate = 320,
                coverArtSize = 768,
                remoteWriteEnabled = true,
            ),
            title = "OpenSub",
            musicCount = 0u,
            validation = SourceEditorValidation(),
            removeDialogOpen = false,
            testResult = SourceConnectionTestStatus.None,
            connectedServerName = "Verified server",
        )

        assertEquals(192, state.remoteServer.streamMaxBitRate)
        assertEquals(320, state.remoteServer.downloadMaxBitRate)
        assertEquals(768, state.remoteServer.coverArtSize)
        assertTrue(state.remoteServer.remoteWriteEnabled)
        assertFalse(SourceEditorState().remoteServer.remoteWriteEnabled)
        assertFalse(state.toString().contains("password-secret"))
        assertFalse(SourceEditorAction.OpenListOtpChanged("otp-secret").toString().contains("otp-secret"))
    }

    @Test
    fun passwordAndOtpActionsNeverPrintTheirValues() {
        val actions = listOf(
            SourceEditorAction.WebDavPasswordChanged(TEST_SECRET),
            SourceEditorAction.RemoteServerPasswordChanged(TEST_SECRET),
            SourceEditorAction.OpenListPasswordChanged(TEST_SECRET),
            SourceEditorAction.SmbPasswordChanged(TEST_SECRET),
            SourceEditorAction.OpenListOtpChanged(TEST_SECRET),
        )

        actions.forEach { action ->
            assertFalse(TEST_SECRET in action.toString())
            assertTrue("<redacted>" in action.toString())
        }
    }

    @Test
    fun oneDriveOAuthEventNeverPrintsAuthorizationUrl() {
        val event = SourceEditorEvent.OpenOneDriveOAuth(
            "https://login.example/authorize?state=$TEST_SECRET&code_challenge=$TEST_SECRET",
        )

        assertFalse(TEST_SECRET in event.toString())
        assertFalse("login.example" in event.toString())
        assertTrue("<redacted>" in event.toString())
    }

    private companion object {
        const val TEST_SECRET = "source-action-fixture-sensitive-value"
    }
}

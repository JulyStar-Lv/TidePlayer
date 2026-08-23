package io.github.julystar.musicapp.feature.sources.presentation

import io.github.julystar.musicapp.core.domain.model.SourceEditorDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceEditorContractTest {
    @Test
    fun selectorGroupsContainEachSourceOnceInProductOrderAndMapExactActions() {
        assertEquals(
            listOf(
                SourceSelectorGroup.FileAndNetworkStorage,
                SourceSelectorGroup.MusicServers,
            ),
            sourceSelectorSections.map { it.group },
        )
        assertEquals(
            listOf(
                SourceSelectorOption.Local,
                SourceSelectorOption.WebDav,
                SourceSelectorOption.Smb,
                SourceSelectorOption.OneDrive,
                SourceSelectorOption.OpenList,
            ),
            sourceSelectorSections[0].options,
        )
        assertEquals(
            listOf(
                SourceSelectorOption.Navidrome,
                SourceSelectorOption.OpenSubsonic,
                SourceSelectorOption.Emby,
            ),
            sourceSelectorSections[1].options,
        )
        val allOptions = sourceSelectorSections.flatMap(SourceSelectorSection::options)
        assertEquals(8, allOptions.size)
        assertEquals(8, allOptions.toSet().size)
        assertEquals(null, SourceSelectorOption.Local.editorType)
        assertEquals(
            SourceEditorAction.ImportLocalLibraryFolder,
            SourceSelectorOption.Local.selectionAction(),
        )
        assertEquals(SourceEditorType.OpenList, SourceSelectorOption.OpenList.editorType)
        assertEquals(SourceSelectorGroup.FileAndNetworkStorage, SourceSelectorOption.OpenList.group)
        listOf(
            SourceSelectorOption.WebDav to SourceEditorType.WebDav,
            SourceSelectorOption.Smb to SourceEditorType.Smb,
            SourceSelectorOption.OneDrive to SourceEditorType.OneDrive,
            SourceSelectorOption.OpenList to SourceEditorType.OpenList,
            SourceSelectorOption.Navidrome to SourceEditorType.Navidrome,
            SourceSelectorOption.OpenSubsonic to SourceEditorType.OpenSubsonic,
            SourceSelectorOption.Emby to SourceEditorType.Emby,
        ).forEach { (option, editorType) ->
            assertEquals(SourceEditorAction.ChangeType(editorType), option.selectionAction())
        }
        assertFalse(SourceEditorType.entries.any { it.name == "Local" })
    }

    @Test
    fun providerFieldsExposeOnlySafeRequiredInputs() {
        assertEquals(
            listOf(
                SourceEditorField.Alias,
                SourceEditorField.Address,
                SourceEditorField.Username,
                SourceEditorField.Password,
            ),
            RemoteServerConfigSection.Basic.fields,
        )
        assertEquals(
            listOf(
                SourceEditorField.SecondaryAddress,
                SourceEditorField.StreamBitRate,
                SourceEditorField.DownloadBitRate,
                SourceEditorField.CoverArtSize,
                SourceEditorField.RemoteWrite,
            ),
            RemoteServerConfigSection.Advanced.fields,
        )
        assertTrue(RemoteServerConfigSection.Basic.initiallyExpanded)
        assertFalse(RemoteServerConfigSection.Advanced.initiallyExpanded)
        val remoteServerFields = RemoteServerConfigSection.entries.flatMap { it.fields }.toSet()
        assertEquals(remoteServerFields, sourceEditorVisibleFields(SourceEditorType.Navidrome))
        assertEquals(remoteServerFields, sourceEditorVisibleFields(SourceEditorType.OpenSubsonic))
        assertEquals(listOf(0, 128, 192, 256, 320), sourceEditorBitRateChoices)
        assertEquals(listOf(256, 512, 768, 1024), sourceEditorCoverArtSizeChoices)

        val newEmby = embyEditorContract(isCreated = true)
        assertEquals(
            listOf(
                SourceEditorField.Alias,
                SourceEditorField.Address,
                SourceEditorField.Username,
                SourceEditorField.Password,
                SourceEditorField.SecondaryAddress,
            ),
            newEmby.editableFields,
        )
        assertEquals(emptyList(), newEmby.readOnlyFields)
        assertEquals("", newEmby.authenticationInputInitialValue)
        assertFalse(newEmby.showAuthenticationRetentionHint)
        assertEquals(newEmby.visibleFields, sourceEditorVisibleFields(SourceEditorType.Emby))

        val existingEmby = embyEditorContract(isCreated = false)
        assertEquals(newEmby.editableFields, existingEmby.editableFields)
        assertEquals(
            listOf(SourceEditorField.ServerName, SourceEditorField.ConnectedAccount),
            existingEmby.readOnlyFields,
        )
        assertEquals("", existingEmby.authenticationInputInitialValue)
        assertTrue(existingEmby.showAuthenticationRetentionHint)
        assertEquals(
            existingEmby.visibleFields,
            sourceEditorVisibleFields(SourceEditorType.Emby, isCreated = false),
        )
        assertFalse(SourceEditorField.StreamBitRate in existingEmby.visibleFields)

        val openListDefault = openListEditorContract(isGuest = false, showOtp = false)
        assertEquals(
            listOf(
                SourceEditorField.Alias,
                SourceEditorField.Address,
                SourceEditorField.Guest,
                SourceEditorField.Username,
                SourceEditorField.Password,
            ),
            openListDefault.visibleFields,
        )
        assertFalse(SourceEditorField.Otp in openListDefault.visibleFields)
        assertEquals("", openListDefault.otpInputInitialValue)
        assertTrue(openListDefault.otpMemoryOnly)

        val openListChallenged = openListEditorContract(isGuest = false, showOtp = true)
        assertEquals(openListDefault.visibleFields + SourceEditorField.Otp, openListChallenged.visibleFields)
        assertEquals(
            OpenListForbiddenField.entries.toSet(),
            openListChallenged.forbiddenFields,
        )
        assertEquals(
            setOf(
                OpenListForbiddenField.Cookie,
                OpenListForbiddenField.Authorization,
                OpenListForbiddenField.AccessToken,
                OpenListForbiddenField.RefreshToken,
                OpenListForbiddenField.Jwt,
                OpenListForbiddenField.UserAgent,
                OpenListForbiddenField.HttpHeaders,
                OpenListForbiddenField.BaiduCookie,
                OpenListForbiddenField.QuarkCookie,
            ),
            openListChallenged.forbiddenFields,
        )

        val openListGuest = openListEditorContract(isGuest = true, showOtp = true)
        assertEquals(
            listOf(SourceEditorField.Alias, SourceEditorField.Address, SourceEditorField.Guest),
            openListGuest.visibleFields,
        )
        assertFalse(SourceEditorField.Username in openListGuest.visibleFields)
        assertFalse(SourceEditorField.Password in openListGuest.visibleFields)
        assertFalse(SourceEditorField.Otp in openListGuest.visibleFields)

        val openListExistingFields = sourceEditorVisibleFields(
            SourceEditorType.OpenList,
            showOtp = true,
            isCreated = false,
        )
        assertTrue(SourceEditorField.Otp in openListExistingFields)
        assertTrue(SourceEditorField.LibraryRoot in openListExistingFields)
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

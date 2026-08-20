package io.github.julystar.musicapp.core.domain.repository

import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.SourceConnectionTestStatus
import io.github.julystar.musicapp.core.domain.model.SourceEditorDraft
import io.github.julystar.musicapp.core.domain.model.SourceEditorStorageState
import io.github.julystar.musicapp.core.domain.model.OneDriveDriveListResult
import io.github.julystar.musicapp.core.domain.model.StorageAccountInfo
import io.github.julystar.musicapp.core.domain.model.StoredCredential
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface StorageRepository {
    val storageAccounts: StateFlow<List<StorageAccountInfo>>
    val onRemoveStorageEvent: SharedFlow<Unit>
    val oauthRefreshToken: StateFlow<String>
    suspend fun reload()
    suspend fun startOneDriveOAuth(): String
    suspend fun upsertSource(draft: SourceEditorDraft): SourceAccountId
    suspend fun upsertOpenListSource(draft: SourceEditorDraft, otpCode: String): SourceAccountId =
        error("OpenList OTP persistence is not supported by this repository")
    suspend fun loadEditorState(id: Long): SourceEditorStorageState?
    suspend fun testSource(draft: SourceEditorDraft): SourceConnectionTestStatus
    suspend fun testOpenListSource(
        draft: SourceEditorDraft,
        otpCode: String,
    ): SourceConnectionTestStatus = error("OpenList OTP testing is not supported by this repository")
    suspend fun listOneDriveDriveInfos(refreshToken: String): OneDriveDriveListResult
    suspend fun updateOneDriveRefreshTokenByAccountId(accountId: SourceAccountId, refreshToken: String)
    fun findStorageAccountByAccountId(accountId: SourceAccountId): StorageAccountInfo?
    suspend fun loadCredentialByAccountId(accountId: SourceAccountId): StoredCredential?
    suspend fun setAccountRootPath(accountId: SourceAccountId, rootPath: String)
    suspend fun listAccountRootPaths(accountId: SourceAccountId): List<String> = emptyList()
    suspend fun removeByAccountId(accountId: SourceAccountId)
}

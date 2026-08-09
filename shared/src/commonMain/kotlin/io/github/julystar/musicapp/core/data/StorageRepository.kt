package io.github.julystar.musicapp.core.data

import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.SourceId
import io.github.julystar.musicapp.core.domain.model.StorageAccountInfo
import io.github.julystar.musicapp.core.domain.model.OneDriveDriveInfo
import io.github.julystar.musicapp.core.domain.model.OneDriveDriveListResult
import io.github.julystar.musicapp.core.domain.model.SourceConnectionTestStatus
import io.github.julystar.musicapp.core.domain.model.SourceEditorDraft
import io.github.julystar.musicapp.core.domain.model.SourceEditorStorageState
import io.github.julystar.musicapp.core.domain.model.SourceEditorType
import io.github.julystar.musicapp.core.domain.model.storageSourceAccountId
import io.github.julystar.musicapp.core.domain.model.toStorageRouteIdOrNull
import io.github.julystar.musicapp.core.domain.repository.StorageRepository
import io.github.julystar.musicapp.singleton.Bridge
import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.database.SourceAccountDao
import io.github.julystar.musicapp.database.SourceAccountEntity
import io.github.julystar.musicapp.database.SourceAccountSummaryRow

import io.github.julystar.musicapp.platform.currentTimeMillis
import io.github.julystar.musicapp.core.data.security.CredentialStore
import io.github.julystar.musicapp.core.domain.model.StoredCredential
import io.github.julystar.musicapp.source.api.BuiltInSourceIds
import io.github.julystar.musicapp.source.api.SmbSourceConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import uniffi.app_backend.ArgUpsertStorage
import uniffi.app_backend.Storage
import uniffi.app_backend.StorageConnectionTestResult
import uniffi.app_backend.OneDriveDriveList
import uniffi.app_backend.ctExchangeOnedriveCode
import uniffi.app_backend.ctListOnedriveDrives
import uniffi.app_backend.ctReleaseStorageBackend
import uniffi.app_backend.ctStartOnedriveOauth
import uniffi.app_backend.ctTestStorage
import uniffi.app_backend.StorageId
import uniffi.app_backend.StorageType
import uniffi.app_backend.ctEmbyLogin
import uniffi.app_backend.ctEmbyRequest
import uniffi.app_backend.ctSubsonicRequest
import io.github.julystar.musicapp.source.smb.toSmbAddress


class StorageRepositoryImpl(
    private val bridge: Bridge,
    private val scope: CoroutineScope,
    private val sourceAccountDao: SourceAccountDao,
    private val credentialStore: CredentialStore,
) : StorageRepository {
    private val _oauthRefreshToken = MutableStateFlow("")
    private val _storages = MutableStateFlow(listOf<Storage>())
    private val _storageAccounts = MutableStateFlow(listOf<StorageAccountInfo>())
    private val _preRemoveStorageEvent = MutableSharedFlow<StorageId>()
    private val _onRemoveStorageEvent = MutableSharedFlow<Unit>()

    override val oauthRefreshToken = _oauthRefreshToken.asStateFlow()
    val storages = _storages.asStateFlow()
    override val storageAccounts = _storageAccounts.asStateFlow()
    val preRemoveStorageEvent = _preRemoveStorageEvent.asSharedFlow()
    override val onRemoveStorageEvent = _onRemoveStorageEvent.asSharedFlow()

    init {
        scope.launch {
            ensureLocalStorage()
        }
        scope.launch {
            sourceAccountDao.observeSummaries().collect { summaries ->
                _storages.value = summaries.filter { summary ->
                    summary.account.providerType in FILE_PROVIDER_TYPES
                }.map { summary ->
                    val entity = summary.account
                    entity.toStorage(password = "")
                }
                _storageAccounts.value = summaries.map(SourceAccountSummaryRow::toStorageAccountInfo)
            }
        }
    }

    override suspend fun startOneDriveOAuth(): String {
        val session = ctStartOnedriveOauth()
        credentialStore.save(
            PENDING_ONEDRIVE_OAUTH_CREDENTIAL_ID,
            StoredCredential(
                username = session.state,
                secret = session.codeVerifier,
                isAnonymous = false,
            )
        )
        return session.authorizationUrl
    }

    suspend fun updateRefreshToken(code: String, state: String): Boolean {
        val pending = credentialStore.load(PENDING_ONEDRIVE_OAUTH_CREDENTIAL_ID)
            ?: return false
        if (pending.username != state) return false

        credentialStore.delete(PENDING_ONEDRIVE_OAUTH_CREDENTIAL_ID)
        val token = bridge.run {
            ctExchangeOnedriveCode(it, code, pending.secret)
        } ?: return false
        _oauthRefreshToken.value = token
        return true
    }

    suspend fun test(arg: ArgUpsertStorage): StorageConnectionTestResult {
        return bridge.runRaw { ctTestStorage(it, arg) }
    }

    suspend fun listOneDriveDrives(refreshToken: String): OneDriveDriveList {
        return bridge.runRaw { ctListOnedriveDrives(refreshToken) }
    }

    suspend fun updateOneDriveRefreshToken(storageId: StorageId, refreshToken: String) {
        val current = credentialStore.load(storageId.value) ?: return
        if (current.secret == refreshToken) return
        val rotated = current.copy(secret = refreshToken)
        credentialStore.save(storageId.value, rotated)
    }

    fun receiveOneDriveOAuthRedirect(code: String, state: String) {
        scope.launch {
            updateRefreshToken(code, state)
        }
    }

    suspend fun upsertStorage(arg: ArgUpsertStorage): StorageId {
        val normalized = arg.normalized()
        val id = normalized.id ?: StorageId((sourceAccountDao.maxId() ?: 0L) + 1L)
        val now = currentTimeMillis()
        val previous = sourceAccountDao.get(id.value)
        val credential = StoredCredential(
            username = normalized.username,
            secret = normalized.password,
            isAnonymous = normalized.isAnonymous,
        )
        credentialStore.save(id.value, credential)
        sourceAccountDao.upsert(
            SourceAccountEntity(
                id = id.value,
                providerType = normalized.typ.toProviderType(),
                displayName = normalized.alias.ifBlank {
                    if (normalized.typ == StorageType.LOCAL) "Local" else normalized.addr
                },
                endpoint = normalized.addr.takeIf { it.isNotBlank() },
                externalAccountId = if (normalized.typ == StorageType.ONE_DRIVE) {
                    normalized.addr.ifBlank { null }
                } else {
                    null
                },
                credentialRef = previous?.credentialRef ?: "storage-${id.value}",
                priority = previous?.priority ?: 0,
                enabled = true,
                createdAt = previous?.createdAt ?: now,
                updatedAt = now,
                rootPath = previous?.rootPath ?: if (normalized.typ == StorageType.WEBDAV) "/" else null,
            )
        )
        return id
    }

    override suspend fun upsertSource(draft: SourceEditorDraft): SourceAccountId {
        return when {
            draft.storageType == SourceEditorType.Smb -> {
                storageSourceAccountId(upsertSmb(draft))
            }
            draft.storageType.isRemoteServer -> {
                storageSourceAccountId(upsertRemoteServer(draft))
            }
            else -> storageSourceAccountId(upsertStorage(draft.toArgUpsertStorage()).value)
        }
    }

    suspend fun remove(id: StorageId) {
        _preRemoveStorageEvent.emit(id)
        ctReleaseStorageBackend(id)
        credentialStore.delete(id.value)
        sourceAccountDao.delete(id.value)
        _onRemoveStorageEvent.emit(Unit)
    }

    override suspend fun reload() {
        ensureLocalStorage()
    }

    suspend fun loadCredential(id: StorageId): StoredCredential? {
        if (sourceAccountDao.get(id.value)?.providerType == ProviderTypes.Local) return null
        return credentialStore.load(id.value)
    }

    suspend fun storageForRust(id: StorageId): Storage? {
        val entity = sourceAccountDao.get(id.value) ?: return null
        val credential = loadCredential(id)
        return entity.toStorage(password = credential?.secret.orEmpty())
            .copyCredential(credential)
    }

    private suspend fun ensureLocalStorage() {
        if (sourceAccountDao.get(LOCAL_STORAGE_ID) != null) return
        val now = currentTimeMillis()
        sourceAccountDao.upsert(
            SourceAccountEntity(
                id = LOCAL_STORAGE_ID,
                providerType = ProviderTypes.Local,
                displayName = "Local",
                endpoint = null,
                externalAccountId = null,
                credentialRef = "storage-$LOCAL_STORAGE_ID",
                priority = 0,
                enabled = true,
                createdAt = now,
                updatedAt = now,
                rootPath = null,
            )
        )
    }

    private fun SourceAccountEntity.toStorage(password: String): Storage {
        val address = if (providerType == ProviderTypes.Smb) {
            val config = smbProviderConfiguration()
            if (config == null) {
                endpoint.orEmpty()
            } else {
                config.toSourceConfiguration(
                    accountId = storageSourceAccountId(id),
                    alias = displayName,
                    host = endpoint.orEmpty(),
                    password = password,
                ).toSmbAddress()
            }
        } else {
            externalAccountId ?: endpoint.orEmpty()
        }
        return Storage(
            id = StorageId(id),
            addr = address,
            alias = displayName,
            username = "",
            password = password,
            isAnonymous = providerType == ProviderTypes.Local,
            typ = providerType.toStorageType(),
            musicCount = 0u,
        )
    }

    private fun ArgUpsertStorage.normalized(): ArgUpsertStorage {
        return if (isAnonymous) {
            copy(username = "", password = "")
        } else {
            this
        }
    }

    private fun Storage.copyCredential(credential: StoredCredential?): Storage {
        return if (credential == null) {
            this
        } else {
            copy(
                username = credential.username,
                password = credential.secret,
                isAnonymous = credential.isAnonymous,
            )
        }
    }

    override suspend fun loadEditorState(id: Long): SourceEditorStorageState? {
        val entity = sourceAccountDao.get(id) ?: return null
        if (entity.providerType !in FILE_PROVIDER_TYPES) {
            val credential = loadCredential(StorageId(id))
            return SourceEditorStorageState(
                accountId = storageSourceAccountId(id),
                draft = SourceEditorDraft(
                    id = id,
                    address = entity.endpoint.orEmpty(),
                    alias = entity.displayName,
                    username = credential?.username.orEmpty(),
                    secret = "",
                    storageType = entity.providerType.toRemoteSourceEditorType(),
                    externalAccountId = entity.externalAccountId.orEmpty(),
                ),
                title = entity.displayName.ifBlank { entity.endpoint.orEmpty() },
                musicCount = 0u,
                isOneDrive = false,
            )
        }
        if (entity.providerType == ProviderTypes.Smb) {
            val credential = loadCredential(StorageId(id))
            val editorConfig = entity.smbEditorConfiguration() ?: return null
            val config = editorConfig.providerConfiguration
            return SourceEditorStorageState(
                accountId = storageSourceAccountId(id),
                draft = SourceEditorDraft(
                    id = id,
                    alias = entity.displayName,
                    username = credential?.username.orEmpty(),
                    secret = "",
                    isAnonymous = credential?.isAnonymous == true,
                    storageType = SourceEditorType.Smb,
                    smbHost = editorConfig.host,
                    smbPort = config.port,
                    smbShare = config.share,
                    smbRootPath = config.rootPath,
                    smbDomain = config.domain.orEmpty(),
                    smbRequireSigning = config.requireSigning,
                    smbRequireEncryption = config.requireEncryption,
                ),
                title = entity.displayName.ifBlank { entity.endpoint.orEmpty() },
                musicCount = _storages.value.find { it.id.value == id }?.musicCount ?: 0u,
                isOneDrive = false,
            )
        }
        val storage = _storages.value.find { it.id.value == id } ?: return null
        val credential = loadCredential(StorageId(id))
        val accountId = storageSourceAccountId(id)
        return SourceEditorStorageState(
            accountId = accountId,
            draft = storage.copy(password = "").toSourceEditorDraft(),
            title = storage.displayNameForEditor(),
            musicCount = storage.musicCount,
            isOneDrive = storage.typ == StorageType.ONE_DRIVE,
        )
    }

    override suspend fun testSource(draft: SourceEditorDraft): SourceConnectionTestStatus {
        if (draft.storageType.isRemoteServer) {
            return testRemoteServer(draft)
        }
        val testDraft = if (
            draft.storageType == SourceEditorType.Smb &&
            !draft.isAnonymous &&
            draft.secret.isBlank()
        ) {
            draft.copy(
                secret = draft.id?.let { loadCredential(StorageId(it))?.secret }.orEmpty()
            )
        } else {
            draft
        }
        val argument = testDraft.toArgUpsertStorage().let { argument ->
            if (draft.storageType == SourceEditorType.Smb) {
                argument.copy(id = null)
            } else {
                argument
            }
        }
        return test(argument).toSourceConnectionTestStatus()
    }

    private suspend fun upsertSmb(draft: SourceEditorDraft): Long {
        val id = draft.id ?: ((sourceAccountDao.maxId() ?: 0L) + 1L)
        val previous = sourceAccountDao.get(id)
        val previousCredential = loadCredential(StorageId(id))
        val secret = if (draft.isAnonymous) {
            ""
        } else {
            draft.secret.ifBlank { previousCredential?.secret.orEmpty() }
        }
        val configuration = draft.copy(secret = secret).toSmbSourceConfiguration()
        configuration.toSmbAddress()
        credentialStore.save(
            id,
            StoredCredential(
                username = if (draft.isAnonymous) "" else draft.username,
                secret = secret,
                isAnonymous = draft.isAnonymous,
            )
        )
        val now = currentTimeMillis()
        sourceAccountDao.upsert(
            SourceAccountEntity(
                id = id,
                providerType = ProviderTypes.Smb,
                displayName = draft.alias.ifBlank { draft.smbHost },
                endpoint = draft.smbHost.trim(),
                externalAccountId = null,
                credentialRef = previous?.credentialRef ?: "storage-$id",
                priority = previous?.priority ?: 0,
                enabled = previous?.enabled ?: true,
                createdAt = previous?.createdAt ?: now,
                updatedAt = now,
                rootPath = configuredSmbPath(configuration.share, configuration.rootPath),
                providerConfig = SMB_JSON.encodeToString(
                    SmbProviderConfiguration(
                        port = configuration.port,
                        share = configuration.share,
                        rootPath = configuration.rootPath,
                        domain = configuration.domain,
                        requireSigning = configuration.requireSigning,
                        requireEncryption = configuration.requireEncryption,
                    )
                ),
            )
        )
        ctReleaseStorageBackend(StorageId(id))
        return id
    }

    private suspend fun testRemoteServer(draft: SourceEditorDraft): SourceConnectionTestStatus =
        runCatching {
            when (draft.storageType) {
                SourceEditorType.Navidrome,
                SourceEditorType.OpenSubsonic -> {
                    val password = draft.secret.ifBlank {
                        draft.id?.let { loadCredential(StorageId(it))?.secret }.orEmpty()
                    }
                    ctSubsonicRequest(
                        baseUrl = draft.address,
                        username = draft.username,
                        password = password,
                        endpoint = "ping",
                        params = emptyMap(),
                    )
                }
                SourceEditorType.Emby -> {
                    if (draft.secret.isNotBlank()) {
                        ctEmbyLogin(draft.address, draft.username, draft.secret)
                    } else {
                        val id = requireNotNull(draft.id)
                        val token = loadCredential(StorageId(id))?.secret.orEmpty()
                        ctEmbyRequest(
                            draft.address,
                            token,
                            "Users/${draft.externalAccountId}",
                            emptyMap(),
                        )
                    }
                }
                else -> error("Unsupported remote server type")
            }
        }.fold(
            onSuccess = { SourceConnectionTestStatus.Success },
            onFailure = { SourceConnectionTestStatus.Error },
        )

    private suspend fun upsertRemoteServer(draft: SourceEditorDraft): Long {
        val id = draft.id ?: ((sourceAccountDao.maxId() ?: 0L) + 1L)
        val previous = sourceAccountDao.get(id)
        val previousCredential = loadCredential(StorageId(id))
        var secret = draft.secret.ifBlank { previousCredential?.secret.orEmpty() }
        var externalAccountId = draft.externalAccountId.ifBlank {
            previous?.externalAccountId.orEmpty()
        }
        if (draft.storageType == SourceEditorType.Emby && draft.secret.isNotBlank()) {
            val login = Json.parseToJsonElement(
                ctEmbyLogin(draft.address, draft.username, draft.secret)
            ).jsonObject
            secret = login["AccessToken"]?.jsonPrimitive?.contentOrNull.orEmpty()
            externalAccountId = login["User"]?.jsonObject
                ?.get("Id")?.jsonPrimitive?.contentOrNull.orEmpty()
            require(secret.isNotBlank() && externalAccountId.isNotBlank())
        } else if (draft.storageType != SourceEditorType.Emby) {
            ctSubsonicRequest(
                draft.address,
                draft.username,
                secret,
                "ping",
                emptyMap(),
            )
        }
        credentialStore.save(
            id,
            StoredCredential(
                username = draft.username,
                secret = secret,
                isAnonymous = false,
            )
        )
        val now = currentTimeMillis()
        sourceAccountDao.upsert(
            SourceAccountEntity(
                id = id,
                providerType = draft.storageType.providerType,
                displayName = draft.alias.ifBlank { draft.address },
                endpoint = draft.address,
                externalAccountId = externalAccountId.ifBlank { null },
                credentialRef = previous?.credentialRef ?: "storage-$id",
                priority = previous?.priority ?: 0,
                enabled = previous?.enabled ?: true,
                createdAt = previous?.createdAt ?: now,
                updatedAt = now,
                rootPath = null,
            )
        )
        return id
    }

    override suspend fun listOneDriveDriveInfos(refreshToken: String): OneDriveDriveListResult {
        val result = listOneDriveDrives(refreshToken)
        return OneDriveDriveListResult(
            drives = result.drives.map { drive ->
                OneDriveDriveInfo(
                    id = drive.id,
                    name = drive.name,
                )
            },
            refreshedToken = result.refreshToken,
        )
    }

    override suspend fun removeByAccountId(accountId: SourceAccountId) {
        val id = accountId.toStorageIdOrNull() ?: return
        remove(id)
    }

    override suspend fun updateOneDriveRefreshTokenByAccountId(accountId: SourceAccountId, refreshToken: String) {
        val id = accountId.toStorageIdOrNull() ?: return
        updateOneDriveRefreshToken(id, refreshToken)
    }

    override suspend fun loadCredentialByAccountId(accountId: SourceAccountId): StoredCredential? {
        val id = accountId.toStorageIdOrNull() ?: return null
        return loadCredential(id)
    }

    override suspend fun setAccountRootPath(accountId: SourceAccountId, rootPath: String) {
        val id = accountId.toStorageIdOrNull() ?: return
        val account = sourceAccountDao.get(id.value) ?: return
        if (account.providerType == ProviderTypes.Smb) {
            val path = rootPath.normalizedRootPath()
            val segments = path.split('/').filter(String::isNotBlank)
            require(segments.isNotEmpty()) { "Select an SMB share or one of its folders" }
            val configuration = account.smbProviderConfiguration()
                ?: SmbProviderConfiguration(share = "")
            val updatedConfiguration = configuration.copy(
                share = segments.first(),
                rootPath = segments.drop(1).joinToString("/"),
            )
            sourceAccountDao.upsert(
                account.copy(
                    rootPath = path,
                    providerConfig = SMB_JSON.encodeToString(updatedConfiguration),
                    updatedAt = currentTimeMillis(),
                )
            )
            ctReleaseStorageBackend(id)
        } else {
            sourceAccountDao.setRootPath(
                id = id.value,
                rootPath = rootPath.normalizedRootPath(),
                updatedAt = currentTimeMillis(),
            )
        }
    }

    fun findStorageAccount(id: Long): StorageAccountInfo? {
        return _storageAccounts.value.find { it.accountId.toStorageRouteIdOrNull() == id }
    }

    override fun findStorageAccountByAccountId(accountId: SourceAccountId): StorageAccountInfo? {
        return _storageAccounts.value.find { it.accountId == accountId }
    }

    private fun SourceAccountId.toStorageIdOrNull(): StorageId? {
        return toStorageRouteIdOrNull()?.let { StorageId(it) }
    }


    private fun Storage.displayNameForEditor(): String {
        return alias.ifBlank { addr }
    }

}

private fun StorageType.toProviderType(): String {
    return when (this) {
        StorageType.LOCAL -> ProviderTypes.Local
        StorageType.WEBDAV -> ProviderTypes.WebDav
        StorageType.ONE_DRIVE -> ProviderTypes.OneDrive
        StorageType.SMB -> ProviderTypes.Smb
    }
}

private fun String.toStorageType(): StorageType {
    return when (this) {
        ProviderTypes.Local -> StorageType.LOCAL
        ProviderTypes.WebDav -> StorageType.WEBDAV
        ProviderTypes.OneDrive -> StorageType.ONE_DRIVE
        ProviderTypes.Smb -> StorageType.SMB
        else -> StorageType.WEBDAV
    }
}

private const val PENDING_ONEDRIVE_OAUTH_CREDENTIAL_ID = Long.MIN_VALUE
private const val LOCAL_STORAGE_ID = 1L
private val FILE_PROVIDER_TYPES = setOf(
    ProviderTypes.Local,
    ProviderTypes.WebDav,
    ProviderTypes.OneDrive,
    ProviderTypes.Smb,
)

private val SourceEditorType.isRemoteServer: Boolean
    get() = this == SourceEditorType.Navidrome ||
        this == SourceEditorType.OpenSubsonic ||
        this == SourceEditorType.Emby

private val SourceEditorType.providerType: String
    get() = when (this) {
        SourceEditorType.Navidrome -> ProviderTypes.Navidrome
        SourceEditorType.OpenSubsonic -> ProviderTypes.OpenSubsonic
        SourceEditorType.Emby -> ProviderTypes.Emby
        else -> error("$this is not a remote server provider")
    }

private fun String.toRemoteSourceEditorType(): SourceEditorType = when (this) {
    ProviderTypes.Navidrome -> SourceEditorType.Navidrome
    ProviderTypes.OpenSubsonic -> SourceEditorType.OpenSubsonic
    ProviderTypes.Emby -> SourceEditorType.Emby
    else -> error("$this is not a remote server provider")
}

private fun SourceAccountSummaryRow.toStorageAccountInfo(): StorageAccountInfo {
    val sourceId = when (account.providerType) {
        ProviderTypes.Local -> BuiltInSourceIds.Local
        ProviderTypes.WebDav -> BuiltInSourceIds.WebDav
        ProviderTypes.OneDrive -> BuiltInSourceIds.OneDrive
        ProviderTypes.Smb -> BuiltInSourceIds.Smb
        ProviderTypes.Navidrome -> BuiltInSourceIds.Navidrome
        ProviderTypes.OpenSubsonic -> BuiltInSourceIds.OpenSubsonic
        ProviderTypes.Emby -> BuiltInSourceIds.Emby
        else -> SourceId(account.providerType)
    }
    return StorageAccountInfo(
        accountId = storageSourceAccountId(account.id),
        sourceId = sourceId,
        isLocal = account.providerType == ProviderTypes.Local,
        isOneDrive = account.providerType == ProviderTypes.OneDrive,
        title = account.displayName.ifBlank { account.endpoint.orEmpty() },
        subtitle = account.endpoint.orEmpty().ifBlank { account.displayName },
        musicCount = trackCount,
        rootPath = account.rootPath,
        enabled = account.enabled,
        lastScanAtEpochMs = lastScanAt,
        lastScanStatus = lastScanStatus,
    )
}

private fun String.normalizedRootPath(): String {
    val trimmed = trim().ifBlank { "/" }
    return if (trimmed.startsWith('/')) trimmed else "/$trimmed"
}
fun SourceConnectionTestStatus.toStorageConnectionTestResult(): StorageConnectionTestResult {
    return when (this) {
        SourceConnectionTestStatus.None -> StorageConnectionTestResult.NONE
        SourceConnectionTestStatus.Testing -> StorageConnectionTestResult.TESTING
        SourceConnectionTestStatus.Success -> StorageConnectionTestResult.SUCCESS
        SourceConnectionTestStatus.Unauthorized -> StorageConnectionTestResult.UNAUTHORIZED
        SourceConnectionTestStatus.Timeout -> StorageConnectionTestResult.TIMEOUT
        SourceConnectionTestStatus.PermissionDenied -> StorageConnectionTestResult.PERMISSION_DENIED
        SourceConnectionTestStatus.NotFound -> StorageConnectionTestResult.NOT_FOUND
        SourceConnectionTestStatus.InvalidAddress -> StorageConnectionTestResult.INVALID_ADDRESS
        SourceConnectionTestStatus.Unavailable -> StorageConnectionTestResult.UNAVAILABLE
        SourceConnectionTestStatus.UnsupportedSecurityPolicy -> StorageConnectionTestResult.UNSUPPORTED
        SourceConnectionTestStatus.Error -> StorageConnectionTestResult.OTHER_ERROR
    }
}

fun StorageConnectionTestResult.toSourceConnectionTestStatus(): SourceConnectionTestStatus {
    return when (this) {
        StorageConnectionTestResult.NONE -> SourceConnectionTestStatus.None
        StorageConnectionTestResult.TESTING -> SourceConnectionTestStatus.Testing
        StorageConnectionTestResult.SUCCESS -> SourceConnectionTestStatus.Success
        StorageConnectionTestResult.UNAUTHORIZED -> SourceConnectionTestStatus.Unauthorized
        StorageConnectionTestResult.TIMEOUT -> SourceConnectionTestStatus.Timeout
        StorageConnectionTestResult.PERMISSION_DENIED -> SourceConnectionTestStatus.PermissionDenied
        StorageConnectionTestResult.NOT_FOUND -> SourceConnectionTestStatus.NotFound
        StorageConnectionTestResult.INVALID_ADDRESS -> SourceConnectionTestStatus.InvalidAddress
        StorageConnectionTestResult.UNAVAILABLE -> SourceConnectionTestStatus.Unavailable
        StorageConnectionTestResult.UNSUPPORTED -> SourceConnectionTestStatus.UnsupportedSecurityPolicy
        StorageConnectionTestResult.OTHER_ERROR -> SourceConnectionTestStatus.Error
    }
}

@Serializable
private data class SmbProviderConfiguration(
    val port: Int = 445,
    val share: String,
    val rootPath: String = "",
    val domain: String? = null,
    val requireSigning: Boolean = false,
    val requireEncryption: Boolean = false,
) {
    fun toSourceConfiguration(
        accountId: SourceAccountId,
        alias: String,
        host: String,
        password: String,
    ): SmbSourceConfiguration {
        return SmbSourceConfiguration(
            accountId = accountId,
            alias = alias,
            host = host,
            port = port,
            share = share,
            rootPath = rootPath,
            domain = domain,
            password = password,
            requireSigning = requireSigning,
            requireEncryption = requireEncryption,
        )
    }
}

private fun configuredSmbPath(share: String, rootPath: String): String? {
    val sharePath = share.trim().trim('/')
    if (sharePath.isEmpty()) return null
    val nestedPath = rootPath.trim().trim('/')
    return if (nestedPath.isEmpty()) "/$sharePath" else "/$sharePath/$nestedPath"
}

private fun SourceAccountEntity.smbProviderConfiguration(): SmbProviderConfiguration? {
    val value = providerConfig ?: return null
    return runCatching {
        SMB_JSON.decodeFromString<SmbProviderConfiguration>(value)
    }.getOrNull()
}

private data class SmbEditorConfiguration(
    val host: String,
    val providerConfiguration: SmbProviderConfiguration,
)

private fun SourceAccountEntity.smbEditorConfiguration(): SmbEditorConfiguration? {
    smbProviderConfiguration()?.let { config ->
        return SmbEditorConfiguration(
            host = endpoint.orEmpty(),
            providerConfiguration = config,
        )
    }
    val host = endpoint?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return SmbEditorConfiguration(
        host = host,
        providerConfiguration = SmbProviderConfiguration(
            share = "",
            rootPath = rootPath.orEmpty(),
        ),
    )
}

private val SMB_JSON = Json {
    ignoreUnknownKeys = true
}

fun uniffi.app_backend.StorageId.toSourceAccountId(): SourceAccountId {
    return storageSourceAccountId(value)
}

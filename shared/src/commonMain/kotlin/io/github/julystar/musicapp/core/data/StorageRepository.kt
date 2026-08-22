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
import io.github.julystar.musicapp.database.LibraryRootDao
import io.github.julystar.musicapp.database.LibraryRootEntity

import io.github.julystar.musicapp.platform.currentTimeMillis
import io.github.julystar.musicapp.core.data.security.CredentialStore
import io.github.julystar.musicapp.core.domain.model.StoredCredential
import io.github.julystar.musicapp.core.domain.model.NeedsReauthenticationException
import io.github.julystar.musicapp.core.domain.model.OpenListOtpRequiredException
import io.github.julystar.musicapp.source.api.BuiltInSourceIds
import io.github.julystar.musicapp.source.api.EmbyProviderConfigurationCodec
import io.github.julystar.musicapp.source.api.NavidromeProviderConfiguration
import io.github.julystar.musicapp.source.api.NavidromeProviderConfigurationCodec
import io.github.julystar.musicapp.source.api.OpenSubsonicProviderConfigurationCodec
import io.github.julystar.musicapp.source.api.SmbSourceConfiguration
import io.github.julystar.musicapp.source.api.OpenListAuthenticator
import io.github.julystar.musicapp.source.api.OpenListProviderConfigurationCodec
import io.github.julystar.musicapp.source.api.OpenListSourceConfiguration
import io.github.julystar.musicapp.source.api.SourceAuthFailureReason
import io.github.julystar.musicapp.source.api.SourceAuthResult
import io.github.julystar.musicapp.source.api.sanitizeRemoteServerBaseUrl
import io.github.julystar.musicapp.source.server.RemoteServerEndpointPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
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
import uniffi.app_backend.EmbyLoginIdentity
import uniffi.app_backend.RemoteMusicException
import uniffi.app_backend.ctSubsonicRequest
import io.github.julystar.musicapp.source.smb.toSmbAddress


class StorageRepositoryImpl(
    private val bridge: Bridge,
    private val scope: CoroutineScope,
    private val sourceAccountDao: SourceAccountDao,
    private val credentialStore: CredentialStore,
    private val embyLogin: (String, String, String) -> EmbyLoginIdentity =
        { address, username, password -> ctEmbyLogin(address, username, password) },
    private val embyRequest: (String, String, String, Map<String, String>) -> String =
        { address, token, path, params -> ctEmbyRequest(address, token, path, params) },
    private val subsonicRequest: (String, String, String, String, Map<String, String>) -> String =
        { address, username, password, endpoint, params ->
            ctSubsonicRequest(address, username, password, endpoint, params)
        },
    private val openListAuthenticator: OpenListAuthenticator = OpenListAuthenticator {
        error("OpenList authenticator is not configured")
    },
    private val openListSessionManager: OpenListSessionManager? = null,
    private val libraryRootDao: LibraryRootDao? = null,
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
        val previousCredential = previous?.let { credentialStore.load(id.value) }
        val credential = StoredCredential(
            username = normalized.username,
            secret = normalized.password,
            isAnonymous = normalized.isAnonymous,
        )
        credentialStore.save(id.value, credential)
        try {
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
                    rootPath = previous?.rootPath ?: if (
                        normalized.typ == StorageType.WEBDAV || normalized.typ == StorageType.OPEN_LIST
                    ) "/" else null,
                )
            )
        } catch (failure: Throwable) {
            restoreCredentialAfterPersistenceFailure(id.value, previousCredential)
            throw failure
        }
        return id
    }

    override suspend fun upsertSource(draft: SourceEditorDraft): SourceAccountId {
        val draftId = draft.id
        val replacedOpenListAccount = if (
            draft.storageType != SourceEditorType.OpenList && draftId != null &&
            sourceAccountDao.get(draftId)?.providerType == ProviderTypes.OpenList
        ) {
            storageSourceAccountId(draftId)
        } else {
            null
        }
        val accountId = when {
            draft.storageType == SourceEditorType.OpenList -> {
                storageSourceAccountId(upsertOpenList(draft, otpCode = ""))
            }
            draft.storageType == SourceEditorType.Smb -> {
                storageSourceAccountId(upsertSmb(draft))
            }
            draft.storageType.isRemoteServer -> {
                storageSourceAccountId(upsertRemoteServer(draft))
            }
            else -> storageSourceAccountId(upsertStorage(draft.toArgUpsertStorage()).value)
        }
        replacedOpenListAccount?.let { openListSessionManager?.clear(it) }
        return accountId
    }

    override suspend fun upsertOpenListSource(
        draft: SourceEditorDraft,
        otpCode: String,
    ): SourceAccountId {
        require(draft.storageType == SourceEditorType.OpenList)
        return storageSourceAccountId(upsertOpenList(draft, otpCode))
    }

    suspend fun remove(id: StorageId) {
        _preRemoveStorageEvent.emit(id)
        openListSessionManager?.clear(storageSourceAccountId(id.value))
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
        if (entity.providerType !in FILE_PROVIDER_TYPES) return null
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
            val navidromeConfig = entity.providerConfig
                ?.takeIf { entity.providerType == ProviderTypes.Navidrome }
                ?.let(NavidromeProviderConfigurationCodec::decode)
            val openSubsonicConfig = entity.providerConfig
                ?.takeIf { entity.providerType == ProviderTypes.OpenSubsonic }
                ?.let(OpenSubsonicProviderConfigurationCodec::decode)
            val embyConfig = entity.providerConfig
                ?.takeIf { entity.providerType == ProviderTypes.Emby }
                ?.let(EmbyProviderConfigurationCodec::decode)
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
                    streamMaxBitRate = navidromeConfig?.streamMaxBitRate
                        ?: openSubsonicConfig?.streamMaxBitRate ?: 0,
                    downloadMaxBitRate = navidromeConfig?.downloadMaxBitRate
                        ?: openSubsonicConfig?.downloadMaxBitRate ?: 0,
                    coverArtSize = navidromeConfig?.coverArtSize
                        ?: openSubsonicConfig?.coverArtSize ?: 512,
                    remoteWriteEnabled = navidromeConfig?.remoteWriteEnabled
                        ?: openSubsonicConfig?.remoteWriteEnabled ?: false,
                    secondaryBaseUrl = navidromeConfig?.secondaryBaseUrl
                        ?: openSubsonicConfig?.secondaryBaseUrl
                        ?: embyConfig?.secondaryBaseUrl.orEmpty(),
                ),
                title = entity.displayName.ifBlank { entity.endpoint.orEmpty() },
                musicCount = 0u,
                isOneDrive = false,
                connectedServerName = embyConfig?.serverName.orEmpty(),
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
            requiresOtp = entity.providerType == ProviderTypes.OpenList &&
                OpenListProviderConfigurationCodec.decode(entity.providerConfig).requiresOtp,
        )
    }

    override suspend fun testSource(draft: SourceEditorDraft): SourceConnectionTestStatus {
        if (draft.storageType == SourceEditorType.OpenList) {
            return testOpenList(draft, otpCode = "")
        }
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
        try {
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
        } catch (failure: Throwable) {
            restoreCredentialAfterPersistenceFailure(id, previousCredential)
            throw failure
        }
        ctReleaseStorageBackend(StorageId(id))
        return id
    }

    private suspend fun testRemoteServer(draft: SourceEditorDraft): SourceConnectionTestStatus =
        try {
            val address = draft.validatedRemoteServerAddress()
            val secondaryBaseUrl = draft.validatedSecondaryBaseUrl()
            when (draft.storageType) {
                SourceEditorType.Navidrome,
                SourceEditorType.OpenSubsonic -> {
                    val password = draft.secret.ifBlank {
                        draft.id?.let { loadCredential(StorageId(it))?.secret }.orEmpty()
                    }
                    RemoteServerEndpointPolicy.execute(address, secondaryBaseUrl) { endpoint ->
                        subsonicRequest(
                            endpoint,
                            draft.username,
                            password,
                            "ping",
                            emptyMap(),
                        )
                    }
                }
                SourceEditorType.Emby -> {
                    if (draft.secret.isNotBlank()) {
                        RemoteServerEndpointPolicy.execute(address, secondaryBaseUrl) { endpoint ->
                            embyLogin(endpoint, draft.username, draft.secret)
                        }
                    } else {
                        val id = requireNotNull(draft.id)
                        val account = requireNotNull(sourceAccountDao.get(id))
                        if (account.providerType != ProviderTypes.Emby) {
                            throw NeedsReauthenticationException()
                        }
                        val credential = loadCredential(StorageId(id))
                            ?: throw NeedsReauthenticationException()
                        if (credential.secret.isBlank()) throw NeedsReauthenticationException()
                        val verified = parseEmbyUser(RemoteServerEndpointPolicy.execute(
                            address,
                            secondaryBaseUrl,
                        ) { endpoint ->
                            embyRequest(
                                endpoint,
                                credential.secret,
                                "Users/${account.externalAccountId ?: throw NeedsReauthenticationException()}",
                                emptyMap(),
                            )
                        }.value)
                        validateEmbyIdentity(
                            verified,
                            expectedUserId = account.externalAccountId,
                            expectedServerId = EmbyProviderConfigurationCodec
                                .decode(account.providerConfig).serverId,
                        )
                    }
                }
                else -> error("Unsupported remote server type")
            }
            SourceConnectionTestStatus.Success
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            when (error) {
                    is NeedsReauthenticationException -> SourceConnectionTestStatus.Unauthorized
                    is RemoteMusicException.Unauthorized -> SourceConnectionTestStatus.Unauthorized
                    is RemoteMusicException.PermissionDenied -> SourceConnectionTestStatus.PermissionDenied
                    is RemoteMusicException.Timeout -> SourceConnectionTestStatus.Timeout
                    is RemoteMusicException.Connectivity -> SourceConnectionTestStatus.Unavailable
                    is RemoteMusicException.NotFound -> SourceConnectionTestStatus.NotFound
                    is RemoteMusicException.InvalidAddress -> SourceConnectionTestStatus.InvalidAddress
                    is RemoteMusicException.Unavailable -> SourceConnectionTestStatus.Unavailable
                    else -> SourceConnectionTestStatus.Error
            }
        }

    override suspend fun testOpenListSource(
        draft: SourceEditorDraft,
        otpCode: String,
    ): SourceConnectionTestStatus {
        require(draft.storageType == SourceEditorType.OpenList)
        return testOpenList(draft, otpCode)
    }

    private suspend fun testOpenList(
        draft: SourceEditorDraft,
        otpCode: String,
    ): SourceConnectionTestStatus {
        val id = draft.id?.let(::storageSourceAccountId)
        val previous = draft.id?.let { sourceAccountDao.get(it) }
        if (draft.id != null && previous != null && previous.providerType != ProviderTypes.OpenList) {
            return SourceConnectionTestStatus.Unauthorized
        }
        val stored = draft.id?.let { loadCredential(StorageId(it)) }
        val result = openListAuthenticator.probe(
            OpenListSourceConfiguration(
                accountId = id,
                alias = draft.alias,
                address = draft.address,
                username = draft.username.ifBlank { stored?.username.orEmpty() },
                password = draft.secret.ifBlank { stored?.secret.orEmpty() },
                isGuest = draft.isAnonymous,
                otpCode = otpCode,
            )
        )
        return result.toSourceConnectionTestStatus()
    }

    private suspend fun upsertOpenList(draft: SourceEditorDraft, otpCode: String): Long {
        val id = draft.id ?: ((sourceAccountDao.maxId() ?: 0L) + 1L)
        val previous = sourceAccountDao.get(id)
        val previousCredential = loadCredential(StorageId(id))
        if (draft.id != null && previous != null && previous.providerType != ProviderTypes.OpenList) {
            openListSessionManager?.clear(storageSourceAccountId(id))
            throw NeedsReauthenticationException()
        }
        val username = if (draft.isAnonymous) "" else {
            draft.username.ifBlank { previousCredential?.username.orEmpty() }
        }
        val password = if (draft.isAnonymous) "" else {
            draft.secret.ifBlank { previousCredential?.secret.orEmpty() }
        }
        val configuration = OpenListSourceConfiguration(
            accountId = storageSourceAccountId(id),
            alias = draft.alias,
            address = draft.address,
            username = username,
            password = password,
            isGuest = draft.isAnonymous,
            otpCode = otpCode,
        )
        val result = openListAuthenticator.authenticate(configuration)
        if (result !is SourceAuthResult.Success) {
            val reason = (result as SourceAuthResult.Failure).reason
            if (reason == SourceAuthFailureReason.OtpRequired) {
                throw OpenListOtpRequiredException()
            }
            if (reason == SourceAuthFailureReason.Unauthorized) {
                throw NeedsReauthenticationException()
            }
            throw OpenListAuthenticationException(reason)
        }
        val oldConfig = OpenListProviderConfigurationCodec.decode(previous?.providerConfig)
        val newConfig = oldConfig.copy(
            requiresOtp = !draft.isAnonymous &&
                (oldConfig.requiresOtp || otpCode.isNotBlank()),
        )
        val now = currentTimeMillis()
        try {
            credentialStore.save(
                id,
                StoredCredential(username = username, secret = password, isAnonymous = draft.isAnonymous),
            )
            sourceAccountDao.upsert(
                SourceAccountEntity(
                    id = id,
                    providerType = ProviderTypes.OpenList,
                    displayName = draft.alias.ifBlank { draft.address },
                    endpoint = draft.address,
                    externalAccountId = null,
                    credentialRef = previous?.credentialRef ?: "storage-$id",
                    priority = previous?.priority ?: 0,
                    enabled = previous?.enabled ?: true,
                    createdAt = previous?.createdAt ?: now,
                    updatedAt = now,
                    rootPath = "/",
                    providerConfig = OpenListProviderConfigurationCodec.encode(newConfig),
                ),
            )
        } catch (failure: Throwable) {
            try {
                if (previousCredential == null) {
                    credentialStore.delete(id)
                } else {
                    credentialStore.save(id, previousCredential)
                }
            } catch (_: Throwable) {
                // Preserve the original persistence failure while making a best-effort rollback.
            }
            openListSessionManager?.clear(storageSourceAccountId(id))
            throw failure
        }
        return id
    }

    private suspend fun upsertRemoteServer(draft: SourceEditorDraft): Long {
        val id = draft.id ?: ((sourceAccountDao.maxId() ?: 0L) + 1L)
        val previous = sourceAccountDao.get(id)
        val address = draft.validatedRemoteServerAddress()
        val secondaryBaseUrl = draft.validatedSecondaryBaseUrl()
        if (draft.storageType == SourceEditorType.Emby &&
            draft.secret.isBlank() &&
            previous?.providerType != ProviderTypes.Emby
        ) {
            throw NeedsReauthenticationException()
        }
        val previousCredential = loadCredential(StorageId(id))
        var secret = draft.secret.ifBlank { previousCredential?.secret.orEmpty() }
        var username = draft.username
        var externalAccountId = draft.externalAccountId.ifBlank {
            previous?.externalAccountId.orEmpty()
        }
        var providerConfig = when (draft.storageType) {
            SourceEditorType.Navidrome -> NavidromeProviderConfigurationCodec.encode(
                NavidromeProviderConfiguration(
                    streamMaxBitRate = draft.streamMaxBitRate,
                    downloadMaxBitRate = draft.downloadMaxBitRate,
                    coverArtSize = draft.coverArtSize,
                    remoteWriteEnabled = draft.remoteWriteEnabled,
                    secondaryBaseUrl = secondaryBaseUrl,
                ),
            )
            SourceEditorType.OpenSubsonic -> OpenSubsonicProviderConfigurationCodec.encode(
                OpenSubsonicProviderConfigurationCodec.decode(
                    previous?.providerConfig.takeIf { previous?.providerType == ProviderTypes.OpenSubsonic },
                ).copy(
                    streamMaxBitRate = draft.streamMaxBitRate,
                    downloadMaxBitRate = draft.downloadMaxBitRate,
                    coverArtSize = draft.coverArtSize,
                    remoteWriteEnabled = draft.remoteWriteEnabled,
                    secondaryBaseUrl = secondaryBaseUrl,
                ),
            )
            SourceEditorType.Emby -> EmbyProviderConfigurationCodec.encode(
                EmbyProviderConfigurationCodec.decode(
                    previous?.providerConfig.takeIf { previous?.providerType == ProviderTypes.Emby },
                ).copy(secondaryBaseUrl = secondaryBaseUrl),
            )
            else -> error("Unsupported remote server type")
        }
        if (draft.storageType == SourceEditorType.Emby && draft.secret.isNotBlank()) {
            val login = try {
                RemoteServerEndpointPolicy.execute(address, secondaryBaseUrl) { endpoint ->
                    embyLogin(endpoint, draft.username, draft.secret)
                }.value
            } catch (error: RemoteMusicException.Unauthorized) {
                throw NeedsReauthenticationException()
            }
            secret = login.accessToken
            externalAccountId = login.userId
            providerConfig = EmbyProviderConfigurationCodec.encode(
                EmbyProviderConfigurationCodec.decode(providerConfig).copy(
                    serverId = login.serverId,
                    serverName = login.serverName,
                ),
            )
        } else if (draft.storageType == SourceEditorType.Emby) {
            val existing = previous ?: throw NeedsReauthenticationException()
            val credential = previousCredential ?: throw NeedsReauthenticationException()
            if (credential.secret.isBlank()) throw NeedsReauthenticationException()
            val persistedUserId = existing.externalAccountId
                ?: throw NeedsReauthenticationException()
            val verified = try {
                parseEmbyUser(RemoteServerEndpointPolicy.execute(
                    address,
                    secondaryBaseUrl,
                ) { endpoint ->
                    embyRequest(
                        endpoint,
                        credential.secret,
                        "Users/$persistedUserId",
                        emptyMap(),
                    )
                }.value)
            } catch (error: RemoteMusicException.Unauthorized) {
                throw NeedsReauthenticationException()
            }
            validateEmbyIdentity(
                verified,
                expectedUserId = persistedUserId,
                expectedServerId = EmbyProviderConfigurationCodec
                    .decode(existing.providerConfig).serverId,
            )
            secret = credential.secret
            externalAccountId = persistedUserId
            username = verified.name ?: credential.username
            val oldConfig = EmbyProviderConfigurationCodec.decode(existing.providerConfig)
            providerConfig = EmbyProviderConfigurationCodec.encode(
                EmbyProviderConfigurationCodec.decode(providerConfig).copy(
                    serverId = verified.serverId ?: oldConfig.serverId,
                    serverName = verified.serverName ?: oldConfig.serverName,
                ),
            )
        } else if (draft.storageType != SourceEditorType.Emby) {
            RemoteServerEndpointPolicy.execute(address, secondaryBaseUrl) { endpoint ->
                subsonicRequest(
                    endpoint,
                    draft.username,
                    secret,
                    "ping",
                    emptyMap(),
                )
            }
        }
        credentialStore.save(
            id,
            StoredCredential(
                username = username,
                secret = secret,
                isAnonymous = false,
            )
        )
        val now = currentTimeMillis()
        try {
            sourceAccountDao.upsert(
                SourceAccountEntity(
                    id = id,
                    providerType = draft.storageType.providerType,
                    displayName = draft.alias.ifBlank { address },
                    endpoint = address,
                    externalAccountId = externalAccountId.ifBlank { null },
                    credentialRef = previous?.credentialRef ?: "storage-$id",
                    priority = previous?.priority ?: 0,
                    enabled = previous?.enabled ?: true,
                    createdAt = previous?.createdAt ?: now,
                    updatedAt = now,
                    rootPath = null,
                    providerConfig = providerConfig,
                )
            )
        } catch (failure: Throwable) {
            restoreCredentialAfterPersistenceFailure(id, previousCredential)
            throw failure
        }
        return id
    }

    private suspend fun restoreCredentialAfterPersistenceFailure(
        id: Long,
        previousCredential: StoredCredential?,
    ) {
        try {
            if (previousCredential == null) {
                credentialStore.delete(id)
            } else {
                credentialStore.save(id, previousCredential)
            }
        } catch (_: Throwable) {
            // Preserve the original DAO failure while making a best-effort credential rollback.
        }
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
        if (account.providerType == ProviderTypes.OpenList) {
            val roots = libraryRootDao
                ?: error("Library root storage is not configured")
            val path = rootPath.openListCanonicalPath()
            val existing = roots.findByPath(id.value, path)
            val now = currentTimeMillis()
            roots.upsert(
                existing?.copy(
                    providerRootId = path,
                    canonicalPath = path,
                    displayName = path.substringAfterLast('/').ifBlank { "/" },
                    syncStatus = "PENDING",
                    syncCursor = null,
                    updatedAt = now,
                ) ?: LibraryRootEntity(
                    sourceAccountId = id.value,
                    providerRootId = path,
                    canonicalPath = path,
                    displayName = path.substringAfterLast('/').ifBlank { "/" },
                    syncStatus = "PENDING",
                    syncCursor = null,
                    lastSyncAt = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } else if (account.providerType == ProviderTypes.Smb) {
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

    override suspend fun listAccountRootPaths(accountId: SourceAccountId): List<String> {
        val id = accountId.toStorageRouteIdOrNull() ?: return emptyList()
        val account = sourceAccountDao.get(id) ?: return emptyList()
        val roots = libraryRootDao?.listBySourceAccount(id).orEmpty()
        val paths = roots.mapNotNull { root ->
            if (account.providerType == ProviderTypes.Smb) {
                root.providerRootId ?: root.canonicalPath
            } else {
                root.canonicalPath ?: root.providerRootId
            }
        }
        return paths.ifEmpty { listOfNotNull(account.rootPath) }
    }

    override suspend fun replaceAccountRootPaths(
        accountId: SourceAccountId,
        rootPaths: List<String>,
    ) {
        val id = accountId.toStorageRouteIdOrNull() ?: return
        val account = sourceAccountDao.get(id) ?: return
        val roots = libraryRootDao ?: error("Library root storage is not configured")
        val normalizedPaths = rootPaths
            .map { path ->
                if (account.providerType == ProviderTypes.OpenList) {
                    path.openListCanonicalPath()
                } else {
                    path.normalizedRootPath()
                }
            }
            .distinct()
            .let(::removeCoveredRootPaths)
        val now = currentTimeMillis()
        val existing = roots.listBySourceAccount(id)
        if (account.providerType == ProviderTypes.Smb) {
            replaceSmbAccountRoots(
                account = account,
                normalizedPaths = normalizedPaths,
                existing = existing,
                roots = roots,
                now = now,
            )
            ctReleaseStorageBackend(StorageId(id))
            return
        }
        existing.filter { root -> root.canonicalPath !in normalizedPaths }.forEach { root ->
            roots.delete(root.id)
        }
        normalizedPaths.forEach { path ->
            val previous = existing.firstOrNull { root -> root.canonicalPath == path }
            roots.upsert(
                previous?.copy(updatedAt = now) ?: LibraryRootEntity(
                    sourceAccountId = id,
                    providerRootId = if (account.providerType == ProviderTypes.OpenList) path else null,
                    canonicalPath = path,
                    displayName = path.trimEnd('/').substringAfterLast('/').ifBlank { "/" },
                    syncStatus = "PENDING",
                    syncCursor = null,
                    lastSyncAt = null,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }

        val legacyPath = normalizedPaths.firstOrNull()
        sourceAccountDao.upsert(account.copy(rootPath = legacyPath, updatedAt = now))
    }

    private suspend fun replaceSmbAccountRoots(
        account: SourceAccountEntity,
        normalizedPaths: List<String>,
        existing: List<LibraryRootEntity>,
        roots: LibraryRootDao,
        now: Long,
    ) {
        val pathsWithSegments = normalizedPaths.associateWith { path ->
            path.split('/').filter(String::isNotBlank)
        }
        require(pathsWithSegments.values.all(List<String>::isNotEmpty)) {
            "Select an SMB share or one of its folders"
        }
        val shares = pathsWithSegments.values.map(List<String>::first).distinct()
        require(shares.size <= 1) { "Music folders in one SMB source must use the same share" }

        val desired = pathsWithSegments.mapValues { (_, segments) ->
            segments.drop(1).joinToString("/", prefix = "/").ifBlank { "/" }
        }
        existing.filter { root ->
            val pickerPath = root.providerRootId ?: root.canonicalPath
            pickerPath !in desired.keys
        }.forEach { root -> roots.delete(root.id) }
        desired.forEach { (pickerPath, canonicalPath) ->
            val previous = existing.firstOrNull { root ->
                root.providerRootId == pickerPath ||
                    root.canonicalPath == pickerPath ||
                    root.canonicalPath == canonicalPath
            }
            roots.upsert(
                (previous ?: LibraryRootEntity(
                    sourceAccountId = account.id,
                    providerRootId = pickerPath,
                    canonicalPath = canonicalPath,
                    displayName = pickerPath.substringAfterLast('/'),
                    syncStatus = "PENDING",
                    syncCursor = null,
                    lastSyncAt = null,
                    createdAt = now,
                    updatedAt = now,
                )).copy(
                    providerRootId = pickerPath,
                    canonicalPath = canonicalPath,
                    displayName = pickerPath.substringAfterLast('/'),
                    updatedAt = now,
                )
            )
        }

        val configuration = account.smbProviderConfiguration()
            ?: SmbProviderConfiguration(share = "")
        sourceAccountDao.upsert(
            account.copy(
                rootPath = normalizedPaths.firstOrNull(),
                providerConfig = SMB_JSON.encodeToString(
                    configuration.copy(share = shares.firstOrNull().orEmpty(), rootPath = "")
                ),
                updatedAt = now,
            )
        )
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

private data class EmbyVerifiedUser(
    val id: String,
    val serverId: String?,
    val serverName: String?,
    val name: String?,
)

private fun parseEmbyUser(raw: String): EmbyVerifiedUser {
    val root = Json.parseToJsonElement(raw).jsonObject
    val user = root["User"]?.jsonObject ?: root
    val id = user["Id"]?.jsonPrimitive?.contentOrNull
        ?.takeIf(String::isNotBlank)
        ?: throw IllegalArgumentException("Emby user identity is missing")
    val serverId = jsonString(root["ServerId"])
        ?: jsonString(user["ServerId"])
    val serverName = jsonString(user["ServerName"])
        ?: jsonString(root["ServerName"])
    val name = user["Name"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
    return EmbyVerifiedUser(id, serverId, serverName, name)
}

private fun jsonString(value: kotlinx.serialization.json.JsonElement?): String? =
    (value as? kotlinx.serialization.json.JsonPrimitive)
        ?.takeIf(kotlinx.serialization.json.JsonPrimitive::isString)
        ?.contentOrNull
        ?.takeIf(String::isNotBlank)

private fun validateEmbyIdentity(
    user: EmbyVerifiedUser,
    expectedUserId: String?,
    expectedServerId: String?,
) {
    if (expectedUserId != null && user.id != expectedUserId) {
        throw IllegalArgumentException("Emby user identity does not match the saved account")
    }
    if (expectedServerId != null && user.serverId != null && user.serverId != expectedServerId) {
        throw IllegalArgumentException("Emby server identity does not match the saved account")
    }
}

private fun StorageType.toProviderType(): String {
    return when (this) {
        StorageType.LOCAL -> ProviderTypes.Local
        StorageType.WEBDAV -> ProviderTypes.WebDav
        StorageType.ONE_DRIVE -> ProviderTypes.OneDrive
        StorageType.SMB -> ProviderTypes.Smb
        StorageType.OPEN_LIST -> ProviderTypes.OpenList
    }
}

private fun String.toStorageType(): StorageType {
    return when (this) {
        ProviderTypes.Local -> StorageType.LOCAL
        ProviderTypes.WebDav -> StorageType.WEBDAV
        ProviderTypes.OneDrive -> StorageType.ONE_DRIVE
        ProviderTypes.Smb -> StorageType.SMB
        ProviderTypes.OpenList -> StorageType.OPEN_LIST
        else -> error("Unsupported file provider type")
    }
}

private const val PENDING_ONEDRIVE_OAUTH_CREDENTIAL_ID = Long.MIN_VALUE
private const val LOCAL_STORAGE_ID = 1L
private val FILE_PROVIDER_TYPES = setOf(
    ProviderTypes.Local,
    ProviderTypes.WebDav,
    ProviderTypes.OneDrive,
    ProviderTypes.Smb,
    ProviderTypes.OpenList,
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
        ProviderTypes.OpenList -> BuiltInSourceIds.OpenList
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
        SourceConnectionTestStatus.OtpRequired -> StorageConnectionTestResult.UNAUTHORIZED
        SourceConnectionTestStatus.Timeout -> StorageConnectionTestResult.TIMEOUT
        SourceConnectionTestStatus.PermissionDenied -> StorageConnectionTestResult.PERMISSION_DENIED
        SourceConnectionTestStatus.NotFound -> StorageConnectionTestResult.NOT_FOUND
        SourceConnectionTestStatus.InvalidAddress -> StorageConnectionTestResult.INVALID_ADDRESS
        SourceConnectionTestStatus.Unavailable -> StorageConnectionTestResult.UNAVAILABLE
        SourceConnectionTestStatus.UnsupportedSecurityPolicy -> StorageConnectionTestResult.UNSUPPORTED
        SourceConnectionTestStatus.Error -> StorageConnectionTestResult.OTHER_ERROR
    }
}

private fun SourceAuthResult.toSourceConnectionTestStatus(): SourceConnectionTestStatus = when (this) {
    SourceAuthResult.Success -> SourceConnectionTestStatus.Success
    is SourceAuthResult.Failure -> when (reason) {
        SourceAuthFailureReason.Timeout -> SourceConnectionTestStatus.Timeout
        SourceAuthFailureReason.Unauthorized -> SourceConnectionTestStatus.Unauthorized
        SourceAuthFailureReason.PermissionDenied -> SourceConnectionTestStatus.PermissionDenied
        SourceAuthFailureReason.NotFound -> SourceConnectionTestStatus.NotFound
        SourceAuthFailureReason.InvalidAddress -> SourceConnectionTestStatus.InvalidAddress
        SourceAuthFailureReason.OtpRequired -> SourceConnectionTestStatus.OtpRequired
        SourceAuthFailureReason.UnsupportedSecurityPolicy -> SourceConnectionTestStatus.UnsupportedSecurityPolicy
        SourceAuthFailureReason.UnsupportedConfiguration,
        SourceAuthFailureReason.Unavailable,
        SourceAuthFailureReason.Unknown -> SourceConnectionTestStatus.Unavailable
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

private fun removeCoveredRootPaths(paths: List<String>): List<String> = paths.filter { candidate ->
    paths.none { other -> other != candidate && other.isRootPathAncestorOf(candidate) }
}

private fun String.isRootPathAncestorOf(other: String): Boolean {
    val parentSegments = trim().trimEnd('/').split('/').filter(String::isNotEmpty)
    val childSegments = other.trim().trimEnd('/').split('/').filter(String::isNotEmpty)
    return parentSegments.size < childSegments.size &&
        childSegments.take(parentSegments.size) == parentSegments
}

private fun SourceEditorDraft.validatedRemoteServerAddress(): String =
    sanitizeRemoteServerBaseUrl(address)
        ?: throw RemoteMusicException.InvalidAddress()

private fun SourceEditorDraft.validatedSecondaryBaseUrl(): String? {
    if (secondaryBaseUrl.isBlank()) return null
    return sanitizeRemoteServerBaseUrl(secondaryBaseUrl)
        ?: throw RemoteMusicException.InvalidAddress()
}

private fun String.openListCanonicalPath(): String {
    require(isNotEmpty() && !contains('\u0000')) {
        "OpenList root must be a non-empty canonical path"
    }
    val canonical = if (startsWith('/')) this else "/$this"
    if (canonical == "/") return "/"
    val segments = canonical.removePrefix("/").split('/')
    require(segments.none { it.isEmpty() || it == "." || it == ".." }) {
        "OpenList root contains an invalid path segment"
    }
    return canonical
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

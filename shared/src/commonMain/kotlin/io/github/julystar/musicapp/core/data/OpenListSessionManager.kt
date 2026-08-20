package io.github.julystar.musicapp.core.data

import io.github.julystar.musicapp.core.domain.model.NeedsReauthenticationException
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.StoredCredential
import io.github.julystar.musicapp.source.api.OpenListAuthenticator
import io.github.julystar.musicapp.source.api.OpenListProviderConfigurationCodec
import io.github.julystar.musicapp.source.api.OpenListSourceConfiguration
import io.github.julystar.musicapp.source.api.SourceAuthFailureReason
import io.github.julystar.musicapp.source.api.SourceAuthResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uniffi.app_backend.OpenListAuthException
import uniffi.app_backend.ctOpenlistLogin
import uniffi.app_backend.ctOpenlistValidateSession

class OpenListAccountMaterial(
    val accountId: SourceAccountId,
    val endpoint: String,
    val credential: StoredCredential,
    val providerConfig: String?,
) {
    override fun toString(): String =
        "OpenListAccountMaterial(accountId=$accountId, endpoint=<redacted>, credential=<redacted>)"
}

fun interface OpenListAccountMaterialReader {
    suspend fun load(accountId: SourceAccountId): OpenListAccountMaterial?
}

interface OpenListAuthTransport {
    suspend fun login(endpoint: String, username: String, password: String, otpCode: String): String
    suspend fun validateSession(endpoint: String, token: String)
}

class OpenListRustAuthTransport : OpenListAuthTransport {
    override suspend fun login(endpoint: String, username: String, password: String, otpCode: String): String =
        try {
            ctOpenlistLogin(endpoint, username, password, otpCode)
        } catch (error: OpenListAuthException) {
            throw OpenListAuthTransportException(error.toReason())
        }

    override suspend fun validateSession(endpoint: String, token: String) {
        try {
            ctOpenlistValidateSession(endpoint, token)
        } catch (error: OpenListAuthException) {
            throw OpenListAuthTransportException(error.toReason())
        }
    }
}

private fun OpenListAuthException.toReason(): OpenListAuthTransportFailureReason = when (this) {
    is OpenListAuthException.InvalidAddress -> OpenListAuthTransportFailureReason.InvalidAddress
    is OpenListAuthException.Timeout -> OpenListAuthTransportFailureReason.Timeout
    is OpenListAuthException.Unauthorized -> OpenListAuthTransportFailureReason.Unauthorized
    is OpenListAuthException.PermissionDenied -> OpenListAuthTransportFailureReason.PermissionDenied
    is OpenListAuthException.OtpRequired -> OpenListAuthTransportFailureReason.OtpRequired
    is OpenListAuthException.RateLimited -> OpenListAuthTransportFailureReason.RateLimited
    is OpenListAuthException.InvalidResponse -> OpenListAuthTransportFailureReason.InvalidResponse
    is OpenListAuthException.ProtocolFailure -> OpenListAuthTransportFailureReason.ProtocolFailure
    is OpenListAuthException.Unavailable -> OpenListAuthTransportFailureReason.Unavailable
}

enum class OpenListAuthTransportFailureReason {
    InvalidAddress,
    Timeout,
    Unauthorized,
    PermissionDenied,
    OtpRequired,
    RateLimited,
    InvalidResponse,
    ProtocolFailure,
    Unavailable,
}

class OpenListAuthTransportException(
    val reason: OpenListAuthTransportFailureReason,
) : Exception("OpenList authentication transport failed: $reason")

class OpenListAuthenticationException(
    val reason: SourceAuthFailureReason,
) : Exception("OpenList authentication failed: $reason")

class OpenListSessionManager(
    private val materials: OpenListAccountMaterialReader,
    private val transport: OpenListAuthTransport,
) : OpenListAuthenticator {
    private class Session(val token: String)

    private val mutex = Mutex()
    private val sessions = mutableMapOf<SourceAccountId, Session>()

    override suspend fun authenticate(configuration: OpenListSourceConfiguration): SourceAuthResult {
        return authenticateInternal(configuration, bindSession = true)
    }

    override suspend fun probe(configuration: OpenListSourceConfiguration): SourceAuthResult {
        return authenticateInternal(configuration, bindSession = false)
    }

    private suspend fun authenticateInternal(
        configuration: OpenListSourceConfiguration,
        bindSession: Boolean,
    ): SourceAuthResult {
        val accountId = configuration.accountId
        return try {
            require(configuration.address.isNotBlank())
            if (configuration.isGuest) {
                transport.validateSession(configuration.address, "")
                if (bindSession) {
                    accountId?.let { mutex.withLock { sessions[it] = Session("") } }
                }
            } else {
                val material = accountId?.let { materials.load(it) }
                val effectiveUsername = configuration.username.ifBlank { material?.credential?.username.orEmpty() }
                val effectivePassword = configuration.password.ifBlank { material?.credential?.secret.orEmpty() }
                if (effectiveUsername.isBlank() || effectivePassword.isBlank()) {
                    throw NeedsReauthenticationException()
                }
                val persistedRequiresOtp = OpenListProviderConfigurationCodec
                    .decode(material?.providerConfig)
                    .requiresOtp
                if (persistedRequiresOtp && configuration.otpCode.isBlank()) {
                    throw OpenListAuthTransportException(OpenListAuthTransportFailureReason.OtpRequired)
                }
                val token = transport.login(
                    configuration.address,
                    effectiveUsername,
                    effectivePassword,
                    configuration.otpCode,
                )
                if (token.isBlank()) throw OpenListAuthTransportException(
                    OpenListAuthTransportFailureReason.InvalidResponse,
                )
                transport.validateSession(configuration.address, token)
                if (bindSession) accountId?.let {
                    mutex.withLock {
                        sessions[it] = Session(token)
                    }
                }
            }
            SourceAuthResult.Success
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: NeedsReauthenticationException) {
            SourceAuthResult.Failure(SourceAuthFailureReason.Unauthorized)
        } catch (error: OpenListAuthTransportException) {
            SourceAuthResult.Failure(error.reason.toSourceAuthFailureReason())
        } catch (_: IllegalArgumentException) {
            SourceAuthResult.Failure(SourceAuthFailureReason.InvalidAddress)
        }
    }

    suspend fun <T> authorized(accountId: SourceAccountId, operation: suspend (String) -> T): T {
        val session = ensureSession(accountId)
        return try {
            operation(session.token)
        } catch (error: OpenListAuthTransportException) {
            if (error.reason != OpenListAuthTransportFailureReason.Unauthorized) throw error
            mutex.withLock { sessions.remove(accountId) }
            val material = materials.load(accountId)
                ?: throw NeedsReauthenticationException()
            if (material.credential.isAnonymous) {
                throw NeedsReauthenticationException()
            }
            val retry = try {
                ensureSession(accountId)
            } catch (otp: OpenListAuthTransportException) {
                if (otp.reason == OpenListAuthTransportFailureReason.OtpRequired ||
                    otp.reason == OpenListAuthTransportFailureReason.Unauthorized
                ) {
                    throw NeedsReauthenticationException()
                }
                throw otp
            }
            try {
                operation(retry.token)
            } catch (second: OpenListAuthTransportException) {
                if (second.reason == OpenListAuthTransportFailureReason.Unauthorized) {
                    mutex.withLock { sessions.remove(accountId) }
                    throw NeedsReauthenticationException()
                }
                throw second
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        }
    }

    /**
     * Runs one operation only after validating the cached raw token against the
     * account endpoint. The existing authorized() retry boundary remains the
     * single place that handles one-shot reauthentication.
     */
    suspend fun <T> validatedAuthorized(
        accountId: SourceAccountId,
        operation: suspend (endpoint: String, token: String) -> T,
    ): T {
        return authorized(accountId) { token ->
            val material = materials.load(accountId)
                ?: throw NeedsReauthenticationException()
            transport.validateSession(material.endpoint, token)
            operation(material.endpoint, token)
        }
    }

    suspend fun clear(accountId: SourceAccountId) {
        mutex.withLock { sessions.remove(accountId) }
    }

    suspend fun clearAll() {
        mutex.withLock { sessions.clear() }
    }

    private suspend fun ensureSession(accountId: SourceAccountId): Session {
        mutex.withLock { sessions[accountId] }?.let { return it }
        val material = materials.load(accountId) ?: throw NeedsReauthenticationException()
        if (material.credential.isAnonymous) {
            transport.validateSession(material.endpoint, "")
            return Session("").also { mutex.withLock { sessions[accountId] = it } }
        }
        if (material.credential.secret.isBlank() ||
            OpenListProviderConfigurationCodec.decode(material.providerConfig).requiresOtp
        ) {
            throw NeedsReauthenticationException()
        }
        val token = transport.login(
            material.endpoint,
            material.credential.username,
            material.credential.secret,
            "",
        )
        if (token.isBlank()) {
            throw OpenListAuthTransportException(OpenListAuthTransportFailureReason.InvalidResponse)
        }
        transport.validateSession(material.endpoint, token)
        return Session(token).also { mutex.withLock { sessions[accountId] = it } }
    }
}

private fun OpenListAuthTransportFailureReason.toSourceAuthFailureReason(): SourceAuthFailureReason = when (this) {
    OpenListAuthTransportFailureReason.InvalidAddress -> SourceAuthFailureReason.InvalidAddress
    OpenListAuthTransportFailureReason.Timeout -> SourceAuthFailureReason.Timeout
    OpenListAuthTransportFailureReason.Unauthorized -> SourceAuthFailureReason.Unauthorized
    OpenListAuthTransportFailureReason.PermissionDenied -> SourceAuthFailureReason.PermissionDenied
    OpenListAuthTransportFailureReason.OtpRequired -> SourceAuthFailureReason.OtpRequired
    OpenListAuthTransportFailureReason.RateLimited,
    OpenListAuthTransportFailureReason.InvalidResponse,
    OpenListAuthTransportFailureReason.ProtocolFailure,
    OpenListAuthTransportFailureReason.Unavailable -> SourceAuthFailureReason.Unavailable
}

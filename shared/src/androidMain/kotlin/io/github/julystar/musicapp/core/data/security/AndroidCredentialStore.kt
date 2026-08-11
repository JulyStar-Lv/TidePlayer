package io.github.julystar.musicapp.core.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.github.julystar.musicapp.core.domain.model.StoredCredential
import io.github.julystar.musicapp.core.domain.model.DiagnosticLogCategory
import io.github.julystar.musicapp.diagnostics.AppLogger
import android.util.Base64
import io.github.julystar.musicapp.migration.AppIdentifiers
import io.github.julystar.musicapp.migration.LegacyCredentialIds
import io.github.julystar.musicapp.platform.appContext
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import androidx.core.content.edit

private const val KEY_ALIAS = "${AppIdentifiers.CREDENTIAL_SERVICE}.key"
private const val PREFERENCES = "${AppIdentifiers.CREDENTIAL_SERVICE}.preferences"

actual fun createCredentialStore(): CredentialStore = AndroidCredentialStore()

private class AndroidCredentialStore : CredentialStore {
    private val preferences = appContext.getSharedPreferences(PREFERENCES, 0)
    private val legacyPreferences =
        appContext.getSharedPreferences(LegacyCredentialIds.ANDROID_PREFERENCES, 0)

    override suspend fun load(storageId: Long): StoredCredential? {
        val key = storageId.toString()
        val currentEncoded = preferences.getString(key, null)
        if (currentEncoded != null) {
            runCatching { decrypt(currentEncoded, KEY_ALIAS) }
                .onSuccess { return it }
                .onFailure { error ->
                    AppLogger.warn(
                        DiagnosticLogCategory.Security,
                        "AndroidCredentialStore",
                        "Stored credential cannot be decrypted; reauthentication is required",
                        error.stackTraceToString(),
                    )
                    preferences.edit { remove(key) }
                }
        }
        val legacyEncoded = legacyPreferences.getString(key, null) ?: return null
        val credential = runCatching {
            decrypt(legacyEncoded, LegacyCredentialIds.ANDROID_KEY_ALIAS)
        }.getOrElse { error ->
            AppLogger.warn(
                DiagnosticLogCategory.Security,
                "AndroidCredentialStore",
                "Legacy credential cannot be decrypted; reauthentication is required",
                error.stackTraceToString(),
            )
            legacyPreferences.edit { remove(key) }
            return null
        }
        save(storageId, credential)
        check(loadCurrent(storageId) == credential) {
            "Unable to verify migrated Android credential"
        }
        legacyPreferences.edit { remove(key) }
        return credential
    }

    private fun loadCurrent(storageId: Long): StoredCredential? {
        val encoded = preferences.getString(storageId.toString(), null) ?: return null
        return runCatching { decrypt(encoded, KEY_ALIAS) }.getOrNull()
    }

    private fun decrypt(encoded: String, keyAlias: String): StoredCredential {
        val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
        require(encrypted.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(keyAlias, create = false)
                ?: error("Credential key $keyAlias is unavailable"),
            GCMParameterSpec(128, encrypted.copyOfRange(0, 12)),
        )
        return decodeCredential(
            cipher.doFinal(encrypted.copyOfRange(12, encrypted.size))
                .toString(StandardCharsets.UTF_8)
        )
    }

    override suspend fun save(storageId: Long, credential: StoredCredential) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(KEY_ALIAS, create = true))
        val encrypted = cipher.iv + cipher.doFinal(
            encodeCredential(credential).toByteArray(StandardCharsets.UTF_8)
        )
        preferences.edit {
            putString(storageId.toString(), Base64.encodeToString(encrypted, Base64.NO_WRAP))
        }
    }

    override suspend fun delete(storageId: Long) {
        preferences.edit { remove(storageId.toString()) }
        legacyPreferences.edit { remove(storageId.toString()) }
    }

    override suspend fun clear() {
        preferences.edit { clear() }
        legacyPreferences.edit { clear() }
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        listOf(KEY_ALIAS, LegacyCredentialIds.ANDROID_KEY_ALIAS).forEach { keyAlias ->
            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias)
            }
        }
    }

    private fun secretKey(keyAlias: String, create: Boolean): SecretKey? {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        if (!create) return null

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}

private fun encodeCredential(value: StoredCredential): String {
    return listOf(value.username, value.secret, value.isAnonymous.toString())
        .joinToString("\u0000")
}

private fun decodeCredential(value: String): StoredCredential {
    val fields = value.split('\u0000', limit = 3)
    require(fields.size == 3)
    return StoredCredential(fields[0], fields[1], fields[2].toBooleanStrict())
}

package io.github.julystar.musicapp.core.domain.model

data class StoredCredential(
    val username: String,
    val secret: String,
    val isAnonymous: Boolean,
) {
    override fun toString(): String =
        "StoredCredential(username=$username, secret=<redacted>, isAnonymous=$isAnonymous)"
}

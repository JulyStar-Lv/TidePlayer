package io.github.julystar.musicapp.migration

internal object LegacyDeepLinks {
    const val PREVIOUS_SCHEME = "melodytrove"
    const val ORIGINAL_SCHEME = "tidetunes"
    const val OAUTH_HOST = "oauth2redirect"

    val SCHEMES = listOf(PREVIOUS_SCHEME, ORIGINAL_SCHEME)
    val OAUTH_REDIRECT_URIS = SCHEMES.map { scheme -> "$scheme://$OAUTH_HOST/" }
}

package io.github.julystar.musicapp.migration

internal object AppIdentifiers {
    const val BRAND_NAME = "TidePlayer"
    const val BRAND_SLUG = "tideplayer"
    const val PACKAGE_ID = "io.github.julystar.musicapp"
    const val DEEP_LINK_SCHEME = BRAND_SLUG
    const val CREDENTIAL_SERVICE = "$PACKAGE_ID.credentials"
    const val DATABASE_FILE = "library.db"
    const val PREFERENCES_FILE = "settings.preferences_pb"
}

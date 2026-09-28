package io.github.julystar.musicapp.plugin.runtime

internal actual fun pluginLocalePreferences(): List<String> = listOf(java.util.Locale.getDefault().toLanguageTag())

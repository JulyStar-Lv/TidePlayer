package io.github.julystar.musicapp.plugin.runtime

internal actual fun pluginLocalePreferences(): List<String> {
    val locales = android.os.LocaleList.getDefault()
    return (0 until locales.size()).map { locales[it].toLanguageTag() }
}

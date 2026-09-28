package io.github.julystar.musicapp.plugin.runtime

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages
import platform.Foundation.NSUserDefaults

internal actual fun pluginLocalePreferences(): List<String> =
    (NSUserDefaults.standardUserDefaults.stringArrayForKey("AppleLanguages") ?: NSLocale.preferredLanguages)
        .filterIsInstance<String>()

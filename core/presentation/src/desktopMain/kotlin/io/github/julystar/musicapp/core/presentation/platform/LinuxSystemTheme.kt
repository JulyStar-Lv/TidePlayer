package io.github.julystar.musicapp.core.presentation.platform

internal object LinuxSystemTheme {
    private val portalColorSchemePattern = Regex("uint32\\s+(\\d+)")

    fun current(): Boolean = when (readPortalColorScheme()) {
        1 -> true
        2 -> false
        else -> readGnomeTheme() ?: false
    }

    fun observe(onChanged: (Boolean) -> Unit): AutoCloseable {
        return if (readPortalColorScheme() != null) {
            ProcessThemeObserver(
                command = listOf(
                    "gdbus",
                    "monitor",
                    "--session",
                    "--dest",
                    "org.freedesktop.portal.Desktop",
                    "--object-path",
                    "/org/freedesktop/portal/desktop",
                ),
                isThemeEvent = { line ->
                    line.contains("SettingChanged") ||
                        line.contains("org.freedesktop.appearance") ||
                        line.contains("color-scheme")
                },
                onChanged = { onChanged(current()) },
            )
        } else {
            ProcessThemeObserver(
                command = listOf(
                    "gsettings",
                    "monitor",
                    "org.gnome.desktop.interface",
                    "color-scheme",
                ),
                isThemeEvent = { true },
                onChanged = { onChanged(current()) },
            )
        }
    }

    private fun readPortalColorScheme(): Int? = runCatching {
        val output = runDesktopCommand(
            "gdbus",
            "call",
            "--session",
            "--dest",
            "org.freedesktop.portal.Desktop",
            "--object-path",
            "/org/freedesktop/portal/desktop",
            "--method",
            "org.freedesktop.portal.Settings.Read",
            "org.freedesktop.appearance",
            "color-scheme",
        )
        portalColorSchemePattern.find(output)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }.getOrNull()

    private fun readGnomeTheme(): Boolean? = runCatching {
        val colorScheme = runDesktopCommand(
            "gsettings",
            "get",
            "org.gnome.desktop.interface",
            "color-scheme",
        )
        when {
            colorScheme.contains("prefer-dark", ignoreCase = true) -> true
            colorScheme.contains("prefer-light", ignoreCase = true) -> false
            else -> runDesktopCommand(
                "gsettings",
                "get",
                "org.gnome.desktop.interface",
                "gtk-theme",
            ).contains("dark", ignoreCase = true)
        }
    }.getOrNull()
}

private class ProcessThemeObserver(
    command: List<String>,
    private val isThemeEvent: (String) -> Boolean,
    private val onChanged: () -> Unit,
) : AutoCloseable {
    private val process = ProcessBuilder(command)
        .redirectErrorStream(true)
        .start()
    private val watcher = Thread {
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (isThemeEvent(line)) onChanged()
            }
        }
    }.apply {
        name = "linux-system-theme-observer"
        isDaemon = true
        start()
    }

    override fun close() {
        process.destroy()
        watcher.interrupt()
        if (process.isAlive) process.destroyForcibly()
    }
}

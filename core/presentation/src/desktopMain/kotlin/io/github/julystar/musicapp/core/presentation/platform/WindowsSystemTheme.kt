package io.github.julystar.musicapp.core.presentation.platform

import com.sun.jna.platform.win32.Advapi32
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinError
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinReg
import java.util.concurrent.atomic.AtomicBoolean

internal object WindowsSystemTheme {
    private const val PersonalizeKey =
        "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize"
    private const val AppsUseLightTheme = "AppsUseLightTheme"

    fun current(): Boolean = runCatching {
        Advapi32Util.registryGetIntValue(
            WinReg.HKEY_CURRENT_USER,
            PersonalizeKey,
            AppsUseLightTheme,
        ) == 0
    }.getOrDefault(false)

    fun observe(onChanged: (Boolean) -> Unit): AutoCloseable {
        val keyReference = WinReg.HKEYByReference()
        val openResult = Advapi32.INSTANCE.RegOpenKeyEx(
            WinReg.HKEY_CURRENT_USER,
            PersonalizeKey,
            0,
            WinNT.KEY_READ,
            keyReference,
        )
        if (openResult != WinError.ERROR_SUCCESS) return AutoCloseable { }

        val running = AtomicBoolean(true)
        val key = keyReference.value
        val watcher = Thread {
            while (running.get()) {
                val result = Advapi32.INSTANCE.RegNotifyChangeKeyValue(
                    key,
                    false,
                    WinNT.REG_NOTIFY_CHANGE_LAST_SET,
                    null,
                    false,
                )
                if (result != WinError.ERROR_SUCCESS || !running.get()) break
                onChanged(current())
            }
        }.apply {
            name = "windows-system-theme-observer"
            isDaemon = true
            start()
        }

        return AutoCloseable {
            if (running.compareAndSet(true, false)) {
                Advapi32.INSTANCE.RegCloseKey(key)
                watcher.interrupt()
            }
        }
    }
}

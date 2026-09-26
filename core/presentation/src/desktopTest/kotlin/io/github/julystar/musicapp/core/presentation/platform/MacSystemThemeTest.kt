package io.github.julystar.musicapp.core.presentation.platform

import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MacSystemThemeTest {
    @Test
    fun nativeThemeObserverRegistersAndCloses() {
        if (!System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) return

        val expectedDark = runCatching {
            runDesktopCommand("defaults", "read", "-g", "AppleInterfaceStyle") == "Dark"
        }.getOrDefault(false)
        assertEquals(expectedDark, MacSystemTheme.current())
        val notificationReceived = CountDownLatch(1)
        val observer = MacSystemTheme.observe { notificationReceived.countDown() }
        postThemeChangedNotification()
        assertTrue(notificationReceived.await(2, TimeUnit.SECONDS))
        observer.close()
    }

    private fun postThemeChangedNotification() {
        val objectiveC = NativeLibrary.getInstance("objc")
        val getClass = objectiveC.getFunction("objc_getClass")
        val registerSelector = objectiveC.getFunction("sel_registerName")
        val send = objectiveC.getFunction("objc_msgSend")
        fun selector(name: String): Pointer = checkNotNull(registerSelector.invokePointer(arrayOf(name)))
        fun pointer(receiver: Pointer, selector: String, vararg arguments: Any?): Pointer =
            checkNotNull(send.invokePointer(arrayOf(receiver, selector(selector), *arguments)))

        val centerClass = checkNotNull(getClass.invokePointer(arrayOf("NSDistributedNotificationCenter")))
        val center = pointer(centerClass, "defaultCenter")
        val stringClass = checkNotNull(getClass.invokePointer(arrayOf("NSString")))
        val name = pointer(
            stringClass,
            "stringWithUTF8String:",
            "AppleInterfaceThemeChangedNotification",
        )
        send.invokeVoid(arrayOf(center, selector("postNotificationName:object:"), name, null))
    }
}

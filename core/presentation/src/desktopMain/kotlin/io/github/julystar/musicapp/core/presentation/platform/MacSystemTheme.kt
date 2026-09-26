package io.github.julystar.musicapp.core.presentation.platform

import com.sun.jna.Callback
import com.sun.jna.Function
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.awt.EventQueue
import java.util.concurrent.ConcurrentHashMap

internal object MacSystemTheme {
    fun current(): Boolean = MacThemeBridge.isDark()

    fun observe(onChanged: (Boolean) -> Unit): AutoCloseable = MacThemeBridge.observe(onChanged)
}

private object MacThemeBridge {
    private const val NotificationName = "AppleInterfaceThemeChangedNotification"
    private const val ObserverClassName = "TidePlayerSystemThemeObserver"

    private val objectiveC by lazy { NativeLibrary.getInstance("objc") }
    private val objcGetClass: Function by lazy { objectiveC.getFunction("objc_getClass") }
    private val objcAllocateClassPair: Function by lazy { objectiveC.getFunction("objc_allocateClassPair") }
    private val objcRegisterClassPair: Function by lazy { objectiveC.getFunction("objc_registerClassPair") }
    private val classAddMethod: Function by lazy { objectiveC.getFunction("class_addMethod") }
    private val objcMsgSend: Function by lazy { objectiveC.getFunction("objc_msgSend") }
    private val selRegisterName: Function by lazy { objectiveC.getFunction("sel_registerName") }
    private val listeners = ConcurrentHashMap<Long, () -> Unit>()

    private val notificationCallback = object : MacThemeNotificationCallback {
        override fun invoke(self: Pointer?, command: Pointer?, notification: Pointer?) {
            self ?: return
            val listener = listeners[Pointer.nativeValue(self)] ?: return
            EventQueue.invokeLater(listener)
        }
    }

    private val observerClass: Pointer by lazy {
        objcGetClass.invokePointer(arrayOf(ObserverClassName)) ?: run {
            val nsObject = checkNotNull(objcGetClass.invokePointer(arrayOf("NSObject")))
            val created = checkNotNull(
                objcAllocateClassPair.invokePointer(arrayOf(nsObject, ObserverClassName, 0L)),
            )
            check(
                classAddMethod.invokeInt(
                    arrayOf(created, selector("systemThemeChanged:"), notificationCallback, "v@:@"),
                ) != 0,
            )
            objcRegisterClassPair.invokeVoid(arrayOf(created))
            created
        }
    }

    fun isDark(): Boolean = runCatching {
        val applicationClass = checkNotNull(objcGetClass.invokePointer(arrayOf("NSApplication")))
        val application = checkNotNull(sendPointer(applicationClass, "sharedApplication"))
        val appearance = checkNotNull(sendPointer(application, "effectiveAppearance"))
        val name = checkNotNull(sendPointer(appearance, "name"))
        val utf8 = checkNotNull(sendPointer(name, "UTF8String"))
        utf8.getString(0).contains("Dark", ignoreCase = true)
    }.getOrElse {
        runCatching {
            runDesktopCommand("defaults", "read", "-g", "AppleInterfaceStyle")
                .equals("Dark", ignoreCase = true)
        }.getOrDefault(false)
    }

    fun observe(onChanged: (Boolean) -> Unit): AutoCloseable {
        val observer = checkNotNull(sendPointer(observerClass, "new"))
        val centerClass = checkNotNull(objcGetClass.invokePointer(arrayOf("NSDistributedNotificationCenter")))
        val center = checkNotNull(sendPointer(centerClass, "defaultCenter"))
        val name = nsString(NotificationName)
        val key = Pointer.nativeValue(observer)
        listeners[key] = { onChanged(isDark()) }
        sendVoid(
            center,
            "addObserver:selector:name:object:",
            observer,
            selector("systemThemeChanged:"),
            name,
            null,
        )
        return AutoCloseable {
            listeners.remove(key)
            runCatching {
                sendVoid(center, "removeObserver:name:object:", observer, name, null)
                sendVoid(observer, "release")
            }
        }
    }

    private fun nsString(value: String): Pointer {
        val stringClass = checkNotNull(objcGetClass.invokePointer(arrayOf("NSString")))
        return checkNotNull(sendPointer(stringClass, "stringWithUTF8String:", value))
    }

    private fun selector(name: String): Pointer =
        checkNotNull(selRegisterName.invokePointer(arrayOf(name)))

    private fun sendPointer(receiver: Pointer, selector: String, vararg arguments: Any?): Pointer? =
        objcMsgSend.invokePointer(arrayOf(receiver, selector(selector), *arguments))

    private fun sendVoid(receiver: Pointer, selector: String, vararg arguments: Any?) {
        objcMsgSend.invokeVoid(arrayOf(receiver, selector(selector), *arguments))
    }
}

internal interface MacThemeNotificationCallback : Callback {
    fun invoke(self: Pointer?, command: Pointer?, notification: Pointer?)
}

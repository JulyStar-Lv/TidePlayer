package io.github.julystar.musicapp

import com.sun.jna.Callback
import com.sun.jna.Function
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Structure
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame

// Native title-bar coordinates measured from the 980 × 600 Apple Music reference.
private val AppleMusicTrafficLightOriginsX = doubleArrayOf(19.0, 42.0, 65.0)
private const val AppleMusicTrafficLightSize = 14.0
private const val AppleMusicTrafficLightOriginY = 7.0
private const val AppleMusicTitleBarHeight = 40.0
private const val AppleMusicWindowCornerRadius = 26.0

internal fun positionMacTrafficLights(window: JFrame) {
    val listener = object : WindowAdapter() {
        override fun windowOpened(event: WindowEvent) {
            window.removeWindowListener(this)
            MacTrafficLightBridge.offsetButtons(window.title)
        }
    }
    window.addWindowListener(listener)
}

private object MacTrafficLightBridge {
    private val objectiveC by lazy { NativeLibrary.getInstance("objc") }
    private val objcGetClass: Function by lazy { objectiveC.getFunction("objc_getClass") }
    private val objcMsgSend: Function by lazy { objectiveC.getFunction("objc_msgSend") }
    private val selRegisterName: Function by lazy { objectiveC.getFunction("sel_registerName") }
    private val system by lazy { NativeLibrary.getInstance("System") }
    private val dispatchSync: Function by lazy { system.getFunction("dispatch_sync_f") }
    private val mainQueue: Pointer by lazy {
        checkNotNull(system.getGlobalVariableAddress("_dispatch_main_q"))
    }

    fun offsetButtons(windowTitle: String) {
        runCatching {
            onAppKitThread {
                val window = findWindow(windowTitle) ?: return@onAppKitThread
                val contentView = sendPointer(window, "contentView") ?: return@onAppKitThread
                val frameView = sendPointer(contentView, "superview") ?: return@onAppKitThread
                val frameHeight = sendRect(frameView, "bounds").size.height
                configureRoundedWindow(window, frameView)
                val closeButton = sendPointer(window, "standardWindowButton:", 0L) ?: return@onAppKitThread
                val titleBarView = sendPointer(closeButton, "superview") ?: return@onAppKitThread
                val titleBarContainer = sendPointer(titleBarView, "superview") ?: return@onAppKitThread
                configureTitleBar(frameHeight, titleBarView, titleBarContainer)
                repeat(3) { buttonType ->
                    val button = sendPointer(window, "standardWindowButton:", buttonType.toLong()) ?: return@repeat
                    val size = NSSize().apply {
                        width = AppleMusicTrafficLightSize
                        height = AppleMusicTrafficLightSize
                    }
                    val origin = NSPoint().apply {
                        x = AppleMusicTrafficLightOriginsX[buttonType]
                        y = AppleMusicTrafficLightOriginY
                    }
                    sendVoid(button, "setFrameSize:", size)
                    sendVoid(button, "setFrameOrigin:", origin)
                }
            }
        }
    }

    private fun configureTitleBar(
        frameHeight: Double,
        titleBarView: Pointer,
        titleBarContainer: Pointer,
    ) {
        val width = sendRect(titleBarContainer, "frame").size.width
        val containerOrigin = NSPoint().apply {
            x = 0.0
            y = frameHeight - AppleMusicTitleBarHeight
        }
        val titleBarSize = NSSize().apply {
            this.width = width
            height = AppleMusicTitleBarHeight
        }
        sendVoid(titleBarContainer, "setFrameOrigin:", containerOrigin)
        sendVoid(titleBarContainer, "setFrameSize:", titleBarSize)
        sendVoid(titleBarView, "setFrameSize:", titleBarSize)
    }

    private fun configureRoundedWindow(window: Pointer, frameView: Pointer) {
        sendBoolean(window, "setOpaque:", false)
        val colorClass = objcGetClass.invokePointer(arrayOf("NSColor")) ?: return
        val clearColor = sendPointer(colorClass, "clearColor") ?: return
        sendVoid(window, "setBackgroundColor:", clearColor)

        sendBoolean(frameView, "setWantsLayer:", true)
        val layer = sendPointer(frameView, "layer") ?: return
        sendVoid(layer, "setCornerRadius:", AppleMusicWindowCornerRadius)
        sendBoolean(layer, "setMasksToBounds:", true)

        val stringClass = objcGetClass.invokePointer(arrayOf("NSString")) ?: return
        val continuous = sendPointer(stringClass, "stringWithUTF8String:", "continuous") ?: return
        sendVoid(layer, "setCornerCurve:", continuous)
        sendVoid(window, "invalidateShadow")
    }

    private fun onAppKitThread(block: () -> Unit) {
        val callback = object : DispatchCallback {
            override fun invoke(context: Pointer?) = block()
        }
        dispatchSync.invoke(Void.TYPE, arrayOf(mainQueue, null, callback))
    }

    private fun findWindow(title: String): Pointer? {
        val applicationClass = objcGetClass.invokePointer(arrayOf("NSApplication")) ?: return null
        val application = sendPointer(applicationClass, "sharedApplication") ?: return null
        val windows = sendPointer(application, "windows") ?: return null
        val count = sendLong(windows, "count")
        repeat(count.toInt()) { index ->
            val window = sendPointer(windows, "objectAtIndex:", index.toLong()) ?: return@repeat
            val nsTitle = sendPointer(window, "title") ?: return@repeat
            val utf8 = sendPointer(nsTitle, "UTF8String") ?: return@repeat
            if (utf8.getString(0) == title) return window
        }
        return null
    }

    private fun selector(name: String): Pointer =
        checkNotNull(selRegisterName.invokePointer(arrayOf(name)))

    private fun sendPointer(receiver: Pointer, selector: String, vararg arguments: Any): Pointer? =
        objcMsgSend.invokePointer(arrayOf(receiver, selector(selector), *arguments))

    private fun sendLong(receiver: Pointer, selector: String): Long =
        objcMsgSend.invokeLong(arrayOf(receiver, selector(selector)))

    private fun sendRect(receiver: Pointer, selector: String): NSRect =
        objcMsgSend.invoke(NSRect::class.java, arrayOf(receiver, selector(selector))) as NSRect

    private fun sendBoolean(receiver: Pointer, selector: String, value: Boolean) {
        objcMsgSend.invoke(Void.TYPE, arrayOf(receiver, selector(selector), if (value) 1.toByte() else 0.toByte()))
    }

    private fun sendVoid(receiver: Pointer, selector: String, vararg arguments: Any) {
        objcMsgSend.invoke(Void.TYPE, arrayOf(receiver, selector(selector), *arguments))
    }
}

@Structure.FieldOrder("x", "y")
internal class NSPoint : Structure(), Structure.ByValue {
    @JvmField var x: Double = 0.0
    @JvmField var y: Double = 0.0
}

@Structure.FieldOrder("width", "height")
internal class NSSize : Structure(), Structure.ByValue {
    @JvmField var width: Double = 0.0
    @JvmField var height: Double = 0.0
}

@Structure.FieldOrder("origin", "size")
internal class NSRect : Structure(), Structure.ByValue {
    @JvmField var origin: NSPoint = NSPoint()
    @JvmField var size: NSSize = NSSize()
}

private interface DispatchCallback : Callback {
    fun invoke(context: Pointer?)
}

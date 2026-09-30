package io.github.julystar.musicapp

import com.sun.jna.Callback
import com.sun.jna.Function
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Structure
import java.awt.EventQueue
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JFrame

// Native title-bar coordinates measured from the 980 × 600 Apple Music reference.
private val DesktopTrafficLightOriginsX = doubleArrayOf(17.0, 40.0, 63.0)
private const val DesktopTrafficLightSize = 18.0
private const val DesktopTrafficLightOriginY = 5.0
private const val DesktopTitleBarHeight = 40.0
private const val DesktopWindowCornerRadius = 26.0

internal fun positionMacTrafficLights(window: JFrame) {
    var repositionScheduled = false
    fun repositionButtons() {
        MacTrafficLightBridge.offsetButtons(
            windowTitle = window.title,
            frameWidth = window.width.toDouble(),
            frameHeight = window.height.toDouble(),
        )
    }
    fun scheduleReposition() {
        if (repositionScheduled) return
        repositionScheduled = true
        EventQueue.invokeLater {
            repositionScheduled = false
            repositionButtons()
        }
    }

    window.addWindowListener(object : WindowAdapter() {
        override fun windowOpened(event: WindowEvent) {
            scheduleReposition()
        }

        override fun windowActivated(event: WindowEvent) {
            scheduleReposition()
        }

        override fun windowDeiconified(event: WindowEvent) {
            scheduleReposition()
        }
    })
    window.addComponentListener(object : ComponentAdapter() {
        override fun componentResized(event: ComponentEvent) {
            scheduleReposition()
        }
    })
}

private object MacTrafficLightBridge {
    private val objectiveC by lazy { NativeLibrary.getInstance("objc") }
    private val objcGetClass: Function by lazy { objectiveC.getFunction("objc_getClass") }
    private val objcMsgSend: Function by lazy { objectiveC.getFunction("objc_msgSend") }
    private val selRegisterName: Function by lazy { objectiveC.getFunction("sel_registerName") }
    private val system by lazy { NativeLibrary.getInstance("System") }
    private val dispatchAsync: Function by lazy { system.getFunction("dispatch_async_f") }
    private val pendingCallbacks = ConcurrentHashMap.newKeySet<DispatchCallback>()
    private val mainQueue: Pointer by lazy {
        checkNotNull(system.getGlobalVariableAddress("_dispatch_main_q"))
    }

    fun offsetButtons(
        windowTitle: String,
        frameWidth: Double,
        frameHeight: Double,
    ) {
        runCatching {
            onAppKitThread {
                val window = findWindow(windowTitle) ?: return@onAppKitThread
                val contentView = sendPointer(window, "contentView") ?: return@onAppKitThread
                val frameView = sendPointer(contentView, "superview") ?: return@onAppKitThread
                configureRoundedWindow(window, frameView)
                val closeButton = sendPointer(window, "standardWindowButton:", 0L) ?: return@onAppKitThread
                val titleBarView = sendPointer(closeButton, "superview") ?: return@onAppKitThread
                val titleBarContainer = sendPointer(titleBarView, "superview") ?: return@onAppKitThread
                configureTitleBar(frameWidth, frameHeight, titleBarView, titleBarContainer)
                repeat(3) { buttonType ->
                    val button = sendPointer(window, "standardWindowButton:", buttonType.toLong()) ?: return@repeat
                    val size = NSSize().apply {
                        width = DesktopTrafficLightSize
                        height = DesktopTrafficLightSize
                    }
                    val origin = NSPoint().apply {
                        x = DesktopTrafficLightOriginsX[buttonType]
                        y = DesktopTrafficLightOriginY
                    }
                    sendVoid(button, "setFrameSize:", size)
                    sendVoid(button, "setFrameOrigin:", origin)
                }
            }
        }
    }

    private fun configureTitleBar(
        frameWidth: Double,
        frameHeight: Double,
        titleBarView: Pointer,
        titleBarContainer: Pointer,
    ) {
        val containerOrigin = NSPoint().apply {
            x = 0.0
            y = frameHeight - DesktopTitleBarHeight
        }
        val titleBarSize = NSSize().apply {
            width = frameWidth
            height = DesktopTitleBarHeight
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
        sendVoid(layer, "setCornerRadius:", DesktopWindowCornerRadius)
        sendBoolean(layer, "setMasksToBounds:", true)

        val stringClass = objcGetClass.invokePointer(arrayOf("NSString")) ?: return
        val continuous = sendPointer(stringClass, "stringWithUTF8String:", "continuous") ?: return
        sendVoid(layer, "setCornerCurve:", continuous)
        sendVoid(window, "invalidateShadow")
    }

    private fun onAppKitThread(block: () -> Unit) {
        val callback = object : DispatchCallback {
            override fun invoke(context: Pointer?) {
                try {
                    block()
                } finally {
                    pendingCallbacks.remove(this)
                }
            }
        }
        pendingCallbacks.add(callback)
        try {
            dispatchAsync.invoke(Void.TYPE, arrayOf(mainQueue, null, callback))
        } catch (error: Throwable) {
            pendingCallbacks.remove(callback)
            throw error
        }
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

private interface DispatchCallback : Callback {
    fun invoke(context: Pointer?)
}

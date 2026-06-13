package com.inmoair.xrcompanion.client.network

import com.inmoair.xrcompanion.shared.protocol.XRCommand
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper that builds typed XRCommand objects and delegates to XRWebSocketClient.
 * All calls are fire-and-forget.
 */
@Singleton
class CommandSender @Inject constructor(
    private val client: XRWebSocketClient,
) {
    // --- touch ---
    fun sendTap(nx: Float, ny: Float) =
        send(XRCommand(type = "touch", action = "tap", x = nx, y = ny, timestamp = now()))

    fun sendDoubleTap(nx: Float, ny: Float) =
        send(XRCommand(type = "touch", action = "double_tap", x = nx, y = ny, timestamp = now()))

    fun sendLongPress(nx: Float, ny: Float) =
        send(XRCommand(type = "touch", action = "long_press", x = nx, y = ny, timestamp = now()))

    fun sendMove(nx: Float, ny: Float) =
        send(XRCommand(type = "touch", action = "move", x = nx, y = ny, timestamp = now()))

    fun sendSwipe(x1: Float, y1: Float, x2: Float, y2: Float) =
        send(XRCommand(type = "touch", action = "swipe", x = x1, y = y1, x2 = x2, y2 = y2, timestamp = now()))

    // --- scroll ---
    fun sendScrollVertical(delta: Int, speed: Float = 1f) =
        send(XRCommand(type = "scroll", axis = "vertical", delta = delta, speed = speed))

    fun sendScrollHorizontal(delta: Int, speed: Float = 1f) =
        send(XRCommand(type = "scroll", axis = "horizontal", delta = delta, speed = speed))

    // --- system navigation ---
    fun sendBack()    = send(XRCommand(type = "touch", action = "back"))
    fun sendHome()    = send(XRCommand(type = "touch", action = "home"))
    fun sendRecents() = send(XRCommand(type = "touch", action = "recents"))

    // --- keyboard ---
    fun sendText(text: String) =
        send(XRCommand(type = "keyboard", action = "text", value = text))

    fun sendBackspace() = send(XRCommand(type = "keyboard", action = "backspace"))
    fun sendEnter()     = send(XRCommand(type = "keyboard", action = "enter"))
    fun sendKeyCode(keyCode: Int) = send(XRCommand(type = "keyboard", action = "keycode", keyCode = keyCode))

    // --- system controls ---
    fun setBrightness(value: Float) =
        send(XRCommand(type = "system", action = "set_brightness", floatValue = value))

    fun setVolume(value: Float) =
        send(XRCommand(type = "system", action = "set_volume", floatValue = value))

    fun volumeUp()   = send(XRCommand(type = "system", action = "volume_up"))
    fun volumeDown() = send(XRCommand(type = "system", action = "volume_down"))
    fun mute()       = send(XRCommand(type = "system", action = "mute"))
    fun sleep()      = send(XRCommand(type = "system", action = "sleep"))
    fun wake()       = send(XRCommand(type = "system", action = "wake"))

    // --- apps ---
    fun requestAppList() = send(XRCommand(type = "apps", action = "list"))

    fun setAppBackground(pkg: String, allowed: Boolean) =
        send(XRCommand(type = "apps", action = "set_background_allowed", packageName = pkg, allowed = allowed))

    // --- spacewalker ---
    fun spaceWalkerZoomIn()  = send(XRCommand(type = "spacewalker", action = "zoom_in"))
    fun spaceWalkerZoomOut() = send(XRCommand(type = "spacewalker", action = "zoom_out"))

    fun spaceWalkerSetRotation(degrees: Float) =
        send(XRCommand(type = "spacewalker", action = "set_rotation", floatValue = degrees))

    fun spaceWalkerAddScreen()    = send(XRCommand(type = "spacewalker", action = "add_screen"))
    fun spaceWalkerRemoveScreen() = send(XRCommand(type = "spacewalker", action = "remove_screen"))

    // --- misc ---
    fun ping() = client.send("""{"type":"ping"}""")

    fun requestScreenshot() = send(XRCommand(type = "screenshot", action = "capture"))

    private fun send(cmd: XRCommand) { client.sendCommand(cmd) }
    private fun now() = System.currentTimeMillis()
}

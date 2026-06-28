package com.inmoair.xrcompanion.core.command

import android.util.Log
import com.inmoair.xrcompanion.core.accessibility.XRAccessibilityService
import com.inmoair.xrcompanion.shared.protocol.XRCommand
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TouchCommandHandler @Inject constructor() {
    private val TAG = "TouchHandler"

    fun handle(cmd: XRCommand) {
        val svc = XRAccessibilityService.instance ?: run {
            Log.w(TAG, "Accessibility service not running")
            return
        }
        // Coordinates are normalized [0..1] — convert to screen pixels
        val metrics = svc.resources.displayMetrics
        val screenW = metrics.widthPixels.toFloat()
        val screenH = metrics.heightPixels.toFloat()
        val px = cmd.x * screenW
        val py = cmd.y * screenH

        when (cmd.action) {
            "move"        -> svc.injectMove(px, py)
            "cursor_show" -> svc.showCursor(px, py)
            "cursor_hide" -> svc.hideCursor()
            "down"        -> { /* pointer down — handled as part of swipe continuity */ }
            "up"          -> svc.injectTap(px, py)
            "tap"         -> svc.injectTap(px, py)
            "double_tap"  -> svc.injectDoubleTap(px, py)
            "long_press"  -> svc.injectLongPress(px, py)
            "swipe"       -> {
                val px2 = cmd.x2 * screenW
                val py2 = cmd.y2 * screenH
                svc.injectSwipe(px, py, px2, py2)
            }
            "back"        -> svc.performBack()
            "home"        -> svc.performHome()
            "recents"     -> svc.performRecents()
            else          -> Log.w(TAG, "Unknown touch action: ${cmd.action}")
        }
    }

    fun handleScroll(cmd: XRCommand) {
        val svc = XRAccessibilityService.instance ?: run {
            Log.w(TAG, "Accessibility service not running for scroll")
            return
        }
        Log.v(TAG, "scroll axis=${cmd.axis} delta=${cmd.delta}")
        svc.injectScroll(cmd.axis, cmd.delta)
    }
}

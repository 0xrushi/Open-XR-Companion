package com.inmoair.xrcompanion.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class XRAccessibilityService : AccessibilityService() {

    companion object {
        private val TAG = "XRAccessibility"
        /** Singleton reference so command handlers can call it */
        @Volatile
        var instance: XRAccessibilityService? = null
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var cursorOverlay: VirtualCursorOverlay? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        cursorOverlay = VirtualCursorOverlay(this)
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        cursorOverlay?.destroy()
        cursorOverlay = null
        instance = null
        Log.i(TAG, "Accessibility service destroyed")
    }

    // -----------------------------------------------------------------------
    // Shadow text buffer
    // -----------------------------------------------------------------------
    // We never read focused.text at injection time because getText() may return
    // the field's hint/placeholder ("Search App", "Message…") when the field is
    // empty, even if isShowingHintText is not reliably set by the app.
    //
    // Instead, we maintain our own shadow of the field's real content:
    //   • Sync it once when a new editable field gains focus (reading actual text,
    //     safely ignoring hint via isShowingHintText + null/empty guard).
    //   • injectText / injectBackspace update the shadow and pass it to
    //     ACTION_SET_TEXT — no further reads from the node.
    private var shadowText: String = ""

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED) return
        val src = event.source ?: return
        if (!src.isEditable) return
        // Read the real text once on focus, treating hint text as empty.
        val rawText = src.text?.toString() ?: ""
        shadowText = if (src.isShowingHintText || rawText.isEmpty()) "" else rawText
    }

    override fun onInterrupt() = Unit

    // -----------------------------------------------------------------------
    // System navigation
    // -----------------------------------------------------------------------

    fun performBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun performHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun performRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)

    // -----------------------------------------------------------------------
    // Touch / gesture injection
    // -----------------------------------------------------------------------

    fun injectMove(x: Float, y: Float) {
        showCursor(x, y)
    }

    fun showCursor(x: Float, y: Float) {
        cursorOverlay?.showAt(x, y)
    }

    fun hideCursor() {
        cursorOverlay?.hide()
    }

    /** Single tap */
    fun injectTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        dispatchGesture(buildGesture(path, 50L, 0L), null, null)
    }

    /** Double tap */
    fun injectDoubleTap(x: Float, y: Float) {
        scope.launch {
            injectTap(x, y)
            delay(80)
            injectTap(x, y)
        }
    }

    /** Long press */
    fun injectLongPress(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        dispatchGesture(buildGesture(path, 800L, 0L), null, null)
    }

    /** Swipe from (x1,y1) to (x2,y2) */
    fun injectSwipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 200L) {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        dispatchGesture(buildGesture(path, durationMs, 0L), null, null)
    }

    /** Scroll by injecting a swipe in the opposite direction */
    fun injectScroll(axis: String, delta: Int) {
        val metrics = resources.displayMetrics
        val w = metrics.widthPixels.toFloat()
        val h = metrics.heightPixels.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val dist = delta.coerceIn(-600, 600).toFloat()
        if (axis == "vertical") {
            injectSwipe(cx, cy, cx, cy - dist, 150L)
        } else {
            injectSwipe(cx, cy, cx - dist, cy, 150L)
        }
    }

    // -----------------------------------------------------------------------
    // Text / keyboard injection
    // -----------------------------------------------------------------------

    fun injectText(text: String) {
        val focused = findFocusedEditableNode()
        if (focused != null) {
            // Use shadow, not focused.text — getText() can return the hint placeholder.
            shadowText += text
            val args = android.os.Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    shadowText,
                )
            }
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } else {
            // No focused editable node — fall back to clipboard paste.
            try {
                val cm = getSystemService(android.content.ClipboardManager::class.java)
                cm.setPrimaryClip(android.content.ClipData.newPlainText("xr_input", text))
                rootInActiveWindow
                    ?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    ?.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            } catch (e: Exception) {
                Log.w(TAG, "Clipboard fallback failed: ${e.message}")
            }
        }
    }

    fun injectBackspace() {
        val focused = findFocusedEditableNode() ?: return
        if (shadowText.isEmpty()) return
        shadowText = shadowText.dropLast(1)
        val args = android.os.Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                shadowText,
            )
        }
        focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun injectEnter() = injectKeyEvent(android.view.KeyEvent.KEYCODE_ENTER)

    // -----------------------------------------------------------------------
    // Screenshot capture (API 30+, minSdk 31 on Core so always available)
    // -----------------------------------------------------------------------

    /**
     * Captures a screenshot and delivers a *software* Bitmap to [onResult] on the main thread.
     *
     * We do the hardware→software copy HERE, while the GPU context is still valid, before
     * handing off to callers who might run on arbitrary threads.  Null means capture failed.
     */
    fun captureScreenshot(onResult: (Bitmap?) -> Unit) {
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val softBmp = try {
                            // wrapHardwareBuffer keeps a reference to the HardwareBuffer; do NOT close it.
                            // copy(ARGB_8888) reads from GPU → CPU. Must be done while GPU context is valid.
                            Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)
                        } catch (e: Exception) {
                            Log.w(TAG, "Hardware bitmap copy failed: ${e.message}")
                            null
                        }
                        onResult(softBmp)
                    }
                    override fun onFailure(errorCode: Int) {
                        Log.w(TAG, "takeScreenshot failed, errorCode=$errorCode")
                        onResult(null)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "takeScreenshot threw: ${e.message}")
            onResult(null)
        }
    }

    private fun injectKeyEvent(keyCode: Int) {
        // Attempt via global key event (requires INJECT_EVENTS permission which is system-level)
        // Fall back to root node action
        rootInActiveWindow?.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun findFocusedEditableNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?.takeIf { it.isEditable }
    }

    private fun buildGesture(path: Path, duration: Long, startTime: Long): GestureDescription {
        val stroke = GestureDescription.StrokeDescription(path, startTime, duration)
        return GestureDescription.Builder().addStroke(stroke).build()
    }
}

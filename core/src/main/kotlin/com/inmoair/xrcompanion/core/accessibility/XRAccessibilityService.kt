package com.inmoair.xrcompanion.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

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
    private var pendingVerticalScrollDelta = 0
    private var pendingHorizontalScrollDelta = 0
    private var scrollDispatching = false
    private var scrollFlushJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        cursorOverlay = VirtualCursorOverlay(this)
        setSoftKeyboardHidden(true)
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        setSoftKeyboardHidden(false)
        scrollFlushJob?.cancel()
        scrollFlushJob = null
        cursorOverlay?.destroy()
        cursorOverlay = null
        lastEditableNode?.recycle()
        lastEditableNode = null
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
    private var lastEditableNode: AccessibilityNodeInfo? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED) return
        val src = event.source ?: return
        if (!src.isEditable) return
        setSoftKeyboardHidden(true)
        lastEditableNode?.recycle()
        lastEditableNode = AccessibilityNodeInfo.obtain(src)
        // Read the real text once on focus, treating hint text as empty.
        val rawText = src.text?.toString() ?: ""
        shadowText = if (src.isShowingHintText || rawText.isEmpty()) "" else rawText
        Log.v(TAG, "Editable focused class=${src.className} pkg=${src.packageName} textLength=${shadowText.length}")
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
        val (safeX, safeY) = clampToDisplay(x, y)
        val path = Path().apply { moveTo(safeX, safeY) }
        safeDispatchGesture(buildGesture(path, 50L, 0L), null)
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
        val (safeX, safeY) = clampToDisplay(x, y)
        val path = Path().apply { moveTo(safeX, safeY) }
        safeDispatchGesture(buildGesture(path, 800L, 0L), null)
    }

    /** Swipe from (x1,y1) to (x2,y2) */
    fun injectSwipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 200L) {
        val (safeX1, safeY1) = clampToDisplay(x1, y1)
        val (safeX2, safeY2) = clampToDisplay(x2, y2)
        val path = Path().apply { moveTo(safeX1, safeY1); lineTo(safeX2, safeY2) }
        safeDispatchGesture(buildGesture(path, durationMs, 0L), null)
    }

    /** Scroll by injecting a swipe in the opposite direction */
    fun injectScroll(axis: String, delta: Int) {
        if (delta == 0) return
        scope.launch {
            if (axis == "horizontal") {
                pendingHorizontalScrollDelta =
                    (pendingHorizontalScrollDelta + delta).coerceIn(-1200, 1200)
            } else {
                pendingVerticalScrollDelta =
                    (pendingVerticalScrollDelta + delta).coerceIn(-1200, 1200)
            }
            scheduleScrollFlush()
        }
    }

    private fun scheduleScrollFlush(delayMs: Long = 35L) {
        if (scrollDispatching || scrollFlushJob != null) return
        scrollFlushJob = scope.launch {
            delay(delayMs)
            scrollFlushJob = null
            flushPendingScroll()
        }
    }

    private fun flushPendingScroll() {
        if (scrollDispatching) return

        val axis: String
        val delta: Int
        if (pendingVerticalScrollDelta != 0) {
            axis = "vertical"
            delta = pendingVerticalScrollDelta
            pendingVerticalScrollDelta = 0
        } else if (pendingHorizontalScrollDelta != 0) {
            axis = "horizontal"
            delta = pendingHorizontalScrollDelta
            pendingHorizontalScrollDelta = 0
        } else {
            return
        }

        scrollDispatching = true
        val handledByNode = performAccessibilityScroll(axis, delta)
        Log.v(TAG, "scroll axis=$axis delta=$delta nodeHandled=$handledByNode")
        if (handledByNode) {
            finishScrollGesture()
            return
        }

        val accepted = dispatchScrollGesture(axis, delta)
        if (!accepted) {
            scrollDispatching = false
            scheduleScrollFlush(50L)
        }
    }

    private fun performAccessibilityScroll(axis: String, delta: Int): Boolean {
        val root = rootInActiveWindow ?: return false
        val actions = scrollActionIds(axis, delta)
        val repetitions = (abs(delta) / 120).coerceIn(1, 5)
        val candidates = mutableListOf<AccessibilityNodeInfo>()

        root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { candidates += it }
        root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)?.let { candidates += it }
        if (root.isScrollable) candidates += root
        collectScrollableNodes(root, candidates, depth = 0)

        var handled = false
        repeat(repetitions) {
            for (node in candidates) {
                for (action in actions) {
                    if (node.performAction(action)) {
                        handled = true
                        return@repeat
                    }
                }
            }
        }
        return handled
    }

    private fun scrollActionIds(axis: String, delta: Int): IntArray {
        return if (axis == "horizontal") {
            if (delta > 0) {
                intArrayOf(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id,
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,
                )
            } else {
                intArrayOf(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id,
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
                )
            }
        } else {
            if (delta > 0) {
                intArrayOf(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id,
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,
                )
            } else {
                intArrayOf(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id,
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
                )
            }
        }
    }

    private fun collectScrollableNodes(
        node: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeInfo>,
        depth: Int,
    ) {
        if (depth > 8) return
        if (node.isScrollable) out += node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectScrollableNodes(child, out, depth + 1)
        }
    }

    private fun dispatchScrollGesture(axis: String, delta: Int): Boolean {
        val metrics = resources.displayMetrics
        val w = metrics.widthPixels.toFloat()
        val h = metrics.heightPixels.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val rawDist = (delta * 8f).coerceIn(-1400f, 1400f)
        if (abs(rawDist) < 1f) return false
        val dist = if (abs(rawDist) < 80f) {
            if (rawDist > 0f) 140f else -140f
        } else {
            rawDist
        }
        val path = Path().apply {
            if (axis == "vertical") {
                val (startX, startY) = clampToDisplay(cx, cy)
                val (endX, endY) = clampToDisplay(cx, cy - dist)
                moveTo(startX, startY)
                lineTo(endX, endY)
            } else {
                val (startX, startY) = clampToDisplay(cx, cy)
                val (endX, endY) = clampToDisplay(cx - dist, cy)
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
        }
        return safeDispatchGesture(
            buildGesture(path, 90L, 0L),
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    scope.launch { finishScrollGesture() }
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    scope.launch { finishScrollGesture() }
                }
            },
        )
    }

    private fun finishScrollGesture() {
        scrollDispatching = false
        if (pendingVerticalScrollDelta != 0 || pendingHorizontalScrollDelta != 0) {
            scheduleScrollFlush(16L)
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
            val handled = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            Log.v(TAG, "injectText len=${text.length} shadowLen=${shadowText.length} handled=$handled")
        } else {
            Log.w(TAG, "No focused editable node for text; trying last editable and paste fallback")
            if (performSetTextOnLastEditable(text)) return
            // No focused editable node — fall back to clipboard paste.
            try {
                val cm = getSystemService(android.content.ClipboardManager::class.java)
                cm.setPrimaryClip(android.content.ClipData.newPlainText("xr_input", text))
                val pasted = rootInActiveWindow
                    ?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    ?.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                Log.v(TAG, "Clipboard paste fallback handled=$pasted")
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
        val handled = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        Log.v(TAG, "injectBackspace shadowLen=${shadowText.length} handled=$handled")
    }

    fun injectEnter() {
        val inputFocus = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val imeEnterAction = AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
        if (inputFocus?.performAction(imeEnterAction) == true) {
            return
        }

        if (findFocusedEditableNode() != null) {
            injectText("\n")
            return
        }

        Log.w(TAG, "No focused editable node for enter")
    }

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

    private fun performSetTextOnLastEditable(text: String): Boolean {
        val node = lastEditableNode ?: return false
        return try {
            val nextText = shadowText + text
            val args = android.os.Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    nextText,
                )
            }
            val handled = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (handled) shadowText = nextText
            Log.v(TAG, "lastEditable injectText len=${text.length} shadowLen=${nextText.length} handled=$handled")
            handled
        } catch (e: Exception) {
            Log.w(TAG, "Last editable text injection failed: ${e.message}")
            false
        }
    }

    private fun clampToDisplay(x: Float, y: Float): Pair<Float, Float> {
        val metrics = resources.displayMetrics
        val maxX = (metrics.widthPixels - 1).coerceAtLeast(0).toFloat()
        val maxY = (metrics.heightPixels - 1).coerceAtLeast(0).toFloat()
        return x.coerceIn(0f, maxX) to y.coerceIn(0f, maxY)
    }

    private fun safeDispatchGesture(
        gesture: GestureDescription,
        callback: GestureResultCallback?,
    ): Boolean {
        return try {
            dispatchGesture(gesture, callback, null)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Gesture rejected: ${e.message}")
            false
        }
    }

    private fun setSoftKeyboardHidden(hidden: Boolean) {
        val mode = if (hidden) SHOW_MODE_HIDDEN else SHOW_MODE_AUTO
        val modeName = if (hidden) "hidden" else "auto"
        runCatching {
            softKeyboardController.setShowMode(mode)
        }.onSuccess { accepted ->
            if (!accepted) Log.w(TAG, "Soft keyboard show mode $modeName was rejected")
        }.onFailure {
            Log.w(TAG, "Failed to set soft keyboard show mode $modeName: ${it.message}")
        }
    }

    private fun buildGesture(path: Path, duration: Long, startTime: Long): GestureDescription {
        val stroke = GestureDescription.StrokeDescription(path, startTime, duration)
        return GestureDescription.Builder().addStroke(stroke).build()
    }
}

package com.inmoair.xrcompanion.core.accessibility

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/**
 * App-owned cursor shown above other windows by the accessibility service.
 *
 * Android's public accessibility gesture API can tap and swipe, but it cannot
 * emit SOURCE_MOUSE hover/move events. This overlay gives remote cursor mode a
 * visible pointer while taps are still injected at the same screen coordinate.
 */
internal class VirtualCursorOverlay(context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private val cursorTipInsetPx = (4f * density).roundToInt()
    private val cursorWidthPx = (64f * density).roundToInt()
    private val cursorHeightPx = (72f * density).roundToInt()
    private val cursorView = CursorView(context, cursorTipInsetPx.toFloat())
    private val updatePosted = AtomicBoolean(false)
    private val pendingVersion = AtomicInteger(0)

    @Volatile private var visibleRequested = false
    @Volatile private var pendingX = 0f
    @Volatile private var pendingY = 0f

    private var attached = false
    private var appliedVersion = 0
    private var layoutParams: WindowManager.LayoutParams? = null

    fun showAt(x: Float, y: Float) {
        visibleRequested = true
        pendingX = x
        pendingY = y
        pendingVersion.incrementAndGet()
        scheduleUpdate()
    }

    fun hide() {
        visibleRequested = false
        if (Looper.myLooper() == Looper.getMainLooper()) {
            hideOnMain()
        } else {
            mainHandler.post { hideOnMain() }
        }
    }

    fun destroy() {
        hide()
    }

    private fun scheduleUpdate() {
        if (!updatePosted.compareAndSet(false, true)) return

        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyPendingPosition()
        } else {
            mainHandler.post { applyPendingPosition() }
        }
    }

    private fun applyPendingPosition() {
        try {
            if (!visibleRequested) {
                hideOnMain()
                return
            }

            val version = pendingVersion.get()
            ensureAttached()
            val params = layoutParams
            if (params != null) {
                params.x = (pendingX - cursorTipInsetPx).roundToInt()
                params.y = (pendingY - cursorTipInsetPx).roundToInt()
                if (attached) {
                    runCatching {
                        windowManager.updateViewLayout(cursorView, params)
                    }.onFailure {
                        Log.w(TAG, "Failed to move cursor overlay: ${it.message}")
                    }
                }
            }
            cursorView.invalidate()
            appliedVersion = version
        } finally {
            updatePosted.set(false)
        }

        if (visibleRequested && pendingVersion.get() != appliedVersion) {
            scheduleUpdate()
        }
    }

    private fun hideOnMain() {
        if (!attached) return
        runCatching {
            windowManager.removeView(cursorView)
        }.onFailure {
            Log.w(TAG, "Failed to remove cursor overlay: ${it.message}")
        }
        attached = false
        layoutParams = null
    }

    private fun ensureAttached() {
        if (attached) return

        val params = WindowManager.LayoutParams(
            cursorWidthPx,
            cursorHeightPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (pendingX - cursorTipInsetPx).roundToInt()
            y = (pendingY - cursorTipInsetPx).roundToInt()
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            setTitle("XR Virtual Cursor")
        }

        runCatching {
            windowManager.addView(cursorView, params)
            layoutParams = params
            attached = true
        }.onFailure {
            Log.w(TAG, "Failed to add cursor overlay: ${it.message}")
        }
    }

    private class CursorView(
        context: Context,
        private val tipInset: Float,
    ) : View(context) {
        private val density = resources.displayMetrics.density
        private val cursorPath = Path()

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 28, 40)
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * density
            strokeJoin = Paint.Join.ROUND
        }
        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 0, 0, 0)
            style = Paint.Style.FILL
        }

        init {
            setWillNotDraw(false)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            buildCursorPath(tipInset, tipInset)

            canvas.save()
            canvas.translate(2.5f * density, 2.5f * density)
            canvas.drawPath(cursorPath, shadowPaint)
            canvas.restore()

            canvas.drawPath(cursorPath, fillPaint)
            canvas.drawPath(cursorPath, strokePaint)
        }

        private fun buildCursorPath(x: Float, y: Float) {
            val d = density
            cursorPath.reset()
            cursorPath.moveTo(x, y)
            cursorPath.lineTo(x, y + 30f * d)
            cursorPath.lineTo(x + 8.5f * d, y + 22f * d)
            cursorPath.lineTo(x + 14.5f * d, y + 37f * d)
            cursorPath.lineTo(x + 22f * d, y + 34f * d)
            cursorPath.lineTo(x + 16f * d, y + 20f * d)
            cursorPath.lineTo(x + 28f * d, y + 20f * d)
            cursorPath.close()
        }
    }

    private companion object {
        private const val TAG = "VirtualCursorOverlay"
    }
}

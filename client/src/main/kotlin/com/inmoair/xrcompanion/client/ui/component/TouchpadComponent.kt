package com.inmoair.xrcompanion.client.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.inmoair.xrcompanion.client.ui.theme.TouchpadBorder
import com.inmoair.xrcompanion.client.ui.theme.TouchpadSurface
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class TouchpadMode { CURSOR, DIRECT }

/**
 * Touchpad surface — supports two modes:
 *
 * DIRECT (absolute, "mobile mirror"):
 *   Finger position maps directly to the same normalised position on the glasses screen.
 *   Useful when mirroring a phone screen 1:1.
 *    - Finger down + up, no movement      → onTap(nx, ny)
 *    - Two quick taps                      → onDoubleTap(nx, ny)
 *    - Finger held still                   → onLongPress(nx, ny)
 *    - Finger moves then lifts             → onSwipe(start → end)
 *
 * CURSOR (relative, laptop-trackpad):
 *   Finger movement nudges a virtual cursor by a *delta*; the glasses show the cursor
 *   moving. Taps act on wherever the cursor currently sits, not where you touched.
 *    - Finger drag                         → onCursorMove(dxNorm, dyNorm) continuously
 *    - Finger down + up, no movement       → onCursorTap()
 *    - Two quick taps                      → onCursorDoubleTap()
 *    - Finger held still                   → onCursorLongPress()
 *
 * Coordinates / deltas are normalised [0..1] relative to the touchpad dimensions.
 */
@Composable
fun TouchpadSurface(
    modifier: Modifier = Modifier,
    mode: TouchpadMode = TouchpadMode.CURSOR,
    // Direct-mode callbacks
    onTap: (nx: Float, ny: Float) -> Unit,
    onDoubleTap: (nx: Float, ny: Float) -> Unit,
    onLongPress: (nx: Float, ny: Float) -> Unit,
    onSwipe: (x1: Float, y1: Float, x2: Float, y2: Float) -> Unit,
    // Cursor-mode callbacks
    onCursorMove: (dxNorm: Float, dyNorm: Float) -> Unit = { _, _ -> },
    onCursorTap: () -> Unit = {},
    onCursorDoubleTap: () -> Unit = {},
    onCursorLongPress: () -> Unit = {},
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()

    var lastTapTime by remember { mutableLongStateOf(0L) }
    var lastTapPos  by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .background(TouchpadSurface, RoundedCornerShape(16.dp))
            .border(1.dp, TouchpadBorder, RoundedCornerShape(16.dp))
            .pointerInput(mode) {
                val dragSlop    = viewConfiguration.touchSlop
                val longPressMs = viewConfiguration.longPressTimeoutMillis
                val doubleTapMs = viewConfiguration.doubleTapTimeoutMillis

                awaitEachGesture {
                    val down      = awaitFirstDown(requireUnconsumed = false)
                    val startPos  = down.position
                    var curPos    = startPos
                    var prevPos   = startPos          // for incremental cursor deltas
                    var isDrag    = false
                    var longFired = false
                    var multiTouch = false

                    // Long-press fires if finger sits still long enough
                    var lpJob: Job? = scope.launch {
                        delay(longPressMs)
                        if (!isDrag) {
                            longFired = true
                            if (mode == TouchpadMode.CURSOR) {
                                onCursorLongPress()
                            } else {
                                val w = size.width.coerceAtLeast(1).toFloat()
                                val h = size.height.coerceAtLeast(1).toFloat()
                                onLongPress(
                                    (startPos.x / w).coerceIn(0f, 1f),
                                    (startPos.y / h).coerceIn(0f, 1f),
                                )
                            }
                        }
                    }

                    try {
                        while (true) {
                            val event  = awaitPointerEvent()
                            val pressedChanges = event.changes.filter { it.pressed }
                            if (pressedChanges.isEmpty()) {
                                event.changes.forEach { it.consume() }
                                break
                            }

                            if (mode == TouchpadMode.CURSOR && pressedChanges.size >= 2) {
                                multiTouch = true
                                isDrag = true
                                lpJob?.cancel()
                                lpJob = null
                                event.changes.forEach { it.consume() }
                                continue
                            }

                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                            if (change.pressed) {
                                curPos = change.position
                                val dist = (curPos - startPos).getDistance()
                                if (!isDrag && dist > dragSlop) {
                                    isDrag = true
                                    lpJob?.cancel()   // started moving → not a long press
                                    lpJob = null
                                }
                                // In cursor mode emit incremental deltas while dragging.
                                if (mode == TouchpadMode.CURSOR && isDrag) {
                                    val w  = size.width.coerceAtLeast(1).toFloat()
                                    val h  = size.height.coerceAtLeast(1).toFloat()
                                    val dx = (curPos.x - prevPos.x) / w
                                    val dy = (curPos.y - prevPos.y) / h
                                    if (dx != 0f || dy != 0f) onCursorMove(dx, dy)
                                    change.consume()
                                }
                                prevPos = curPos
                            } else {
                                change.consume()
                                break
                            }
                        }
                    } finally {
                        lpJob?.cancel()
                    }

                    if (longFired) return@awaitEachGesture

                    val w  = size.width.coerceAtLeast(1).toFloat()
                    val h  = size.height.coerceAtLeast(1).toFloat()

                    if (mode == TouchpadMode.CURSOR) {
                        // Drag already moved the cursor; only a stationary touch is a click.
                        if (!isDrag && !multiTouch) {
                            val now     = System.currentTimeMillis()
                            val prevAge = now - lastTapTime
                            if (prevAge < doubleTapMs) {
                                lastTapTime = 0L
                                onCursorDoubleTap()
                            } else {
                                lastTapTime = now
                                onCursorTap()
                            }
                        }
                    } else if (isDrag) {
                        // ── Swipe: send start → end, let Core inject the gesture ──
                        onSwipe(
                            (startPos.x / w).coerceIn(0f, 1f),
                            (startPos.y / h).coerceIn(0f, 1f),
                            (curPos.x / w).coerceIn(0f, 1f),
                            (curPos.y / h).coerceIn(0f, 1f),
                        )
                    } else {
                        // ── Tap or double-tap ─────────────────────────────────────
                        val now      = System.currentTimeMillis()
                        val nx       = (startPos.x / w).coerceIn(0f, 1f)
                        val ny       = (startPos.y / h).coerceIn(0f, 1f)
                        val prevAge  = now - lastTapTime
                        val prevDist = (startPos - lastTapPos).getDistance()

                        if (prevAge < doubleTapMs && prevDist < dragSlop * 4) {
                            lastTapTime = 0L        // reset — no triple-tap
                            onDoubleTap(nx, ny)
                        } else {
                            lastTapTime = now
                            lastTapPos  = startPos
                            onTap(nx, ny)
                        }
                    }
                }
            }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Scroll strips
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun VerticalScrollStrip(
    modifier: Modifier = Modifier,
    onScroll: (delta: Int) -> Unit,
) {
    var lastY by remember { mutableFloatStateOf(0f) }
    var scrollAccumY by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = modifier
            .width(28.dp)
            .fillMaxHeight()
            .background(TouchpadSurface, RoundedCornerShape(14.dp))
            .border(1.dp, TouchpadBorder, RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    lastY = down.position.y
                    scrollAccumY = 0f
                    while (true) {
                        val event  = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.pressed) {
                            val dy = change.position.y - lastY
                            lastY  = change.position.y
                            scrollAccumY += -(dy * 7.5f)
                            val delta = scrollAccumY.toInt()
                            if (delta != 0) {
                                onScroll(delta)
                                scrollAccumY -= delta
                            }
                            change.consume()
                        } else {
                            change.consume(); break
                        }
                    }
                }
            }
    )
}

@Composable
fun HorizontalScrollStrip(
    modifier: Modifier = Modifier,
    onScroll: (delta: Int) -> Unit,
) {
    var lastX by remember { mutableFloatStateOf(0f) }
    var scrollAccumX by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(TouchpadSurface, RoundedCornerShape(14.dp))
            .border(1.dp, TouchpadBorder, RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    lastX = down.position.x
                    scrollAccumX = 0f
                    while (true) {
                        val event  = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.pressed) {
                            val dx = change.position.x - lastX
                            lastX  = change.position.x
                            scrollAccumX += dx * 7.5f
                            val delta = scrollAccumX.toInt()
                            if (delta != 0) {
                                onScroll(delta)
                                scrollAccumX -= delta
                            }
                            change.consume()
                        } else {
                            change.consume(); break
                        }
                    }
                }
            }
    )
}

package com.inmoair.xrcompanion.client.screenshot

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.view.Display
import android.view.Surface
import com.inmoair.xrcompanion.client.service.MediaProjectionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class PhoneCastStreamer(
    private val context: Context,
    private val resultCode: Int,
    private val data: Intent,
    private val onFrame: (String) -> Unit,
    private val onCaptureReconfigured: () -> Unit,
    private val onError: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var handlerThread: HandlerThread? = null
    private val encoding = AtomicBoolean(false)

    fun start() {
        if (job != null) return
        job = scope.launch {
            runCatching { stream() }
                .onFailure { onError(it.message ?: "Phone cast failed") }
            cleanup()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        cleanup()
    }

    private fun cleanup() {
        virtualDisplay?.release()
        virtualDisplay = null
        reader?.close()
        reader = null
        projection?.stop()
        projection = null
        handlerThread?.quitSafely()
        handlerThread = null
        MediaProjectionService.stop(appContext)
    }

    private suspend fun stream() {
        MediaProjectionService.start(appContext)
        withTimeout(2_000L) {
            MediaProjectionService.awaitForegroundReady()
        }

        val projectionManager = appContext.getSystemService(MediaProjectionManager::class.java)
        val activeProjection = projectionManager.getMediaProjection(resultCode, data)
        projection = activeProjection

        val thread = HandlerThread("PhoneCastStreamer").apply { start() }
        handlerThread = thread
        val handler = Handler(thread.looper)
        var capture = createCaptureTarget(activeProjection, handler, currentCaptureSpec())
        onCaptureReconfigured()

        try {
            while (job?.isActive == true) {
                val nextSpec = currentCaptureSpec()
                if (nextSpec != capture.spec) {
                    capture.release()
                    capture = createCaptureTarget(activeProjection, handler, nextSpec)
                    onCaptureReconfigured()
                    delay(ROTATION_SETTLE_MS)
                    continue
                }

                val image = capture.reader.acquireLatestImage()
                if (image != null) {
                    if (encoding.compareAndSet(false, true)) {
                        runCatching { image.toJpegBase64(capture.spec.width, capture.spec.height) }
                            .onSuccess(onFrame)
                            .onFailure { onError(it.message ?: "Frame encode failed") }
                        encoding.set(false)
                    } else {
                        image.close()
                    }
                }
                delay(FRAME_INTERVAL_MS)
            }
        } finally {
            capture.release()
        }
    }

    private fun createCaptureTarget(
        activeProjection: MediaProjection,
        handler: Handler,
        spec: CaptureSpec,
    ): CaptureTarget {
        val activeReader = ImageReader.newInstance(spec.width, spec.height, PixelFormat.RGBA_8888, 3)
        reader = activeReader
        val display = activeProjection.createVirtualDisplay(
            "XRCompanionPhoneCast",
            spec.width,
            spec.height,
            spec.density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            activeReader.surface,
            null,
            handler,
        )
        virtualDisplay = display
        return CaptureTarget(spec, activeReader, display)
    }

    private fun CaptureTarget.release() {
        if (this@PhoneCastStreamer.virtualDisplay === display) this@PhoneCastStreamer.virtualDisplay = null
        if (this@PhoneCastStreamer.reader === reader) this@PhoneCastStreamer.reader = null
        runCatching { display.release() }
        runCatching { reader.close() }
    }

    private fun currentCaptureSpec(): CaptureSpec {
        val metrics = appContext.resources.displayMetrics
        val shortEdge = minOf(metrics.widthPixels, metrics.heightPixels)
        val longEdge = maxOf(metrics.widthPixels, metrics.heightPixels)
        val landscape = isDisplayLandscape()
        val sourceWidth = if (landscape) longEdge else shortEdge
        val sourceHeight = if (landscape) shortEdge else longEdge
        val scale = minOf(1f, MAX_FRAME_EDGE / maxOf(sourceWidth, sourceHeight))
        return CaptureSpec(
            width = (sourceWidth * scale).toInt().coerceAtLeast(1),
            height = (sourceHeight * scale).toInt().coerceAtLeast(1),
            density = metrics.densityDpi,
        )
    }

    private fun isDisplayLandscape(): Boolean {
        val displayManager = appContext.getSystemService(DisplayManager::class.java)
        val rotation = displayManager
            ?.getDisplay(Display.DEFAULT_DISPLAY)
            ?.rotation
            ?: Surface.ROTATION_0
        return rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
    }

    private fun Image.toJpegBase64(targetWidth: Int, targetHeight: Int): String {
        val plane = planes.first()
        val buffer: ByteBuffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val paddedWidth = width + rowPadding / pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(buffer)
        close()

        val cropped = Bitmap.createBitmap(
            padded,
            0,
            0,
            targetWidth.coerceAtMost(padded.width),
            targetHeight.coerceAtMost(padded.height),
        )
        if (cropped !== padded) padded.recycle()

        val bytes = ByteArrayOutputStream().use { out ->
            cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
        cropped.recycle()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private data class CaptureSpec(
        val width: Int,
        val height: Int,
        val density: Int,
    )

    private data class CaptureTarget(
        val spec: CaptureSpec,
        val reader: ImageReader,
        val display: VirtualDisplay,
    )

    private companion object {
        private const val MAX_FRAME_EDGE = 840f
        private const val FRAME_INTERVAL_MS = 90L
        private const val ROTATION_SETTLE_MS = 180L
        private const val JPEG_QUALITY = 62
    }
}

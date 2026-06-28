package com.inmoair.xrcompanion.client.screenshot

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import com.inmoair.xrcompanion.client.service.MediaProjectionService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object LocalScreenshotCapturer {
    fun createCaptureIntent(context: Context): Intent {
        val manager = context.getSystemService(MediaProjectionManager::class.java)
        return manager.createScreenCaptureIntent()
    }

    suspend fun capture(context: Context, resultCode: Int, data: Intent): Bitmap {
        val appContext = context.applicationContext
        MediaProjectionService.start(appContext)

        val projectionManager = appContext.getSystemService(MediaProjectionManager::class.java)
        val projection = projectionManager.getMediaProjection(resultCode, data)
        val metrics = appContext.resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val density = metrics.densityDpi

        val handlerThread = HandlerThread("LocalScreenshotCapture").apply { start() }
        val handler = Handler(handlerThread.looper)
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val display = projection.createVirtualDisplay(
            "XRCompanionPhoneScreenshot",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler,
        )

        return try {
            withTimeout(4_000L) {
                suspendCancellableCoroutine { cont ->
                    reader.setOnImageAvailableListener({ imageReader ->
                        if (!cont.isActive) return@setOnImageAvailableListener
                        val image = imageReader.acquireLatestImage() ?: return@setOnImageAvailableListener
                        runCatching { image.toBitmap(width, height) }
                            .onSuccess { cont.resume(it) }
                            .onFailure { cont.resumeWithException(it) }
                    }, handler)
                    cont.invokeOnCancellation {
                        reader.setOnImageAvailableListener(null, null)
                    }
                }
            }
        } finally {
            reader.setOnImageAvailableListener(null, null)
            display.release()
            reader.close()
            projection.stop()
            handlerThread.quitSafely()
            MediaProjectionService.stop(appContext)
        }
    }

    private fun Image.toBitmap(targetWidth: Int, targetHeight: Int): Bitmap {
        val plane = planes.first()
        val buffer: ByteBuffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val paddedWidth = width + rowPadding / pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(buffer)
        val cropped = Bitmap.createBitmap(
            padded,
            0,
            0,
            targetWidth.coerceAtMost(padded.width),
            targetHeight.coerceAtMost(padded.height),
        )
        if (cropped !== padded) padded.recycle()
        close()
        return cropped
    }
}

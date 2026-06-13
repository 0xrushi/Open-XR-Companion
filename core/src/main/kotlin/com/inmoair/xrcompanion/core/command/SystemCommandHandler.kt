package com.inmoair.xrcompanion.core.command

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import android.util.Log
import com.inmoair.xrcompanion.core.accessibility.XRAccessibilityService
import com.inmoair.xrcompanion.shared.protocol.ScreenshotResponse
import com.inmoair.xrcompanion.shared.protocol.XRCommand
import com.inmoair.xrcompanion.shared.protocol.xrJson
import dagger.hilt.android.qualifiers.ApplicationContext
import org.java_websocket.WebSocket
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemCommandHandler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val TAG = "SystemHandler"

    private val audioManager: AudioManager
        get() = context.getSystemService(AudioManager::class.java)

    fun handle(cmd: XRCommand, socket: WebSocket) {
        when (cmd.action) {
            "set_brightness"    -> setBrightness(cmd.floatValue)
            "brightness_up"     -> adjustBrightness(+0.1f)
            "brightness_down"   -> adjustBrightness(-0.1f)
            "set_volume"        -> setVolume(cmd.floatValue)
            "volume_up"         -> audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
            "volume_down"       -> audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
            "mute"              -> audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
            "back"              -> XRAccessibilityService.instance?.performBack()
            "home"              -> XRAccessibilityService.instance?.performHome()
            "recents"           -> XRAccessibilityService.instance?.performRecents()
            "sleep"             -> trySleep()
            "wake"              -> tryWake()
            "shutdown"          -> tryShutdown()
            else                -> Log.w(TAG, "Unknown system action: ${cmd.action}")
        }
    }

    fun handleScreenshot(cmd: XRCommand, socket: WebSocket) {
        val svc = XRAccessibilityService.instance
        if (svc == null) {
            socket.sendResponse(ScreenshotResponse(status = "permission_required"))
            return
        }

        // captureScreenshot delivers a *software* Bitmap on the main thread.
        // We immediately hand off to a background thread so we never block main.
        svc.captureScreenshot { swBitmap ->
            if (swBitmap == null) {
                socket.sendResponse(ScreenshotResponse(status = "failed"))
                return@captureScreenshot
            }
            // Off-load JPEG encoding + base64 + socket.send to a background thread.
            Thread {
                try {
                    val maxPx = 1280
                    val scale = minOf(1f, maxPx.toFloat() / maxOf(swBitmap.width, swBitmap.height))
                    val scaled = if (scale < 1f)
                        Bitmap.createScaledBitmap(
                            swBitmap,
                            maxOf(1, (swBitmap.width  * scale).toInt()),
                            maxOf(1, (swBitmap.height * scale).toInt()),
                            true,
                        )
                    else swBitmap

                    val baos = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                    val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

                    socket.sendResponse(ScreenshotResponse(
                        status = "captured",
                        data   = b64,
                        format = "jpeg",
                        width  = scaled.width,
                        height = scaled.height,
                    ))
                } catch (e: Exception) {
                    Log.e(TAG, "Screenshot encode failed: ${e.message}")
                    socket.sendResponse(ScreenshotResponse(status = "failed"))
                }
            }.start()
        }
    }

    private fun WebSocket.sendResponse(resp: ScreenshotResponse) {
        try { send(xrJson.encodeToString(ScreenshotResponse.serializer(), resp)) }
        catch (e: Exception) { Log.w(TAG, "Socket send failed: ${e.message}") }
    }

    fun handleFile(cmd: XRCommand, socket: WebSocket) {
        when (cmd.action) {
            "list" -> listDirectory(cmd.path, socket)
            else   -> Log.w(TAG, "Unimplemented file action: ${cmd.action}")
        }
    }

    // -----------------------------------------------------------------------

    private fun setBrightness(value: Float) {
        try {
            val brightness = (value.coerceIn(0f, 1f) * 255).toInt()
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                brightness
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set brightness: ${e.message}")
        }
    }

    private fun adjustBrightness(delta: Float) {
        try {
            val current = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS, 128
            ) / 255f
            setBrightness((current + delta).coerceIn(0f, 1f))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to adjust brightness: ${e.message}")
        }
    }

    private fun setVolume(value: Float) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val vol = (value.coerceIn(0f, 1f) * max).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
    }

    private fun trySleep() {
        XRAccessibilityService.instance?.performGlobalAction(
            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
        )
    }

    @SuppressLint("WakelockTimeout")
    private fun tryWake() {
        try {
            val pm = context.getSystemService(PowerManager::class.java)
            // ACQUIRE_CAUSES_WAKEUP turns the screen on when the lock is acquired.
            // FULL_WAKE_LOCK is deprecated but still effective on API 31+ for this use-case.
            // The 3-second timeout releases the lock automatically; we need no manual release.
            @Suppress("DEPRECATION")
            pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "XRCompanion:WakeUp",
            ).acquire(3_000L)
        } catch (e: Exception) {
            Log.w(TAG, "Wake failed: ${e.message}")
        }
    }

    private fun tryShutdown() {
        // Requires system permission; signal via accessibility
        Log.w(TAG, "Shutdown requested — requires system permissions on this device")
    }

    private fun listDirectory(path: String, socket: WebSocket) {
        try {
            val dir = if (path.isEmpty()) android.os.Environment.getExternalStorageDirectory()
                      else File(path)
            if (!dir.exists() || !dir.isDirectory) {
                socket.send("""{"type":"file_list","path":"$path","entries":[],"error":"Not a directory"}""")
                return
            }
            val entries = dir.listFiles()?.map { f ->
                mapOf(
                    "name" to f.name,
                    "path" to f.absolutePath,
                    "isDirectory" to f.isDirectory,
                    "size" to f.length(),
                    "lastModified" to f.lastModified(),
                )
            } ?: emptyList()
            val json = xrJson.encodeToString(
                kotlinx.serialization.serializer(),
                mapOf(
                    "type" to "file_list",
                    "path" to dir.absolutePath,
                    "entries" to entries,
                    "error" to "",
                )
            )
            socket.send(json)
        } catch (e: Exception) {
            socket.send("""{"type":"file_list","path":"$path","entries":[],"error":"${e.message}"}""")
        }
    }
}

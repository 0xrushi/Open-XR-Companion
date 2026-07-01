package com.inmoair.xrcompanion.core.command

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import android.util.Log
import com.inmoair.xrcompanion.core.accessibility.XRAccessibilityService
import com.inmoair.xrcompanion.shared.protocol.FileChunkResponse
import com.inmoair.xrcompanion.shared.protocol.FileEntry
import com.inmoair.xrcompanion.shared.protocol.FileListResponse
import com.inmoair.xrcompanion.shared.protocol.FileOperationResponse
import com.inmoair.xrcompanion.shared.protocol.ScreenshotResponse
import com.inmoair.xrcompanion.shared.protocol.XRCommand
import com.inmoair.xrcompanion.shared.protocol.xrJson
import dagger.hilt.android.qualifiers.ApplicationContext
import org.java_websocket.WebSocket
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemCommandHandler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val TAG = "SystemHandler"
    private val castDecoding = AtomicBoolean(false)

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
            "screen_record"     -> startScreenRecord()
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

    fun handleCast(cmd: XRCommand) {
        when (cmd.action) {
            "start" -> XRAccessibilityService.instance?.showCastOverlay(cmd.value.ifBlank { "screen" })
            "transform" -> XRAccessibilityService.instance?.setCastTransform(
                zoom = cmd.floatValue,
                offsetY = cmd.y,
                landscape = cmd.allowed,
            )
            "frame" -> {
                val svc = XRAccessibilityService.instance
                if (svc == null) {
                    Log.w(TAG, "Cast frame received without accessibility service")
                    return
                }
                if (!castDecoding.compareAndSet(false, true)) return
                Thread {
                    try {
                        runCatching {
                            val bytes = Base64.decode(cmd.data, Base64.NO_WRAP)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        }.onSuccess { bitmap ->
                            if (bitmap != null) svc.showCastFrame(bitmap)
                        }.onFailure { e ->
                            Log.w(TAG, "Cast frame decode failed: ${e.message}")
                        }
                    } finally {
                        castDecoding.set(false)
                    }
                }.start()
            }
            "stop" -> {
                castDecoding.set(false)
                XRAccessibilityService.instance?.hideCastOverlay()
            }
            else -> Log.w(TAG, "Unknown cast action: ${cmd.action}")
        }
    }

    private fun WebSocket.sendResponse(resp: ScreenshotResponse) {
        try { send(xrJson.encodeToString(ScreenshotResponse.serializer(), resp)) }
        catch (e: Exception) { Log.w(TAG, "Socket send failed: ${e.message}") }
    }

    fun handleFile(cmd: XRCommand, socket: WebSocket) {
        when (cmd.action) {
            "list"   -> listDirectory(cmd.path, socket)
            "read"   -> readFile(cmd, socket)
            "write"  -> writeFile(cmd, socket)
            "mkdir"  -> makeDirectory(cmd.path, socket)
            "delete" -> deletePath(cmd.path, socket)
            else     -> Log.w(TAG, "Unimplemented file action: ${cmd.action}")
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

    private fun startScreenRecord() {
        val svc = XRAccessibilityService.instance
        if (svc == null) {
            Log.w(TAG, "Screen record requested but accessibility service is not running")
            return
        }
        svc.openQuickSettingsAndTap(
            listOf(
                "Screen record",
                "Screen recorder",
                "Record screen",
                "Screen Record",
                "Recorder",
            )
        )
    }

    private fun listDirectory(path: String, socket: WebSocket) {
        try {
            val dir = resolveFile(path, directory = true)
            if (!dir.exists() || !dir.isDirectory) {
                socket.sendFileList(path = dir.absolutePath, error = "Not a directory")
                return
            }
            val entries = dir.listFiles()
                ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                ?.map { f ->
                    FileEntry(
                        name = f.name,
                        path = f.absolutePath,
                        isDirectory = f.isDirectory,
                        size = f.length(),
                        lastModified = f.lastModified(),
                        mimeType = guessMimeType(f),
                    )
                } ?: emptyList()
            socket.sendFileList(path = dir.absolutePath, entries = entries)
        } catch (e: Exception) {
            socket.sendFileList(path = path, error = e.message ?: "List failed")
        }
    }

    private fun readFile(cmd: XRCommand, socket: WebSocket) {
        try {
            val file = resolveFile(cmd.path)
            if (!file.exists() || !file.isFile) {
                socket.sendFileChunk(path = file.absolutePath, error = "Not a file")
                return
            }

            val offset = cmd.offset.coerceIn(0L, file.length())
            val chunkSize = cmd.chunkSize.coerceIn(4 * 1024, 512 * 1024)
            val buffer = ByteArray(chunkSize)
            val bytesRead = RandomAccessFile(file, "r").use { raf ->
                raf.seek(offset)
                raf.read(buffer)
            }.coerceAtLeast(0)
            val data = if (bytesRead > 0) {
                Base64.encodeToString(buffer.copyOf(bytesRead), Base64.NO_WRAP)
            } else ""
            val nextOffset = offset + bytesRead
            socket.sendFileChunk(
                path = file.absolutePath,
                offset = offset,
                size = file.length(),
                data = data,
                eof = nextOffset >= file.length(),
            )
        } catch (e: Exception) {
            socket.sendFileChunk(path = cmd.path, error = e.message ?: "Read failed")
        }
    }

    private fun writeFile(cmd: XRCommand, socket: WebSocket) {
        try {
            val file = resolveFile(cmd.path)
            file.parentFile?.mkdirs()
            val bytes = if (cmd.data.isNotEmpty()) Base64.decode(cmd.data, Base64.NO_WRAP) else ByteArray(0)
            RandomAccessFile(file, "rw").use { raf ->
                if (cmd.offset == 0L) raf.setLength(0L)
                raf.seek(cmd.offset.coerceAtLeast(0L))
                raf.write(bytes)
            }
            socket.sendFileResult(
                action = "write",
                path = file.absolutePath,
                status = if (cmd.eof) "complete" else "ok",
                message = if (cmd.eof) "Upload complete" else "Chunk written",
            )
        } catch (e: Exception) {
            socket.sendFileResult(
                action = "write",
                path = cmd.path,
                status = "failed",
                message = e.message ?: "Write failed",
            )
        }
    }

    private fun makeDirectory(path: String, socket: WebSocket) {
        val dir = resolveFile(path, directory = true)
        val ok = dir.mkdirs() || dir.isDirectory
        socket.sendFileResult(
            action = "mkdir",
            path = dir.absolutePath,
            status = if (ok) "complete" else "failed",
            message = if (ok) "Folder created" else "Folder create failed",
        )
    }

    private fun deletePath(path: String, socket: WebSocket) {
        val file = resolveFile(path)
        val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
        socket.sendFileResult(
            action = "delete",
            path = file.absolutePath,
            status = if (ok) "complete" else "failed",
            message = if (ok) "Deleted" else "Delete failed",
        )
    }

    private fun resolveFile(path: String, directory: Boolean = false): File {
        if (path.isBlank()) return android.os.Environment.getExternalStorageDirectory()
        val file = File(path)
        if (file.isAbsolute) return file
        val downloads = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS,
        )
        val base = File(downloads, "XRCompanion")
        return if (directory && path == ".") base else File(base, path)
    }

    private fun guessMimeType(file: File): String {
        if (file.isDirectory) return ""
        return android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase())
            ?: "application/octet-stream"
    }

    private fun WebSocket.sendFileList(
        path: String,
        entries: List<FileEntry> = emptyList(),
        error: String = "",
    ) {
        try {
            send(
                xrJson.encodeToString(
                    FileListResponse.serializer(),
                    FileListResponse(path = path, entries = entries, error = error),
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "File list send failed: ${e.message}")
        }
    }

    private fun WebSocket.sendFileChunk(
        path: String,
        offset: Long = 0L,
        size: Long = 0L,
        data: String = "",
        eof: Boolean = false,
        error: String = "",
    ) {
        try {
            send(
                xrJson.encodeToString(
                    FileChunkResponse.serializer(),
                    FileChunkResponse(
                        path = path,
                        offset = offset,
                        size = size,
                        data = data,
                        eof = eof,
                        error = error,
                    ),
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "File chunk send failed: ${e.message}")
        }
    }

    private fun WebSocket.sendFileResult(
        action: String,
        path: String,
        status: String,
        message: String,
    ) {
        try {
            send(
                xrJson.encodeToString(
                    FileOperationResponse.serializer(),
                    FileOperationResponse(
                        action = action,
                        path = path,
                        status = status,
                        message = message,
                    ),
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "File result send failed: ${e.message}")
        }
    }
}

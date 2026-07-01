package com.inmoair.xrcompanion.client.ui.viewmodel

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inmoair.xrcompanion.client.data.CustomButton
import com.inmoair.xrcompanion.client.data.DeviceRepository
import com.inmoair.xrcompanion.client.network.CommandSender
import com.inmoair.xrcompanion.client.network.XRWebSocketClient
import com.inmoair.xrcompanion.client.voice.VoiceInputManager
import com.inmoair.xrcompanion.client.voice.VoiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class ControlTab { TOUCHPAD, REMOTE }
enum class ScreenshotSource { REMOTE, LOCAL_PHONE }

data class ControlUiState(
    val activeTab: ControlTab = ControlTab.TOUCHPAD,
    val voiceState: VoiceState = VoiceState.IDLE,
    val voiceText: String = "",
    val scrollSpeed: Float = 1f,
    // Touchpad mode: true = relative cursor/trackpad (glasses cursor moves);
    // false = direct absolute touch (mobile mirror).
    val cursorMode: Boolean = true,
    val cursorSpeed: Float = 1.6f,
    // Screenshot
    val isCapturingScreenshot: Boolean = false,
    val screenshotBitmap: Bitmap? = null,
    val screenshotError: String? = null,
    val screenshotSource: ScreenshotSource = ScreenshotSource.REMOTE,
    // Sleep/wake toggle (client-side best-guess; resets on app restart)
    val isSleeping: Boolean = false,
)

@HiltViewModel
class ControlViewModel @Inject constructor(
    private val commandSender: CommandSender,
    private val wsClient: XRWebSocketClient,
    private val voiceManager: VoiceInputManager,
    private val deviceRepository: DeviceRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ControlUiState())
    val uiState: StateFlow<ControlUiState> = _uiState.asStateFlow()

    val customButtons: StateFlow<List<CustomButton>> = deviceRepository.customButtons
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // A virtual cursor position is tracked on the client and pushed to the glasses.
    private var cursorX = 0.5f
    private var cursorY = 0.5f

    init {
        viewModelScope.launch {
            voiceManager.state.collect { _uiState.value = _uiState.value.copy(voiceState = it) }
        }
        viewModelScope.launch {
            voiceManager.lastText.collect { _uiState.value = _uiState.value.copy(voiceText = it) }
        }
        voiceManager.onResult = { text ->
            commandSender.sendText(text)
        }
        commandSender.setCursorVisible(true, cursorX, cursorY)
        // Listen for screenshot results from the glasses
        viewModelScope.launch {
            wsClient.screenshots.collect { resp ->
                when (resp.status) {
                    "captured" -> {
                        val bmp = runCatching {
                            val bytes = Base64.decode(resp.data, Base64.NO_WRAP)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        }.getOrNull()
                        _uiState.value = _uiState.value.copy(
                            isCapturingScreenshot = false,
                            screenshotBitmap = bmp,
                            screenshotError = if (bmp == null) "Failed to decode image" else null,
                            screenshotSource = ScreenshotSource.REMOTE,
                        )
                    }
                    else -> _uiState.value = _uiState.value.copy(
                        isCapturingScreenshot = false,
                        screenshotBitmap = null,
                        screenshotError = when (resp.status) {
                            "permission_required" -> "Enable Accessibility Service on glasses"
                            else -> "Screenshot failed"
                        },
                    )
                }
            }
        }
    }

    fun setTab(tab: ControlTab) { _uiState.value = _uiState.value.copy(activeTab = tab) }

    // --- touchpad (direct/absolute mode) ---
    fun onTap(nx: Float, ny: Float)               = commandSender.sendTap(nx, ny)
    fun onDoubleTap(nx: Float, ny: Float)         = commandSender.sendDoubleTap(nx, ny)
    fun onLongPress(nx: Float, ny: Float)         = commandSender.sendLongPress(nx, ny)
    fun onMove(nx: Float, ny: Float)              = commandSender.sendMove(nx, ny)
    fun onSwipe(x1: Float, y1: Float, x2: Float, y2: Float) = commandSender.sendSwipe(x1, y1, x2, y2)
    fun onScrollV(delta: Int)                     = commandSender.sendScrollVertical(delta)
    fun onScrollH(delta: Int)                     = commandSender.sendScrollHorizontal(delta)

    // --- touchpad (cursor/relative mode) ---
    fun toggleCursorMode() {
        val now = !_uiState.value.cursorMode
        _uiState.value = _uiState.value.copy(cursorMode = now)
        if (now) {
            // Re-centre and show the cursor when entering cursor mode.
            cursorX = 0.5f; cursorY = 0.5f
            commandSender.setCursorVisible(true, cursorX, cursorY)
        } else {
            commandSender.setCursorVisible(false)
        }
    }

    fun moveCursorBy(dxNorm: Float, dyNorm: Float) {
        if (!_uiState.value.cursorMode) return
        val speed = _uiState.value.cursorSpeed
        cursorX = (cursorX + dxNorm * speed).coerceIn(0f, 1f)
        cursorY = (cursorY + dyNorm * speed).coerceIn(0f, 1f)
        commandSender.sendMove(cursorX, cursorY)
    }

    fun cursorTap()        = commandSender.sendTap(cursorX, cursorY)
    fun cursorDoubleTap()  = commandSender.sendDoubleTap(cursorX, cursorY)
    fun cursorLongPress()  = commandSender.sendLongPress(cursorX, cursorY)
    fun setCursorSpeed(v: Float) { _uiState.value = _uiState.value.copy(cursorSpeed = v) }

    // --- remote buttons ---
    fun pressBack()    = commandSender.sendBack()
    fun pressHome()    = commandSender.sendHome()
    fun pressRecents() = commandSender.sendRecents()
    fun pressSleep() {
        if (_uiState.value.isSleeping) {
            commandSender.wake()
            _uiState.value = _uiState.value.copy(isSleeping = false)
        } else {
            commandSender.sleep()
            _uiState.value = _uiState.value.copy(isSleeping = true)
        }
    }
    fun pressShutdown() = commandSender.shutdown()
    fun pressBrightnessUp() = commandSender.brightnessUp()
    fun pressBrightnessDown() = commandSender.brightnessDown()
    fun pressVolumeUp()   = commandSender.volumeUp()
    fun pressVolumeDown() = commandSender.volumeDown()
    fun pressMute() = commandSender.mute()

    // --- keyboard ---
    fun sendText(text: String)  = commandSender.sendText(text)
    fun sendBackspace()          = commandSender.sendBackspace()
    fun sendEnter()              = commandSender.sendEnter()

    // --- voice ---
    fun startVoice() = voiceManager.start()
    fun stopVoice()  = voiceManager.stop()
    fun cancelVoice() = voiceManager.cancel()

    fun setScrollSpeed(v: Float)  { _uiState.value = _uiState.value.copy(scrollSpeed = v) }

    // --- screenshot ---

    fun requestScreenshot() {
        _uiState.value = _uiState.value.copy(
            isCapturingScreenshot = true,
            screenshotBitmap = null,
            screenshotError = null,
            screenshotSource = ScreenshotSource.REMOTE,
        )
        commandSender.requestScreenshot()
    }

    fun prepareLocalScreenshotCapture() {
        _uiState.value = _uiState.value.copy(
            isCapturingScreenshot = true,
            screenshotBitmap = null,
            screenshotError = null,
            screenshotSource = ScreenshotSource.LOCAL_PHONE,
        )
    }

    fun onLocalScreenshotCaptured(bitmap: Bitmap) {
        _uiState.value = _uiState.value.copy(
            isCapturingScreenshot = false,
            screenshotBitmap = bitmap,
            screenshotError = null,
            screenshotSource = ScreenshotSource.LOCAL_PHONE,
        )
    }

    fun onLocalScreenshotFailed(message: String = "Phone screenshot failed") {
        _uiState.value = _uiState.value.copy(
            isCapturingScreenshot = false,
            screenshotBitmap = null,
            screenshotError = message,
            screenshotSource = ScreenshotSource.LOCAL_PHONE,
        )
    }

    fun dismissScreenshot() {
        _uiState.value = _uiState.value.copy(
            screenshotBitmap = null,
            screenshotError = null,
            isCapturingScreenshot = false,
        )
    }

    /**
     * Crop the screenshot to [l,t,r,b] (normalized 0-1) then save to the gallery.
     * Pass default values to save the full image without cropping.
     */
    fun saveScreenshotToGallery(
        cropL: Float = 0f,
        cropT: Float = 0f,
        cropR: Float = 1f,
        cropB: Float = 1f,
    ): Boolean {
        val orig = _uiState.value.screenshotBitmap ?: return false
        val bmp = cropBitmap(orig, cropL, cropT, cropR, cropB)
        return try {
            val name = "XRScreenshot_${System.currentTimeMillis()}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/XRCompanion")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = appContext.contentResolver
                .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            appContext.contentResolver.openOutputStream(uri)?.use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                appContext.contentResolver.update(uri, values, null, null)
            }
            true
        } catch (e: Exception) {
            Log.e("ControlVM", "Save screenshot failed: ${e.message}")
            false
        }
    }

    fun sendScreenshotToDevice(
        cropL: Float = 0f,
        cropT: Float = 0f,
        cropR: Float = 1f,
        cropB: Float = 1f,
    ) {
        val orig = _uiState.value.screenshotBitmap ?: return
        val bmp = cropBitmap(orig, cropL, cropT, cropR, cropB)
        viewModelScope.launch {
            try {
                val bytes = java.io.ByteArrayOutputStream().use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    out.toByteArray()
                }
                uploadBytesToDevice(
                    fileName = "PhoneScreenshot_${System.currentTimeMillis()}.jpg",
                    bytes = bytes,
                )
            } catch (e: Exception) {
                Log.e("ControlVM", "Send phone screenshot failed: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    screenshotError = e.message ?: "Send phone screenshot failed",
                )
            }
        }
    }

    private fun cropBitmap(
        orig: Bitmap,
        cropL: Float,
        cropT: Float,
        cropR: Float,
        cropB: Float,
    ): Bitmap {
        if (cropL == 0f && cropT == 0f && cropR == 1f && cropB == 1f) return orig
        return try {
            val x = (cropL * orig.width).toInt().coerceIn(0, orig.width - 1)
            val y = (cropT * orig.height).toInt().coerceIn(0, orig.height - 1)
            val w = ((cropR - cropL) * orig.width).toInt().coerceIn(1, orig.width - x)
            val h = ((cropB - cropT) * orig.height).toInt().coerceIn(1, orig.height - y)
            Bitmap.createBitmap(orig, x, y, w, h)
        } catch (e: Exception) {
            Log.e("ControlVM", "Crop failed: ${e.message}")
            orig
        }
    }

    private fun uploadBytesToDevice(fileName: String, bytes: ByteArray) {
        val chunkSize = 96 * 1024
        var offset = 0
        while (offset < bytes.size || (bytes.isEmpty() && offset == 0)) {
            val end = minOf(bytes.size, offset + chunkSize)
            val chunk = bytes.copyOfRange(offset, end)
            val eof = end >= bytes.size
            val b64 = Base64.encodeToString(chunk, Base64.NO_WRAP)
            commandSender.uploadFileChunk(
                path = fileName,
                offset = offset.toLong(),
                base64Data = b64,
                eof = eof,
            )
            if (bytes.isEmpty()) break
            offset = end
        }
    }

    // --- custom buttons ---

    fun executeCustomButton(button: CustomButton) {
        when (button.type) {
            "enter" -> commandSender.sendEnter()
            "tab"   -> commandSender.sendText("\t")
            "esc"   -> commandSender.sendKeyCode(android.view.KeyEvent.KEYCODE_ESCAPE)
            else    -> if (button.macro.isNotEmpty()) commandSender.sendText(button.macro)
        }
    }

    fun saveCustomButton(name: String, macro: String, type: String, id: String? = null) {
        viewModelScope.launch {
            deviceRepository.saveCustomButton(
                CustomButton(
                    id = id ?: UUID.randomUUID().toString(),
                    name = name.trim(),
                    macro = macro,
                    type = type,
                )
            )
        }
    }

    fun deleteCustomButton(id: String) {
        viewModelScope.launch { deviceRepository.deleteCustomButton(id) }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.cancel()
    }
}

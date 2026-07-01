package com.inmoair.xrcompanion.client.ui.viewmodel

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inmoair.xrcompanion.client.data.DeviceRepository
import com.inmoair.xrcompanion.client.network.CommandSender
import com.inmoair.xrcompanion.client.network.ConnectionStatus
import com.inmoair.xrcompanion.client.network.DeviceDiscovery
import com.inmoair.xrcompanion.client.network.DiscoveredDevice
import com.inmoair.xrcompanion.client.network.XRWebSocketClient
import com.inmoair.xrcompanion.client.screenshot.PhoneCastStreamer
import com.inmoair.xrcompanion.shared.protocol.DeviceState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DashboardScreenshotSource { REMOTE, LOCAL_PHONE }

data class DashboardUiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val connectedDevice: DiscoveredDevice? = null,
    val deviceState: DeviceState? = null,
    val discoveredDevices: List<DiscoveredDevice> = emptyList(),
    val isScanning: Boolean = false,
    val brightness: Float = 0.5f,
    val volume: Float = 0.5f,
    val isCapturingScreenshot: Boolean = false,
    val screenshotBitmap: Bitmap? = null,
    val screenshotError: String? = null,
    val screenshotSource: DashboardScreenshotSource = DashboardScreenshotSource.REMOTE,
    val isPhoneCasting: Boolean = false,
    val castZoom: Float = 1f,
    val castOffsetY: Float = 0f,
    val castLandscape: Boolean = false,
    // SpaceWalker
    val swRotation: Float = 0f,
    val swScreenCount: Int = 1,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val discovery: DeviceDiscovery,
    private val wsClient: XRWebSocketClient,
    private val commandSender: CommandSender,
    private val deviceRepository: DeviceRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    private var phoneCastStreamer: PhoneCastStreamer? = null

    override fun onCleared() {
        phoneCastStreamer?.stop()
        phoneCastStreamer = null
        commandSender.castStop()
        super.onCleared()
    }

    init {
        viewModelScope.launch {
            wsClient.status.collectLatest { status ->
                _uiState.value = _uiState.value.copy(connectionStatus = status)
                if (status == ConnectionStatus.CONNECTED) {
                    deviceRepository.saveSessionToken(wsClient.lastApprovedToken)
                }
            }
        }
        viewModelScope.launch {
            wsClient.deviceState.collectLatest { state ->
                state ?: return@collectLatest
                _uiState.value = _uiState.value.copy(
                    deviceState = state,
                    brightness  = state.brightness / 100f,
                    volume      = state.volume / 100f,
                )
            }
        }
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
        viewModelScope.launch {
            discovery.devices.collectLatest { devices ->
                _uiState.value = _uiState.value.copy(discoveredDevices = devices)
            }
        }
        viewModelScope.launch {
            discovery.isScanning.collectLatest { scanning ->
                _uiState.value = _uiState.value.copy(isScanning = scanning)
            }
        }
        viewModelScope.launch {
            val dm    = deviceRepository.lastDevice.first()
            val token = deviceRepository.sessionToken.first()
            if (dm != null && wsClient.status.value == ConnectionStatus.DISCONNECTED) {
                wsClient.connect(dm, token)
            }
        }
    }

    fun startScan() { discovery.startScan() }
    fun stopScan()  { discovery.stopScan() }

    fun connectToDevice(device: DiscoveredDevice) {
        viewModelScope.launch {
            wsClient.disconnect()
            discovery.stopScan()
            deviceRepository.saveLastDevice(device.message)
            val token = deviceRepository.sessionToken.first()
            _uiState.value = _uiState.value.copy(connectedDevice = device)
            wsClient.connect(device.message, token)
        }
    }

    fun disconnect() {
        wsClient.disconnect()
        _uiState.value = _uiState.value.copy(connectedDevice = null)
    }

    fun setBrightness(value: Float) {
        _uiState.value = _uiState.value.copy(brightness = value)
        commandSender.setBrightness(value)
    }

    fun setVolume(value: Float) {
        _uiState.value = _uiState.value.copy(volume = value)
        commandSender.setVolume(value)
    }

    fun requestScreenshot() {
        _uiState.value = _uiState.value.copy(
            isCapturingScreenshot = true,
            screenshotBitmap = null,
            screenshotError = null,
            screenshotSource = DashboardScreenshotSource.REMOTE,
        )
        commandSender.requestScreenshot()
    }

    fun preparePhoneCastCapture() {
        _uiState.value = _uiState.value.copy(
            isCapturingScreenshot = true,
            screenshotBitmap = null,
            screenshotError = null,
            screenshotSource = DashboardScreenshotSource.LOCAL_PHONE,
        )
    }

    fun startPhoneCast(resultCode: Int, data: Intent) {
        stopPhoneCast(sendStop = false)
        _uiState.value = _uiState.value.copy(
            isCapturingScreenshot = false,
            screenshotBitmap = null,
            screenshotError = null,
            screenshotSource = DashboardScreenshotSource.LOCAL_PHONE,
            isPhoneCasting = true,
            castZoom = 1f,
            castOffsetY = 0f,
            castLandscape = false,
        )
        commandSender.castStart()
        commandSender.castTransform(zoom = 1f, offsetY = 0f, landscape = false)
        phoneCastStreamer = PhoneCastStreamer(
            context = appContext,
            resultCode = resultCode,
            data = data,
            onFrame = { frame -> commandSender.castFrame(frame) },
            onCaptureReconfigured = { setCastLandscape(false) },
            onError = { message ->
                Log.e("DashboardVM", "Phone cast error: $message")
                _uiState.value = _uiState.value.copy(
                    screenshotError = message,
                    isPhoneCasting = false,
                )
                commandSender.castStop()
            },
        ).also { it.start() }
    }

    fun stopPhoneCast(sendStop: Boolean = true) {
        phoneCastStreamer?.stop()
        phoneCastStreamer = null
        if (sendStop) commandSender.castStop()
        _uiState.value = _uiState.value.copy(
            isPhoneCasting = false,
            isCapturingScreenshot = false,
        )
    }

    fun setCastZoom(value: Float) {
        val zoom = value.coerceIn(1f, 3f)
        val state = _uiState.value
        _uiState.value = state.copy(castZoom = zoom)
        commandSender.castTransform(
            zoom = zoom,
            offsetY = state.castOffsetY,
            landscape = state.castLandscape,
        )
    }

    fun setCastOffsetY(value: Float) {
        val offsetY = value.coerceIn(-1f, 1f)
        val state = _uiState.value
        _uiState.value = state.copy(castOffsetY = offsetY)
        commandSender.castTransform(
            zoom = state.castZoom,
            offsetY = offsetY,
            landscape = state.castLandscape,
        )
    }

    fun setCastLandscape(value: Boolean) {
        val state = _uiState.value
        _uiState.value = state.copy(castLandscape = value)
        commandSender.castTransform(
            zoom = state.castZoom,
            offsetY = state.castOffsetY,
            landscape = value,
        )
    }

    fun onPhoneCastFailed(message: String = "Phone screen capture failed") {
        _uiState.value = _uiState.value.copy(
            isCapturingScreenshot = false,
            screenshotBitmap = null,
            screenshotError = message,
            screenshotSource = DashboardScreenshotSource.LOCAL_PHONE,
        )
    }

    fun dismissScreenshot() {
        _uiState.value = _uiState.value.copy(
            isCapturingScreenshot = false,
            screenshotBitmap = null,
            screenshotError = null,
        )
    }

    fun saveScreenshotToGallery(): Boolean {
        val bmp = _uiState.value.screenshotBitmap ?: return false
        return try {
            val name = "XRScreenshot_${System.currentTimeMillis()}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/XR Companion")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = appContext.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
            } ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun startScreenRecord() {
        commandSender.screenRecord()
    }

    // ---- SpaceWalker ----
    // Commands are sent via WebSocket to xr-companion core on the glasses.
    // The core then forwards them as local broadcasts to SpaceDesk on the glasses.

    fun swZoomIn() { commandSender.spaceWalkerZoomIn() }
    fun swZoomOut() { commandSender.spaceWalkerZoomOut() }

    fun swSetRotation(degrees: Float) {
        _uiState.value = _uiState.value.copy(swRotation = degrees)
        commandSender.spaceWalkerSetRotation(degrees)
    }

    fun swAddScreen() {
        val next = (_uiState.value.swScreenCount + 1).coerceAtMost(3)
        _uiState.value = _uiState.value.copy(swScreenCount = next)
        commandSender.spaceWalkerAddScreen()
    }

    fun swRemoveScreen() {
        val next = (_uiState.value.swScreenCount - 1).coerceAtLeast(1)
        _uiState.value = _uiState.value.copy(swScreenCount = next)
        commandSender.spaceWalkerRemoveScreen()
    }
}

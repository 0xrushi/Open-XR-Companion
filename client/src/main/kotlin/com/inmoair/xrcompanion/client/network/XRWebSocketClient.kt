package com.inmoair.xrcompanion.client.network

import android.util.Log
import com.inmoair.xrcompanion.shared.discovery.DiscoveryMessage
import com.inmoair.xrcompanion.shared.protocol.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, PAIRING, ERROR }

@Singleton
class XRWebSocketClient @Inject constructor() {

    private val TAG = "XRWSClient"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)   // no read timeout for WS
        .build()

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var currentDevice: DiscoveryMessage? = null
    private var sessionToken: String = ""
    private var deviceId: String = android.os.Build.SERIAL.ifBlank { "phone-${System.currentTimeMillis()}" }
    private var deviceName: String = android.os.Build.MODEL

    private val _status = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _deviceState = MutableStateFlow<DeviceState?>(null)
    val deviceState: StateFlow<DeviceState?> = _deviceState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _screenshots = MutableSharedFlow<ScreenshotResponse>(replay = 1, extraBufferCapacity = 1)
    val screenshots: SharedFlow<ScreenshotResponse> = _screenshots.asSharedFlow()

    private val _fileLists = MutableSharedFlow<FileListResponse>(replay = 1, extraBufferCapacity = 4)
    val fileLists: SharedFlow<FileListResponse> = _fileLists.asSharedFlow()

    private val _fileChunks = MutableSharedFlow<FileChunkResponse>(extraBufferCapacity = 16)
    val fileChunks: SharedFlow<FileChunkResponse> = _fileChunks.asSharedFlow()

    private val _fileResults = MutableSharedFlow<FileOperationResponse>(extraBufferCapacity = 16)
    val fileResults: SharedFlow<FileOperationResponse> = _fileResults.asSharedFlow()

    // -----------------------------------------------------------------------

    fun connect(device: DiscoveryMessage, token: String = "") {
        currentDevice = device
        sessionToken = token
        _status.value = ConnectionStatus.CONNECTING
        doConnect(device)
    }

    fun disconnect() {
        reconnectJob?.cancel()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _status.value = ConnectionStatus.DISCONNECTED
        currentDevice = null
    }

    fun send(json: String): Boolean {
        return try {
            webSocket?.send(json) ?: false
        } catch (e: Exception) {
            Log.w(TAG, "Send failed: ${e.message}")
            false
        }
    }

    fun sendCommand(cmd: XRCommand): Boolean =
        send(xrJson.encodeToString(XRCommand.serializer(), cmd))

    fun isConnected() = _status.value == ConnectionStatus.CONNECTED

    // -----------------------------------------------------------------------

    private fun doConnect(device: DiscoveryMessage) {
        val url = "ws://${device.ip}:${device.wsPort}"
        Log.i(TAG, "Connecting to $url")

        val request = Request.Builder().url(url).build()
        webSocket = http.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket opened")
                _status.value = ConnectionStatus.PAIRING
                // Send pairing/hello
                val pairCmd = XRCommand(
                    type = "pairing",
                    action = "request",
                    deviceId = deviceId,
                    deviceName = deviceName,
                    token = sessionToken,
                )
                ws.send(xrJson.encodeToString(XRCommand.serializer(), pairCmd))
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleIncoming(text)
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                handleIncoming(bytes.utf8())
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
                onDisconnected()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                onDisconnected()
                scheduleReconnect()
            }
        })
    }

    private fun handleIncoming(raw: String) {
        scope.launch {
            _messages.emit(raw)

            try {
                // Try DeviceState first
                if (raw.contains("\"device_state\"")) {
                    val state = xrJson.decodeFromString(DeviceState.serializer(), raw)
                    _deviceState.value = state
                    return@launch
                }
                // Screenshot result
                if (raw.contains("\"screenshot_result\"")) {
                    val resp = xrJson.decodeFromString(ScreenshotResponse.serializer(), raw)
                    _screenshots.emit(resp)
                    return@launch
                }
                if (raw.contains("\"file_list\"")) {
                    val resp = xrJson.decodeFromString(FileListResponse.serializer(), raw)
                    _fileLists.emit(resp)
                    return@launch
                }
                if (raw.contains("\"file_chunk\"")) {
                    val resp = xrJson.decodeFromString(FileChunkResponse.serializer(), raw)
                    _fileChunks.emit(resp)
                    return@launch
                }
                if (raw.contains("\"file_result\"")) {
                    val resp = xrJson.decodeFromString(FileOperationResponse.serializer(), raw)
                    _fileResults.emit(resp)
                    return@launch
                }
                // Pairing responses
                if (raw.contains("\"pairing\"")) {
                    val cmd = xrJson.decodeFromString(XRCommand.serializer(), raw)
                    when (cmd.action) {
                        "approved" -> {
                            sessionToken = cmd.token
                            _status.value = ConnectionStatus.CONNECTED
                            Log.i(TAG, "Pairing approved, token saved")
                        }
                        "pending" -> {
                            Log.i(TAG, "Pairing pending — approve on glasses")
                            _status.value = ConnectionStatus.PAIRING
                        }
                        "rejected" -> {
                            Log.w(TAG, "Pairing rejected")
                            disconnect()
                        }
                    }
                    return@launch
                }
            } catch (e: Exception) {
                Log.w(TAG, "Parse error: ${e.message}")
            }
        }
    }

    private fun onDisconnected() {
        _status.value = ConnectionStatus.DISCONNECTED
        _deviceState.value = null
    }

    private fun scheduleReconnect() {
        val device = currentDevice ?: return
        reconnectJob = scope.launch {
            delay(3000)
            if (_status.value == ConnectionStatus.DISCONNECTED) {
                Log.i(TAG, "Auto-reconnecting…")
                _status.value = ConnectionStatus.CONNECTING
                doConnect(device)
            }
        }
    }

    /** Store the last approved session token for a device */
    var lastApprovedToken: String get() = sessionToken; set(v) { sessionToken = v }
}

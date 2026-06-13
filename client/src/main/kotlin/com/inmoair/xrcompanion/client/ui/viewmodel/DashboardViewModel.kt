package com.inmoair.xrcompanion.client.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inmoair.xrcompanion.client.data.DeviceRepository
import com.inmoair.xrcompanion.client.network.CommandSender
import com.inmoair.xrcompanion.client.network.ConnectionStatus
import com.inmoair.xrcompanion.client.network.DeviceDiscovery
import com.inmoair.xrcompanion.client.network.DiscoveredDevice
import com.inmoair.xrcompanion.client.network.XRWebSocketClient
import com.inmoair.xrcompanion.shared.protocol.DeviceState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val connectedDevice: DiscoveredDevice? = null,
    val deviceState: DeviceState? = null,
    val discoveredDevices: List<DiscoveredDevice> = emptyList(),
    val isScanning: Boolean = false,
    val brightness: Float = 0.5f,
    val volume: Float = 0.5f,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val discovery: DeviceDiscovery,
    private val wsClient: XRWebSocketClient,
    private val commandSender: CommandSender,
    private val deviceRepository: DeviceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            wsClient.status.collectLatest { status ->
                _uiState.value = _uiState.value.copy(connectionStatus = status)
                if (status == ConnectionStatus.CONNECTED) {
                    // Save token
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
            discovery.devices.collectLatest { devices ->
                _uiState.value = _uiState.value.copy(discoveredDevices = devices)
            }
        }
        viewModelScope.launch {
            discovery.isScanning.collectLatest { scanning ->
                _uiState.value = _uiState.value.copy(isScanning = scanning)
            }
        }
        // Auto-connect to last known device — only runs ONCE at startup via .first()
        // so saving a new device later does NOT re-trigger a second connect attempt.
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
            // Cancel any pending reconnect first
            wsClient.disconnect()
            discovery.stopScan()
            // Persist for future sessions
            deviceRepository.saveLastDevice(device.message)
            val token = deviceRepository.sessionToken.first()
            // Update UI immediately so the button reflects "Connecting"
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
}

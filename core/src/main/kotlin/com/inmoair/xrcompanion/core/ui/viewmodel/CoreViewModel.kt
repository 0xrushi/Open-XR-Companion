package com.inmoair.xrcompanion.core.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inmoair.xrcompanion.core.auth.PairingManager
import com.inmoair.xrcompanion.core.auth.TrustedDevice
import com.inmoair.xrcompanion.core.data.AppPreferences
import com.inmoair.xrcompanion.core.permission.PermissionManager
import com.inmoair.xrcompanion.core.permission.PermissionState
import com.inmoair.xrcompanion.core.service.WebSocketServerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CoreUiState(
    val isServerRunning: Boolean = false,
    val connectedClientName: String? = null,
    val wsPort: Int = 8765,
    val deviceName: String = "",
    val permissions: PermissionState = PermissionState(),
    val trustedDevices: List<TrustedDevice> = emptyList(),
    val pendingPairRequest: Pair<String, String>? = null, // deviceId to deviceName
)

@HiltViewModel
class CoreViewModel @Inject constructor(
    private val permissionManager: PermissionManager,
    private val pairingManager: PairingManager,
    private val wsServer: WebSocketServerManager,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoreUiState())
    val uiState: StateFlow<CoreUiState> = _uiState.asStateFlow()

    val trustedDevices = pairingManager.trustedDevicesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            appPreferences.wsPort.collect { port ->
                _uiState.value = _uiState.value.copy(wsPort = port)
            }
        }
        viewModelScope.launch {
            appPreferences.deviceName.collect { name ->
                _uiState.value = _uiState.value.copy(deviceName = name)
            }
        }
        refreshPermissions()
    }

    fun refreshPermissions() {
        permissionManager.refresh()
        _uiState.value = _uiState.value.copy(permissions = permissionManager.state.value)
    }

    fun setServerRunning(running: Boolean) {
        _uiState.value = _uiState.value.copy(isServerRunning = running)
    }

    fun setConnectedClient(name: String?) {
        _uiState.value = _uiState.value.copy(connectedClientName = name)
    }

    fun onPairingRequest(deviceId: String, deviceName: String) {
        _uiState.value = _uiState.value.copy(pendingPairRequest = deviceId to deviceName)
    }

    fun approvePairing() {
        val pending = _uiState.value.pendingPairRequest ?: return
        viewModelScope.launch {
            wsServer.approvePairing(pending.first)
            _uiState.value = _uiState.value.copy(pendingPairRequest = null)
        }
    }

    fun rejectPairing() {
        _uiState.value = _uiState.value.copy(pendingPairRequest = null)
    }

    fun revokeDevice(deviceId: String) {
        viewModelScope.launch {
            pairingManager.revokeDevice(deviceId)
        }
    }

    fun saveDeviceName(name: String) {
        viewModelScope.launch { appPreferences.setDeviceName(name) }
    }
}

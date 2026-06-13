package com.inmoair.xrcompanion.client.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inmoair.xrcompanion.client.network.CommandSender
import com.inmoair.xrcompanion.client.network.XRWebSocketClient
import com.inmoair.xrcompanion.shared.protocol.AppInfo
import com.inmoair.xrcompanion.shared.protocol.AppListResponse
import com.inmoair.xrcompanion.shared.protocol.xrJson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppManagerUiState(
    val apps: List<AppInfo> = emptyList(),
    val filteredApps: List<AppInfo> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
)

@HiltViewModel
class AppManagerViewModel @Inject constructor(
    private val commandSender: CommandSender,
    private val wsClient: XRWebSocketClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppManagerUiState())
    val uiState: StateFlow<AppManagerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            wsClient.messages.collect { raw ->
                if (raw.contains("\"apps_list\"")) {
                    try {
                        val response = xrJson.decodeFromString(AppListResponse.serializer(), raw)
                        val apps = response.apps
                        _uiState.value = _uiState.value.copy(
                            apps = apps,
                            filteredApps = filterApps(apps, _uiState.value.searchQuery),
                            isLoading = false,
                        )
                    } catch (_: Exception) {}
                }
                if (raw.contains("\"background_updated\"")) {
                    // Refresh
                    refreshApps()
                }
            }
        }
    }

    fun refreshApps() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        commandSender.requestAppList()
    }

    fun setSearch(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            filteredApps = filterApps(_uiState.value.apps, query),
        )
    }

    fun toggleBackground(app: AppInfo) {
        val newAllowed = app.backgroundStatus != "allowed"
        commandSender.setAppBackground(app.packageName, newAllowed)
        // Optimistic UI update
        val updated = _uiState.value.apps.map {
            if (it.packageName == app.packageName)
                it.copy(backgroundStatus = if (newAllowed) "allowed" else "restricted")
            else it
        }
        _uiState.value = _uiState.value.copy(
            apps = updated,
            filteredApps = filterApps(updated, _uiState.value.searchQuery),
        )
    }

    private fun filterApps(apps: List<AppInfo>, query: String): List<AppInfo> {
        if (query.isBlank()) return apps
        val q = query.lowercase()
        return apps.filter {
            it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }
}

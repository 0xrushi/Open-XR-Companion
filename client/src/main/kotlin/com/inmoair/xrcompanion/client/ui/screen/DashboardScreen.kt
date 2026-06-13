package com.inmoair.xrcompanion.client.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inmoair.xrcompanion.client.network.ConnectionStatus
import com.inmoair.xrcompanion.client.network.DiscoveredDevice
import com.inmoair.xrcompanion.client.ui.theme.*
import com.inmoair.xrcompanion.client.ui.viewmodel.DashboardUiState
import com.inmoair.xrcompanion.client.ui.viewmodel.DashboardViewModel

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    viewModel: DashboardViewModel,
    onNavigateControl: () -> Unit,
    onNavigateApps: () -> Unit,
    onNavigateSettings: () -> Unit,
) {
    var showDeviceSheet by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(DarkBackground)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // App title
            item {
                Text(
                    "Smart Companion",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // Connection card
            item {
                ConnectionCard(
                    uiState = uiState,
                    onScanClick = {
                        if (uiState.isScanning) viewModel.stopScan()
                        else { viewModel.startScan(); showDeviceSheet = true }
                    },
                    onDisconnect = { viewModel.disconnect() },
                )
            }

            // Device list (visible while scanning)
            if (uiState.isScanning || uiState.discoveredDevices.isNotEmpty()) {
                item {
                    DeviceListCard(
                        devices = uiState.discoveredDevices,
                        isScanning = uiState.isScanning,
                        onConnect = { viewModel.connectToDevice(it) },
                    )
                }
            }

            // Brightness card (only when connected)
            if (uiState.connectionStatus == ConnectionStatus.CONNECTED) {
                item {
                    SliderCard(
                        title       = "Brightness",
                        icon        = Icons.Default.WbSunny,
                        value       = uiState.brightness,
                        unit        = "${(uiState.brightness * 100).toInt()}%",
                        onValueChange = { viewModel.setBrightness(it) }
                    )
                }
                item {
                    SliderCard(
                        title       = "Volume",
                        icon        = Icons.Default.VolumeUp,
                        value       = uiState.volume,
                        unit        = "${(uiState.volume * 100).toInt()}%",
                        onValueChange = { viewModel.setVolume(it) }
                    )
                }
            }

            // SpaceWalker card (always shown; controls disabled when not connected)
            item {
                SpaceWalkerCard(
                    uiState   = uiState,
                    onZoomIn  = { viewModel.swZoomIn() },
                    onZoomOut = { viewModel.swZoomOut() },
                    onRotate  = { viewModel.swSetRotation(it) },
                    onAddScreen    = { viewModel.swAddScreen() },
                    onRemoveScreen = { viewModel.swRemoveScreen() },
                )
            }

            // Quick action row
            item { Spacer(Modifier.height(8.dp)) }
            item {
                QuickActionsRow(
                    onControl  = onNavigateControl,
                    onApps     = onNavigateApps,
                    onSettings = onNavigateSettings,
                    connected  = uiState.connectionStatus == ConnectionStatus.CONNECTED,
                )
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    uiState: DashboardUiState,
    onScanClick: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val isConnected = uiState.connectionStatus == ConnectionStatus.CONNECTED
    val isConnecting = uiState.connectionStatus == ConnectionStatus.CONNECTING ||
                       uiState.connectionStatus == ConnectionStatus.PAIRING

    XRCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Status indicator dot
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isConnected  -> StatusGreen
                            isConnecting -> StatusYellow
                            else         -> StatusRed
                        }
                    )
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        isConnected  -> uiState.connectedDevice?.message?.deviceName ?: "XR Glasses"
                        isConnecting -> if (uiState.connectionStatus == ConnectionStatus.PAIRING)
                                            "Waiting for approval…" else "Connecting…"
                        else         -> "Not Connected"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                val battery = uiState.deviceState?.battery ?: -1
                Text(
                    when {
                        isConnected && battery >= 0 -> "Battery: $battery%"
                        isConnected -> "Connected"
                        uiState.connectionStatus == ConnectionStatus.PAIRING ->
                            "Approve on glasses to pair"
                        isConnecting -> "Opening connection…"
                        else -> "Tap to scan for XR glasses"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            when {
                isConnected -> {
                    IconButton(onClick = onDisconnect) {
                        Icon(Icons.Default.LinkOff, contentDescription = "Disconnect", tint = StatusRed)
                    }
                }
                isConnecting -> {
                    // Spinner while handshake is in progress — no tap target
                    CircularProgressIndicator(
                        Modifier.size(28.dp),
                        color = StatusYellow,
                        strokeWidth = 3.dp,
                    )
                }
                else -> {
                    Button(
                        onClick = onScanClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.isScanning) AccentBlueDim else AccentBlue
                        ),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        if (uiState.isScanning) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp), color = TextPrimary, strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Scanning…")
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Scan & Connect")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceListCard(
    devices: List<DiscoveredDevice>,
    isScanning: Boolean,
    onConnect: (DiscoveredDevice) -> Unit,
) {
    XRCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Nearby Devices", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (isScanning) CircularProgressIndicator(Modifier.size(16.dp), color = AccentBlue, strokeWidth = 2.dp)
        }
        Spacer(Modifier.height(8.dp))
        if (devices.isEmpty()) {
            Text("No devices found yet…", style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp))
        } else {
            devices.forEach { device ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onConnect(device) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Devices, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(device.message.deviceName, style = MaterialTheme.typography.bodyLarge)
                        Text("${device.message.ip}:${device.message.wsPort}", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (device.message.battery >= 0) {
                        Text("${device.message.battery}%", fontSize = 12.sp, color = StatusGreen)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
                }
                HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun SliderCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Float,
    unit: String,
    onValueChange: (Float) -> Unit,
) {
    XRCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(unit, style = MaterialTheme.typography.labelLarge, color = AccentBlue)
        }
        Spacer(Modifier.height(8.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = AccentBlue,
                activeTrackColor = AccentBlue,
                inactiveTrackColor = DividerColor,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun QuickActionsRow(
    onControl: () -> Unit,
    onApps: () -> Unit,
    onSettings: () -> Unit,
    connected: Boolean,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QuickActionButton(
            icon  = Icons.Default.Gamepad,
            label = "Control",
            onClick = onControl,
            enabled = connected,
            modifier = Modifier.weight(1f),
        )
        QuickActionButton(
            icon  = Icons.Default.Apps,
            label = "App Manager",
            onClick = onApps,
            enabled = connected,
            modifier = Modifier.weight(1f),
        )
        QuickActionButton(
            icon  = Icons.Default.Settings,
            label = "Settings",
            onClick = onSettings,
            enabled = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) CardDark else CardDark.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                icon, contentDescription = label,
                tint = if (enabled) AccentBlue else TextSecondary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = if (enabled) TextPrimary else TextSecondary)
        }
    }
}

@Composable
private fun SpaceWalkerCard(
    uiState: DashboardUiState,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRotate: (Float) -> Unit,
    onAddScreen: () -> Unit,
    onRemoveScreen: () -> Unit,
) {
    val connected = uiState.connectionStatus == ConnectionStatus.CONNECTED

    XRCard {
        // ── Header ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.ViewInAr,
                contentDescription = null,
                tint = if (connected) AccentBlue else TextSecondary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("SpaceWalker", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (connected) "${uiState.swScreenCount}/3 screens active"
                    else "Connect to XR glasses to control",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (connected) StatusGreen else TextSecondary,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
        Spacer(Modifier.height(16.dp))

        // ── Zoom row ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.ZoomIn,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Zoom",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onZoomOut,
                enabled = connected,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, if (connected) AccentBlueDim else DividerColor
                ),
            ) {
                Text(
                    "−",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (connected) AccentBlue else TextSecondary,
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onZoomIn,
                enabled = connected,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentBlue,
                    disabledContainerColor = DividerColor,
                ),
            ) {
                Text("+", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Rotation slider ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.RotateRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Rotation",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${uiState.swRotation.toInt()}°",
                style = MaterialTheme.typography.labelLarge,
                color = AccentBlue,
                modifier = Modifier.weight(1f),
            )
        }
        Slider(
            value = uiState.swRotation,
            onValueChange = { if (connected) onRotate(it) },
            valueRange = -180f..180f,
            enabled = connected,
            colors = SliderDefaults.colors(
                thumbColor = AccentBlue,
                activeTrackColor = AccentBlue,
                inactiveTrackColor = DividerColor,
                disabledThumbColor = DividerColor,
                disabledActiveTrackColor = DividerColor,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
        Spacer(Modifier.height(16.dp))

        // ── Screen management row ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Default.Splitscreen,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                "Screens",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
                modifier = Modifier.weight(1f),
            )
            // Screen count indicator dots
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { i ->
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(
                                if (connected && i < uiState.swScreenCount) AccentBlue
                                else DividerColor
                            )
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = onRemoveScreen,
                enabled = connected && uiState.swScreenCount > 1,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (connected && uiState.swScreenCount > 1) StatusRed else DividerColor,
                ),
            ) {
                Text(
                    "− Screen",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (connected && uiState.swScreenCount > 1) StatusRed
                    else TextSecondary,
                )
            }
            Button(
                onClick = onAddScreen,
                enabled = connected && uiState.swScreenCount < 3,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentBlue,
                    disabledContainerColor = DividerColor,
                ),
            ) {
                Text("+ Screen", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun XRCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

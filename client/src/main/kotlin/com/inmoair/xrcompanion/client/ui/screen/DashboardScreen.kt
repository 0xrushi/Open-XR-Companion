package com.inmoair.xrcompanion.client.ui.screen

import android.app.Activity
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inmoair.xrcompanion.client.BuildConfig
import com.inmoair.xrcompanion.client.network.ConnectionStatus
import com.inmoair.xrcompanion.client.network.DiscoveredDevice
import com.inmoair.xrcompanion.client.screenshot.LocalScreenshotCapturer
import com.inmoair.xrcompanion.client.ui.theme.*
import com.inmoair.xrcompanion.client.ui.viewmodel.CastMode
import com.inmoair.xrcompanion.client.ui.viewmodel.DashboardUiState
import com.inmoair.xrcompanion.client.ui.viewmodel.DashboardViewModel

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    viewModel: DashboardViewModel,
    onNavigateControl: () -> Unit,
    onNavigateApps: () -> Unit,
    onNavigateFiles: () -> Unit,
    onNavigateSettings: () -> Unit,
) {
    var showDeviceSheet by remember { mutableStateOf(false) }
    var pendingCastMode by remember { mutableStateOf(CastMode.SCREEN) }
    val context = LocalContext.current
    val phoneCastLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) {
            viewModel.onPhoneCastFailed("Phone screen capture permission was cancelled")
            return@rememberLauncherForActivityResult
        }
        viewModel.startPhoneCast(result.resultCode, data, pendingCastMode)
    }

    Box(Modifier.fillMaxSize().background(DarkBackground)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                DashboardHeader()
            }
            item {
                ReferenceDeviceCard(
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

            item {
                DeviceControlPanel(
                    brightness = uiState.brightness,
                    volume = uiState.volume,
                    onBrightnessChange = { viewModel.setBrightness(it) },
                    onVolumeChange = { viewModel.setVolume(it) },
                )
            }
            item {
                FeatureGrid(
                    onControl  = onNavigateControl,
                    onFiles    = onNavigateFiles,
                    onSettings = onNavigateSettings,
                    onScreenCapture = { viewModel.requestScreenshot() },
                    onScreenRecord = { viewModel.startScreenRecord() },
                    onPhoneCast = {
                        if (uiState.isPhoneCasting) {
                            viewModel.stopPhoneCast()
                        } else {
                            pendingCastMode = CastMode.SCREEN
                            viewModel.preparePhoneCastCapture()
                            phoneCastLauncher.launch(LocalScreenshotCapturer.createCaptureIntent(context))
                        }
                    },
                    onLocationCast = {
                        if (uiState.isPhoneCasting) {
                            viewModel.stopPhoneCast()
                        } else {
                            pendingCastMode = CastMode.LOCATION
                            viewModel.preparePhoneCastCapture()
                            phoneCastLauncher.launch(LocalScreenshotCapturer.createCaptureIntent(context))
                        }
                    },
                    phoneCastLabel = if (uiState.isPhoneCasting) "Stop cast" else "Phone cast",
                    locationCastLabel = if (uiState.isPhoneCasting && uiState.castMode == CastMode.LOCATION) {
                        "Stop cast"
                    } else {
                        "Location cast"
                    },
                )
            }
            if (uiState.isPhoneCasting) {
                item {
                    CastControlPanel(
                        title = if (uiState.castMode == CastMode.LOCATION) "Location cast" else "Phone cast",
                        zoom = uiState.castZoom,
                        offsetY = uiState.castOffsetY,
                        landscape = uiState.castLandscape,
                        onZoomChange = { viewModel.setCastZoom(it) },
                        onOffsetYChange = { viewModel.setCastOffsetY(it) },
                        onLandscapeChange = { viewModel.setCastLandscape(it) },
                    )
                }
            }
            if (uiState.showSpaceWalkerSettings) {
                item {
                    SpaceWalkerCard(
                        uiState = uiState,
                        onZoomIn = { viewModel.swZoomIn() },
                        onZoomOut = { viewModel.swZoomOut() },
                        onRotate = { viewModel.swSetRotation(it) },
                        onAddScreen = { viewModel.swAddScreen() },
                        onRemoveScreen = { viewModel.swRemoveScreen() },
                    )
                }
            }
        }

        uiState.screenshotBitmap?.let { bitmap ->
            DashboardScreenshotSheet(
                bitmap = bitmap,
                isPhoneCast = false,
                onCancel = { viewModel.dismissScreenshot() },
                onDownload = {
                    viewModel.saveScreenshotToGallery()
                    viewModel.dismissScreenshot()
                },
            )
        }
        uiState.screenshotError?.let { err ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissScreenshot() },
                title = { Text("Screenshot failed") },
                text = { Text(err) },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissScreenshot() }) { Text("OK") }
                },
            )
        }
    }
}

@Composable
private fun CastControlPanel(
    title: String,
    zoom: Float,
    offsetY: Float,
    landscape: Boolean,
    onZoomChange: (Float) -> Unit,
    onOffsetYChange: (Float) -> Unit,
    onLandscapeChange: (Boolean) -> Unit,
) {
    XRCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
            )
            Text(
                "Rotate",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
            )
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = landscape,
                onCheckedChange = onLandscapeChange,
            )
        }
        Spacer(Modifier.height(14.dp))
        CastSlider(
            title = "Zoom",
            valueText = "${(zoom * 100).toInt()}%",
            value = zoom,
            valueRange = 1f..3f,
            onValueChange = onZoomChange,
        )
        Spacer(Modifier.height(16.dp))
        CastSlider(
            title = "Move up/down",
            valueText = "${(offsetY * 100).toInt()}%",
            value = offsetY,
            valueRange = -1f..1f,
            onValueChange = onOffsetYChange,
        )
    }
}

@Composable
private fun CastSlider(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            valueText,
            style = MaterialTheme.typography.labelLarge,
            color = AccentBlue,
        )
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        colors = SliderDefaults.colors(
            thumbColor = AccentBlue,
            activeTrackColor = AccentBlue,
            inactiveTrackColor = DividerColor,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DashboardHeader() {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "OpenXR Companion",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 31.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun ReferenceDeviceCard(
    uiState: DashboardUiState,
    onScanClick: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val isConnected = uiState.connectionStatus == ConnectionStatus.CONNECTED
    val isConnecting = uiState.connectionStatus == ConnectionStatus.CONNECTING ||
        uiState.connectionStatus == ConnectionStatus.PAIRING
    val battery = uiState.deviceState?.battery?.takeIf { it >= 0 } ?: 86
    val name = when {
        isConnected -> uiState.connectedDevice?.message?.deviceName ?: "INMO AIR3"
        isConnecting -> "INMO AIR3"
        else -> "INMO AIR3"
    }

    XRCard(
        modifier = Modifier
            .heightIn(min = 130.dp)
            .clickable {
                when {
                    isConnected -> onDisconnect()
                    !isConnecting -> onScanClick()
                }
            },
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChipEmblem(Modifier.size(92.dp))
            Spacer(Modifier.width(22.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 29.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(Modifier.height(15.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(15.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) StatusGreen else TextSecondary),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        when {
                            isConnected -> "Connected"
                            isConnecting -> "Connecting"
                            uiState.isScanning -> "Scanning"
                            else -> "Tap to scan"
                        },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Normal,
                        ),
                        color = TextPrimary.copy(alpha = 0.78f),
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                Icon(
                    Icons.Default.Wifi,
                    contentDescription = null,
                    tint = Color(0xFF2EA9FF),
                    modifier = Modifier.size(31.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$battery%",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = StatusGreen,
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Default.BatteryFull,
                        contentDescription = null,
                        tint = StatusGreen,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChipEmblem(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val teal = Color(0xFF4DFFF0)
        val dimTeal = teal.copy(alpha = 0.34f)
        val stroke = 4.dp.toPx()
        val c = center
        val r = size.minDimension * 0.38f

        drawCircle(dimTeal, r, c, style = Stroke(stroke))
        drawRoundRect(
            color = Color(0xFF0B1020),
            topLeft = Offset(c.x - r * 0.48f, c.y - r * 0.48f),
            size = Size(r * 0.96f, r * 0.96f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
            style = Stroke(stroke),
        )
        repeat(4) { i ->
            val horizontal = i < 2
            val sign = if (i % 2 == 0) -1f else 1f
            if (horizontal) {
                drawLine(
                    teal.copy(alpha = 0.7f),
                    Offset(c.x + sign * r * 0.75f, c.y - r * 0.18f),
                    Offset(c.x + sign * r * 1.05f, c.y - r * 0.18f),
                    stroke,
                )
                drawLine(
                    teal.copy(alpha = 0.7f),
                    Offset(c.x + sign * r * 0.75f, c.y + r * 0.18f),
                    Offset(c.x + sign * r * 1.05f, c.y + r * 0.18f),
                    stroke,
                )
            } else {
                drawLine(
                    teal.copy(alpha = 0.7f),
                    Offset(c.x - r * 0.18f, c.y + sign * r * 0.75f),
                    Offset(c.x - r * 0.18f, c.y + sign * r * 1.05f),
                    stroke,
                )
                drawLine(
                    teal.copy(alpha = 0.7f),
                    Offset(c.x + r * 0.18f, c.y + sign * r * 0.75f),
                    Offset(c.x + r * 0.18f, c.y + sign * r * 1.05f),
                    stroke,
                )
            }
        }
        drawCircle(teal.copy(alpha = 0.12f), r * 1.12f, c)
        drawCircle(teal.copy(alpha = 0.5f), r * 1.12f, c, style = Stroke(2.dp.toPx()))
    }
}

@Composable
private fun DeviceControlPanel(
    brightness: Float,
    volume: Float,
    onBrightnessChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
) {
    XRCard {
        ReferenceSlider(
            title = "Brightness",
            icon = Icons.Default.Settings,
            value = brightness,
            unit = "${(brightness * 100).toInt()}%",
            onValueChange = onBrightnessChange,
        )
        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = DividerColor, thickness = 1.dp)
        Spacer(Modifier.height(18.dp))
        ReferenceSlider(
            title = "Volume",
            icon = Icons.Default.VolumeUp,
            value = volume,
            unit = "${(volume * 100).toInt()}%",
            onValueChange = onVolumeChange,
        )
    }
}

@Composable
private fun ReferenceSlider(
    title: String,
    icon: ImageVector,
    value: Float,
    unit: String,
    onValueChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(35.dp))
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    unit,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                    color = TextSecondary,
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                colors = SliderDefaults.colors(
                    thumbColor = TextPrimary,
                    activeTrackColor = AccentBlue,
                    inactiveTrackColor = DividerColor,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FeatureGrid(
    onControl: () -> Unit,
    onFiles: () -> Unit,
    onSettings: () -> Unit,
    onScreenCapture: () -> Unit,
    onScreenRecord: () -> Unit,
    onPhoneCast: () -> Unit,
    onLocationCast: () -> Unit,
    phoneCastLabel: String,
    locationCastLabel: String,
) {
    val tiles = listOf(
        Triple(Icons.Default.Gamepad, "Control mode", onControl),
        Triple(Icons.Default.Folder, "File transfer", onFiles),
        Triple(Icons.Default.Notifications, "Notification", onSettings),
        Triple(Icons.Default.CameraAlt, "Screen capture", onScreenCapture),
        Triple(Icons.Default.Videocam, "Screen record", onScreenRecord),
        Triple(Icons.Default.Cast, phoneCastLabel, onPhoneCast),
        Triple(Icons.Default.LocationOn, locationCastLabel, onLocationCast),
    )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        tiles.chunked(3).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { (icon, label, action) ->
                    FeatureTile(
                        icon = icon,
                        label = label,
                        onClick = action,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FeatureTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .aspectRatio(1.04f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, DividerColor),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(38.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                color = TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun DashboardScreenshotSheet(
    bitmap: Bitmap,
    isPhoneCast: Boolean,
    onCancel: () -> Unit,
    onDownload: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(Color(0xFF1A1F26))
                .padding(horizontal = 16.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (isPhoneCast) "Phone cast" else "Screen capture",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            )
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Screen capture",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(255.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(58.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue),
                ) {
                    Text("Cancel", style = MaterialTheme.typography.titleMedium)
                }
                Button(
                    onClick = onDownload,
                    modifier = Modifier.weight(1f).height(58.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF171D24)),
                ) {
                    Icon(
                        if (isPhoneCast) Icons.Default.Cast else Icons.Default.Download,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(25.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        if (isPhoneCast) "Send" else "Download",
                        style = MaterialTheme.typography.titleMedium,
                        color = AccentBlue,
                    )
                }
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
    onFiles: () -> Unit,
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
            icon = Icons.Default.Folder,
            label = "Files",
            onClick = onFiles,
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, DividerColor),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

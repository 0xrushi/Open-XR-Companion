package com.inmoair.xrcompanion.core.ui.screen

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inmoair.xrcompanion.core.ui.theme.*
import com.inmoair.xrcompanion.core.ui.viewmodel.CoreUiState
import com.inmoair.xrcompanion.core.ui.viewmodel.CoreViewModel

@Composable
fun DashboardScreen(
    uiState: CoreUiState,
    viewModel: CoreViewModel,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
) {
    val context = LocalContext.current

    // Pairing dialog
    uiState.pendingPairRequest?.let { (deviceId, deviceName) ->
        AlertDialog(
            onDismissRequest = { viewModel.rejectPairing() },
            title = { Text("Pairing Request") },
            text  = { Text("Allow \"$deviceName\" to control this device?") },
            confirmButton = {
                Button(onClick = { viewModel.approvePairing() }) { Text("Allow") }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.rejectPairing() }) { Text("Deny") }
            },
            containerColor = CardDark,
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 20.dp),
    ) {
        // Header
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("XR Core", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        if (uiState.isServerRunning) "Server Active" else "Server Stopped",
                        color = if (uiState.isServerRunning) StatusGreen else TextSecondary,
                        fontSize = 13.sp
                    )
                }
                IconButton(onClick = { viewModel.refreshPermissions() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextSecondary)
                }
            }
        }

        // Server status card
        item { ServerCard(uiState, onStartServer, onStopServer) }

        // Permissions card
        item { PermissionsCard(uiState, context) }

        // Trusted devices card
        item {
            val devices by viewModel.trustedDevices.collectAsState()
            if (devices.isNotEmpty()) {
                TrustedDevicesCard(devices.map { it.deviceName to it.deviceId }) {
                    viewModel.revokeDevice(it)
                }
            }
        }
    }
}

@Composable
private fun ServerCard(
    uiState: CoreUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    XRCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Wifi,
                contentDescription = null,
                tint = if (uiState.isServerRunning) AccentBlue else TextSecondary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Local Server Status", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        uiState.connectedClientName != null ->
                            "Connected — ${uiState.connectedClientName}"
                        uiState.isServerRunning ->
                            "Waiting for phone… (port ${uiState.wsPort})"
                        else -> "Server stopped"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        uiState.connectedClientName != null -> StatusGreen
                        uiState.isServerRunning -> AccentBlue
                        else -> TextSecondary
                    }
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = if (uiState.isServerRunning) onStop else onStart,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isServerRunning) StatusRed else AccentBlue
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(if (uiState.isServerRunning) "Stop" else "Start")
            }
        }
    }
}

@Composable
private fun PermissionsCard(uiState: CoreUiState, context: android.content.Context) {
    XRCard {
        Text("Permissions", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        PermissionRow(
            label   = "Accessibility Service",
            granted = uiState.permissions.accessibilityEnabled,
            onGrant = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        )
        PermissionRow(
            label   = "Change Brightness",
            granted = uiState.permissions.modifySettingsGranted,
            onGrant = {
                context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        )
        PermissionRow(
            label   = "Display Over Apps",
            granted = uiState.permissions.overlayGranted,
            onGrant = {
                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        )
        PermissionRow(
            label   = "Usage Access",
            granted = uiState.permissions.usageAccessGranted,
            onGrant = {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        )
        PermissionRow(
            label   = "Notifications",
            granted = uiState.permissions.notificationGranted,
            onGrant = {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        )
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onGrant: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (granted) StatusGreen else StatusRed,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (!granted) {
            TextButton(onClick = onGrant) {
                Text("Grant", color = AccentBlue, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun TrustedDevicesCard(
    devices: List<Pair<String, String>>,
    onRevoke: (String) -> Unit,
) {
    XRCard {
        Text("Trusted Phones", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        devices.forEach { (name, id) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { onRevoke(id) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Revoke", tint = StatusRed)
                }
            }
        }
    }
}

@Composable
fun XRCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

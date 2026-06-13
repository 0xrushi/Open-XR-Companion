package com.inmoair.xrcompanion.client.ui.screen

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inmoair.xrcompanion.client.ui.theme.*
import com.inmoair.xrcompanion.client.ui.viewmodel.AppManagerUiState
import com.inmoair.xrcompanion.client.ui.viewmodel.AppManagerViewModel
import com.inmoair.xrcompanion.shared.protocol.AppInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerScreen(
    uiState: AppManagerUiState,
    viewModel: AppManagerViewModel,
    onBack: () -> Unit,
) {
    var showProtectedWarning by remember { mutableStateOf<AppInfo?>(null) }

    // Warning dialog before disabling system apps
    showProtectedWarning?.let { app ->
        AlertDialog(
            onDismissRequest = { showProtectedWarning = null },
            title = { Text("Warning") },
            text  = { Text("\"${app.label}\" is a system app. Restricting it may cause instability. Continue?") },
            confirmButton = {
                Button(
                    onClick = { viewModel.toggleBackground(app); showProtectedWarning = null },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed),
                ) { Text("Restrict Anyway") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showProtectedWarning = null }) { Text("Cancel") }
            },
            containerColor = CardDark,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Manager") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshApps() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark),
            )
        },
        containerColor = DarkBackground,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearch(it) },
                placeholder = { Text("Search apps…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = DividerColor,
                    cursorColor = AccentBlue,
                ),
            )

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentBlue)
                }
            } else if (uiState.filteredApps.isEmpty() && uiState.apps.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(64.dp), tint = TextSecondary)
                        Spacer(Modifier.height(12.dp))
                        Text("No apps loaded", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.refreshApps() },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) {
                            Text("Load App List")
                        }
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(uiState.filteredApps, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            onToggle = {
                                if (app.isProtected) return@AppRow
                                if (app.isSystemApp && app.backgroundStatus == "allowed") {
                                    showProtectedWarning = app
                                } else {
                                    viewModel.toggleBackground(app)
                                }
                            }
                        )
                        HorizontalDivider(color = DividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 72.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppInfo, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon
        AppIcon(app, Modifier.size(44.dp))
        Spacer(Modifier.width(12.dp))
        // Info
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val (statusColor, statusText) = when {
                app.isProtected -> TextSecondary to "Protected"
                app.backgroundStatus == "allowed" -> StatusGreen to "Allowed"
                else -> StatusRed to "Restricted"
            }
            Text(statusText, fontSize = 12.sp, color = statusColor)
        }
        // Toggle
        Switch(
            checked = app.backgroundStatus == "allowed",
            onCheckedChange = { onToggle() },
            enabled = !app.isProtected,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TextPrimary,
                checkedTrackColor = StatusGreen,
                uncheckedThumbColor = TextPrimary,
                uncheckedTrackColor = StatusRed.copy(alpha = 0.5f),
            ),
        )
    }
}

@Composable
private fun AppIcon(app: AppInfo, modifier: Modifier = Modifier) {
    val image = remember(app.iconBase64) {
        if (app.iconBase64.isEmpty()) {
            null
        } else {
            runCatching {
            val bytes = Base64.decode(app.iconBase64, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }

    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(8.dp)),
        )
    } else {
        DefaultAppIcon(modifier)
    }
}

@Composable
private fun DefaultAppIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CardDark),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Android, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(24.dp))
    }
}

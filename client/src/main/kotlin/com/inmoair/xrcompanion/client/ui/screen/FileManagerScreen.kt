package com.inmoair.xrcompanion.client.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inmoair.xrcompanion.client.ui.theme.*
import com.inmoair.xrcompanion.client.ui.viewmodel.FileManagerUiState
import com.inmoair.xrcompanion.client.ui.viewmodel.FileManagerViewModel
import com.inmoair.xrcompanion.shared.protocol.FileEntry
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    uiState: FileManagerUiState,
    viewModel: FileManagerViewModel,
    onBack: () -> Unit,
) {
    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) viewModel.upload(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Files") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.goUp() }, enabled = uiState.remotePath.isNotBlank()) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up", tint = AccentBlue)
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = AccentBlue)
                    }
                    IconButton(onClick = { uploadLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Default.UploadFile, contentDescription = "Upload", tint = AccentBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark),
            )
        },
        containerColor = DarkBackground,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PathCard(path = uiState.remotePath.ifBlank { "Device storage" })

            if (uiState.isTransferring || uiState.isLoading || uiState.status.isNotEmpty() || uiState.error.isNotEmpty()) {
                StatusCard(uiState)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(uiState.entries, key = { it.path }) { entry ->
                    FileEntryRow(
                        entry = entry,
                        onOpen = { viewModel.openDirectory(entry) },
                        onDownload = { viewModel.download(entry) },
                        enabled = !uiState.isTransferring,
                    )
                }
            }
        }
    }
}

@Composable
private fun PathCard(path: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CardDark)
            .padding(10.dp),
    ) {
        Text("Remote path", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(
            path,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusCard(uiState: FileManagerUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CardDark)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (uiState.isLoading || uiState.isTransferring) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = AccentBlue,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            uiState.error.ifEmpty { uiState.status.ifEmpty { "Loading" } },
            style = MaterialTheme.typography.bodySmall,
            color = if (uiState.error.isEmpty()) TextSecondary else StatusRed,
        )
    }
}

@Composable
private fun FileEntryRow(
    entry: FileEntry,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CardDark)
            .clickable(enabled = enabled && entry.isDirectory, onClick = onOpen)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (entry.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
            contentDescription = null,
            tint = if (entry.isDirectory) AccentBlue else TextSecondary,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (entry.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                fileMeta(entry),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!entry.isDirectory) {
            IconButton(onClick = onDownload, enabled = enabled) {
                Icon(Icons.Default.Download, contentDescription = "Download", tint = AccentBlue)
            }
        }
    }
}

private fun fileMeta(entry: FileEntry): String {
    val date = if (entry.lastModified > 0) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.lastModified))
    } else ""
    val size = if (entry.isDirectory) "Folder" else formatBytes(entry.size)
    return listOf(size, date).filter { it.isNotEmpty() }.joinToString(" • ")
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return "%.1f MB".format(mb)
    return "%.1f GB".format(mb / 1024.0)
}

package com.inmoair.xrcompanion.client.ui.viewmodel

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inmoair.xrcompanion.client.network.CommandSender
import com.inmoair.xrcompanion.client.network.XRWebSocketClient
import com.inmoair.xrcompanion.shared.protocol.FileEntry
import com.inmoair.xrcompanion.shared.protocol.FileOperationResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

data class FileManagerUiState(
    val remotePath: String = "",
    val entries: List<FileEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isTransferring: Boolean = false,
    val status: String = "",
    val error: String = "",
)

@HiltViewModel
class FileManagerViewModel @Inject constructor(
    private val commandSender: CommandSender,
    private val wsClient: XRWebSocketClient,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileManagerUiState(isLoading = true))
    val uiState: StateFlow<FileManagerUiState> = _uiState.asStateFlow()

    private var activeDownload: ActiveDownload? = null

    init {
        viewModelScope.launch {
            wsClient.fileLists.collect { resp ->
                _uiState.value = _uiState.value.copy(
                    remotePath = resp.path,
                    entries = resp.entries,
                    isLoading = false,
                    error = resp.error,
                    status = if (resp.error.isEmpty()) "${resp.entries.size} items" else "",
                )
            }
        }
        viewModelScope.launch {
            wsClient.fileChunks.collect { chunk ->
                handleFileChunk(chunk.path, chunk.offset, chunk.size, chunk.data, chunk.eof, chunk.error)
            }
        }
        viewModelScope.launch {
            wsClient.fileResults.collect { result ->
                handleFileResult(result)
            }
        }
        refresh()
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = "")
        commandSender.requestFileList(_uiState.value.remotePath)
    }

    fun openDirectory(entry: FileEntry) {
        if (!entry.isDirectory) return
        _uiState.value = _uiState.value.copy(isLoading = true, error = "")
        commandSender.requestFileList(entry.path)
    }

    fun goUp() {
        val path = _uiState.value.remotePath
        if (path.isBlank()) return
        val parent = File(path).parent ?: ""
        _uiState.value = _uiState.value.copy(isLoading = true, error = "")
        commandSender.requestFileList(parent)
    }

    fun download(entry: FileEntry) {
        if (entry.isDirectory) return
        activeDownload = ActiveDownload(
            path = entry.path,
            name = entry.name.ifBlank { "download_${System.currentTimeMillis()}" },
            size = entry.size,
        )
        _uiState.value = _uiState.value.copy(
            isTransferring = true,
            status = "Downloading ${entry.name}",
            error = "",
        )
        commandSender.requestFileChunk(entry.path, offset = 0L)
    }

    fun upload(uri: Uri) {
        viewModelScope.launch {
            val name = resolveDisplayName(uri)
            val targetPath = targetRemotePath(name)
            _uiState.value = _uiState.value.copy(
                isTransferring = true,
                status = "Uploading $name",
                error = "",
            )

            try {
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        val buffer = ByteArray(96 * 1024)
                        var offset = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            val chunk = buffer.copyOf(read)
                            val b64 = Base64.encodeToString(chunk, Base64.NO_WRAP)
                            commandSender.uploadFileChunk(targetPath, offset, b64, eof = false)
                            offset += read
                            delay(8)
                        }
                        commandSender.uploadFileChunk(targetPath, offset, "", eof = true)
                    } ?: error("Unable to open selected file")
                }
                _uiState.value = _uiState.value.copy(status = "Uploaded $name")
                delay(250)
                commandSender.requestFileList(_uiState.value.remotePath)
            } catch (e: Exception) {
                Log.e("FileManagerVM", "Upload failed: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isTransferring = false,
                    error = e.message ?: "Upload failed",
                )
            }
        }
    }

    private fun handleFileChunk(
        path: String,
        offset: Long,
        size: Long,
        data: String,
        eof: Boolean,
        error: String,
    ) {
        val download = activeDownload ?: return
        if (path != download.path && path.isNotEmpty()) return
        if (error.isNotEmpty()) {
            activeDownload = null
            _uiState.value = _uiState.value.copy(
                isTransferring = false,
                error = error,
                status = "",
            )
            return
        }

        val bytes = if (data.isNotEmpty()) Base64.decode(data, Base64.NO_WRAP) else ByteArray(0)
        download.output.write(bytes)
        val nextOffset = offset + bytes.size
        val label = if (size > 0) "${(nextOffset * 100 / size).coerceIn(0, 100)}%" else "${nextOffset} bytes"
        _uiState.value = _uiState.value.copy(status = "Downloading ${download.name} $label")

        if (eof) {
            activeDownload = null
            val payload = download.output.toByteArray()
            viewModelScope.launch {
                val saved = saveToDownloads(download.name, payload)
                _uiState.value = _uiState.value.copy(
                    isTransferring = false,
                    status = if (saved) "Saved ${download.name} to Downloads/XRCompanion" else "",
                    error = if (saved) "" else "Failed to save ${download.name}",
                )
            }
        } else {
            commandSender.requestFileChunk(download.path, nextOffset)
        }
    }

    private fun handleFileResult(result: FileOperationResponse) {
        if (result.action != "write") return
        if (result.status == "failed") {
            _uiState.value = _uiState.value.copy(
                isTransferring = false,
                error = result.message.ifEmpty { "Upload failed" },
            )
        } else if (result.status == "complete") {
            _uiState.value = _uiState.value.copy(
                isTransferring = false,
                status = result.message.ifEmpty { "Upload complete" },
                error = "",
            )
        }
    }

    private fun targetRemotePath(fileName: String): String {
        val current = _uiState.value.remotePath
        return if (current.isBlank()) fileName
        else "${current.trimEnd('/')}/$fileName"
    }

    private suspend fun resolveDisplayName(uri: Uri): String = withContext(Dispatchers.IO) {
        val cursor = appContext.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && it.moveToFirst()) {
                return@withContext sanitizeFileName(it.getString(index))
            }
        }
        sanitizeFileName(uri.lastPathSegment ?: "upload_${System.currentTimeMillis()}")
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank {
            "file_${System.currentTimeMillis()}"
        }
    }

    private suspend fun saveToDownloads(name: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val mime = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
                ?: "application/octet-stream"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/XRCompanion",
                    )
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            }
            val uri = appContext.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values,
            ) ?: return@withContext false
            appContext.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                appContext.contentResolver.update(uri, values, null, null)
            }
            true
        } catch (e: Exception) {
            Log.e("FileManagerVM", "Save download failed: ${e.message}")
            false
        }
    }

    private data class ActiveDownload(
        val path: String,
        val name: String,
        val size: Long,
        val output: ByteArrayOutputStream = ByteArrayOutputStream(),
    )
}

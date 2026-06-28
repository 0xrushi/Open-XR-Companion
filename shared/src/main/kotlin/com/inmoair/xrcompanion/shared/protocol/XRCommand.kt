package com.inmoair.xrcompanion.shared.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ---------------------------------------------------------------------------
// Central JSON codec — shared by both Core and Client
// ---------------------------------------------------------------------------
val xrJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}

// ---------------------------------------------------------------------------
// All commands are flattened into a single class to avoid sealed-class
// polymorphism issues across the wire.  Only the fields relevant to each
// command type are populated; the rest carry their defaults.
// ---------------------------------------------------------------------------
@Serializable
data class XRCommand(
    /** One of: touch | scroll | keyboard | system | apps | pairing | file | screenshot | spacewalker | ping */
    val type: String,

    // --- touch / gesture ---
    /** down | move | up | tap | double_tap | long_press | swipe | cursor_show | cursor_hide */
    val action: String = "",
    val x: Float = 0f,
    val y: Float = 0f,
    val x2: Float = 0f,
    val y2: Float = 0f,
    val timestamp: Long = 0L,
    val pointerId: Int = 0,

    // --- scroll ---
    /** vertical | horizontal */
    val axis: String = "",
    val delta: Int = 0,
    val speed: Float = 1f,

    // --- keyboard ---
    /** text | key | backspace | enter | paste */
    val value: String = "",
    val keyCode: Int = 0,

    // --- system ---
    /** set_brightness | set_volume | back | home | recents | sleep | shutdown |
     *  volume_up | volume_down | brightness_up | brightness_down | mute */
    val floatValue: Float = 0f,

    // --- apps ---
    val packageName: String = "",
    val allowed: Boolean = true,

    // --- pairing ---
    /** request | approve | reject | revoke | hello */
    val deviceId: String = "",
    val deviceName: String = "",
    val token: String = "",

    // --- file ---
    val path: String = "",
    val newPath: String = "",
    val offset: Long = 0L,
    val chunkSize: Int = 65536,
    val data: String = "",          // base64-encoded file chunk

    // --- screenshot ---
    val format: String = "jpeg",    // jpeg | png
)

// ---------------------------------------------------------------------------
// Server → Client state updates
// ---------------------------------------------------------------------------
@Serializable
data class DeviceState(
    val type: String = "device_state",
    val battery: Int = -1,
    val brightness: Int = -1,
    val volume: Int = -1,
    val isMuted: Boolean = false,
    /** connected | disconnected */
    val connection: String = "connected",
    val serverVersion: String = "1.0.0",
    val deviceName: String = "",
    val deviceModel: String = "",
)

// ---------------------------------------------------------------------------
// App info (sent as part of apps/list response)
// ---------------------------------------------------------------------------
@Serializable
data class AppInfo(
    val packageName: String,
    val label: String,
    /** allowed | restricted | disabled */
    val backgroundStatus: String = "allowed",
    val isSystemApp: Boolean = false,
    val isProtected: Boolean = false,
    /** base64-encoded PNG icon, may be empty */
    val iconBase64: String = "",
)

// ---------------------------------------------------------------------------
// File entry (sent as part of file/list response)
// ---------------------------------------------------------------------------
@Serializable
data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val mimeType: String = "",
)

// ---------------------------------------------------------------------------
// Bulk app-list response
// ---------------------------------------------------------------------------
@Serializable
data class AppListResponse(
    val type: String = "apps_list",
    val apps: List<AppInfo> = emptyList(),
)

// ---------------------------------------------------------------------------
// File listing response
// ---------------------------------------------------------------------------
@Serializable
data class FileListResponse(
    val type: String = "file_list",
    val path: String = "",
    val entries: List<FileEntry> = emptyList(),
    val error: String = "",
)

// ---------------------------------------------------------------------------
// Permission status (sent from Core to Client on connect)
// ---------------------------------------------------------------------------
@Serializable
data class PermissionStatus(
    val type: String = "permissions",
    val accessibilityEnabled: Boolean = false,
    val overlayGranted: Boolean = false,
    val modifySettingsGranted: Boolean = false,
    val usageAccessGranted: Boolean = false,
    val notificationGranted: Boolean = false,
)

// ---------------------------------------------------------------------------
// Generic error response
// ---------------------------------------------------------------------------
@Serializable
data class ErrorResponse(
    val type: String = "error",
    val code: String = "",
    val message: String = "",
)

// ---------------------------------------------------------------------------
// Screenshot response  (Core → Client)
// ---------------------------------------------------------------------------
@Serializable
data class ScreenshotResponse(
    val type: String = "screenshot_result",
    /** captured | failed | permission_required */
    val status: String,
    /** base64-encoded image data */
    val data: String = "",
    val format: String = "jpeg",
    val width: Int = 0,
    val height: Int = 0,
)

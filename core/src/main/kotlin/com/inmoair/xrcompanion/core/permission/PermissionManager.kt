package com.inmoair.xrcompanion.core.permission

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class PermissionState(
    val accessibilityEnabled: Boolean = false,
    val overlayGranted: Boolean = false,
    val modifySettingsGranted: Boolean = false,
    val usageAccessGranted: Boolean = false,
    val notificationGranted: Boolean = false,
    val storageGranted: Boolean = false,
)

@Singleton
class PermissionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _state = MutableStateFlow(PermissionState())
    val state: StateFlow<PermissionState> = _state.asStateFlow()

    fun refresh() {
        _state.value = PermissionState(
            accessibilityEnabled = isAccessibilityEnabled(),
            overlayGranted = Settings.canDrawOverlays(context),
            modifySettingsGranted = Settings.System.canWrite(context),
            usageAccessGranted = isUsageAccessGranted(),
            notificationGranted = isNotificationGranted(),
            storageGranted = isStorageGranted(),
        )
    }

    fun allCriticalGranted(): Boolean = with(_state.value) {
        accessibilityEnabled && modifySettingsGranted
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = context.getSystemService(AccessibilityManager::class.java)
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == context.packageName
        }
    }

    private fun isUsageAccessGranted(): Boolean {
        return try {
            val appOps = context.getSystemService(android.app.AppOpsManager::class.java)
            val mode = appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) { false }
    }

    private fun isNotificationGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun isStorageGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }
}

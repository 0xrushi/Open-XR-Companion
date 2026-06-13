package com.inmoair.xrcompanion.core.command

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.util.Base64
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.inmoair.xrcompanion.shared.protocol.AppInfo
import com.inmoair.xrcompanion.shared.protocol.AppListResponse
import com.inmoair.xrcompanion.shared.protocol.XRCommand
import com.inmoair.xrcompanion.shared.protocol.xrJson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.java_websocket.WebSocket
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appControlStore: DataStore<Preferences>
        by preferencesDataStore(name = "app_control")

private val DISABLED_APPS_KEY = stringPreferencesKey("disabled_apps")

/** Packages that must never be restricted */
private val PROTECTED_PACKAGES = setOf(
    "com.inmoair.xrcompanion.core",
    "android",
    "com.android.systemui",
    "com.android.launcher3",
    "com.android.launcher",
    "com.android.settings",
    "com.android.inputmethod.latin",
)

@Singleton
class AppControlHandler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val TAG = "AppControl"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    fun handle(cmd: XRCommand, socket: WebSocket) {
        when (cmd.action) {
            "list"                   -> scope.launch { sendAppList(socket) }
            "set_background_allowed" -> scope.launch {
                setBackgroundAllowed(cmd.packageName, cmd.allowed, socket)
            }
            else -> Log.w(TAG, "Unknown apps action: ${cmd.action}")
        }
    }

    private suspend fun sendAppList(socket: WebSocket) {
        val pm = context.packageManager
        val disabledSet = getDisabledApps()

        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0 || isUserVisible(it, pm) }
            .map { info ->
                val label = pm.getApplicationLabel(info).toString()
                val pkg = info.packageName
                val isProtected = PROTECTED_PACKAGES.contains(pkg)
                val isSystem = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
                val status = if (disabledSet.contains(pkg)) "restricted" else "allowed"
                AppInfo(
                    packageName = pkg,
                    label = label,
                    backgroundStatus = status,
                    isSystemApp = isSystem,
                    isProtected = isProtected,
                    iconBase64 = getIconBase64(info, pm),
                )
            }
            .sortedWith(compareBy({ it.isSystemApp }, { it.label.lowercase() }))

        val response = AppListResponse(apps = apps)
        socket.send(xrJson.encodeToString(AppListResponse.serializer(), response))
    }

    private suspend fun setBackgroundAllowed(pkg: String, allowed: Boolean, socket: WebSocket) {
        if (PROTECTED_PACKAGES.contains(pkg)) {
            socket.send("""{"type":"error","code":"protected","message":"Cannot restrict this app"}""")
            return
        }
        val disabled = getDisabledApps().toMutableSet()
        if (allowed) disabled.remove(pkg) else disabled.add(pkg)
        saveDisabledApps(disabled)

        if (!allowed) {
            // Best-effort: kill the app process via ActivityManager
            try {
                val am = context.getSystemService(ActivityManager::class.java)
                am.killBackgroundProcesses(pkg)
                Log.i(TAG, "Killed background processes for $pkg")
            } catch (e: Exception) {
                Log.w(TAG, "Could not kill $pkg: ${e.message}")
            }
        }

        socket.send("""{"type":"apps","action":"background_updated","package":"$pkg","allowed":$allowed}""")
    }

    private suspend fun getDisabledApps(): Set<String> {
        val raw = context.appControlStore.data.first()[DISABLED_APPS_KEY] ?: return emptySet()
        return try { json.decodeFromString<Set<String>>(raw) } catch (_: Exception) { emptySet() }
    }

    private suspend fun saveDisabledApps(set: Set<String>) {
        context.appControlStore.edit { prefs ->
            prefs[DISABLED_APPS_KEY] = json.encodeToString(set)
        }
    }

    private fun isUserVisible(info: ApplicationInfo, pm: PackageManager): Boolean {
        return pm.getLaunchIntentForPackage(info.packageName) != null
    }

    private fun getIconBase64(info: ApplicationInfo, pm: PackageManager): String {
        return try {
            val drawable = pm.getApplicationIcon(info)
            val bitmap = if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else {
                val bmp = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, 48, 48)
                drawable.draw(canvas)
                bmp
            }
            val scaled = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, 85, baos)
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) { "" }
    }
}

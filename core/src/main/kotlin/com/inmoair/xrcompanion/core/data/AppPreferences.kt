package com.inmoair.xrcompanion.core.data

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.inmoair.xrcompanion.shared.discovery.DiscoveryConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.prefsStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.prefsStore

    companion object {
        private val KEY_DEVICE_NAME   = stringPreferencesKey("device_name")
        private val KEY_WS_PORT       = intPreferencesKey("ws_port")
        private val KEY_START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        private val KEY_DEBUG_LOGS    = booleanPreferencesKey("debug_logs")
    }

    val deviceName: Flow<String> = store.data.map {
        it[KEY_DEVICE_NAME] ?: Build.MODEL
    }

    val wsPort: Flow<Int> = store.data.map {
        it[KEY_WS_PORT] ?: DiscoveryConstants.DEFAULT_WS_PORT
    }

    val startOnBoot: Flow<Boolean> = store.data.map { it[KEY_START_ON_BOOT] ?: true }
    val debugLogs: Flow<Boolean>   = store.data.map { it[KEY_DEBUG_LOGS] ?: false }

    suspend fun setDeviceName(name: String) = store.edit { it[KEY_DEVICE_NAME] = name }
    suspend fun setWsPort(port: Int)        = store.edit { it[KEY_WS_PORT] = port }
    suspend fun setStartOnBoot(v: Boolean)  = store.edit { it[KEY_START_ON_BOOT] = v }
    suspend fun setDebugLogs(v: Boolean)    = store.edit { it[KEY_DEBUG_LOGS] = v }
}

package com.inmoair.xrcompanion.client.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.inmoair.xrcompanion.client.network.DiscoveredDevice
import com.inmoair.xrcompanion.shared.discovery.DiscoveryMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.builtins.ListSerializer

private val Context.deviceStore: DataStore<Preferences> by preferencesDataStore(name = "devices")

@Singleton
class DeviceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val store = context.deviceStore

    companion object {
        private val KEY_LAST_DEVICE = stringPreferencesKey("last_device")
        private val KEY_SESSION_TOKEN = stringPreferencesKey("session_token")
        private val KEY_TOUCH_SENSITIVITY = floatPreferencesKey("touch_sensitivity")
        private val KEY_SCROLL_SPEED = floatPreferencesKey("scroll_speed")
        private val KEY_AIRMOUSE_SPEED = floatPreferencesKey("airmouse_speed")
        private val KEY_POINTER_SPEED = floatPreferencesKey("pointer_speed")
        private val KEY_CUSTOM_BUTTONS = stringPreferencesKey("custom_buttons")
    }

    val lastDevice: Flow<DiscoveryMessage?> = store.data.map { prefs ->
        prefs[KEY_LAST_DEVICE]?.let {
            try { json.decodeFromString<DiscoveryMessage>(it) } catch (_: Exception) { null }
        }
    }

    val sessionToken: Flow<String> = store.data.map { it[KEY_SESSION_TOKEN] ?: "" }

    val touchSensitivity: Flow<Float> = store.data.map { it[KEY_TOUCH_SENSITIVITY] ?: 1.0f }
    val scrollSpeed: Flow<Float>      = store.data.map { it[KEY_SCROLL_SPEED]      ?: 1.0f }
    val airMouseSpeed: Flow<Float>    = store.data.map { it[KEY_AIRMOUSE_SPEED]    ?: 1.0f }
    val pointerSpeed: Flow<Float>     = store.data.map { it[KEY_POINTER_SPEED]     ?: 1.0f }

    suspend fun saveLastDevice(device: DiscoveryMessage) {
        store.edit { it[KEY_LAST_DEVICE] = json.encodeToString(device) }
    }

    suspend fun saveSessionToken(token: String) {
        store.edit { it[KEY_SESSION_TOKEN] = token }
    }

    suspend fun setTouchSensitivity(v: Float) { store.edit { it[KEY_TOUCH_SENSITIVITY] = v } }
    suspend fun setScrollSpeed(v: Float)      { store.edit { it[KEY_SCROLL_SPEED] = v } }
    suspend fun setAirMouseSpeed(v: Float)    { store.edit { it[KEY_AIRMOUSE_SPEED] = v } }
    suspend fun setPointerSpeed(v: Float)     { store.edit { it[KEY_POINTER_SPEED] = v } }

    // ── Custom buttons ────────────────────────────────────────────────────────

    val customButtons: Flow<List<CustomButton>> = store.data.map { prefs ->
        prefs[KEY_CUSTOM_BUTTONS]?.let {
            try { json.decodeFromString(ListSerializer(CustomButton.serializer()), it) }
            catch (_: Exception) { emptyList() }
        } ?: emptyList()
    }

    suspend fun saveCustomButton(button: CustomButton) {
        store.edit { prefs ->
            val current = prefs[KEY_CUSTOM_BUTTONS]?.let {
                try { json.decodeFromString(ListSerializer(CustomButton.serializer()), it) }
                catch (_: Exception) { emptyList() }
            } ?: emptyList()
            // Replace if id exists, otherwise append
            val updated = if (current.any { it.id == button.id })
                current.map { if (it.id == button.id) button else it }
            else
                current + button
            prefs[KEY_CUSTOM_BUTTONS] = json.encodeToString(ListSerializer(CustomButton.serializer()), updated)
        }
    }

    suspend fun deleteCustomButton(id: String) {
        store.edit { prefs ->
            val current = prefs[KEY_CUSTOM_BUTTONS]?.let {
                try { json.decodeFromString(ListSerializer(CustomButton.serializer()), it) }
                catch (_: Exception) { emptyList() }
            } ?: emptyList()
            prefs[KEY_CUSTOM_BUTTONS] = json.encodeToString(
                ListSerializer(CustomButton.serializer()),
                current.filter { it.id != id }
            )
        }
    }
}

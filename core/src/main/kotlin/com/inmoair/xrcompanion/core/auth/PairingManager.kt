package com.inmoair.xrcompanion.core.auth

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

private val Context.pairingDataStore: DataStore<Preferences> by preferencesDataStore(name = "pairing")

private val TRUSTED_DEVICES_KEY = stringPreferencesKey("trusted_devices")

@Serializable
data class TrustedDevice(
    val deviceId: String,
    val deviceName: String,
    val token: String,
    val pairedAt: Long = System.currentTimeMillis(),
)

@Singleton
class PairingManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "PairingManager"
    private val json = Json { ignoreUnknownKeys = true }

    // Pending pair requests awaiting user approval (in-memory)
    private val pendingRequests = mutableMapOf<String, TrustedDevice>()

    val trustedDevicesFlow: Flow<List<TrustedDevice>> =
        context.pairingDataStore.data.map { prefs ->
            val raw = prefs[TRUSTED_DEVICES_KEY] ?: return@map emptyList()
            try {
                json.decodeFromString<List<TrustedDevice>>(raw)
            } catch (e: Exception) {
                emptyList()
            }
        }

    /** Returns true if this token is valid for the given deviceId */
    suspend fun isAuthenticated(deviceId: String, token: String): Boolean {
        val devices = trustedDevicesFlow.first()
        return devices.any { it.deviceId == deviceId && it.token == token }
    }

    /** Called when a pairing request arrives — returns a generated token if auto-approved or null */
    fun onPairingRequest(deviceId: String, deviceName: String): String? {
        val token = generateToken()
        pendingRequests[deviceId] = TrustedDevice(deviceId, deviceName, token)
        Log.d(TAG, "Pending pair request from $deviceName ($deviceId)")
        // In production this triggers a UI confirmation dialog; we return null to indicate pending
        return null
    }

    /** Called when the user approves a pending pairing request */
    suspend fun approvePairing(deviceId: String): String? {
        val pending = pendingRequests.remove(deviceId) ?: return null
        saveTrustedDevice(pending)
        Log.d(TAG, "Approved pairing for ${pending.deviceName}")
        return pending.token
    }

    /** Directly trust a device (e.g., after user confirms on-glasses dialog) */
    suspend fun trustDevice(deviceId: String, deviceName: String, token: String) {
        saveTrustedDevice(TrustedDevice(deviceId, deviceName, token))
    }

    suspend fun revokeDevice(deviceId: String) {
        context.pairingDataStore.edit { prefs ->
            val current = prefs[TRUSTED_DEVICES_KEY]
                ?.let { json.decodeFromString<List<TrustedDevice>>(it) }
                ?: emptyList()
            val updated = current.filter { it.deviceId != deviceId }
            prefs[TRUSTED_DEVICES_KEY] = json.encodeToString(updated)
        }
        Log.d(TAG, "Revoked device $deviceId")
    }

    fun getPendingRequests(): Map<String, TrustedDevice> = pendingRequests.toMap()

    private suspend fun saveTrustedDevice(device: TrustedDevice) {
        context.pairingDataStore.edit { prefs ->
            val current = prefs[TRUSTED_DEVICES_KEY]
                ?.let { json.decodeFromString<List<TrustedDevice>>(it) }
                ?: emptyList()
            // replace if already exists
            val updated = current.filter { it.deviceId != device.deviceId } + device
            prefs[TRUSTED_DEVICES_KEY] = json.encodeToString(updated)
        }
    }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

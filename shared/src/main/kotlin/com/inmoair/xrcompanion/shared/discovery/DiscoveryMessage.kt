package com.inmoair.xrcompanion.shared.discovery

import kotlinx.serialization.Serializable
import com.inmoair.xrcompanion.shared.protocol.xrJson

/**
 * JSON payload embedded in the UDP response from the Core app.
 * Also used as the NSD TXT record payload.
 */
@Serializable
data class DiscoveryMessage(
    val deviceName: String,
    val deviceModel: String,
    val ip: String,
    val wsPort: Int = DiscoveryConstants.DEFAULT_WS_PORT,
    val httpPort: Int = DiscoveryConstants.DEFAULT_HTTP_PORT,
    val battery: Int = -1,
    val version: String = "1.0.0",
    val protocolVersion: String = DiscoveryConstants.PROTOCOL_VERSION,
    /** true if this device has already trusted this client */
    val isPaired: Boolean = false,
) {
    fun toJson(): String = xrJson.encodeToString(serializer(), this)

    companion object {
        fun fromJson(json: String): DiscoveryMessage? = try {
            xrJson.decodeFromString(serializer(), json)
        } catch (_: Exception) {
            null
        }
    }
}

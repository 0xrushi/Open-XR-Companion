package com.inmoair.xrcompanion.shared.discovery

object DiscoveryConstants {
    const val NSD_SERVICE_TYPE = "_xrcompanion._tcp"
    const val NSD_SERVICE_NAME = "XRCompanion"
    const val DEFAULT_WS_PORT = 8765
    const val DEFAULT_HTTP_PORT = 8766
    const val UDP_BROADCAST_PORT = 8767
    const val UDP_DISCOVERY_MESSAGE = "XR_COMPANION_DISCOVER"
    const val UDP_RESPONSE_PREFIX = "XR_COMPANION_HERE:"
    const val PROTOCOL_VERSION = "1"
    /** How long the client scans before giving up (ms) */
    const val SCAN_TIMEOUT_MS = 10_000L
    /** UDP broadcast interval during active scan (ms) */
    const val UDP_BROADCAST_INTERVAL_MS = 2_000L
}

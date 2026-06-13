package com.inmoair.xrcompanion.core.service

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.inmoair.xrcompanion.shared.discovery.DiscoveryConstants
import com.inmoair.xrcompanion.shared.discovery.DiscoveryMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UdpDiscoveryServer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val TAG = "UDPDiscovery"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var socket: DatagramSocket? = null

    /** Current device info injected by CoreForegroundService */
    var discoveryMessage: DiscoveryMessage? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                socket = DatagramSocket(DiscoveryConstants.UDP_BROADCAST_PORT)
                Log.i(TAG, "UDP discovery server started on port ${DiscoveryConstants.UDP_BROADCAST_PORT}")
                val buf = ByteArray(1024)
                while (isActive) {
                    val packet = DatagramPacket(buf, buf.size)
                    socket!!.receive(packet)
                    val msg = String(packet.data, 0, packet.length).trim()
                    if (msg == DiscoveryConstants.UDP_DISCOVERY_MESSAGE) {
                        respondToDiscovery(packet)
                    }
                }
            } catch (e: Exception) {
                if (job?.isActive == true) Log.e(TAG, "UDP error: ${e.message}", e)
            }
        }
    }

    fun stop() {
        job?.cancel()
        socket?.close()
        socket = null
        Log.i(TAG, "UDP discovery server stopped")
    }

    private fun respondToDiscovery(request: DatagramPacket) {
        val dm = discoveryMessage ?: return
        val response = (DiscoveryConstants.UDP_RESPONSE_PREFIX + dm.toJson()).toByteArray()
        val reply = DatagramPacket(response, response.size, request.address, request.port)
        socket?.send(reply)
        Log.d(TAG, "Responded to discovery from ${request.address.hostAddress}")
    }
}

package com.inmoair.xrcompanion.client.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.inmoair.xrcompanion.shared.discovery.DiscoveryConstants
import com.inmoair.xrcompanion.shared.discovery.DiscoveryMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import javax.inject.Inject
import javax.inject.Singleton

data class DiscoveredDevice(
    val message: DiscoveryMessage,
    val discoveredAt: Long = System.currentTimeMillis(),
) {
    val id get() = "${message.deviceName}@${message.ip}"
}

@Singleton
class DeviceDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val TAG = "DeviceDiscovery"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: Job? = null
    private var nsdManager: NsdManager? = null

    fun startScan() {
        if (_isScanning.value) return
        _isScanning.value = true
        _devices.value = emptyList()

        // Start UDP broadcast scan
        scanJob = scope.launch {
            udpBroadcastScan()
        }

        // Also start NSD discovery
        startNsdDiscovery()

        Log.i(TAG, "Discovery scan started")
    }

    fun stopScan() {
        scanJob?.cancel()
        stopNsdDiscovery()
        _isScanning.value = false
        Log.i(TAG, "Discovery scan stopped")
    }

    fun clearDevices() { _devices.value = emptyList() }

    // -----------------------------------------------------------------------
    // UDP broadcast
    // -----------------------------------------------------------------------
    private suspend fun udpBroadcastScan() {
        val coroutineContext = currentCoroutineContext()
        val socket = DatagramSocket()
        socket.broadcast = true
        socket.soTimeout = 2000

        val message = DiscoveryConstants.UDP_DISCOVERY_MESSAGE.toByteArray()
        val broadcastAddr = InetAddress.getByName("255.255.255.255")
        val sendPacket = DatagramPacket(
            message, message.size,
            broadcastAddr, DiscoveryConstants.UDP_BROADCAST_PORT
        )

        val receiveBuf = ByteArray(4096)
        val receivePacket = DatagramPacket(receiveBuf, receiveBuf.size)

        try {
            while (coroutineContext.isActive) {
                try { socket.send(sendPacket) } catch (e: Exception) { Log.w(TAG, "UDP send: ${e.message}") }

                val deadline = System.currentTimeMillis() + DiscoveryConstants.UDP_BROADCAST_INTERVAL_MS
                while (System.currentTimeMillis() < deadline && coroutineContext.isActive) {
                    try {
                        socket.receive(receivePacket)
                        val response = String(receivePacket.data, 0, receivePacket.length)
                        if (response.startsWith(DiscoveryConstants.UDP_RESPONSE_PREFIX)) {
                            val json = response.removePrefix(DiscoveryConstants.UDP_RESPONSE_PREFIX)
                            DiscoveryMessage.fromJson(json)?.let { dm ->
                                // Override with the actual sender IP for accuracy
                                val actualDm = dm.copy(ip = receivePacket.address.hostAddress ?: dm.ip)
                                addDevice(actualDm)
                            }
                        }
                    } catch (_: java.net.SocketTimeoutException) { /* normal */ }
                }
            }
        } finally {
            socket.close()
        }
    }

    // -----------------------------------------------------------------------
    // NSD (mDNS)
    // -----------------------------------------------------------------------
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private fun startNsdDiscovery() {
        nsdManager = context.getSystemService(NsdManager::class.java)
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "NSD start failed: $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStarted(serviceType: String) { Log.d(TAG, "NSD discovery started") }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val dm = DiscoveryMessage(
                            deviceName = serviceInfo.serviceName,
                            deviceModel = serviceInfo.serviceName,
                            ip = serviceInfo.host?.hostAddress ?: return,
                            wsPort = serviceInfo.port,
                        )
                        addDevice(dm)
                    }
                })
            }
            override fun onServiceLost(service: NsdServiceInfo) {}
        }
        try {
            nsdManager?.discoverServices(
                DiscoveryConstants.NSD_SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener
            )
        } catch (e: Exception) { Log.w(TAG, "NSD error: ${e.message}") }
    }

    private fun stopNsdDiscovery() {
        try {
            discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (_: Exception) {}
        discoveryListener = null
    }

    private fun addDevice(dm: DiscoveryMessage) {
        val current = _devices.value.toMutableList()
        val idx = current.indexOfFirst { it.id == "${dm.deviceName}@${dm.ip}" }
        val discovered = DiscoveredDevice(dm)
        if (idx >= 0) current[idx] = discovered else current.add(discovered)
        _devices.value = current.toList()
        Log.d(TAG, "Device discovered: ${dm.deviceName} @ ${dm.ip}:${dm.wsPort}")
    }
}

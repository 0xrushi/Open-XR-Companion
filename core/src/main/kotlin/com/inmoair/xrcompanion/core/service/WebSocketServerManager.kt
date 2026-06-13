package com.inmoair.xrcompanion.core.service

import android.util.Log
import com.inmoair.xrcompanion.core.auth.PairingManager
import com.inmoair.xrcompanion.core.command.CommandDispatcher
import com.inmoair.xrcompanion.shared.protocol.DeviceState
import com.inmoair.xrcompanion.shared.protocol.ErrorResponse
import com.inmoair.xrcompanion.shared.protocol.XRCommand
import com.inmoair.xrcompanion.shared.protocol.xrJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Tracks per-connection authentication state */
private data class ConnectionState(
    val socket: WebSocket,
    var deviceId: String = "",
    var deviceName: String = "",
    var isAuthenticated: Boolean = false,
)

@Singleton
class WebSocketServerManager @Inject constructor(
    private val pairingManager: PairingManager,
    private val commandDispatcher: CommandDispatcher,
) {
    private val TAG = "WSServer"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var server: InternalServer? = null
    private val connections = ConcurrentHashMap<WebSocket, ConnectionState>()

    var onClientConnected: ((String) -> Unit)? = null
    var onClientDisconnected: ((String) -> Unit)? = null
    var onPairingRequest: ((deviceId: String, deviceName: String) -> Unit)? = null

    val connectedClients: List<String>
        get() = connections.values.filter { it.isAuthenticated }.map { it.deviceName }

    fun start(port: Int) {
        if (server != null) return
        server = InternalServer(InetSocketAddress(port)).also {
            it.isReuseAddr = true
            it.start()
            Log.i(TAG, "WebSocket server started on port $port")
        }
    }

    fun stop() {
        server?.stop(1000)
        server = null
        connections.clear()
        Log.i(TAG, "WebSocket server stopped")
    }

    fun broadcastState(state: DeviceState) {
        val json = xrJson.encodeToString(DeviceState.serializer(), state)
        connections.values.filter { it.isAuthenticated }.forEach {
            try { it.socket.send(json) } catch (e: Exception) { /* ignore */ }
        }
    }

    fun sendToAll(json: String) {
        connections.values.filter { it.isAuthenticated }.forEach {
            try { it.socket.send(json) } catch (e: Exception) { /* ignore */ }
        }
    }

    /** Called from PairingManager after user approves on-screen dialog */
    fun approvePairing(deviceId: String) {
        scope.launch {
            val token = pairingManager.approvePairing(deviceId) ?: return@launch
            // Find the pending socket and notify it
            val conn = connections.values.find { it.deviceId == deviceId } ?: return@launch
            conn.isAuthenticated = true
            val response = xrJson.encodeToString(
                XRCommand.serializer(),
                XRCommand(type = "pairing", action = "approved", token = token, deviceId = deviceId)
            )
            try { conn.socket.send(response) } catch (e: Exception) { /* ignore */ }
            onClientConnected?.invoke(conn.deviceName)
        }
    }

    // -----------------------------------------------------------------------
    // Internal server
    // -----------------------------------------------------------------------
    private inner class InternalServer(address: InetSocketAddress) : WebSocketServer(address) {

        override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
            this@WebSocketServerManager.connections[conn] = ConnectionState(conn)
            Log.d(TAG, "New connection from ${conn.remoteSocketAddress}")
        }

        override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
            val state = this@WebSocketServerManager.connections.remove(conn)
            if (state?.isAuthenticated == true) {
                onClientDisconnected?.invoke(state.deviceName)
                Log.d(TAG, "Client disconnected: ${state.deviceName}")
            }
        }

        override fun onMessage(conn: WebSocket, message: String) {
            scope.launch {
                handleMessage(conn, message)
            }
        }

        override fun onError(conn: WebSocket?, ex: Exception) {
            Log.e(TAG, "WebSocket error: ${ex.message}", ex)
        }

        override fun onStart() {
            Log.i(TAG, "Server started")
        }
    }

    // -----------------------------------------------------------------------
    // Message handling
    // -----------------------------------------------------------------------
    private suspend fun handleMessage(conn: WebSocket, raw: String) {
        val cmd = try {
            xrJson.decodeFromString(XRCommand.serializer(), raw)
        } catch (e: Exception) {
            sendError(conn, "parse_error", "Invalid JSON: ${e.message}")
            return
        }

        val state = connections[conn] ?: return

        when (cmd.type) {
            "pairing" -> handlePairing(conn, state, cmd)
            "ping" -> conn.send("""{"type":"pong"}""")
            else -> {
                if (!state.isAuthenticated) {
                    sendError(conn, "unauthorized", "Authenticate first via pairing handshake")
                    return
                }
                commandDispatcher.dispatch(cmd, conn)
            }
        }
    }

    private suspend fun handlePairing(conn: WebSocket, state: ConnectionState, cmd: XRCommand) {
        when (cmd.action) {
            "request" -> {
                state.deviceId = cmd.deviceId
                state.deviceName = cmd.deviceName
                // Check if already trusted
                if (pairingManager.isAuthenticated(cmd.deviceId, cmd.token)) {
                    state.isAuthenticated = true
                    val resp = XRCommand(
                        type = "pairing",
                        action = "approved",
                        token = cmd.token,
                        deviceId = cmd.deviceId,
                    )
                    conn.send(xrJson.encodeToString(XRCommand.serializer(), resp))
                    onClientConnected?.invoke(cmd.deviceName)
                } else {
                    // New device — notify UI to show approval dialog
                    pairingManager.onPairingRequest(cmd.deviceId, cmd.deviceName)
                    onPairingRequest?.invoke(cmd.deviceId, cmd.deviceName)
                    conn.send("""{"type":"pairing","action":"pending"}""")
                }
            }
            "revoke" -> {
                pairingManager.revokeDevice(cmd.deviceId)
                conn.send("""{"type":"pairing","action":"revoked"}""")
                conn.close()
            }
        }
    }

    private fun sendError(conn: WebSocket, code: String, message: String) {
        try {
            val err = ErrorResponse(code = code, message = message)
            conn.send(xrJson.encodeToString(ErrorResponse.serializer(), err))
        } catch (_: Exception) {}
    }
}

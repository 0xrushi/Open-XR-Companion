package com.inmoair.xrcompanion.core.service

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.inmoair.xrcompanion.core.BuildConfig
import com.inmoair.xrcompanion.core.MainActivity
import com.inmoair.xrcompanion.core.R
import com.inmoair.xrcompanion.core.XRCoreApp
import com.inmoair.xrcompanion.core.auth.PairingManager
import com.inmoair.xrcompanion.core.data.AppPreferences
import com.inmoair.xrcompanion.core.permission.PermissionManager
import com.inmoair.xrcompanion.shared.discovery.DiscoveryConstants
import com.inmoair.xrcompanion.shared.discovery.DiscoveryMessage
import com.inmoair.xrcompanion.shared.protocol.DeviceState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject

@AndroidEntryPoint
class CoreForegroundService : Service() {

    companion object {
        private const val TAG = "CoreService"
        const val ACTION_START = "com.inmoair.xrcompanion.core.START"
        const val ACTION_STOP  = "com.inmoair.xrcompanion.core.STOP"
        private const val STATE_BROADCAST_INTERVAL_MS = 5_000L

        fun start(context: Context) {
            // minSdk=31 (>= O=26), so startForegroundService is always available.
            context.startForegroundService(
                Intent(context, CoreForegroundService::class.java).setAction(ACTION_START)
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, CoreForegroundService::class.java).setAction(ACTION_STOP)
            )
        }
    }

    @Inject lateinit var wsServer: WebSocketServerManager
    @Inject lateinit var udpServer: UdpDiscoveryServer
    @Inject lateinit var pairingManager: PairingManager
    @Inject lateinit var permissionManager: PermissionManager
    @Inject lateinit var appPreferences: AppPreferences

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var stateBroadcastJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            else        -> startServer()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
        scope.cancel()
        wakeLock?.release()
    }

    // -----------------------------------------------------------------------

    private fun startServer() {
        Log.i(TAG, "Starting Core server")
        startForeground(XRCoreApp.NOTIFICATION_ID, buildNotification("Starting…"))

        acquireWakeLock()

        scope.launch {
            val port = appPreferences.wsPort.first()
            val deviceName = appPreferences.deviceName.first()

            wsServer.onClientConnected = { name ->
                updateNotification("Connected — $name")
                broadcastStateNow()
            }
            wsServer.onClientDisconnected = { _ ->
                updateNotification("Waiting for phone…")
            }
            wsServer.onPairingRequest = { deviceId, name ->
                // Notify MainActivity to show approval dialog.
                // setPackage makes the intent explicit (targets our own app only),
                // satisfying the UnsafeImplicitIntentLaunch lint rule.
                sendBroadcast(Intent("com.inmoair.xrcompanion.PAIRING_REQUEST").apply {
                    setPackage(packageName)
                    putExtra("deviceId", deviceId)
                    putExtra("deviceName", name)
                })
            }

            wsServer.start(port)

            val ip = getLocalIpAddress() ?: "0.0.0.0"
            val model = Build.MODEL

            udpServer.discoveryMessage = DiscoveryMessage(
                deviceName = deviceName,
                deviceModel = model,
                ip = ip,
                wsPort = port,
                battery = getBatteryLevel(),
                version = BuildConfig.VERSION_NAME,
            )
            udpServer.start()

            updateNotification(getString(R.string.notification_text_running, port))
            startStateBroadcast()
        }
    }

    private fun stopServer() {
        stateBroadcastJob?.cancel()
        wsServer.stop()
        udpServer.stop()
        Log.i(TAG, "Core server stopped")
    }

    private fun startStateBroadcast() {
        stateBroadcastJob = scope.launch {
            while (true) {
                delay(STATE_BROADCAST_INTERVAL_MS)
                broadcastStateNow()
                // Refresh UDP discovery battery level
                udpServer.discoveryMessage = udpServer.discoveryMessage?.copy(
                    battery = getBatteryLevel()
                )
            }
        }
    }

    private fun broadcastStateNow() {
        val audioManager = getSystemService(AudioManager::class.java)
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        val brightness = try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        } catch (_: Exception) { 128 }

        val state = DeviceState(
            battery = getBatteryLevel(),
            brightness = (brightness / 255f * 100).toInt(),
            volume = if (maxVol > 0) (curVol * 100 / maxVol) else 0,
            isMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC),
            connection = "connected",
            serverVersion = BuildConfig.VERSION_NAME,
            deviceName = Build.MODEL,
            deviceModel = Build.MODEL,
        )
        wsServer.broadcastState(state)
    }

    private fun getBatteryLevel(): Int {
        val bm = getSystemService(BatteryManager::class.java)
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun getLocalIpAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress
        } catch (_: Exception) { null }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "XRCore:ServerLock").apply {
            acquire(10 * 60 * 60 * 1000L) // 10 hours max
        }
    }

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, XRCoreApp.NOTIFICATION_CHANNEL_ID)
        .setContentTitle(getString(R.string.notification_title))
        .setContentText(text)
        .setSmallIcon(R.drawable.ic_launcher)
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    private fun updateNotification(text: String) {
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.notify(XRCoreApp.NOTIFICATION_ID, buildNotification(text))
    }
}

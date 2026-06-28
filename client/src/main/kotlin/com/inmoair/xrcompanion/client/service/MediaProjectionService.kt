package com.inmoair.xrcompanion.client.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.inmoair.xrcompanion.client.R
import kotlinx.coroutines.CompletableDeferred

class MediaProjectionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Phone screenshot")
            .setContentText("Capturing phone screen")
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        markForegroundReady()
    }

    override fun onDestroy() {
        super.onDestroy()
        synchronized(lock) {
            running = false
            if (!foregroundReady.isCompleted) foregroundReady.complete(Unit)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Phone screenshots",
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    companion object {
        private const val CHANNEL_ID = "phone_screenshot"
        private const val NOTIFICATION_ID = 4002
        private val lock = Any()

        @Volatile
        private var running = false

        @Volatile
        private var foregroundReady = CompletableDeferred<Unit>()

        fun start(context: Context) {
            synchronized(lock) {
                if (!running && foregroundReady.isCompleted) {
                    foregroundReady = CompletableDeferred()
                }
            }
            ContextCompat.startForegroundService(
                context,
                Intent(context, MediaProjectionService::class.java),
            )
        }

        suspend fun awaitForegroundReady() {
            foregroundReady.await()
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MediaProjectionService::class.java))
        }

        private fun markForegroundReady() {
            synchronized(lock) {
                running = true
                foregroundReady.complete(Unit)
            }
        }
    }
}

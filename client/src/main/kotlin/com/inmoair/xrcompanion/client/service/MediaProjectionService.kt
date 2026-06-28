package com.inmoair.xrcompanion.client.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.inmoair.xrcompanion.client.R

class MediaProjectionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Phone screenshot")
                .setContentText("Capturing phone screen")
                .setOngoing(true)
                .build(),
        )
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

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MediaProjectionService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MediaProjectionService::class.java))
        }
    }
}

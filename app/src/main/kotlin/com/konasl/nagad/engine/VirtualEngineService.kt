// ============================================================
// VirtualEngineService.kt
// Package: com.konasl.nagad.engine
// Foreground service that keeps the virtual engine alive.
// Runs in :engine process as declared in AndroidManifest.xml.
// ============================================================
package com.konasl.nagad.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class VirtualEngineService : LifecycleService() {

    companion object {
        private const val TAG              = "VirtualEngineService"
        private const val NOTIF_CHANNEL_ID = "ve_engine_channel"
        private const val NOTIF_ID         = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        Timber.tag(TAG).i("VirtualEngineService created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Timber.tag(TAG).d("onStartCommand")
        lifecycleScope.launch(Dispatchers.IO) {
            // Engine initialization is handled in CoreApplication.initEngineProcess()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.tag(TAG).w("VirtualEngineService destroyed — will restart.")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                "Virtual Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description  = "Keeps virtual app containers running"
                lightColor   = Color.CYAN
                enableLights(false)
                enableVibration(false)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIF_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Nagad Virtual Space")
            .setContentText("Virtual engine running")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
    }
}

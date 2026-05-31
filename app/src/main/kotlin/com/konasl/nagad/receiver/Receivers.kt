// ============================================================
// Receivers.kt
// Package: com.konasl.nagad.receiver
// ============================================================
package com.konasl.nagad.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.konasl.nagad.engine.VirtualEngineService
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("[BootReceiver] ${intent.action}")
        val serviceIntent = Intent(context, VirtualEngineService::class.java)
        context.startForegroundService(serviceIntent)
    }
}

class PackageEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.data?.schemeSpecificPart ?: return
        Timber.d("[PackageEventReceiver] ${intent.action} → $pkg")
        // Notify VirtualEngineSDK to refresh app list or clean up removed app
    }
}

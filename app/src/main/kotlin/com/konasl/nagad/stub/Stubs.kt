// ============================================================
// Stubs.kt
// Package: com.konasl.nagad.stub
// Stub components declared in the manifest for multi-process
// virtual container slots.  Each stub receives its VE session
// via Intent extras and delegates to VirtualEngineSDK.
// ============================================================
package com.konasl.nagad.stub

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import com.konasl.nagad.CoreApplication
import com.konasl.nagad.engine.model.CloneSession
import timber.log.Timber

// ─────────────────────────────────────────────────────────────────────────────
// StubActivity — launched in its slot's process to host a virtual app
// ─────────────────────────────────────────────────────────────────────────────
sealed class StubActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionId = intent.getStringExtra("ve_session_id") ?: return finish()
        val slot      = intent.getIntExtra("ve_slot", -1)
        Timber.d("[Stub] Activity started — session=$sessionId slot=$slot")
        // The VirtualEngineSDK (already initialized in CoreApplication.initStubProcess)
        // will take over from here and load the cloned app's entry point.
        CoreApplication.get().let { app ->
            // Load virtual app's Application class and call through
        }
    }

    class A0 : StubActivity()
    class A1 : StubActivity()
    class A2 : StubActivity()
    class A3 : StubActivity()
    class A4 : StubActivity()
    class A5 : StubActivity()
    class A6 : StubActivity()
    class A7 : StubActivity()
    class A8 : StubActivity()
    class A9 : StubActivity()
}

// ─────────────────────────────────────────────────────────────────────────────
// StubProcess — LifecycleService that keeps a slot process alive
// ─────────────────────────────────────────────────────────────────────────────
sealed class StubProcess : LifecycleService() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }
    class StubProcess0 : StubProcess()
    class StubProcess1 : StubProcess()
    class StubProcess2 : StubProcess()
    class StubProcess3 : StubProcess()
    class StubProcess4 : StubProcess()
    class StubProcess5 : StubProcess()
    class StubProcess6 : StubProcess()
    class StubProcess7 : StubProcess()
    class StubProcess8 : StubProcess()
    class StubProcess9 : StubProcess()
}

// ─────────────────────────────────────────────────────────────────────────────
// StubReceiver — intercepts broadcasts dispatched to virtual apps
// ─────────────────────────────────────────────────────────────────────────────
sealed class StubReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("[StubReceiver] ${this::class.simpleName} action=${intent.action}")
        // Route broadcast to the correct cloned app's BroadcastReceiver
    }
    class R0 : StubReceiver()
    class R1 : StubReceiver()
    class R2 : StubReceiver()
    class R3 : StubReceiver()
    class R4 : StubReceiver()
}

// ─────────────────────────────────────────────────────────────────────────────
// StubContentProvider — proxies ContentProvider queries from virtual apps
// ─────────────────────────────────────────────────────────────────────────────
class StubContentProvider : ContentProvider() {
    override fun onCreate()                                                    = true
    override fun query(uri: Uri, p: Array<String>?, s: String?, a: Array<String>?, so: String?): Cursor? = null
    override fun getType(uri: Uri): String?                                    = null
    override fun insert(uri: Uri, values: ContentValues?): Uri?                = null
    override fun delete(uri: Uri, s: String?, a: Array<String>?): Int         = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?): Int = 0
}

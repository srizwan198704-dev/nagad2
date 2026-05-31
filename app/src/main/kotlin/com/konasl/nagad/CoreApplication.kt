// ============================================================
// CoreApplication.kt
// Package: com.konasl.nagad
// Bootstraps the Virtual Engine SDK on app startup.
// MVVM + Hilt — programmatic UI only.
// ============================================================
package com.konasl.nagad

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import androidx.lifecycle.ProcessLifecycleOwner
import com.konasl.nagad.engine.VirtualEngineSDK
import com.konasl.nagad.engine.hooks.NativeHookManager
import com.konasl.nagad.engine.process.ProcessIdentity
import com.konasl.nagad.util.AppLogger
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class CoreApplication : Application() {

    /** Application-wide coroutine scope — survives configuration changes */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Inject lateinit var virtualEngineSDK: VirtualEngineSDK
    @Inject lateinit var nativeHookManager: NativeHookManager

    companion object {
        private const val TAG = "CoreApplication"
        private lateinit var instance: CoreApplication

        fun get(): CoreApplication = instance

        /** Process name of the current running process */
        fun currentProcessName(context: Context): String {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return getProcessName()
            }
            val pid = Process.myPid()
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            return manager.runningAppProcesses
                ?.firstOrNull { it.pid == pid }
                ?.processName
                ?: context.packageName
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        val processName = currentProcessName(this)
        Timber.tag(TAG).d("onCreate — process: $processName | pid: ${Process.myPid()}")

        when {
            // ── Main (dashboard) process ──────────────────────────────────
            processName == packageName -> {
                initMainProcess()
            }

            // ── Virtual Engine core process (:engine) ─────────────────────
            processName.endsWith(":engine") -> {
                initEngineProcess()
            }

            // ── Stub container processes (:p0 – :p9) ─────────────────────
            processName.matches(Regex(".*:p\\d+$")) -> {
                val slotIndex = processName.substringAfterLast(":p").toIntOrNull() ?: 0
                initStubProcess(slotIndex, processName)
            }

            // ── Binder relay process (:binder) ───────────────────────────
            processName.endsWith(":binder") -> {
                initBinderProcess()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAIN PROCESS — Dashboard, ViewModel, UI layer
    // ─────────────────────────────────────────────────────────────────────────
    private fun initMainProcess() {
        // Logging
        if (BuildConfig.DEBUG) {
            Timber.plant(AppLogger.DebugTree())
        } else {
            Timber.plant(AppLogger.ReleaseTree())
        }
        Timber.tag(TAG).i("Main process initialized.")

        // Observe lifecycle for graceful engine shutdown
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver())

        // Pre-warm engine connection asynchronously
        applicationScope.launch {
            virtualEngineSDK.preWarm()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ENGINE PROCESS — Virtual container core, runs in :engine
    // ─────────────────────────────────────────────────────────────────────────
    private fun initEngineProcess() {
        Timber.tag(TAG).i("Engine process starting.")

        // 1. Load native hooking library
        NativeHookManager.loadNativeLibrary()

        // 2. Patch ART runtime via JNI to intercept method calls
        val artPatchResult = NativeHookManager.patchArtRuntime()
        Timber.tag(TAG).d("ART patch result: $artPatchResult")

        // 3. Set up file-system redirect table from virtual storage mapping
        NativeHookManager.installFsRedirectTable(packageName)

        // 4. Hook Binder IPC to intercept service manager calls
        NativeHookManager.hookBinderIpc()

        // 5. Start Virtual Service Manager (replaces system_server calls)
        applicationScope.launch(Dispatchers.IO) {
            virtualEngineSDK.startVirtualServiceManager()
        }

        Timber.tag(TAG).i("Engine process ready.")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STUB PROCESS — A sandboxed slot that hosts a single cloned app
    // ─────────────────────────────────────────────────────────────────────────
    private fun initStubProcess(slotIndex: Int, processName: String) {
        Timber.tag(TAG).i("Stub process $slotIndex starting — $processName")

        // Identify which cloned package runs in this slot
        val identity = ProcessIdentity.resolveForSlot(this, slotIndex)
            ?: run {
                Timber.tag(TAG).w("No app mapped to slot $slotIndex — aborting stub init.")
                return
            }

        Timber.tag(TAG).i("Slot $slotIndex → ${identity.packageName} | uid: ${identity.uid}")

        // 1. Load native library in this process
        NativeHookManager.loadNativeLibrary()

        // 2. Install hooks specific to this cloned package
        NativeHookManager.installPackageHooks(identity.packageName)

        // 3. Spoof UID / GID to match the cloned app expectation
        NativeHookManager.spoofUid(identity.uid)

        // 4. Redirect all file access to virtual sandbox directory
        NativeHookManager.installFsRedirectTable(identity.packageName)

        // 5. Replace ClassLoader so the cloned APK can load its own dex
        virtualEngineSDK.installVirtualClassLoader(this, identity)

        Timber.tag(TAG).i("Stub process $slotIndex ready for ${identity.packageName}")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BINDER PROCESS — relay IPC between engine and stub slots
    // ─────────────────────────────────────────────────────────────────────────
    private fun initBinderProcess() {
        Timber.tag(TAG).i("Binder relay process starting.")
        NativeHookManager.loadNativeLibrary()
        NativeHookManager.hookBinderIpc()
        applicationScope.launch(Dispatchers.IO) {
            virtualEngineSDK.startBinderRelay()
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Must be called before super.onCreate() to install MultiDex
        // and set up hidden API bypass as early as possible
        HiddenApiBypass.install()
        MultiDexCompat.install(this)
    }
}

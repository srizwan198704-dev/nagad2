// ============================================================
// NativeHookManager.kt
// Package: com.konasl.nagad.engine.hooks
// JNI bridge — Kotlin ↔ native hooking layer (nagad_ve.so)
// ============================================================
package com.konasl.nagad.engine.hooks

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeHookManager @Inject constructor() {

    companion object {
        private const val TAG        = "NativeHookManager"
        private const val LIB_NAME  = "nagad_ve"
        private var nativeLoaded    = false

        // ── Library bootstrap ─────────────────────────────────────────────────
        fun loadNativeLibrary() {
            if (nativeLoaded) return
            try {
                System.loadLibrary(LIB_NAME)
                nativeLoaded = true
                Timber.tag(TAG).i("Native library loaded: lib$LIB_NAME.so")
            } catch (e: UnsatisfiedLinkError) {
                Timber.tag(TAG).e(e, "Failed to load lib$LIB_NAME.so")
                throw RuntimeException("Virtual engine native library unavailable", e)
            }
        }

        // ── ART Runtime ──────────────────────────────────────────────────────
        /** Patches ART method dispatch table for the current process. */
        @JvmStatic external fun patchArtRuntime(): Int

        // ── File-System Redirect ─────────────────────────────────────────────
        /** Installs an FS redirect table so /data/data/<pkg> → virtual dir. */
        @JvmStatic external fun installFsRedirectTable(packageName: String): Boolean

        // ── Binder IPC ───────────────────────────────────────────────────────
        /** Hooks the Binder driver to intercept service-manager transactions. */
        @JvmStatic external fun hookBinderIpc(): Boolean

        // ── Package hooks ─────────────────────────────────────────────────────
        /** Installs package-specific hooks (PM queries, settings, etc.). */
        @JvmStatic external fun installPackageHooks(packageName: String): Boolean

        // ── UID spoofing ──────────────────────────────────────────────────────
        /** Spoofs getuid()/getgid() syscall return values for the sandbox. */
        @JvmStatic external fun spoofUid(targetUid: Int): Boolean

        // ── System call intercept ─────────────────────────────────────────────
        /** Enables raw syscall interception for open/read/stat/ioctl. */
        @JvmStatic external fun enableSyscallIntercept(): Boolean
        @JvmStatic external fun disableSyscallIntercept(): Boolean

        // ── Location spoof ───────────────────────────────────────────────────
        @JvmStatic external fun spoofLocation(lat: Double, lng: Double): Boolean

        // ── Device identity spoof ─────────────────────────────────────────────
        @JvmStatic external fun spoofDeviceId(androidId: String, imei: String): Boolean

        // ── Network redirect ─────────────────────────────────────────────────
        @JvmStatic external fun setNetworkProxy(host: String, port: Int): Boolean

        // ── Status ───────────────────────────────────────────────────────────
        @JvmStatic external fun getHookStatus(): IntArray   // [artHook, fsHook, binderHook]
        @JvmStatic external fun getEngineVersion(): String
    }
}

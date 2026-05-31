// ============================================================
// VirtualEngineSDK.kt
// Package: com.konasl.nagad.engine
// Core Virtual Environment SDK — orchestrates all hooking,
// cloning, and sandbox lifecycle operations.
// ============================================================
package com.konasl.nagad.engine

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import com.konasl.nagad.BuildConfig
import com.konasl.nagad.engine.fs.VirtualFileSystem
import com.konasl.nagad.engine.hooks.NativeHookManager
import com.konasl.nagad.engine.ipc.VirtualServiceManager
import com.konasl.nagad.engine.loader.VirtualClassLoader
import com.konasl.nagad.engine.model.CloneSession
import com.konasl.nagad.engine.model.VirtualApp
import com.konasl.nagad.engine.process.ProcessIdentity
import com.konasl.nagad.engine.process.ProcessSlotManager
import com.konasl.nagad.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VirtualEngineSDK @Inject constructor(
    @ApplicationContext private val context: Context,
    private val virtualFileSystem: VirtualFileSystem,
    private val processSlotManager: ProcessSlotManager,
    private val virtualServiceManager: VirtualServiceManager,
) {
    companion object {
        private const val TAG = "VirtualEngineSDK"
        const val ENGINE_VERSION = BuildConfig.ENGINE_VERSION
        const val VIRTUAL_DATA_DIR = "virtual_space"
    }

    private val _engineState = MutableStateFlow<EngineState>(EngineState.Idle)
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    // ─────────────────────────────────────────────────────────────────────────
    // Engine lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun preWarm() {
        Timber.tag(TAG).d("Pre-warming engine connection …")
        _engineState.value = EngineState.Starting
        try {
            virtualFileSystem.ensureBaseDirectories()
            _engineState.value = EngineState.Ready
            Timber.tag(TAG).i("Engine pre-warm complete.")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Engine pre-warm failed")
            _engineState.value = EngineState.Error(e)
        }
    }

    suspend fun startVirtualServiceManager() {
        Timber.tag(TAG).d("Starting Virtual Service Manager …")
        virtualServiceManager.start()
    }

    suspend fun startBinderRelay() {
        Timber.tag(TAG).d("Starting Binder Relay …")
        virtualServiceManager.startBinderRelay()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // App querying
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all installed packages on the device that can be cloned.
     * Uses QUERY_ALL_PACKAGES permission (granted in manifest).
     */
    fun queryInstallableApps(): List<PackageInfo> {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            PackageManager.GET_META_DATA or
            PackageManager.MATCH_UNINSTALLED_PACKAGES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_META_DATA
        }
        return pm.getInstalledPackages(flags)
            .filter { it.packageName != context.packageName }
            .sortedBy { it.applicationInfo?.loadLabel(pm)?.toString() ?: it.packageName }
    }

    /**
     * Returns all currently cloned (virtual) apps managed by this engine.
     */
    fun getVirtualApps(): List<VirtualApp> {
        return virtualFileSystem.listVirtualApps()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cloning pipeline
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Full cloning pipeline for [packageName]:
     * 1. Parse source APK and extract dex/native libs
     * 2. Allocate a process slot
     * 3. Set up virtual file-system sandbox
     * 4. Rewrite package name in dex headers (optional multi-clone)
     * 5. Persist CloneSession metadata
     */
    suspend fun cloneApp(packageName: String, multiInstance: Boolean = false): Result<CloneSession> {
        return try {
            Timber.tag(TAG).i("Cloning $packageName (multi=$multiInstance) …")
            _engineState.value = EngineState.Cloning(packageName, 0)

            val sourceApk = resolveSourceApk(packageName)
                ?: return Result.failure(IllegalStateException("APK not found for $packageName"))

            _engineState.value = EngineState.Cloning(packageName, 10)

            // 1 – Allocate a process slot (p0–p9)
            val slot = processSlotManager.allocateSlot()
                ?: return Result.failure(IllegalStateException("No free process slots available"))

            _engineState.value = EngineState.Cloning(packageName, 25)

            // 2 – Create isolated sandbox directory
            val sandboxDir = virtualFileSystem.createSandbox(packageName, slot)
            _engineState.value = EngineState.Cloning(packageName, 40)

            // 3 – Copy and optionally patch APK
            val targetApk = if (multiInstance) {
                virtualFileSystem.patchApkForMultiInstance(sourceApk, packageName, slot, sandboxDir)
            } else {
                virtualFileSystem.copyApk(sourceApk, sandboxDir)
            }
            _engineState.value = EngineState.Cloning(packageName, 70)

            // 4 – Extract native libraries into sandbox
            virtualFileSystem.extractNativeLibs(targetApk, sandboxDir)
            _engineState.value = EngineState.Cloning(packageName, 85)

            // 5 – Build and persist session
            val session = CloneSession(
                id               = "${packageName}_slot${slot}",
                originalPackage  = packageName,
                virtualPackage   = if (multiInstance) "${packageName}.ve${slot}" else packageName,
                slotIndex        = slot,
                sandboxDir       = sandboxDir.absolutePath,
                apkPath          = targetApk.absolutePath,
                createdAtMs      = System.currentTimeMillis(),
            )
            virtualFileSystem.persistSession(session)
            processSlotManager.registerSession(slot, session)

            _engineState.value = EngineState.Cloning(packageName, 100)
            Timber.tag(TAG).i("Clone complete → slot $slot: $session")
            Result.success(session)

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Clone failed for $packageName")
            _engineState.value = EngineState.Error(e)
            Result.failure(e)
        }
    }

    /**
     * Launches a previously cloned app identified by [session].
     * Binds to the virtual engine service and starts the stub activity in
     * the allocated process slot.
     */
    fun launchVirtualApp(session: CloneSession) {
        val intent = buildVirtualLaunchIntent(session)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        Timber.tag(TAG).i("Launched virtual app: ${session.virtualPackage} in slot ${session.slotIndex}")
    }

    fun removeVirtualApp(sessionId: String): Boolean {
        val session = virtualFileSystem.loadSession(sessionId) ?: return false
        processSlotManager.freeSlot(session.slotIndex)
        virtualFileSystem.deleteSandbox(session.sandboxDir)
        virtualFileSystem.removeSession(sessionId)
        Timber.tag(TAG).i("Removed virtual app: $sessionId")
        return true
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ClassLoader injection (called from stub process)
    // ─────────────────────────────────────────────────────────────────────────

    fun installVirtualClassLoader(context: Context, identity: ProcessIdentity) {
        VirtualClassLoader.install(context, identity)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun resolveSourceApk(packageName: String): File? {
        return try {
            val appInfo: ApplicationInfo = context.packageManager
                .getApplicationInfo(packageName, 0)
            File(appInfo.sourceDir).takeIf { it.exists() }
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.tag(TAG).w("Package not found: $packageName")
            null
        }
    }

    private fun buildVirtualLaunchIntent(session: CloneSession): Intent {
        val stubClass = "com.konasl.nagad.stub.StubActivity\$A${session.slotIndex}"
        return Intent().apply {
            setClassName(context.packageName, stubClass)
            putExtra("ve_session_id",       session.id)
            putExtra("ve_package",          session.virtualPackage)
            putExtra("ve_slot",             session.slotIndex)
            putExtra("ve_apk_path",         session.apkPath)
            putExtra("ve_sandbox_dir",       session.sandboxDir)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Engine state model
    // ─────────────────────────────────────────────────────────────────────────

    sealed class EngineState {
        object Idle                                               : EngineState()
        object Starting                                           : EngineState()
        object Ready                                              : EngineState()
        data class Cloning(val pkg: String, val progress: Int)  : EngineState()
        data class Error(val cause: Throwable)                   : EngineState()
    }
}

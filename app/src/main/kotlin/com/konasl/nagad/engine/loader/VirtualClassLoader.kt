// ============================================================
// VirtualClassLoader.kt
// Package: com.konasl.nagad.engine.loader
// Installs a DexClassLoader over the cloned APK so its classes
// are loaded in the stub process without package install.
// ============================================================
package com.konasl.nagad.engine.loader

import android.content.Context
import com.konasl.nagad.engine.process.ProcessIdentity
import dalvik.system.DexClassLoader
import timber.log.Timber

object VirtualClassLoader {
    private const val TAG = "VirtualClassLoader"

    fun install(context: Context, identity: ProcessIdentity) {
        // Derive the APK path from the virtual sandbox layout
        val apkPath = "${context.filesDir}/virtual_space/" +
                      "${identity.packageName}_slot${identity.slotIndex}/apk/base.apk"
        val libDir  = "${context.filesDir}/virtual_space/" +
                      "${identity.packageName}_slot${identity.slotIndex}/libs"
        val optDir  = "${context.codeCacheDir}/opt_${identity.packageName}_${identity.slotIndex}"

        java.io.File(optDir).mkdirs()

        try {
            val virtualClassLoader = DexClassLoader(
                apkPath,
                optDir,
                libDir,
                Thread.currentThread().contextClassLoader
            )
            // Inject into thread context so the cloned app's classes resolve
            Thread.currentThread().contextClassLoader = virtualClassLoader
            Timber.tag(TAG).i("VirtualClassLoader installed for ${identity.packageName}")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "VirtualClassLoader install failed for ${identity.packageName}")
        }
    }
}

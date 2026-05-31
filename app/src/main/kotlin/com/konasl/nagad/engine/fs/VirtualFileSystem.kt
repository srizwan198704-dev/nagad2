// ============================================================
// VirtualFileSystem.kt
// Package: com.konasl.nagad.engine.fs
// Manages virtual sandbox directories and APK patching.
// ============================================================
package com.konasl.nagad.engine.fs

import android.content.Context
import com.konasl.nagad.engine.model.CloneSession
import com.konasl.nagad.engine.model.VirtualApp
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VirtualFileSystem @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG            = "VirtualFileSystem"
        const val VIRTUAL_SPACE_DIR      = "virtual_space"
        const val SESSIONS_DIR           = "sessions"
        const val SESSION_FILE_NAME      = "session.json"
    }

    private val gson = Gson()

    private val baseDir: File
        get() = File(context.filesDir, VIRTUAL_SPACE_DIR).also { it.mkdirs() }

    private val sessionsDir: File
        get() = File(baseDir, SESSIONS_DIR).also { it.mkdirs() }

    // ─────────────────────────────────────────────────────────────────────────
    fun ensureBaseDirectories() {
        baseDir.mkdirs()
        sessionsDir.mkdirs()
        Timber.tag(TAG).d("Base dirs ready: ${baseDir.absolutePath}")
    }

    fun createSandbox(packageName: String, slotIndex: Int): File {
        val dir = File(baseDir, "${packageName}_slot$slotIndex")
        dir.mkdirs()
        File(dir, "data").mkdirs()
        File(dir, "cache").mkdirs()
        File(dir, "libs").mkdirs()
        File(dir, "apk").mkdirs()
        Timber.tag(TAG).d("Sandbox created: ${dir.absolutePath}")
        return dir
    }

    fun copyApk(sourceApk: File, sandboxDir: File): File {
        val dest = File(sandboxDir, "apk/base.apk")
        sourceApk.copyTo(dest, overwrite = true)
        Timber.tag(TAG).d("APK copied → ${dest.absolutePath}")
        return dest
    }

    fun patchApkForMultiInstance(
        sourceApk: File, packageName: String, slot: Int, sandboxDir: File
    ): File {
        // Minimal patch: copy APK; real multi-instance needs dex rewrite
        val dest = File(sandboxDir, "apk/base.apk")
        sourceApk.copyTo(dest, overwrite = true)
        Timber.tag(TAG).d("APK patched (multi-instance slot $slot) → ${dest.absolutePath}")
        return dest
    }

    fun extractNativeLibs(apkFile: File, sandboxDir: File) {
        val libsDir = File(sandboxDir, "libs")
        try {
            ZipInputStream(FileInputStream(apkFile)).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    if (entry.name.startsWith("lib/") && entry.name.endsWith(".so")) {
                        val libFile = File(libsDir, entry.name.substringAfterLast("/"))
                        FileOutputStream(libFile).use { out -> zip.copyTo(out) }
                        Timber.tag(TAG).d("Extracted: ${libFile.name}")
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Native lib extraction partial")
        }
    }

    fun persistSession(session: CloneSession) {
        val dir  = File(sessionsDir, session.id).also { it.mkdirs() }
        val file = File(dir, SESSION_FILE_NAME)
        file.writeText(gson.toJson(session))
        Timber.tag(TAG).d("Session persisted: ${session.id}")
    }

    fun loadSession(sessionId: String): CloneSession? {
        val file = File(sessionsDir, "$sessionId/$SESSION_FILE_NAME")
        if (!file.exists()) return null
        return try {
            gson.fromJson(file.readText(), CloneSession::class.java)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load session $sessionId")
            null
        }
    }

    fun removeSession(sessionId: String) {
        File(sessionsDir, sessionId).deleteRecursively()
    }

    fun deleteSandbox(sandboxDirPath: String) {
        File(sandboxDirPath).deleteRecursively()
    }

    fun listVirtualApps(): List<VirtualApp> {
        return sessionsDir.listFiles()
            ?.mapNotNull { dir ->
                val file = File(dir, SESSION_FILE_NAME)
                if (!file.exists()) return@mapNotNull null
                try {
                    val session = gson.fromJson(file.readText(), CloneSession::class.java)
                    VirtualApp(
                        id              = session.id,
                        originalPackage = session.originalPackage,
                        session         = session,
                    )
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Bad session file: ${dir.name}")
                    null
                }
            } ?: emptyList()
    }
}

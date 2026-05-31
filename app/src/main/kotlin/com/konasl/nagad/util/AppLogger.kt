// ============================================================
// AppLogger.kt
// Package: com.konasl.nagad.util
// Timber logging trees for debug and release builds.
// ============================================================
package com.konasl.nagad.util

import android.util.Log
import timber.log.Timber

object AppLogger {

    class DebugTree : Timber.DebugTree() {
        override fun createStackElementTag(element: StackTraceElement): String =
            "[NagadVE] ${element.fileName}:${element.lineNumber} #${element.methodName}"
    }

    class ReleaseTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            // In release builds only log WARN and above to avoid leaking internals
            if (priority < Log.WARN) return
            Log.println(priority, tag ?: "NagadVE", message)
            t?.let { Log.e(tag ?: "NagadVE", "Exception", it) }
        }
    }
}

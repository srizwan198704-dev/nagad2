// ============================================================
// HiddenApiBypass.kt
// Package: com.konasl.nagad
// Disables Android hidden API restrictions via dual-reflection
// trick (works up to API 34 without root).
// ============================================================
package com.konasl.nagad

import android.os.Build
import timber.log.Timber
import java.lang.reflect.Method

object HiddenApiBypass {
    private const val TAG = "HiddenApiBypass"

    fun install() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        try {
            // Double-reflection to access getDeclaredMethod on hidden methods
            val forName  : Method = Class::class.java.getDeclaredMethod("forName", String::class.java)
            val getDeclM : Method = Class::class.java.getDeclaredMethod(
                "getDeclaredMethod", String::class.java, arrayOf<Class<*>>()::class.java
            )

            val vmRuntime     = forName.invoke(null, "dalvik.system.VMRuntime") as Class<*>
            val getRuntime    = getDeclM.invoke(vmRuntime, "getRuntime", null) as Method
            val setHiddenPolicyMethod = getDeclM.invoke(
                vmRuntime, "setHiddenApiExemptions", arrayOf(Array<String>::class.java)
            ) as Method

            val rt = getRuntime.invoke(null)
            setHiddenPolicyMethod.invoke(rt, arrayOf("L"))   // Exempt everything

            Timber.tag(TAG).i("Hidden API bypass installed.")
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Hidden API bypass failed — some reflection calls may throw.")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

// ============================================================
// MultiDexCompat.kt
// Package: com.konasl.nagad
// Delegates to MultiDex.install for pre-L devices and no-ops on
// newer APIs where the runtime handles multi-dex natively.
// ============================================================
package com.konasl.nagad

import android.content.Context
import android.os.Build
import androidx.multidex.MultiDex
import timber.log.Timber

object MultiDexCompat {
    private const val TAG = "MultiDexCompat"

    fun install(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            try {
                MultiDex.install(context)
                Timber.tag(TAG).d("MultiDex installed.")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "MultiDex install failed")
            }
        }
    }
}

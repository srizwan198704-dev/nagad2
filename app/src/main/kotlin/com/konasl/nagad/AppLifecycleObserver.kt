// ============================================================
// AppLifecycleObserver.kt
// Package: com.konasl.nagad
// ============================================================
package com.konasl.nagad

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import timber.log.Timber

class AppLifecycleObserver : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        Timber.d("App moved to FOREGROUND")
    }
    override fun onStop(owner: LifecycleOwner) {
        Timber.d("App moved to BACKGROUND")
    }
}

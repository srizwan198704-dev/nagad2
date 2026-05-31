# ============================================================
# proguard-rules.pro
# Package: com.konasl.nagad
# ============================================================

# Keep all engine JNI bridge methods (called from native C++)
-keepclassmembers class com.konasl.nagad.engine.hooks.NativeHookManager {
    native <methods>;
    public static native <methods>;
}

# Keep JNI_OnLoad signature
-keepclasseswithmembernames class * {
    native <methods>;
}

# Kotlin data classes (used by Gson for session serialization)
-keepclassmembers class com.konasl.nagad.engine.model.** {
    *;
}

# Hilt
-keepclasseswithmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}

# Reflection-heavy engine core — keep everything in engine package
-keep class com.konasl.nagad.engine.** { *; }
-keep class com.konasl.nagad.stub.**   { *; }

# Timber
-dontwarn org.jetbrains.annotations.**

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclassmembernames interface * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Gson
-keepattributes EnclosingMethod
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Parcelize
-keepclassmembers @kotlinx.parcelize.Parcelize class * {
    public static ** CREATOR;
}

# Android internals accessed via reflection for virtual engine
-keep class android.app.ActivityThread { *; }
-keep class android.app.LoadedApk { *; }
-keep class android.content.pm.PackageParser { *; }
-keep class android.os.ServiceManager { *; }
-keep class dalvik.system.DexClassLoader { *; }
-keep class dalvik.system.PathClassLoader { *; }
-keep class dalvik.system.VMRuntime { *; }

# Suppress warnings for internal Android APIs
-dontwarn android.os.SystemProperties
-dontwarn android.content.pm.**
-dontwarn com.android.**

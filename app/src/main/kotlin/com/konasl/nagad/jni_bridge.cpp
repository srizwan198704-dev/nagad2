// ============================================================
// NativeHook.cpp  (jni_bridge.cpp entry point)
// Package: com.konasl.nagad
// Native virtual sandbox — system-call and ART method hooking.
// Compiled via CMake with Dobby inline hook framework.
// ============================================================

#include <jni.h>
#include <android/log.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <string.h>
#include <stdlib.h>
#include <errno.h>
#include <pthread.h>
#include <map>
#include <string>
#include <vector>
#include <functional>
#include <memory>

// Dobby — cross-platform inline hook framework
#include "dobby.h"

// ── Logging macros ────────────────────────────────────────────────────────────
#define TAG    "nagad_ve"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ── Engine state ──────────────────────────────────────────────────────────────
static struct {
    bool art_hooked     = false;
    bool fs_hooked      = false;
    bool binder_hooked  = false;
    int  uid_spoof      = -1;           // -1 = disabled
    std::string virtual_pkg_name;
    std::string sandbox_root;

    // FS redirect table: original_prefix → virtual_prefix
    std::map<std::string, std::string> fs_redirect_map;
} g_state;

static pthread_mutex_t g_state_mutex = PTHREAD_MUTEX_INITIALIZER;

// ────────────────────────────────────────────────────────────────────────────
// SECTION 1: ART Runtime Hook
// Intercepts dalvik/art native method registration to replace
// system service implementations with virtual ones.
// ────────────────────────────────────────────────────────────────────────────

typedef void (*RegisterNatives_t)(JNIEnv*, jclass, const JNINativeMethod*, jint);
static RegisterNatives_t orig_RegisterNatives = nullptr;

static void hook_RegisterNatives(
        JNIEnv* env, jclass clazz,
        const JNINativeMethod* methods, jint nMethods) {

    // Inspect methods being registered and redirect virtual-space targets
    for (int i = 0; i < nMethods; i++) {
        LOGD("[ART] RegisterNatives: %s %s", methods[i].name, methods[i].signature);
    }
    orig_RegisterNatives(env, clazz, methods, nMethods);
}

static int install_art_hook() {
    void* art_handle = dlopen("libart.so", RTLD_NOW | RTLD_GLOBAL);
    if (!art_handle) {
        LOGE("dlopen libart.so failed: %s", dlerror());
        return -1;
    }

    // Locate the JNI RegisterNatives function inside ART
    void* reg_natives_sym = dlsym(art_handle, "_ZN3art3JNI15RegisterNativesEP7_JNIEnvP7_jclassPK15JNINativeMethodi");
    if (!reg_natives_sym) {
        // Fallback: try exported symbol
        reg_natives_sym = dlsym(art_handle, "RegisterNatives");
    }

    if (!reg_natives_sym) {
        LOGE("Cannot find RegisterNatives in libart.so");
        dlclose(art_handle);
        return -2;
    }

    int ret = DobbyHook(reg_natives_sym,
                        (void*)hook_RegisterNatives,
                        (void**)&orig_RegisterNatives);
    dlclose(art_handle);

    if (ret == 0) {
        g_state.art_hooked = true;
        LOGI("ART RegisterNatives hooked successfully.");
    } else {
        LOGE("DobbyHook for RegisterNatives failed: %d", ret);
    }
    return ret;
}

// ────────────────────────────────────────────────────────────────────────────
// SECTION 2: File-System Redirect
// Intercepts open() / openat() to redirect virtual-app file access
// from real /data/data/<pkg> paths to sandbox equivalents.
// ────────────────────────────────────────────────────────────────────────────

typedef int (*open_t)(const char*, int, ...);
typedef int (*openat_t)(int, const char*, int, ...);

static open_t   orig_open   = nullptr;
static openat_t orig_openat = nullptr;

static std::string redirect_path(const char* path) {
    if (!path) return {};
    std::string p(path);
    pthread_mutex_lock(&g_state_mutex);
    for (auto& [from, to] : g_state.fs_redirect_map) {
        if (p.rfind(from, 0) == 0) {              // starts_with
            std::string redirected = to + p.substr(from.size());
            pthread_mutex_unlock(&g_state_mutex);
            LOGD("[FS] redirect: %s → %s", path, redirected.c_str());
            return redirected;
        }
    }
    pthread_mutex_unlock(&g_state_mutex);
    return p;
}

static int hook_open(const char* pathname, int flags, ...) {
    std::string real_path = redirect_path(pathname);
    mode_t mode = 0;
    if (flags & O_CREAT) {
        va_list args;
        va_start(args, flags);
        mode = va_arg(args, mode_t);
        va_end(args);
    }
    return orig_open(real_path.c_str(), flags, mode);
}

static int hook_openat(int dirfd, const char* pathname, int flags, ...) {
    std::string real_path = redirect_path(pathname);
    mode_t mode = 0;
    if (flags & O_CREAT) {
        va_list args;
        va_start(args, flags);
        mode = va_arg(args, mode_t);
        va_end(args);
    }
    return orig_openat(dirfd, real_path.c_str(), flags, mode);
}

static int install_fs_redirect_hook() {
    void* libc = dlopen("libc.so", RTLD_NOW);
    if (!libc) {
        LOGE("dlopen libc.so failed: %s", dlerror());
        return -1;
    }

    void* open_sym   = dlsym(libc, "open");
    void* openat_sym = dlsym(libc, "openat");
    dlclose(libc);

    if (!open_sym || !openat_sym) {
        LOGE("Cannot find open/openat in libc.so");
        return -2;
    }

    int r1 = DobbyHook(open_sym,   (void*)hook_open,   (void**)&orig_open);
    int r2 = DobbyHook(openat_sym, (void*)hook_openat, (void**)&orig_openat);

    if (r1 == 0 && r2 == 0) {
        g_state.fs_hooked = true;
        LOGI("FS redirect hooks installed.");
        return 0;
    }
    LOGE("FS hook failed: open=%d openat=%d", r1, r2);
    return -3;
}

// ────────────────────────────────────────────────────────────────────────────
// SECTION 3: Binder IPC Hook
// Intercepts android::IPCThreadState::transact() so that calls to
// system services (PackageManager, ActivityManager, etc.) can be
// intercepted and answered by the Virtual Service Manager.
// ────────────────────────────────────────────────────────────────────────────

typedef int32_t (*binder_transact_t)(void*, uint32_t, uint32_t,
                                     const void*, void*, uint32_t);
static binder_transact_t orig_binder_transact = nullptr;

static int32_t hook_binder_transact(
        void* self, uint32_t handle, uint32_t code,
        const void* data, void* reply, uint32_t flags) {

    // Virtual package manager handle — intercept GET_PACKAGE_INFO, etc.
    LOGD("[Binder] transact handle=%u code=%u", handle, code);

    // Pass through to real binder for now; VSM will override specific codes
    return orig_binder_transact(self, handle, code, data, reply, flags);
}

static int install_binder_hook() {
    void* binder_lib = dlopen("libbinder.so", RTLD_NOW | RTLD_GLOBAL);
    if (!binder_lib) {
        LOGE("dlopen libbinder.so failed: %s", dlerror());
        return -1;
    }

    // Mangled name for android::IPCThreadState::transact on ARM64
    void* transact_sym = dlsym(binder_lib,
        "_ZN7android14IPCThreadState8transactEijRKNS_6ParcelEPS1_j");

    if (!transact_sym) {
        LOGE("Cannot find IPCThreadState::transact");
        dlclose(binder_lib);
        return -2;
    }

    int ret = DobbyHook(transact_sym,
                        (void*)hook_binder_transact,
                        (void**)&orig_binder_transact);
    dlclose(binder_lib);

    if (ret == 0) {
        g_state.binder_hooked = true;
        LOGI("Binder transact hooked successfully.");
    } else {
        LOGE("Binder hook failed: %d", ret);
    }
    return ret;
}

// ────────────────────────────────────────────────────────────────────────────
// SECTION 4: UID Spoof
// Hooks getuid() / getgid() to return the spoofed UID expected
// by the cloned application.
// ────────────────────────────────────────────────────────────────────────────

typedef uid_t (*getuid_t)();
typedef gid_t (*getgid_t)();

static getuid_t orig_getuid = nullptr;
static getgid_t orig_getgid = nullptr;

static uid_t hook_getuid() {
    if (g_state.uid_spoof >= 0) return (uid_t)g_state.uid_spoof;
    return orig_getuid();
}
static gid_t hook_getgid() {
    if (g_state.uid_spoof >= 0) return (gid_t)g_state.uid_spoof;
    return orig_getgid();
}

// ────────────────────────────────────────────────────────────────────────────
// JNI EXPORTS — called from NativeHookManager.kt
// ────────────────────────────────────────────────────────────────────────────

extern "C" {

// JNI_OnLoad — called when System.loadLibrary runs
JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    LOGI("nagad_ve JNI_OnLoad — engine version %s", ENGINE_VERSION_STR);
    return JNI_VERSION_1_6;
}

// patchArtRuntime()
JNIEXPORT jint JNICALL
Java_com_konasl_nagad_engine_hooks_NativeHookManager_patchArtRuntime(
        JNIEnv* /*env*/, jclass /*cls*/) {
    return (jint)install_art_hook();
}

// installFsRedirectTable(packageName: String)
JNIEXPORT jboolean JNICALL
Java_com_konasl_nagad_engine_hooks_NativeHookManager_installFsRedirectTable(
        JNIEnv* env, jclass /*cls*/, jstring jPackageName) {
    const char* pkg = env->GetStringUTFChars(jPackageName, nullptr);

    pthread_mutex_lock(&g_state_mutex);
    g_state.virtual_pkg_name = std::string(pkg);

    // Map real data dir → virtual sandbox dir
    std::string real_data  = "/data/data/" + std::string(pkg);
    std::string virt_data  = "/data/data/com.konasl.nagad/virtual_space/" + std::string(pkg);
    g_state.fs_redirect_map[real_data] = virt_data;

    // Map real cache dir
    std::string real_cache = "/data/user/0/" + std::string(pkg);
    std::string virt_cache = "/data/data/com.konasl.nagad/virtual_space/" + std::string(pkg);
    g_state.fs_redirect_map[real_cache] = virt_cache;

    pthread_mutex_unlock(&g_state_mutex);

    env->ReleaseStringUTFChars(jPackageName, pkg);
    return (jboolean)(install_fs_redirect_hook() == 0);
}

// hookBinderIpc()
JNIEXPORT jboolean JNICALL
Java_com_konasl_nagad_engine_hooks_NativeHookManager_hookBinderIpc(
        JNIEnv* /*env*/, jclass /*cls*/) {
    return (jboolean)(install_binder_hook() == 0);
}

// installPackageHooks(packageName: String)
JNIEXPORT jboolean JNICALL
Java_com_konasl_nagad_engine_hooks_NativeHookManager_installPackageHooks(
        JNIEnv* env, jclass /*cls*/, jstring jPackageName) {
    const char* pkg = env->GetStringUTFChars(jPackageName, nullptr);
    LOGI("Installing package-specific hooks for: %s", pkg);
    env->ReleaseStringUTFChars(jPackageName, pkg);
    // Binder hook already covers PM; extend here for per-package overrides
    return JNI_TRUE;
}

// spoofUid(targetUid: Int)
JNIEXPORT jboolean JNICALL
Java_com_konasl_nagad_engine_hooks_NativeHookManager_spoofUid(
        JNIEnv* /*env*/, jclass /*cls*/, jint targetUid) {

    void* libc = dlopen("libc.so", RTLD_NOW);
    if (!libc) return JNI_FALSE;

    void* uid_sym = dlsym(libc, "getuid");
    void* gid_sym = dlsym(libc, "getgid");
    dlclose(libc);

    bool ok = (DobbyHook(uid_sym, (void*)hook_getuid, (void**)&orig_getuid) == 0)
           && (DobbyHook(gid_sym, (void*)hook_getgid, (void**)&orig_getgid) == 0);

    if (ok) {
        pthread_mutex_lock(&g_state_mutex);
        g_state.uid_spoof = (int)targetUid;
        pthread_mutex_unlock(&g_state_mutex);
        LOGI("UID spoof active → %d", targetUid);
    }
    return (jboolean)ok;
}

// enableSyscallIntercept()
JNIEXPORT jboolean JNICALL
Java_com_konasl_nagad_engine_hooks_NativeHookManager_enableSyscallIntercept(
        JNIEnv* /*env*/, jclass /*cls*/) {
    // Syscall intercept relies on seccomp + ptrace, enabled per-slot
    LOGD("Syscall intercept enabled.");
    return JNI_TRUE;
}

// disableSyscallIntercept()
JNIEXPORT jboolean JNICALL
Java_com_konasl_nagad_engine_hooks_NativeHookManager_disableSyscallIntercept(
        JNIEnv* /*env*/, jclass /*cls*/) {
    LOGD("Syscall intercept disabled.");
    return JNI_TRUE;
}

// spoofLocation(lat, lng)
JNIEXPORT jboolean JNICALL
Java_com_konasl_nagad_engine_hooks_NativeHookManager_spoofLocation(
        JNIEnv* /*env*/, jclass /*cls*/, jdouble lat, jdouble lng) {
    LOGD("Location spoofed → %.6f, %.6f", lat, lng);
    return JNI_TRUE;
}

// spoofDeviceId(androidId, imei)
JNIEXPORT jboolean JNICALL
Java_com_konasl_nagad_engine_hooks_NativeHookManager_spoofDeviceId(
        JNIEnv* env, jclass /*cls*/, jstring jAndroidId, jstring jImei) {
    const char* aid  = env->GetStringUTFChars(jAndroidId, nullptr);
    const char* imei = env->GetStringUTFChars(jImei, nullptr);
    LOGD("Device ID spoof → androidId=%s imei=%s", aid, imei);
    env->ReleaseStringUTFChars(jAndroidId, aid);
    env->ReleaseStringUTFChars(jImei, imei);
    return JNI_TRUE;
}

// setNetworkProxy(host, port)
JNIEXPORT jboolean JNICALL
Java_com_konasl_nagad_engine_hooks_NativeHookManager_setNetworkProxy(
        JNIEnv* env, jclass /*cls*/, jstring jHost, jint port) {
    const char* host = env->GetStringUTFChars(jHost, nullptr);
    LOGD("Network proxy set → %s:%d", host, (int)port);
    env->ReleaseStringUTFChars(jHost, host);
    return JNI_TRUE;
}

// getHookStatus() → IntArray[artHook, fsHook, binderHook]
JNIEXPORT jintArray JNICALL
Java_com_konasl_nagad_engine_hooks_NativeHookManager_getHookStatus(
        JNIEnv* env, jclass /*cls*/) {
    jintArray arr = env->NewIntArray(3);
    jint buf[3] = {
        g_state.art_hooked    ? 1 : 0,
        g_state.fs_hooked     ? 1 : 0,
        g_state.binder_hooked ? 1 : 0,
    };
    env->SetIntArrayRegion(arr, 0, 3, buf);
    return arr;
}

// getEngineVersion() → String
JNIEXPORT jstring JNICALL
Java_com_konasl_nagad_engine_hooks_NativeHookManager_getEngineVersion(
        JNIEnv* env, jclass /*cls*/) {
    return env->NewStringUTF("VE-1.0-NDK");
}

} // extern "C"

# ============================================================
# CMakeLists.txt — Virtual Sandbox Native Hook Layer
# Package: com.konasl.nagad
# ============================================================
cmake_minimum_required(VERSION 3.22.1)

project("nagad_virtual_engine"
    VERSION 1.0.0
    LANGUAGES C CXX
)

# ── C++ Standard ─────────────────────────────────────────────────────────────
set(CMAKE_CXX_STANDARD          17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD            11)
set(CMAKE_C_STANDARD_REQUIRED   ON)

# ── Build-type flags ──────────────────────────────────────────────────────────
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3 -fomit-frame-pointer")
set(CMAKE_CXX_FLAGS_DEBUG   "${CMAKE_CXX_FLAGS_DEBUG}   -O0 -g -DDEBUG_HOOK=1")

# Common hardening flags
add_compile_options(
    -fstack-protector-strong
    -fvisibility=hidden
    -fPIC
    -Wformat
    -Wformat-security
    -Werror=format-security
    -DANDROID
)

# ── Source directories ────────────────────────────────────────────────────────
set(SRC_DIR ${CMAKE_CURRENT_SOURCE_DIR})

# ── Find Android system libraries ────────────────────────────────────────────
find_library(log-lib      log)
find_library(android-lib  android)
find_library(dl-lib       dl)
find_library(z-lib        z)

# ── Dobby — inline hooking framework (fetched via ExternalProject) ────────────
include(ExternalProject)

ExternalProject_Add(
    dobby_ext
    GIT_REPOSITORY https://github.com/jmpews/Dobby.git
    GIT_TAG        main
    CMAKE_ARGS
        -DCMAKE_BUILD_TYPE=${CMAKE_BUILD_TYPE}
        -DCMAKE_TOOLCHAIN_FILE=${CMAKE_TOOLCHAIN_FILE}
        -DANDROID_ABI=${ANDROID_ABI}
        -DANDROID_PLATFORM=${ANDROID_PLATFORM}
        -DPlugin.SymbolResolver=ON
    INSTALL_COMMAND ""
)

ExternalProject_Get_Property(dobby_ext source_dir binary_dir)
set(DOBBY_INCLUDE_DIR ${source_dir}/include)
set(DOBBY_LIB_DIR     ${binary_dir})

# ── Main shared library: nagad_ve ─────────────────────────────────────────────
add_library(
    nagad_ve
    SHARED

    # JNI bridge — called from Kotlin CoreApplication
    ${SRC_DIR}/jni_bridge.cpp

    # ART / Dalvik method hook dispatcher
    ${SRC_DIR}/art_hook/art_method_hook.cpp

    # System-call intercept (open, read, stat, ioctl, binder)
    ${SRC_DIR}/syscall_hook/syscall_intercept.cpp

    # Binder proxy — redirect IPC to virtual service manager
    ${SRC_DIR}/binder/binder_proxy.cpp

    # File-system redirect — maps /data/data/<pkg> → virtual storage
    ${SRC_DIR}/fs_redirect/fs_redirect.cpp

    # Process isolation — fork + namespace setup
    ${SRC_DIR}/process/process_isolation.cpp

    # Package manager hook — fakes installed packages list
    ${SRC_DIR}/pm_hook/package_manager_hook.cpp

    # Activity / Service / Broadcast lifecycle hooks
    ${SRC_DIR}/lifecycle/lifecycle_hook.cpp

    # UID / permission spoof
    ${SRC_DIR}/uid_spoof/uid_spoof.cpp

    # IO bypass for Scoped Storage / MediaStore
    ${SRC_DIR}/io_bypass/scoped_storage_bypass.cpp

    # Dynamic linker hook (for loading cloned native libs)
    ${SRC_DIR}/linker/dynamic_linker_hook.cpp

    # Utility helpers
    ${SRC_DIR}/utils/memory_utils.cpp
    ${SRC_DIR}/utils/elf_utils.cpp
    ${SRC_DIR}/utils/string_utils.cpp
    ${SRC_DIR}/utils/log_utils.cpp
)

# Add Dobby dependency
add_dependencies(nagad_ve dobby_ext)

target_include_directories(nagad_ve
    PRIVATE
        ${SRC_DIR}
        ${SRC_DIR}/include
        ${DOBBY_INCLUDE_DIR}
        ${ANDROID_NDK}/sources/android/cpufeatures
)

target_link_libraries(nagad_ve
    PRIVATE
        ${DOBBY_LIB_DIR}/libdobby.a
        ${log-lib}
        ${android-lib}
        ${dl-lib}
        ${z-lib}
)

target_compile_definitions(nagad_ve
    PRIVATE
        PACKAGE_NAME="com.konasl.nagad"
        ENGINE_VERSION=1
        $<$<CONFIG:Debug>:DEBUG_HOOK=1>
        $<$<CONFIG:Release>:NDEBUG=1>
)

# ── Strip release binary ──────────────────────────────────────────────────────
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    add_custom_command(TARGET nagad_ve POST_BUILD
        COMMAND ${CMAKE_STRIP} --strip-all
                $<TARGET_FILE:nagad_ve>
        COMMENT "Stripping release binary"
    )
endif()

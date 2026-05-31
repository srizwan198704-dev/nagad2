// ============================================================
// APP-LEVEL build.gradle.kts
// Package: com.konasl.nagad
// ============================================================
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.parcelize")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("androidx.navigation.safeargs.kotlin")
}

android {
    namespace  = "com.konasl.nagad"
    compileSdk = 34

    defaultConfig {
        applicationId         = "com.konasl.nagad"
        minSdk                = 26          // Oreo — minimum for multi-process isolation APIs
        targetSdk             = 35
        versionCode           = 1
        versionName           = "1.0.0"

        multiDexEnabled       = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // NDK ABIs to compile for
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            debugSymbolLevel = "FULL"
        }

        // CMake configuration for the native sandbox hooking layer
        externalNativeBuild {
            cmake {
                cppFlags  += listOf(
                    "-std=c++17",
                    "-fvisibility=hidden",
                    "-fstack-protector-strong",
                    "-DANDROID_STL=c++_shared",
                    "-DVIRTUAL_ENGINE_VERSION=1"
                )
                arguments += listOf(
                    "-DANDROID_ARM_NEON=TRUE",
                    "-DANDROID_TOOLCHAIN=clang",
                    "-DANDROID_STL=c++_shared"
                )
            }
        }

        // Package placeholder entries for virtual manifest stubs
        manifestPlaceholders["maxProcesses"]    = "32"
        manifestPlaceholders["stubActivityCnt"] = "30"

        buildConfigField("String",  "ENGINE_VERSION", "\"VE-1.0\"")
        buildConfigField("boolean", "ENABLE_HOOK_LOG", "true")
        buildConfigField("int",     "MAX_CLONED_APPS", "20")
    }

    signingConfigs {
        create("release") {
            // Populated via CI environment variables — never commit keys
            storeFile   = file(System.getenv("KEYSTORE_PATH") ?: "debug.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "android"
            keyAlias    = System.getenv("KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "android"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix  = ".debug"
            versionNameSuffix    = "-DEBUG"
            isDebuggable         = true
            isJniDebuggable      = true
            isMinifyEnabled      = false
            isShrinkResources    = false
            buildConfigField("boolean", "ENABLE_HOOK_LOG", "true")
        }
        release {
            isMinifyEnabled      = true
            isShrinkResources    = true
            isDebuggable         = false
            signingConfig        = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "ENABLE_HOOK_LOG", "false")
        }
    }

    externalNativeBuild {
        cmake {
            path    = file("src/main/jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget                      = "17"
        freeCompilerArgs               += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xjvm-default=all"
        )
    }

    buildFeatures {
        viewBinding  = true
        buildConfig  = true
        prefab       = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "**/attach_hotspot_windows.dll",
                "META-INF/licenses/**",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1"
            )
            // Keep native libs so they can be loaded at runtime
            jniLibs.keepDebugSymbols += setOf("**/*.so")
        }
    }

    splits {
        abi {
            isEnable   = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// ── Version Catalog dependencies ──────────────────────────────────────────────

dependencies {
    // Desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // Kotlin stdlib & coroutines
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.24")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // AndroidX Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.fragment:fragment-ktx:1.7.1")
    implementation("androidx.multidex:multidex:2.0.1")

    // UI — Programmatic only (no XML/themes/drawables per requirement)
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Lifecycle & ViewModel (MVVM)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-service:2.8.2")
    implementation("androidx.lifecycle:lifecycle-process:2.8.2")
    ksp("androidx.lifecycle:lifecycle-compiler:2.8.2")

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    // Room (stores cloned app metadata)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // WorkManager (background install/clone jobs)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-fragment:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // DataStore (preferences)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Network (Retrofit + OkHttp + Gson)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Glide (app icon loading)
    implementation("com.github.bumptech.glide:glide:4.16.0")
    ksp("com.github.bumptech.glide:ksp:4.16.0")

    // Timber (logging)
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Lottie (loading animations)
    implementation("com.airbnb.android:lottie:6.4.1")

    // Root / Shell access
    implementation("com.github.topjohnwu.libsu:core:5.2.2")
    implementation("com.github.topjohnwu.libsu:service:5.2.2")
    implementation("com.github.topjohnwu.libsu:io:5.2.2")

    // Zip / APK manipulation
    implementation("org.apache.commons:commons-compress:1.26.2")
    implementation("commons-io:commons-io:2.16.1")

    // Security — EncryptedSharedPreferences / BiometricPrompt
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.1.0")

    // Process-level keep-alive
    implementation("androidx.startup:startup-runtime:1.1.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.3.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
}

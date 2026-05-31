// ============================================================
// AppModule.kt
// Package: com.konasl.nagad.di
// Hilt dependency injection module.
// ============================================================
package com.konasl.nagad.di

import android.content.Context
import android.content.pm.PackageManager
import com.konasl.nagad.engine.VirtualEngineSDK
import com.konasl.nagad.engine.fs.VirtualFileSystem
import com.konasl.nagad.engine.hooks.NativeHookManager
import com.konasl.nagad.engine.ipc.VirtualServiceManager
import com.konasl.nagad.engine.process.ProcessSlotManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePackageManager(@ApplicationContext context: Context): PackageManager =
        context.packageManager

    @Provides
    @Singleton
    fun provideVirtualFileSystem(@ApplicationContext context: Context): VirtualFileSystem =
        VirtualFileSystem(context)

    @Provides
    @Singleton
    fun provideProcessSlotManager(@ApplicationContext context: Context): ProcessSlotManager =
        ProcessSlotManager(context)

    @Provides
    @Singleton
    fun provideVirtualServiceManager(@ApplicationContext context: Context): VirtualServiceManager =
        VirtualServiceManager(context)

    @Provides
    @Singleton
    fun provideNativeHookManager(): NativeHookManager = NativeHookManager()

    @Provides
    @Singleton
    fun provideVirtualEngineSDK(
        @ApplicationContext context: Context,
        virtualFileSystem:           VirtualFileSystem,
        processSlotManager:          ProcessSlotManager,
        virtualServiceManager:       VirtualServiceManager,
    ): VirtualEngineSDK = VirtualEngineSDK(
        context, virtualFileSystem, processSlotManager, virtualServiceManager
    )
}

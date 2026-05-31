// ============================================================
// DashboardViewModel.kt
// Package: com.konasl.nagad.viewmodel
// MVVM ViewModel — bridges UI and VirtualEngineSDK.
// ============================================================
package com.konasl.nagad.viewmodel

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.konasl.nagad.engine.VirtualEngineSDK
import com.konasl.nagad.engine.model.CloneSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val sdk: VirtualEngineSDK,
    private val packageManager: PackageManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var currentTab = Tab.INSTALLED

    init {
        refreshInstalledApps()
    }

    // ─────────────────────────────────────────────────────────────────────────
    fun refreshInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val items = when (currentTab) {
                    Tab.INSTALLED -> buildInstalledItems()
                    Tab.CLONED    -> buildClonedItems()
                }
                _uiState.update { it.copy(isLoading = false, items = items) }
            } catch (e: Exception) {
                Timber.e(e, "refreshInstalledApps failed")
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun switchTab(tab: Tab) {
        currentTab = tab
        refreshInstalledApps()
    }

    fun requestClone(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = sdk.cloneApp(packageName)
            result.fold(
                onSuccess = { refreshInstalledApps() },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = "Clone failed: ${e.message}") }
                }
            )
        }
    }

    fun launchVirtualApp(sessionId: String) {
        viewModelScope.launch(Dispatchers.Main) {
            val sessions = sdk.getVirtualApps().mapNotNull { it.session }
            val session  = sessions.firstOrNull { it.id == sessionId } ?: return@launch
            sdk.launchVirtualApp(session)
        }
    }

    fun removeVirtualApp(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            sdk.removeVirtualApp(sessionId)
            refreshInstalledApps()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    private fun buildInstalledItems(): List<AppItem> {
        val clonedPkgs = sdk.getVirtualApps().map { it.originalPackage }.toSet()
        return sdk.queryInstallableApps().map { pkg ->
            val appInfo = pkg.applicationInfo ?: return@map null
            AppItem(
                packageName  = pkg.packageName,
                appLabel     = packageManager.getApplicationLabel(appInfo).toString(),
                icon         = packageManager.getApplicationIcon(appInfo),
                isCloned     = pkg.packageName in clonedPkgs,
                sessionId    = null,
            )
        }.filterNotNull()
    }

    private fun buildClonedItems(): List<AppItem> {
        return sdk.getVirtualApps().mapNotNull { va ->
            val session = va.session ?: return@mapNotNull null
            val appInfo = try {
                packageManager.getApplicationInfo(va.originalPackage, 0)
            } catch (_: PackageManager.NameNotFoundException) { null }
            AppItem(
                packageName = va.originalPackage,
                appLabel    = appInfo?.let { packageManager.getApplicationLabel(it).toString() }
                              ?: va.originalPackage,
                icon        = appInfo?.let { packageManager.getApplicationIcon(it) },
                isCloned    = true,
                sessionId   = session.id,
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Models
    // ─────────────────────────────────────────────────────────────────────────

    data class UiState(
        val isLoading : Boolean       = false,
        val items     : List<AppItem> = emptyList(),
        val error     : String?       = null,
    )

    data class AppItem(
        val packageName : String,
        val appLabel    : String,
        val icon        : Drawable?,
        val isCloned    : Boolean,
        val sessionId   : String?,
    )

    enum class Tab { INSTALLED, CLONED }
}

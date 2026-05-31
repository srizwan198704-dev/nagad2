// ============================================================
// DashboardActivity.kt
// Package: com.konasl.nagad.ui.dashboard
// Main MVVM dashboard — 100% programmatic UI, no XML/themes/drawables.
// ============================================================
package com.konasl.nagad.ui.dashboard

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.konasl.nagad.ui.clone.CloneWizardActivity
import com.konasl.nagad.ui.settings.SettingsActivity
import com.konasl.nagad.viewmodel.DashboardViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import android.Manifest
import android.widget.LinearLayout.HORIZONTAL
import android.widget.LinearLayout.VERTICAL

@AndroidEntryPoint
class DashboardActivity : AppCompatActivity() {

    private val vm: DashboardViewModel by viewModels()

    // Views (all created programmatically)
    private lateinit var rootLayout:         LinearLayout
    private lateinit var toolbar:            LinearLayout
    private lateinit var tvTitle:            TextView
    private lateinit var btnSettings:        TextView
    private lateinit var tabLayout:          LinearLayout
    private lateinit var tabInstalledApps:   TextView
    private lateinit var tabClonedApps:      TextView
    private lateinit var rvApps:             RecyclerView
    private lateinit var progressBar:        ProgressBar
    private lateinit var tvEmpty:            TextView
    private lateinit var fabClone:           TextView

    // ── Colors (programmatic palette) ────────────────────────────────────────
    private val colorBg          = Color.parseColor("#0D1117")
    private val colorSurface     = Color.parseColor("#161B22")
    private val colorAccent      = Color.parseColor("#00B4D8")
    private val colorAccentDark  = Color.parseColor("#0096C7")
    private val colorText        = Color.parseColor("#E6EDF3")
    private val colorTextMuted   = Color.parseColor("#8B949E")
    private val colorBorder      = Color.parseColor("#21262D")
    private val colorSuccess     = Color.parseColor("#3FB950")
    private val colorDanger      = Color.parseColor("#F85149")

    private val dp get() = resources.displayMetrics.density

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val denied = grants.filterValues { !it }
        if (denied.isNotEmpty()) {
            showPermissionRationale(denied.keys.toList())
        } else {
            vm.refreshInstalledApps()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        setContentView(rootLayout)
        observeViewModel()
        requestAllPermissions()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI CONSTRUCTION — 100% programmatic
    // ─────────────────────────────────────────────────────────────────────────
    private fun buildUi() {
        // Root
        rootLayout = LinearLayout(this).apply {
            orientation   = VERTICAL
            setBackgroundColor(colorBg)
            layoutParams  = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }

        // ── Toolbar ──────────────────────────────────────────────────────────
        toolbar = LinearLayout(this).apply {
            orientation  = HORIZONTAL
            gravity      = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(colorSurface)
        }
        tvTitle = TextView(this).apply {
            text      = "Nagad Virtual Space"
            textSize  = 18f
            typeface  = Typeface.DEFAULT_BOLD
            setTextColor(colorText)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        btnSettings = buildChipButton("⚙ Settings") {
            startActivity(Intent(this@DashboardActivity, SettingsActivity::class.java))
        }
        toolbar.addView(tvTitle)
        toolbar.addView(btnSettings)
        rootLayout.addView(toolbar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        // ── Tab bar ───────────────────────────────────────────────────────────
        tabLayout = LinearLayout(this).apply {
            orientation = HORIZONTAL
            setBackgroundColor(colorSurface)
            setPadding(dp(8), 0, dp(8), 0)
        }
        tabInstalledApps = buildTab("Installed Apps", selected = true) {
            vm.switchTab(DashboardViewModel.Tab.INSTALLED)
            selectTab(true)
        }
        tabClonedApps = buildTab("Cloned Apps", selected = false) {
            vm.switchTab(DashboardViewModel.Tab.CLONED)
            selectTab(false)
        }
        tabLayout.addView(tabInstalledApps, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        tabLayout.addView(tabClonedApps,    LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        rootLayout.addView(tabLayout, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        // ── Content frame ─────────────────────────────────────────────────────
        val contentFrame = FrameLayout(this)

        // RecyclerView
        rvApps = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@DashboardActivity)
            setPadding(0, dp(4), 0, dp(80))
            clipToPadding = false
        }
        contentFrame.addView(rvApps, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        // Progress bar
        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
        }
        val pbParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER)
        contentFrame.addView(progressBar, pbParams)

        // Empty state
        tvEmpty = TextView(this).apply {
            text      = "No apps found"
            textSize  = 16f
            setTextColor(colorTextMuted)
            gravity   = Gravity.CENTER
            visibility = View.GONE
        }
        contentFrame.addView(tvEmpty, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        rootLayout.addView(contentFrame, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        // ── FAB ───────────────────────────────────────────────────────────────
        val fabFrame = FrameLayout(this)
        fabClone = TextView(this).apply {
            text        = "+ Clone App"
            textSize    = 14f
            typeface    = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setBackgroundColor(colorAccent)
            gravity     = Gravity.CENTER
            setPadding(dp(24), dp(14), dp(24), dp(14))
            setOnClickListener {
                startActivity(Intent(this@DashboardActivity, CloneWizardActivity::class.java))
            }
        }
        val fabParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            gravity   = Gravity.BOTTOM or Gravity.END
            setMargins(dp(16), dp(16), dp(16), dp(24))
        }
        fabFrame.addView(fabClone, fabParams)
        rootLayout.addView(fabFrame, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ViewModel observation
    // ─────────────────────────────────────────────────────────────────────────
    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.uiState.collect(::renderState) }
            }
        }
    }

    private fun renderState(state: DashboardViewModel.UiState) {
        progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        tvEmpty.visibility     = if (!state.isLoading && state.items.isEmpty()) View.VISIBLE else View.GONE
        rvApps.visibility      = if (!state.isLoading && state.items.isNotEmpty()) View.VISIBLE else View.GONE

        val adapter = AppListAdapter(
            items        = state.items,
            colorSurface = colorSurface,
            colorText    = colorText,
            colorMuted   = colorTextMuted,
            colorAccent  = colorAccent,
            colorDanger  = colorDanger,
            onClone      = { item -> vm.requestClone(item.packageName) },
            onLaunch     = { item -> vm.launchVirtualApp(item.sessionId!!) },
            onRemove     = { item -> confirmRemove(item.packageName, item.sessionId!!) },
            dp           = ::dp,
        )
        rvApps.adapter = adapter

        state.error?.let { showError(it) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Permissions
    // ─────────────────────────────────────────────────────────────────────────
    private fun requestAllPermissions() {
        val perms = mutableListOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.POST_NOTIFICATIONS,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        }.filter { perm ->
            ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED
        }

        if (perms.isNotEmpty()) {
            permissionLauncher.launch(perms.toTypedArray())
        } else {
            vm.refreshInstalledApps()
        }
    }

    private fun showPermissionRationale(denied: List<String>) {
        val names = denied.joinToString("\n") { "• ${it.substringAfterLast(".")}" }
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("The following permissions are needed for full functionality:\n\n$names")
            .setPositiveButton("Grant") { _, _ -> requestAllPermissions() }
            .setNegativeButton("Continue Limited") { _, _ -> vm.refreshInstalledApps() }
            .show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dialogs
    // ─────────────────────────────────────────────────────────────────────────
    private fun confirmRemove(packageName: String, sessionId: String) {
        AlertDialog.Builder(this)
            .setTitle("Remove Clone")
            .setMessage("Remove virtual clone of $packageName?\nAll virtual data will be deleted.")
            .setPositiveButton("Remove") { _, _ -> vm.removeVirtualApp(sessionId) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI helpers
    // ─────────────────────────────────────────────────────────────────────────
    private fun buildChipButton(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text     = label
            textSize = 12f
            setTextColor(colorAccent)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { onClick() }
        }

    private fun buildTab(label: String, selected: Boolean, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text     = label
            textSize = 14f
            gravity  = Gravity.CENTER
            setPadding(dp(8), dp(14), dp(8), dp(14))
            setTextColor(if (selected) colorAccent else colorTextMuted)
            typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setOnClickListener { onClick() }
        }

    private fun selectTab(installedSelected: Boolean) {
        tabInstalledApps.setTextColor(if (installedSelected) colorAccent else colorTextMuted)
        tabInstalledApps.typeface = if (installedSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        tabClonedApps.setTextColor(if (!installedSelected) colorAccent else colorTextMuted)
        tabClonedApps.typeface = if (!installedSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun dp(value: Int): Int = (value * dp).toInt()
}

// ============================================================
// AppListAdapter.kt
// Package: com.konasl.nagad.ui.dashboard
// RecyclerView adapter — 100% programmatic, no XML item layouts.
// ============================================================
package com.konasl.nagad.ui.dashboard

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.konasl.nagad.viewmodel.DashboardViewModel.AppItem

class AppListAdapter(
    private var items:        List<AppItem>,
    private val colorSurface: Int,
    private val colorText:    Int,
    private val colorMuted:   Int,
    private val colorAccent:  Int,
    private val colorDanger:  Int,
    private val onClone:      (AppItem) -> Unit,
    private val onLaunch:     (AppItem) -> Unit,
    private val onRemove:     (AppItem) -> Unit,
    private val dp:           (Int) -> Int,
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

    inner class ViewHolder(val card: LinearLayout) : RecyclerView.ViewHolder(card) {
        val ivIcon:    ImageView = card.findViewWithTag("icon")
        val tvName:    TextView  = card.findViewWithTag("name")
        val tvPkg:     TextView  = card.findViewWithTag("pkg")
        val tvBadge:   TextView  = card.findViewWithTag("badge")
        val btnAction: TextView  = card.findViewWithTag("action")
        val btnRemove: TextView  = card.findViewWithTag("remove")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val ctx = parent.context

        // ── Card root ──────────────────────────────────────────────────────
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setBackgroundColor(colorSurface)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                setMargins(dp(8), dp(4), dp(8), dp(4))
            }
        }

        // ── App icon ───────────────────────────────────────────────────────
        val ivIcon = ImageView(ctx).apply {
            tag          = "icon"
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                marginEnd = dp(12)
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        card.addView(ivIcon)

        // ── Text column ────────────────────────────────────────────────────
        val textCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        val tvName = TextView(ctx).apply {
            tag      = "name"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorText)
            maxLines = 1
        }
        val tvPkg = TextView(ctx).apply {
            tag      = "pkg"
            textSize = 11f
            setTextColor(colorMuted)
            maxLines = 1
        }
        val tvBadge = TextView(ctx).apply {
            tag       = "badge"
            textSize  = 10f
            setTextColor(Color.WHITE)
            setPadding(dp(6), dp(2), dp(6), dp(2))
            setBackgroundColor(colorAccent)
            visibility = View.GONE
        }
        textCol.addView(tvName)
        textCol.addView(tvPkg)
        textCol.addView(tvBadge)
        card.addView(textCol)

        // ── Action buttons ────────────────────────────────────────────────
        val btnRemove = TextView(ctx).apply {
            tag       = "remove"
            text      = "✕"
            textSize  = 14f
            setTextColor(colorDanger)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            visibility = View.GONE
        }
        val btnAction = TextView(ctx).apply {
            tag      = "action"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(colorAccent)
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        card.addView(btnRemove)
        card.addView(btnAction)

        return ViewHolder(card)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.tvName.text  = item.appLabel
        holder.tvPkg.text   = item.packageName
        item.icon?.let { holder.ivIcon.setImageDrawable(it) }

        if (item.isCloned) {
            holder.tvBadge.visibility = View.VISIBLE
            holder.tvBadge.text       = "CLONED"
            holder.btnAction.text     = "▶ Launch"
            holder.btnAction.setBackgroundColor(colorAccent)
            holder.btnRemove.visibility = View.VISIBLE
            holder.btnAction.setOnClickListener { onLaunch(item) }
            holder.btnRemove.setOnClickListener { onRemove(item) }
        } else {
            holder.tvBadge.visibility  = View.GONE
            holder.btnRemove.visibility = View.GONE
            holder.btnAction.text      = "+ Clone"
            holder.btnAction.setBackgroundColor(Color.parseColor("#21262D"))
            holder.btnAction.setOnClickListener { onClone(item) }
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<AppItem>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(o: Int, n: Int) =
                items[o].packageName == newItems[n].packageName
            override fun areContentsTheSame(o: Int, n: Int) = items[o] == newItems[n]
        })
        items = newItems
        diff.dispatchUpdatesTo(this)
    }
}

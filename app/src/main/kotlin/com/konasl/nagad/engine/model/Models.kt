// ============================================================
// Models.kt
// Package: com.konasl.nagad.engine.model
// Data models for virtual engine sessions and apps.
// ============================================================
package com.konasl.nagad.engine.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CloneSession(
    val id              : String,
    val originalPackage : String,
    val virtualPackage  : String,
    val slotIndex       : Int,
    val sandboxDir      : String,
    val apkPath         : String,
    val createdAtMs     : Long,
) : Parcelable

data class VirtualApp(
    val id              : String,
    val originalPackage : String,
    val session         : CloneSession?,
)

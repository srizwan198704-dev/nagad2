// ============================================================
// ProcessSlotManager.kt + ProcessIdentity.kt
// Package: com.konasl.nagad.engine.process
// ============================================================
package com.konasl.nagad.engine.process

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.konasl.nagad.engine.model.CloneSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val Context.slotDataStore by preferencesDataStore("slot_assignments")
private val KEY_USED_SLOTS = stringSetPreferencesKey("used_slots")

@Singleton
class ProcessSlotManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val MAX_SLOTS = 10
        private const val TAG = "ProcessSlotManager"
    }

    /** Map of slotIndex → CloneSession (in-memory runtime table) */
    private val runtimeMap = mutableMapOf<Int, CloneSession>()

    fun allocateSlot(): Int? {
        val usedSlots = runBlocking {
            context.slotDataStore.data.firstOrNull()
                ?.get(KEY_USED_SLOTS)
                ?.mapNotNull { it.toIntOrNull() }
                ?.toSet() ?: emptySet()
        }
        val free = (0 until MAX_SLOTS).firstOrNull { it !in usedSlots }
        if (free == null) {
            Timber.tag(TAG).w("No free process slots!")
            return null
        }
        runBlocking {
            context.slotDataStore.edit { prefs ->
                val current = prefs[KEY_USED_SLOTS]?.toMutableSet() ?: mutableSetOf()
                current.add(free.toString())
                prefs[KEY_USED_SLOTS] = current
            }
        }
        Timber.tag(TAG).d("Allocated slot $free")
        return free
    }

    fun freeSlot(slotIndex: Int) {
        runtimeMap.remove(slotIndex)
        runBlocking {
            context.slotDataStore.edit { prefs ->
                val current = prefs[KEY_USED_SLOTS]?.toMutableSet() ?: mutableSetOf()
                current.remove(slotIndex.toString())
                prefs[KEY_USED_SLOTS] = current
            }
        }
        Timber.tag(TAG).d("Freed slot $slotIndex")
    }

    fun registerSession(slotIndex: Int, session: CloneSession) {
        runtimeMap[slotIndex] = session
    }

    fun getSessionForSlot(slotIndex: Int): CloneSession? = runtimeMap[slotIndex]
}

// ─────────────────────────────────────────────────────────────────────────────

data class ProcessIdentity(
    val slotIndex   : Int,
    val packageName : String,
    val uid         : Int,
) {
    companion object {
        fun resolveForSlot(context: Context, slotIndex: Int): ProcessIdentity? {
            // Read from DataStore / virtual FS mappings at runtime
            // This is a placeholder that returns null when no app is mapped
            return null
        }
    }
}

package com.swordfish.chimeroid.app.mobile.feature.game

import android.content.SharedPreferences
import com.swordfish.chimeroid.lib.library.SystemID
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ─────────────────────────────────────────────────────────────────────────────
// Data model
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class PanelLayout(
    val xFraction: Float      = 0f,
    val yFraction: Float      = 0f,
    val widthFraction: Float  = 1f,
    val heightFraction: Float = 0.35f,
) {
    companion object {
        const val MIN_W = 0.20f
        const val MIN_H = 0.15f
    }
}

@Serializable
data class DualScreenLayout(val top: PanelLayout, val bottom: PanelLayout)

// ─────────────────────────────────────────────────────────────────────────────
// Optimal layout computation (Drastic-like: each screen fits 4:3 exactly)
// ─────────────────────────────────────────────────────────────────────────────

object DualScreenDefaults {
    /**
     * Computes the portrait panel height fraction so each screen fills its
     * full-width panel with the correct aspect ratio — no letterboxing.
     *
     * @param containerW  Available width  (any unit: dp or px)
     * @param containerH  Available height (same unit)
     * @param systemId    System identifier to select per-screen AR
     */
    fun optimalPortrait(containerW: Float, containerH: Float, systemId: SystemID): DualScreenLayout {
        if (containerH <= 0f) return fallback(systemId)

        // NDS: both screens 256×192 (4:3)
        // 3DS: top 400×240 (5:3), bottom 320×240 (4:3)
        val topAR = if (systemId == SystemID.NINTENDO_3DS) 5f / 3f else 4f / 3f
        val botAR = 4f / 3f

        val topH = (containerW / (topAR * containerH)).coerceIn(0.15f, 0.48f)
        val botH = (containerW / (botAR * containerH)).coerceIn(0.15f, 0.48f)

        // Center horizontally for 3DS bottom (narrower) — match UV x-offset
        val botX = if (systemId == SystemID.NINTENDO_3DS) 0.1f else 0f
        val botW = if (systemId == SystemID.NINTENDO_3DS) 0.8f else 1f

        return DualScreenLayout(
            top    = PanelLayout(0f,  0f,   1f,  topH),
            bottom = PanelLayout(botX, topH, botW, botH),
        )
    }

    private fun fallback(systemId: SystemID) = DualScreenLayout(
        top    = PanelLayout(0f, 0f,   1f, 0.35f),
        bottom = PanelLayout(0f, 0.35f, 1f, 0.35f),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Persistence
// ─────────────────────────────────────────────────────────────────────────────

class DualScreenLayoutManager(
    private val prefs: SharedPreferences,
    systemDbName: String,
) {
    private val key  = "pref_dual_screen_layout_$systemDbName"
    private val json = Json { ignoreUnknownKeys = true }

    /** Returns null if nothing has been saved yet. */
    fun load(): DualScreenLayout? = try {
        prefs.getString(key, null)?.let { json.decodeFromString<DualScreenLayout>(it) }
    } catch (_: Exception) { null }

    fun save(layout: DualScreenLayout) =
        prefs.edit().putString(key, json.encodeToString(layout)).apply()

    fun clear() = prefs.edit().remove(key).apply()
}

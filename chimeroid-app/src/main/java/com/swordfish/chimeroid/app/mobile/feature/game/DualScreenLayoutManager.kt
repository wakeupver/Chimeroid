package com.swordfish.chimeroid.app.mobile.feature.game

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ─────────────────────────────────────────────────────────────────────────────
// Data model
// ─────────────────────────────────────────────────────────────────────────────

/** Position and size of one screen panel as 0-1 fractions of the container. */
@Serializable
data class PanelLayout(
    val xFraction: Float      = 0f,
    val yFraction: Float      = 0f,
    val widthFraction: Float  = 1f,
    val heightFraction: Float = 0.5f,
) {
    fun clamped(containerXBound: Float = 1f, containerYBound: Float = 1f) = copy(
        xFraction      = xFraction.coerceIn(0f, (containerXBound - widthFraction).coerceAtLeast(0f)),
        yFraction      = yFraction.coerceIn(0f, (containerYBound - heightFraction).coerceAtLeast(0f)),
        widthFraction  = widthFraction.coerceIn(MIN_W, 1f),
        heightFraction = heightFraction.coerceIn(MIN_H, 1f),
    )

    companion object {
        const val MIN_W = 0.20f
        const val MIN_H = 0.15f
    }
}

/** Layout for both screens of a dual-screen system. */
@Serializable
data class DualScreenLayout(
    val top: PanelLayout,
    val bottom: PanelLayout,
) {
    companion object {
        /** NDS: both screens same width, stacked 50/50. */
        val NDS_DEFAULT = DualScreenLayout(
            top    = PanelLayout(0f, 0f,   1f, 0.5f),
            bottom = PanelLayout(0f, 0.5f, 1f, 0.5f),
        )

        /** 3DS: top full-width, bottom narrower (320/400 = 0.8) centred. */
        val N3DS_DEFAULT = DualScreenLayout(
            top    = PanelLayout(0f,  0f,   1f,   0.5f),
            bottom = PanelLayout(0.1f, 0.5f, 0.8f, 0.5f),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Persistence
// ─────────────────────────────────────────────────────────────────────────────

class DualScreenLayoutManager(
    private val prefs: SharedPreferences,
    systemDbName: String,
) {
    private val key = "pref_dual_screen_layout_$systemDbName"
    private val json = Json { ignoreUnknownKeys = true }

    fun load(default: DualScreenLayout): DualScreenLayout = try {
        prefs.getString(key, null)
            ?.let { json.decodeFromString<DualScreenLayout>(it) }
            ?: default
    } catch (_: Exception) { default }

    fun save(layout: DualScreenLayout) =
        prefs.edit().putString(key, json.encodeToString(layout)).apply()
}

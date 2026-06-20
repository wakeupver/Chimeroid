package com.swordfish.chimeroid.app.mobile.feature.game

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PanelLayout(
    val xFraction: Float      = 0f,
    val yFraction: Float      = 0f,
    val widthFraction: Float  = 1f,
    val heightFraction: Float = 0.5f,
) {
    companion object {
        const val MIN_W = 0.20f
        const val MIN_H = 0.15f
    }
}

@Serializable
data class DualScreenLayout(
    val top: PanelLayout,
    val bottom: PanelLayout,
) {
    companion object {
        // Portrait: both screens stacked vertically
        val NDS_PORTRAIT = DualScreenLayout(
            top    = PanelLayout(0f, 0f,   1f, 0.5f),
            bottom = PanelLayout(0f, 0.5f, 1f, 0.5f),
        )
        // Landscape: screens side-by-side
        val NDS_LANDSCAPE = DualScreenLayout(
            top    = PanelLayout(0f,   0f, 0.5f, 1f),
            bottom = PanelLayout(0.5f, 0f, 0.5f, 1f),
        )
        // 3DS portrait: top full-width, bottom 80% centred
        val N3DS_PORTRAIT = DualScreenLayout(
            top    = PanelLayout(0f,  0f,   1f,   0.5f),
            bottom = PanelLayout(0.1f, 0.5f, 0.8f, 0.5f),
        )
        // 3DS landscape: top wider (5:3), bottom narrower (4:3)
        val N3DS_LANDSCAPE = DualScreenLayout(
            top    = PanelLayout(0f,   0f,    0.55f, 1f),
            bottom = PanelLayout(0.55f, 0.1f, 0.45f, 0.8f),
        )
    }
}

class DualScreenLayoutManager(
    private val prefs: SharedPreferences,
    systemDbName: String,
) {
    private val portraitKey  = "pref_dual_screen_layout_${systemDbName}_portrait"
    private val landscapeKey = "pref_dual_screen_layout_${systemDbName}_landscape"
    private val json = Json { ignoreUnknownKeys = true }

    fun load(default: DualScreenLayout, isLandscape: Boolean): DualScreenLayout = try {
        val key = if (isLandscape) landscapeKey else portraitKey
        prefs.getString(key, null)
            ?.let { json.decodeFromString<DualScreenLayout>(it) }
            ?: default
    } catch (_: Exception) { default }

    fun save(layout: DualScreenLayout, isLandscape: Boolean) {
        val key = if (isLandscape) landscapeKey else portraitKey
        prefs.edit().putString(key, json.encodeToString(layout)).apply()
    }
}

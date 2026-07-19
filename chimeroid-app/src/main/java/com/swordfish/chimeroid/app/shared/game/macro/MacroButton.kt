package com.swordfish.chimeroid.app.shared.game.macro

import android.view.KeyEvent
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.hypot

/**
 * Represents a virtual macro button that fires one or more key inputs when tapped.
 *
 * @param id          Unique identifier (auto-generated)
 * @param label       Short label displayed on the button (1-4 characters)
 * @param keyCodes    List of [KeyEvent.KEYCODE_*] values to send when triggered
 * @param xFraction   Horizontal position as fraction [0.0, 1.0] of screen width
 * @param yFraction   Vertical position as fraction [0.0, 1.0] of screen height
 * @param scale       Size multiplier, clamped to
 *                    [TouchControllerSettingsManager.MIN_SCALE, TouchControllerSettingsManager.MAX_SCALE]
 *                    — the same range the main touch-controls Edit Controls scale slider uses.
 */
@Serializable
data class MacroButton(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val keyCodes: List<Int>,
    val xFraction: Float = 0.5f,
    val yFraction: Float = 0.75f,
    val scale: Float = 1.0f,
) {
    companion object {
        /** All assignable keys with their display labels. */
        val ALL_KEYS: List<Pair<Int, String>> = listOf(
            KeyEvent.KEYCODE_BUTTON_A to "A",
            KeyEvent.KEYCODE_BUTTON_B to "B",
            KeyEvent.KEYCODE_BUTTON_X to "X",
            KeyEvent.KEYCODE_BUTTON_Y to "Y",
            KeyEvent.KEYCODE_BUTTON_L1 to "L1",
            KeyEvent.KEYCODE_BUTTON_R1 to "R1",
            KeyEvent.KEYCODE_BUTTON_L2 to "L2",
            KeyEvent.KEYCODE_BUTTON_R2 to "R2",
            KeyEvent.KEYCODE_BUTTON_START to "Start",
            KeyEvent.KEYCODE_BUTTON_SELECT to "Select",
        )

        /** Returns the human-readable name of a keycode. */
        fun keyName(keyCode: Int): String =
            ALL_KEYS.firstOrNull { it.first == keyCode }?.second ?: "?"

        /** Builds a compact label from selected key codes (e.g. "A+B"). */
        fun autoLabel(keyCodes: List<Int>): String =
            keyCodes.take(3).joinToString("+") { keyName(it) }.take(8)

        const val MAX_BUTTONS = 8

        // 4 columns x 2 rows = MAX_BUTTONS distinct anchors; kept off the extreme edges so
        // every slot is reachable. Computed once (top-level val, not per-call) — zero
        // allocation on the call path below.
        private val SPAWN_SLOTS: List<Pair<Float, Float>> = listOf(0.55f, 0.74f).let { rows ->
            val cols = listOf(0.20f, 0.40f, 0.60f, 0.80f)
            rows.flatMap { y -> cols.map { x -> x to y } }
        }
        private const val MIN_SLOT_SEPARATION = 0.09f

        /**
         * Picks a default spawn position for a new macro button that doesn't land on top of
         * an [existing] one (root cause of macros becoming stuck/unreachable in edit mode when
         * added back-to-back, since every button previously spawned at the same (0.5, 0.75)
         * default). Falls back to a cyclic slot if the board is unusually crowded; never throws.
         */
        fun nextSpawnPosition(existing: List<MacroButton>): Pair<Float, Float> {
            val free = SPAWN_SLOTS.firstOrNull { (x, y) ->
                existing.none { hypot(it.xFraction - x, it.yFraction - y) < MIN_SLOT_SEPARATION }
            }
            return free ?: SPAWN_SLOTS[existing.size % SPAWN_SLOTS.size]
        }
    }
}

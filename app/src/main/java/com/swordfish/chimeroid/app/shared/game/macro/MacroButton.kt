package com.swordfish.chimeroid.app.shared.game.macro

import android.view.KeyEvent
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.hypot

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

        fun keyName(keyCode: Int): String =
            ALL_KEYS.firstOrNull { it.first == keyCode }?.second ?: "?"

        fun autoLabel(keyCodes: List<Int>): String =
            keyCodes.take(3).joinToString("+") { keyName(it) }.take(8)

        const val MAX_BUTTONS = 8

        private val SPAWN_SLOTS: List<Pair<Float, Float>> = listOf(0.55f, 0.74f).let { rows ->
            val cols = listOf(0.20f, 0.40f, 0.60f, 0.80f)
            rows.flatMap { y -> cols.map { x -> x to y } }
        }
        private const val MIN_SLOT_SEPARATION = 0.09f

        fun nextSpawnPosition(existing: List<MacroButton>): Pair<Float, Float> {
            val free = SPAWN_SLOTS.firstOrNull { (x, y) ->
                existing.none { hypot(it.xFraction - x, it.yFraction - y) < MIN_SLOT_SEPARATION }
            }
            return free ?: SPAWN_SLOTS[existing.size % SPAWN_SLOTS.size]
        }
    }
}

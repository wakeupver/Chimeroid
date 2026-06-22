package com.swordfish.chimeroid.lib.library

import java.io.Serializable

/**
 * Describes one core variable that should be shown in the Core Options menu.
 *
 * Two variants:
 *  - [Registered]   — declared statically in [GameSystem]; carries an Android string-resource id
 *                     for its title and an explicit set of allowed values.
 *  - [AutoDetected] — discovered at runtime from the core's [RETRO_ENVIRONMENT_SET_VARIABLES]
 *                     callback; uses the raw description string from the core as its title.
 */
sealed class ExposedSetting : Serializable {

    abstract val key: String

    // ── Static / manually declared ───────────────────────────────────────────

    data class Registered(
        override val key: String,
        /** Android string resource id for the human-readable setting title. */
        val titleId: Int,
        /** Allowed values; empty means the full option list from the core is used. */
        val values: List<Value> = emptyList(),
    ) : ExposedSetting()

    // ── Discovered from the core at runtime ──────────────────────────────────

    data class AutoDetected(
        override val key: String,
        /** Raw title from the core's variable description (the text before ';'). */
        val rawTitle: String,
    ) : ExposedSetting()

    // ── Shared ───────────────────────────────────────────────────────────────

    data class Value(val key: String, val titleId: Int) : Serializable
}

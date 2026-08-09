package com.swordfish.chimeroid.lib.library

import java.io.Serializable

sealed class ExposedSetting : Serializable {

    abstract val key: String

    data class Registered(
        override val key: String,

        val titleId: Int,

        val values: List<Value> = emptyList(),
    ) : ExposedSetting()

    data class AutoDetected(
        override val key: String,

        val rawTitle: String,
    ) : ExposedSetting()

    data class Value(val key: String, val titleId: Int) : Serializable
}

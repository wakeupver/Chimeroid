package com.swordfish.chimeroid.app.shared.coreoptions

import android.content.Context
import com.swordfish.chimeroid.lib.library.ExposedSetting
import java.io.Serializable

data class ChimeroidCoreOption(
    private val exposedSetting: ExposedSetting,
    private val coreOption: CoreOption,
) : Serializable {

    data class Entries(val values: List<String>, val labels: List<String>, val currentIndex: Int)

    fun getKey(): String = exposedSetting.key

    fun getDisplayName(context: Context): String =
        when (val s = exposedSetting) {
            is ExposedSetting.Registered  -> context.getString(s.titleId)
            is ExposedSetting.AutoDetected -> s.rawTitle.ifBlank { coreOption.name.ifBlank { s.key } }
        }

    fun getCurrentValue(): String = coreOption.variable.value

    fun getEntriesData(context: Context): Entries {
        val setting = exposedSetting as? ExposedSetting.Registered
        val matched = setting?.let { matchedValues(it) } ?: emptyList()

        val values = if (matched.isEmpty()) coreOption.optionValues else matched.map { it.key }
        val labels = if (matched.isEmpty()) {
            coreOption.optionValues.map { it.replaceFirstChar(Char::uppercaseChar) }
        } else {
            matched.map { context.getString(it.titleId) }
        }

        return Entries(values, labels, maxOf(values.indexOf(getCurrentValue()), 0))
    }

    fun isAutoDetected(): Boolean = exposedSetting is ExposedSetting.AutoDetected

    private fun matchedValues(setting: ExposedSetting.Registered): List<ExposedSetting.Value> {
        val coreSet = coreOption.optionValues.map { it.trim().lowercase() }.toHashSet()
        return setting.values.filter { it.key.trim().lowercase() in coreSet }
    }

    companion object {

        fun fromAutoDetected(coreOption: CoreOption): ChimeroidCoreOption =
            ChimeroidCoreOption(
                exposedSetting = ExposedSetting.AutoDetected(
                    key      = coreOption.variable.key,
                    rawTitle = coreOption.name.trim(),
                ),
                coreOption = coreOption,
            )
    }
}

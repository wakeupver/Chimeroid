package com.swordfish.chimeroid.app.shared.coreoptions

import android.content.Context
import com.swordfish.chimeroid.lib.library.ExposedSetting
import java.io.Serializable

data class ChimeroidCoreOption(
    private val exposedSetting: ExposedSetting,
    private val coreOption: CoreOption,
) : Serializable {

    fun getKey(): String = exposedSetting.key

    fun getDisplayName(context: Context): String =
        when (val s = exposedSetting) {
            is ExposedSetting.Registered  -> context.getString(s.titleId)
            is ExposedSetting.AutoDetected -> s.rawTitle.ifBlank { coreOption.name.ifBlank { s.key } }
        }

    fun getEntries(context: Context): List<String> {
        val setting = exposedSetting as? ExposedSetting.Registered
        val matched = setting?.let { matchedValues(it) } ?: emptyList()
        return if (matched.isEmpty()) {
            coreOption.optionValues.map { it.replaceFirstChar(Char::uppercaseChar) }
        } else {
            matched.map { context.getString(it.titleId) }
        }
    }

    fun getEntriesValues(): List<String> {
        val setting = exposedSetting as? ExposedSetting.Registered
        val matched = setting?.let { matchedValues(it) } ?: emptyList()
        return if (matched.isEmpty()) coreOption.optionValues else matched.map { it.key }
    }

    fun getCurrentValue(): String = coreOption.variable.value

    fun getCurrentIndex(): Int = maxOf(getEntriesValues().indexOf(getCurrentValue()), 0)

    /** True when this option was discovered at runtime, not declared in [GameSystem]. */
    fun isAutoDetected(): Boolean = exposedSetting is ExposedSetting.AutoDetected

    /**
     * Filter [ExposedSetting.Registered.values] to those the core actually reports,
     * using case-insensitive comparison to tolerate minor capitalisation drift between
     * the static declaration and the live core build.
     */
    private fun matchedValues(setting: ExposedSetting.Registered): List<ExposedSetting.Value> {
        val coreSet = coreOption.optionValues.map { it.trim().lowercase() }.toHashSet()
        return setting.values.filter { it.key.trim().lowercase() in coreSet }
    }

    companion object {
        /** Wrap a core-reported [CoreOption] that has no static [ExposedSetting] declaration. */
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

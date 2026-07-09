package com.swordfish.chimeroid.app.shared.coreoptions

import android.content.Context
import com.swordfish.chimeroid.lib.library.ExposedSetting
import java.io.Serializable

data class ChimeroidCoreOption(
    private val exposedSetting: ExposedSetting,
    private val coreOption: CoreOption,
) : Serializable {

    /** Entry values/labels and the index of the current value, computed in a single pass. */
    data class Entries(val values: List<String>, val labels: List<String>, val currentIndex: Int)

    fun getKey(): String = exposedSetting.key

    fun getDisplayName(context: Context): String =
        when (val s = exposedSetting) {
            is ExposedSetting.Registered  -> context.getString(s.titleId)
            is ExposedSetting.AutoDetected -> s.rawTitle.ifBlank { coreOption.name.ifBlank { s.key } }
        }

    fun getCurrentValue(): String = coreOption.variable.value

    /**
     * Computes entry values, display labels, and the current-value index together from a
     * single [matchedValues] pass. Call sites previously invoked the equivalent of this logic
     * separately (getEntries + getEntriesValues + getCurrentIndex, each re-deriving
     * [matchedValues]) — up to 3-4 redundant passes per option. That mattered most in the
     * Compose menu, where the per-option composable re-evaluates on every recomposition.
     */
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

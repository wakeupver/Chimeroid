package com.swordfish.chimeroid.app.shared.coreoptions

import android.content.Context
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreference
import com.swordfish.chimeroid.R
import com.swordfish.chimeroid.app.shared.settings.ControllerConfigsManager
import com.swordfish.chimeroid.lib.controller.ControllerConfig
import com.swordfish.chimeroid.lib.core.CoreVariablesManager
import com.swordfish.chimeroid.lib.library.CoreID

object CoreOptionsPreferenceHelper {
    val BOOLEAN_SET = setOf("enabled", "disabled")

    fun addPreferences(
        preferenceScreen: PreferenceScreen,
        systemID: String,
        baseOptions: List<ChimeroidCoreOption>,
        advancedOptions: List<ChimeroidCoreOption>,
    ) {
        if (baseOptions.isEmpty() && advancedOptions.isEmpty()) {
            return
        }

        val context = preferenceScreen.context

        val title = context.getString(R.string.core_settings_category_preferences)
        val preferencesCategory = createCategory(preferenceScreen.context, preferenceScreen, title)

        addPreferences(context, preferencesCategory, baseOptions, systemID)
        addPreferences(context, preferencesCategory, advancedOptions, systemID)
    }

    fun addAutoDetectedPreferences(
        preferenceScreen: PreferenceScreen,
        systemID: String,
        autoDetectedOptions: List<ChimeroidCoreOption>,
    ) {
        if (autoDetectedOptions.isEmpty()) return

        val context = preferenceScreen.context
        val title = context.getString(R.string.core_settings_auto_detected)
        val category = createCategory(context, preferenceScreen, title)
        addPreferences(context, category, autoDetectedOptions, systemID)
    }

    fun addControllers(
        preferenceScreen: PreferenceScreen,
        systemID: String,
        coreID: CoreID,
        connectedGamePads: Int,
        controllers: Map<Int, List<ControllerConfig>>,
    ) {
        val visibleControllers =
            (0 until connectedGamePads)
                .map { it to controllers[it] }
                .filter { (_, controllers) -> controllers != null && controllers.size >= 2 }

        if (visibleControllers.isEmpty()) {
            return
        }

        val context = preferenceScreen.context
        val title = context.getString(R.string.core_settings_category_controllers)
        val category = createCategory(context, preferenceScreen, title)

        visibleControllers
            .forEach { (port, controllers) ->
                val preference = buildControllerPreference(context, systemID, coreID, port, controllers!!)
                category.addPreference(preference)
            }
    }

    private fun addPreferences(
        context: Context,
        preferenceGroup: PreferenceGroup,
        options: List<ChimeroidCoreOption>,
        systemID: String,
    ) {
        options
            .map { convertToPreference(context, it, systemID) }
            .forEach { preferenceGroup.addPreference(it) }
    }

    private fun convertToPreference(
        context: Context,
        it: ChimeroidCoreOption,
        systemID: String,
    ): Preference {
        val entriesData = it.getEntriesData(context)
        return if (entriesData.values.toSet() == BOOLEAN_SET) {
            buildSwitchPreference(context, it, systemID)
        } else {
            buildListPreference(context, it, systemID, entriesData)
        }
    }

    private fun buildListPreference(
        context: Context,
        it: ChimeroidCoreOption,
        systemID: String,
        entriesData: ChimeroidCoreOption.Entries,
    ): ListPreference {
        val preference = ListPreference(context)
        preference.key = CoreVariablesManager.computeSharedPreferenceKey(it.getKey(), systemID)
        preference.title = it.getDisplayName(context)
        preference.entries = entriesData.labels.toTypedArray()
        preference.entryValues = entriesData.values.toTypedArray()
        preference.setDefaultValue(it.getCurrentValue())
        preference.setValueIndex(entriesData.currentIndex)
        preference.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        preference.isIconSpaceReserved = false
        return preference
    }

    private fun buildSwitchPreference(
        context: Context,
        it: ChimeroidCoreOption,
        systemID: String,
    ): SwitchPreference {
        val preference = SwitchPreference(context)
        preference.key = CoreVariablesManager.computeSharedPreferenceKey(it.getKey(), systemID)
        preference.title = it.getDisplayName(context)
        preference.setDefaultValue(it.getCurrentValue() == "enabled")
        preference.isChecked = it.getCurrentValue() == "enabled"
        preference.isIconSpaceReserved = false
        return preference
    }

    private fun buildControllerPreference(
        context: Context,
        systemID: String,
        coreID: CoreID,
        port: Int,
        controllerConfigs: List<ControllerConfig>,
    ): Preference {
        val preference = ListPreference(context)
        val names = controllerConfigs.map { it.name }
        preference.key = ControllerConfigsManager.getSharedPreferencesId(systemID, coreID, port)
        preference.title = context.getString(R.string.core_settings_controller, (port + 1).toString())
        preference.entries = controllerConfigs.map { context.getString(it.displayName) }.toTypedArray()
        preference.entryValues = names.toTypedArray()
        preference.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        preference.isIconSpaceReserved = false
        preference.setDefaultValue(names.first())
        return preference
    }

    private fun createCategory(
        context: Context,
        preferenceScreen: PreferenceScreen,
        title: String,
    ): PreferenceCategory {
        val category = PreferenceCategory(context)
        preferenceScreen.addPreference(category)
        category.title = title
        category.isIconSpaceReserved = false
        return category
    }
}

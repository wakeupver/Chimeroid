package com.swordfish.chimeroid.app.shared.coreoptions

import com.swordfish.chimeroid.lib.core.CoreVariable
import com.swordfish.libretrodroid.Variable
import java.io.Serializable

data class CoreOption(
    val variable: CoreVariable,
    val name: String,
    val optionValues: List<String>,
) : Serializable {
    companion object {

        fun fromLibretroDroidVariable(variable: Variable): CoreOption {
            val key = variable.key
                ?: throw IllegalArgumentException("Variable key must not be null")

            val currentValue = variable.value ?: ""

            val description = variable.description ?: ""

            val separatorIndex = description.indexOf(';')
            val name: String
            val values: List<String>

            if (separatorIndex < 0) {

                name = description.trim().ifEmpty { key }
                values = emptyList()
            } else {
                name = description.substring(0, separatorIndex).trim().ifEmpty { key }
                values = description.substring(separatorIndex + 1)
                    .trim()
                    .split('|')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            }

            return CoreOption(CoreVariable(key, currentValue), name, values)
        }
    }
}

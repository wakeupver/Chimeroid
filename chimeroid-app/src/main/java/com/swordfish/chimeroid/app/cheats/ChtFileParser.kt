package com.swordfish.chimeroid.app.cheats

object ChtFileParser {

    data class ParsedCheat(
        val description: String,
        val code: String,
        val enabled: Boolean,
    )

    data class ParseResult(
        val cheats: List<ParsedCheat>,
        val skippedCount: Int,
    )

    fun parse(content: String): ParseResult {
        val lines = content.lines()
        val descMap = mutableMapOf<Int, String>()
        val codeMap = mutableMapOf<Int, String>()
        val enableMap = mutableMapOf<Int, Boolean>()

        var skipped = 0

        for (rawLine in lines) {
            val line = rawLine.trim()

            if (line.isEmpty() || line.startsWith("#")) continue

            val eqIdx = line.indexOf(" = ")
            if (eqIdx < 0) continue

            val key = line.substring(0, eqIdx).trim()
            val value = line.substring(eqIdx + 3).trim().removeQuotes()

            if (key == "cheats") continue

            val cheatMatch = CHEAT_KEY_REGEX.matchEntire(key) ?: continue
            val index = cheatMatch.groupValues[1].toIntOrNull() ?: continue
            val field = cheatMatch.groupValues[2]

            when (field) {
                "desc" -> descMap[index] = value
                "code" -> codeMap[index] = value
                "enable" -> enableMap[index] = value.equals("true", ignoreCase = true)

            }
        }

        val cheats = mutableListOf<ParsedCheat>()
        for (index in codeMap.keys.sorted()) {
            val code = codeMap[index] ?: continue
            if (code.isBlank()) {
                skipped++
                continue
            }
            val desc = descMap[index]?.takeIf { it.isNotBlank() } ?: code
            val enabled = enableMap[index] ?: false
            cheats += ParsedCheat(description = desc, code = code, enabled = enabled)
        }

        return ParseResult(cheats = cheats, skippedCount = skipped)
    }

    private fun String.removeQuotes(): String {
        return when {
            length >= 2 && startsWith('"') && endsWith('"') -> substring(1, length - 1)
            length >= 2 && startsWith('\'') && endsWith('\'') -> substring(1, length - 1)
            else -> this
        }
    }

    private val CHEAT_KEY_REGEX = Regex("""^cheat(\d+)_(\w+)$""")
}

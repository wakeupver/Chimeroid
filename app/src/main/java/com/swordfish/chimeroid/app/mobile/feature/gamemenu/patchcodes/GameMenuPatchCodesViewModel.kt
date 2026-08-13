package com.swordfish.chimeroid.app.mobile.feature.gamemenu.patchcodes

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.chimeroid.app.cheats.ChtFileParser
import com.swordfish.chimeroid.app.utils.android.viewmodel.viewModelFactory
import com.swordfish.chimeroid.lib.library.db.dao.PatchCodeDao
import com.swordfish.chimeroid.lib.library.db.entity.PatchCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GameMenuPatchCodesViewModel(
    private val gameId: Int,
    private val patchCodeDao: PatchCodeDao,
) : ViewModel() {

    class Factory(
        gameId: Int,
        patchCodeDao: PatchCodeDao,
    ) : ViewModelProvider.Factory by viewModelFactory({ GameMenuPatchCodesViewModel(gameId, patchCodeDao) })

    sealed interface ImportResult {
        data class Success(val imported: Int, val skipped: Int) : ImportResult
        data class Error(val message: String) : ImportResult
    }

    val patchCodes: StateFlow<List<PatchCode>> =
        patchCodeDao
            .getCodesForGame(gameId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importResult = MutableSharedFlow<ImportResult>()
    val importResult: SharedFlow<ImportResult> = _importResult

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting

    fun addCode(description: String, code: String): Boolean {
        val trimmedDesc = description.trim()
        val trimmedCode = code.trim()
        if (trimmedDesc.isBlank() || trimmedCode.isBlank()) return false
        viewModelScope.launch {
            patchCodeDao.insert(
                PatchCode(gameId = gameId, description = trimmedDesc, code = trimmedCode, enabled = false),
            )
        }
        return true
    }

    fun importChtFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            val result = withContext(Dispatchers.IO) {
                runCatching { readAndParse(context, uri) }
                    .getOrElse { e -> ImportResult.Error(e.message ?: "Unknown error reading file") }
            }
            _isImporting.value = false
            _importResult.emit(result)
        }
    }

    private suspend fun readAndParse(context: Context, uri: Uri): ImportResult {
        val content = context.contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: return ImportResult.Error("Cannot open file")

        val parseResult = ChtFileParser.parse(content)
        if (parseResult.cheats.isEmpty()) {
            return ImportResult.Error("No valid cheat codes found in file")
        }

        val existingCodes = patchCodeDao.getCodesForGameOnce(gameId)
            .map { it.code.trim().uppercase() }
            .toHashSet()

        var imported = 0
        var skipped = parseResult.skippedCount

        for (cheat in parseResult.cheats) {
            val normalised = cheat.code.trim().uppercase()
            if (normalised in existingCodes) { skipped++; continue }
            patchCodeDao.insert(
                PatchCode(gameId = gameId, description = cheat.description, code = cheat.code, enabled = cheat.enabled),
            )
            existingCodes += normalised
            imported++
        }

        return ImportResult.Success(imported = imported, skipped = skipped)
    }

    fun toggleCode(patchCode: PatchCode) {
        viewModelScope.launch { patchCodeDao.setEnabled(patchCode.id, !patchCode.enabled) }
    }

    fun deleteCode(patchCode: PatchCode) {
        viewModelScope.launch { patchCodeDao.delete(patchCode) }
    }
}

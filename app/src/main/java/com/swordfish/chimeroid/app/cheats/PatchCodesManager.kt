package com.swordfish.chimeroid.app.cheats

import com.swordfish.chimeroid.lib.library.db.dao.PatchCodeDao
import com.swordfish.chimeroid.lib.library.db.entity.PatchCode
import com.swordfish.libretrodroid.GLRetroView
import timber.log.Timber

object PatchCodesManager {

    fun applyAll(
        retroView: GLRetroView,
        codes: List<PatchCode>,
    ) {
        Timber.d("Applying ${codes.size} patch code(s) to emulator.")
        codes.forEach { code ->
            Timber.d("  ${if (code.enabled) "ON " else "OFF"} '${code.description}': ${code.code}")
        }
        retroView.resetAndApplyCheats(codes.map { GLRetroView.CheatCode(it.enabled, it.code) })
    }

    suspend fun applyFromDao(
        retroView: GLRetroView,
        patchCodeDao: PatchCodeDao,
        gameId: Int,
    ) {
        val codes = patchCodeDao.getCodesForGameOnce(gameId)
        applyAll(retroView, codes)
    }
}

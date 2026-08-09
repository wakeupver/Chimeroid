package com.swordfish.chimeroid.lib.library.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.swordfish.chimeroid.lib.library.db.entity.PatchCode
import kotlinx.coroutines.flow.Flow

@Dao
interface PatchCodeDao {

    @Query("SELECT * FROM patch_codes WHERE gameId = :gameId ORDER BY id ASC")
    fun getCodesForGame(gameId: Int): Flow<List<PatchCode>>

    @Query("SELECT * FROM patch_codes WHERE gameId = :gameId ORDER BY id ASC")
    suspend fun getCodesForGameOnce(gameId: Int): List<PatchCode>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(code: PatchCode): Long

    @Delete
    suspend fun delete(code: PatchCode)

    @Query("UPDATE patch_codes SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Int, enabled: Boolean)
}

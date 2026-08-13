package com.swordfish.chimeroid.lib.library.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(
    tableName = "patch_codes",
    foreignKeys = [
        ForeignKey(
            entity = Game::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("gameId"),
    ],
)
data class PatchCode(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val gameId: Int,

    val description: String,

    val code: String,

    val enabled: Boolean = false,
) : Serializable

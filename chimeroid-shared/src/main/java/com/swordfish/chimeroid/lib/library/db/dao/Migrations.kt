package com.swordfish.chimeroid.lib.library.db.dao

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    val VERSION_8_9: Migration =
        object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `datafiles`(
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `gameId` INTEGER NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `fileUri` TEXT NOT NULL,
                        `lastIndexedAt` INTEGER NOT NULL,
                        `path` TEXT, FOREIGN KEY(`gameId`
                    ) REFERENCES `games`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )
                    """.trimIndent(),
                )

                database.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_datafiles_id` ON `datafiles` (`id`)
                    """.trimIndent(),
                )

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_datafiles_fileUri` ON `datafiles` (`fileUri`)
                    """.trimIndent(),
                )

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_datafiles_gameId` ON `datafiles` (`gameId`)
                    """.trimIndent(),
                )

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_datafiles_lastIndexedAt` ON `datafiles` (`lastIndexedAt`)
                    """.trimIndent(),
                )
            }
        }

    val VERSION_9_10: Migration =
        object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create patch_codes table linked to games via FK
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `patch_codes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `gameId` INTEGER NOT NULL,
                        `description` TEXT NOT NULL,
                        `code` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`gameId`) REFERENCES `games`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )

                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_patch_codes_id` ON `patch_codes` (`id`)",
                )

                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_patch_codes_gameId` ON `patch_codes` (`gameId`)",
                )
            }
        }

    val VERSION_10_11: Migration =
        object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // `id` is already the table's PRIMARY KEY, so SQLite maintains an implicit
                // unique index for it automatically. The explicit index created in
                // VERSION_9_10 was a redundant duplicate — same lookups, double the
                // write-time index maintenance. Drop it; index_patch_codes_gameId (used by
                // every DAO query's WHERE gameId = ...) is unaffected.
                database.execSQL("DROP INDEX IF EXISTS `index_patch_codes_id`")
            }
        }
}

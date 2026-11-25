package com.nexusnotes.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema history
 *  v1 (Milestone 2): notes
 *  v2 (Milestone 4): + notes.folderId, folders, tags, note_tags, notes_fts (full-text index)
 *  v3 (Milestone 5): + prompts, prompt_categories, prompt_variables, prompt_versions, prompts_fts
 *  v4 (Milestone 6): + attachments
 *
 * Migrations only ADD tables, columns and indexes. Existing rows are never rewritten or
 * dropped, so every note survives the upgrade. Room checks the resulting schema on open.
 */
object Migrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Notes get a folder link (null = Unfiled).
            db.execSQL("ALTER TABLE `notes` ADD COLUMN `folderId` TEXT")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_folderId` ON `notes` (`folderId`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `folders` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`parentId` TEXT, `colorIndex` INTEGER, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_folders_parentId` ON `folders` (`parentId`)")

            db.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `note_tags` (`noteId` TEXT NOT NULL, `tagId` TEXT NOT NULL, " +
                    "PRIMARY KEY(`noteId`, `tagId`), " +
                    "FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_tags_tagId` ON `note_tags` (`tagId`)")

            // Full-text index (external content = notes), then index all existing notes.
            db.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS `notes_fts` USING FTS4(" +
                    "`title` TEXT NOT NULL, `body` TEXT NOT NULL, content=`notes`)",
            )
            createNotesFtsTriggers(db)
            db.execSQL("INSERT INTO `notes_fts`(`notes_fts`) VALUES ('rebuild')")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `prompts` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                    "`template` TEXT NOT NULL, `categoryKey` TEXT NOT NULL, `isFavorite` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_prompts_categoryKey` ON `prompts` (`categoryKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_prompts_updatedAt` ON `prompts` (`updatedAt`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `prompt_categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `prompt_variables` (`promptId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`defaultValue` TEXT NOT NULL, PRIMARY KEY(`promptId`, `name`), " +
                    "FOREIGN KEY(`promptId`) REFERENCES `prompts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `prompt_versions` (`id` TEXT NOT NULL, `promptId` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, `template` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`promptId`) REFERENCES `prompts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_prompt_versions_promptId_createdAt` ON `prompt_versions` (`promptId`, `createdAt`)",
            )

            db.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS `prompts_fts` USING FTS4(" +
                    "`title` TEXT NOT NULL, `template` TEXT NOT NULL, content=`prompts`)",
            )
            createPromptsFtsTriggers(db)
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `attachments` (`id` TEXT NOT NULL, `noteId` TEXT NOT NULL, " +
                    "`kind` TEXT NOT NULL, `fileName` TEXT NOT NULL, `displayName` TEXT NOT NULL, " +
                    "`mimeType` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `sha256` TEXT NOT NULL, " +
                    "`durationMs` INTEGER, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_noteId` ON `attachments` (`noteId`)")
        }
    }

    fun createPromptsFtsTriggers(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_prompts_fts_BEFORE_UPDATE BEFORE UPDATE ON `prompts` " +
                "BEGIN DELETE FROM `prompts_fts` WHERE `docid`=OLD.`rowid`; END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_prompts_fts_BEFORE_DELETE BEFORE DELETE ON `prompts` " +
                "BEGIN DELETE FROM `prompts_fts` WHERE `docid`=OLD.`rowid`; END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_prompts_fts_AFTER_UPDATE AFTER UPDATE ON `prompts` " +
                "BEGIN INSERT INTO `prompts_fts`(`docid`, `title`, `template`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`template`); END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_prompts_fts_AFTER_INSERT AFTER INSERT ON `prompts` " +
                "BEGIN INSERT INTO `prompts_fts`(`docid`, `title`, `template`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`template`); END",
        )
    }

    /**
     * Same triggers (and names) Room generates for an external-content FTS table, so Room's
     * own trigger maintenance treats them as its own. IF NOT EXISTS makes this safe to repeat.
     */
    fun createNotesFtsTriggers(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_BEFORE_UPDATE BEFORE UPDATE ON `notes` " +
                "BEGIN DELETE FROM `notes_fts` WHERE `docid`=OLD.`rowid`; END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_BEFORE_DELETE BEFORE DELETE ON `notes` " +
                "BEGIN DELETE FROM `notes_fts` WHERE `docid`=OLD.`rowid`; END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_AFTER_UPDATE AFTER UPDATE ON `notes` " +
                "BEGIN INSERT INTO `notes_fts`(`docid`, `title`, `body`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`body`); END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_AFTER_INSERT AFTER INSERT ON `notes` " +
                "BEGIN INSERT INTO `notes_fts`(`docid`, `title`, `body`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`body`); END",
        )
    }

    /** v5: encrypted vault tables (add-only). */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `vault_items` (`id` TEXT NOT NULL, `payload` BLOB NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `vault_attachments` (`id` TEXT NOT NULL, `itemId` TEXT NOT NULL, " +
                    "`fileName` TEXT NOT NULL, `payload` BLOB NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`itemId`) REFERENCES `vault_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_attachments_itemId` ON `vault_attachments` (`itemId`)")
        }
    }

    /** v6: backup history (add-only). */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `backup_history` (`id` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                    "`kind` TEXT NOT NULL, `fileName` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, " +
                    "`summary` TEXT NOT NULL, `success` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
}

package com.nexusnotes.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema history
 *  v1 (Milestone 2): notes
 *  v2 (Milestone 4): + notes.folderId, folders, tags, note_tags, notes_fts (full-text index)
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

    val ALL = arrayOf(MIGRATION_1_2)
}

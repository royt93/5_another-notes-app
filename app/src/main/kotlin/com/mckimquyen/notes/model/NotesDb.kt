package com.mckimquyen.notes.model

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mckimquyen.notes.model.converter.DateTimeConverter
import com.mckimquyen.notes.model.converter.NoteMetadataConverter
import com.mckimquyen.notes.model.converter.NoteStatusConverter
import com.mckimquyen.notes.model.converter.NoteTypeConverter
import com.mckimquyen.notes.model.converter.PinnedStatusConverter
import com.mckimquyen.notes.model.converter.RecurrenceConverter
import com.mckimquyen.notes.model.entity.Label
import com.mckimquyen.notes.model.entity.LabelRef
import com.mckimquyen.notes.model.entity.Note
import com.mckimquyen.notes.model.entity.NoteFts
import com.mckimquyen.notes.model.entity.NoteHistory

@Database(
    entities = [
        Note::class,
        NoteFts::class,
        Label::class,
        LabelRef::class,
        NoteHistory::class,
    ],
    version = NotesDb.VERSION
)
@TypeConverters(
    DateTimeConverter::class,
    NoteTypeConverter::class,
    NoteStatusConverter::class,
    NoteMetadataConverter::class,
    PinnedStatusConverter::class,
    RecurrenceConverter::class,
)
abstract class NotesDb : RoomDatabase() {

    abstract fun notesDao(): NotesDao

    abstract fun labelsDao(): LabelsDao

    abstract fun noteHistoryDao(): NoteHistoryDao

    @Suppress("MagicNumber")
    companion object {
        const val VERSION = 8

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // By removing the sync feature, some data is now useless.
                // - Deleted notes table
                // - Synced flag on notes
                // - UUID flag on notes (unique ID across devices)
                db.apply {
                    execSQL("DROP TABLE deleted_notes")
                    execSQL(
                        """CREATE TABLE notes_temp (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        type INTEGER NOT NULL, title TEXT NOT NULL, content TEXT NOT NULL, metadata TEXT NOT NULL, 
                        added_date INTEGER NOT NULL, modified_date INTEGER NOT NULL, status INTEGER NOT NULL)"""
                    )
                    execSQL(
                        """INSERT INTO notes_temp (id, type, title, content, metadata, added_date, 
                        modified_date, status) SELECT id, type, title, content, metadata, added_date,
                        modified_date, status FROM notes"""
                    )
                    execSQL("DROP TABLE notes")
                    execSQL("ALTER TABLE notes_temp RENAME TO notes")
                }
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.apply {
                    // Add pinned column to notes table. 'unpinned' for active notes, 'can't pin' for others.
                    execSQL("ALTER TABLE notes ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                    execSQL("UPDATE notes SET pinned = 1 WHERE status == 0")

                    // Add reminder columns, all set to `null` by default.
                    execSQL("ALTER TABLE notes ADD COLUMN reminder_start INTEGER")
                    execSQL("ALTER TABLE notes ADD COLUMN reminder_recurrence TEXT")
                    execSQL("ALTER TABLE notes ADD COLUMN reminder_next INTEGER")
                    execSQL("ALTER TABLE notes ADD COLUMN reminder_count INTEGER")
                    execSQL("ALTER TABLE notes ADD COLUMN reminder_done INTEGER")

                    // Add label tables
                    execSQL("CREATE TABLE labels (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
                    execSQL(
                        """CREATE TABLE label_refs (noteId INTEGER NOT NULL, labelId INTEGER NOT NULL,
                               PRIMARY KEY(noteId, labelId),
                               FOREIGN KEY(noteId) REFERENCES notes(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                               FOREIGN KEY(labelId) REFERENCES labels(id) ON UPDATE NO ACTION ON DELETE CASCADE)"""
                    )
                    execSQL("CREATE INDEX index_labels_name ON labels (name)")
                    execSQL("CREATE INDEX IF NOT EXISTS index_label_refs_noteId ON label_refs (noteId)")
                    execSQL("CREATE INDEX IF NOT EXISTS index_label_refs_labelId ON label_refs (labelId)")
                }
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.apply {
                    // Add hidden attribute for labels
                    execSQL("ALTER TABLE labels ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN mood INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.apply {
                    execSQL("ALTER TABLE notes ADD COLUMN color INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN is_locked INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `note_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `noteId` INTEGER NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `metadata` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_history_noteId` ON `note_history` (`noteId`)")
            }
        }

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
        )
    }
}

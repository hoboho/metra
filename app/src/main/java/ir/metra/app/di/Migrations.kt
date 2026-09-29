package ir.metra.app.di

import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

/**
 * Registered Room migrations.
 *
 * The schema was redesigned from scratch, so the database file was renamed
 * (`metra.db` -> `metra2.db`) and the version restarted at 1. From here on every
 * schema change adds an entry to [ALL]. `fallbackToDestructiveMigration()` is
 * deliberately never called: a missing migration must fail loudly rather than
 * silently wipe a user's work history.
 */
object Migrations {

    /**
     * Gives every project a stable [uuid] so backups can match projects across
     * renames.
     *
     * Additive and lossless: one new column with a default, then each existing
     * row is handed a fresh random UUID. Rows keep their ids, so work records
     * and the FK to `projects.id` are untouched.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `projects` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
            val cursor = db.query("SELECT `id` FROM `projects`")
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                db.execSQL(
                    "UPDATE `projects` SET `uuid` = ? WHERE `id` = ?",
                    arrayOf<Any>(UUID.randomUUID().toString(), id),
                )
            }
            cursor.close()
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}

package ir.metra.app.di

import androidx.room.migration.Migration

/**
 * Registered Room migrations.
 *
 * The schema was redesigned from scratch, so the database file was renamed
 * (`metra.db` -> `metra2.db`) and the version restarted at 1. An install that
 * upgrades over an older build therefore opens a fresh, empty database rather
 * than crashing on an identity-hash mismatch, and no migration chain has to be
 * carried for tables that no longer exist.
 *
 * From here on every schema change adds an entry to [ALL].
 * `fallbackToDestructiveMigration()` is deliberately never called: a missing
 * migration must fail loudly rather than silently wipe a user's work history.
 */
object Migrations {

    val ALL: Array<Migration> = emptyArray()
}

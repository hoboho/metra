package ir.metra.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Project master data.
 *
 * Projects only supply defaults for new workdays. Every work record keeps its
 * own snapshot columns, so renaming a project or changing its supervisor never
 * alters a past report.
 *
 * Work records reference a project through a nullable, non-cascading foreign
 * key with `ON DELETE SET NULL`: deleting a project keeps the historical work
 * rows (and their snapshots) intact.
 */
@Entity(
    tableName = "projects",
    indices = [Index(value = ["is_active"]), Index(value = ["name"])],
)
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "employer") val employer: String = "",
    @ColumnInfo(name = "work_area") val workArea: String = "",
    @ColumnInfo(name = "default_supervisor") val defaultSupervisor: String = "",
    @ColumnInfo(name = "default_worker_count") val defaultWorkerCount: Int = 0,
    @ColumnInfo(name = "notes") val notes: String = "",
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAtEpochMilli: Long,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMilli: Long,
)

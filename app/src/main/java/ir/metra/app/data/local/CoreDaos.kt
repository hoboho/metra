package ir.metra.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import ir.metra.app.domain.model.ExpenseCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {

    @Query("SELECT * FROM user_profile WHERE id = :id LIMIT 1")
    fun observe(id: Long = UserProfileEntity.SINGLETON_ID): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = :id LIMIT 1")
    suspend fun get(id: Long = UserProfileEntity.SINGLETON_ID): UserProfileEntity?

    @Upsert
    suspend fun upsert(profile: UserProfileEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(profile: UserProfileEntity): Long
}

@Dao
interface ProjectDao {

    @Query("SELECT * FROM projects ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE is_active = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ProjectEntity?

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<ProjectEntity?>

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM projects WHERE name = :name AND id != :excludeId")
    suspend fun countByName(name: String, excludeId: Long = -1L): Int

    @Upsert
    suspend fun upsert(project: ProjectEntity): Long

    @Insert
    suspend fun insert(projects: List<ProjectEntity>): List<Long>

    @Delete
    suspend fun delete(project: ProjectEntity)

    /**
     * Soft deactivation: the project disappears from pickers but its history and
     * snapshots stay reachable.
     */
    @Query("UPDATE projects SET is_active = :active, updated_at = :updatedAt WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean, updatedAt: Long)
}

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expenses WHERE work_record_id = :workRecordId ORDER BY created_at ASC, id ASC")
    fun observeByWorkRecord(workRecordId: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE work_record_id = :workRecordId ORDER BY created_at ASC, id ASC")
    suspend fun getByWorkRecord(workRecordId: Long): List<ExpenseEntity>

    @Query("SELECT * FROM expenses ORDER BY id ASC")
    suspend fun getAll(): List<ExpenseEntity>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE work_record_id = :workRecordId")
    suspend fun totalForWorkRecord(workRecordId: Long): Long

    @Upsert
    suspend fun upsert(expense: ExpenseEntity): Long

    @Insert
    suspend fun insertAll(expenses: List<ExpenseEntity>): List<Long>

    @Delete
    suspend fun delete(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE work_record_id = :workRecordId")
    suspend fun deleteByWorkRecord(workRecordId: Long)

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()
}

package ir.metra.app.data.repository

import ir.metra.app.core.common.Clock
import ir.metra.app.core.common.MetraError
import ir.metra.app.R
import ir.metra.app.core.i18n.StringProvider
import ir.metra.app.core.common.MetraResult
import ir.metra.app.core.common.failure
import ir.metra.app.core.common.success
import ir.metra.app.data.local.ProjectDao
import ir.metra.app.data.local.WorkRecordDao
import ir.metra.app.data.mapper.toDomain
import ir.metra.app.data.mapper.toEntity
import ir.metra.app.domain.model.Project
import ir.metra.app.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepositoryImpl @Inject constructor(
    private val projectDao: ProjectDao,
    private val workRecordDao: WorkRecordDao,
    private val clock: Clock,
    private val strings: StringProvider,
) : ProjectRepository {

    override fun observeProjects(): Flow<List<Project>> =
        projectDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeActiveProjects(): Flow<List<Project>> =
        projectDao.observeActive().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getProjects(): List<Project> =
        projectDao.getAll().map { it.toDomain() }

    override suspend fun getProject(id: Long): Project? = projectDao.getById(id)?.toDomain()

    override fun observeProject(id: Long): Flow<Project?> =
        projectDao.observeById(id).map { it?.toDomain() }

    override suspend fun projectExists(id: Long): Boolean = projectDao.getById(id) != null

    override suspend fun upsert(project: Project): MetraResult<Long> = runCatching {
        val name = project.name.trim()
        if (name.isEmpty()) {
            return failure(MetraError.Validation(MetraError.Validation.Field.PROJECT_NAME, strings.string(R.string.msg_project_name_required)))
        }
        val duplicates = projectDao.countByName(name, project.id)
        if (duplicates > 0) {
            return failure(
                MetraError.Validation(
                    MetraError.Validation.Field.PROJECT_NAME,
                    strings.string(R.string.msg_project_duplicate_name),
                ),
            )
        }
        val now = clock.nowEpochMilli()
        val entity = project.toEntity().copy(
            updatedAtEpochMilli = now,
            createdAtEpochMilli = if (project.id == 0L) now else project.createdAtEpochMilli,
        )
        val id = projectDao.upsert(entity)
        // `upsert` returns -1 for an UPDATE, so fall back to the incoming id.
        success(if (id == -1L) project.id else id)
    }.fold({ it }, { failure(MetraError.Unknown(it.message ?: "project upsert")) })

    override suspend fun setActive(id: Long, active: Boolean): MetraResult<Unit> = runCatching {
        projectDao.setActive(id, active, clock.nowEpochMilli())
        success(Unit)
    }.fold({ it }, { failure(MetraError.Unknown(it.message ?: "setActive")) })

    override suspend fun delete(id: Long): MetraResult<Unit> = runCatching {
        val project = projectDao.getById(id)
            ?: return failure(MetraError.NotFound(strings.string(R.string.entity_project)))
        projectDao.delete(project)
        // Work records now have project_id = NULL but keep their snapshots, so
        // historical reports are unaffected.
        success(Unit)
    }.fold({ it }, { failure(MetraError.Unknown(it.message ?: "delete project")) })
}

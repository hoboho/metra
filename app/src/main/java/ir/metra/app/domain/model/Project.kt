package ir.metra.app.domain.model

/**
 * A project master record.
 *
 * Projects supply *defaults* for new workdays. Work records snapshot the values
 * they were created with, so editing a project never rewrites past reports.
 */
data class Project(
    val id: Long = 0L,
    val name: String,
    val employer: String = "",
    val workArea: String = "",
    val defaultSupervisor: String = "",
    val defaultWorkerCount: Int = 0,
    val notes: String = "",
    val isActive: Boolean = true,
    val createdAtEpochMilli: Long,
    val updatedAtEpochMilli: Long,
) {
    init {
        require(defaultWorkerCount >= 0) { "defaultWorkerCount must be >= 0" }
    }
}

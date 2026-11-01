package ir.metra.app.domain.model

/** Lightweight, portable aggregate row for cross-feature statistics and reports. */
data class ShootingDailyTotal(
    val workDateEpochDay: Long,
    val projectName: String,
    val totalMeters: Long,
)

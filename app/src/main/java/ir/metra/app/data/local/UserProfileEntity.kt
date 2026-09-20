package ir.metra.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The signed-in-free local profile. There is exactly one row (id = 1).
 *
 * Values here are copied into report headers; they are never used for
 * authentication and no account of any kind exists.
 */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Long = SINGLETON_ID,
    @ColumnInfo(name = "full_name") val fullName: String = "",
    @ColumnInfo(name = "company_name") val companyName: String = "",
    @ColumnInfo(name = "employee_code") val employeeCode: String = "",
    @ColumnInfo(name = "report_footer_note") val reportFooterNote: String = "",
    @ColumnInfo(name = "onboarding_completed") val onboardingCompleted: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAtEpochMilli: Long,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMilli: Long,
) {
    companion object {
        const val SINGLETON_ID = 1L
    }
}

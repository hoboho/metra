package ir.metra.app.domain.repository

import ir.metra.app.core.common.MetraResult
import ir.metra.app.domain.model.Expense
import ir.metra.app.domain.model.PaymentRule
import ir.metra.app.domain.model.Project
import ir.metra.app.domain.model.AppSettings
import ir.metra.app.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {

    fun observeProjects(): Flow<List<Project>>

    /** Active projects only — what the workday form offers. */
    fun observeActiveProjects(): Flow<List<Project>>

    suspend fun getProjects(): List<Project>

    suspend fun getProject(id: Long): Project?

    fun observeProject(id: Long): Flow<Project?>

    suspend fun projectExists(id: Long): Boolean

    /** Fails with a validation error when another project already uses [project.name]. */
    suspend fun upsert(project: Project): MetraResult<Long>

    /** Deactivation keeps history reachable; the project leaves the pickers. */
    suspend fun setActive(id: Long, active: Boolean): MetraResult<Unit>

    /**
     * Deletes a project. Work records survive: the foreign key is
     * `ON DELETE SET NULL` and every record keeps its own snapshots.
     */
    suspend fun delete(id: Long): MetraResult<Unit>
}

interface ExpenseRepository {

    fun observeExpensesFor(workRecordId: Long): Flow<List<Expense>>

    suspend fun getExpensesFor(workRecordId: Long): List<Expense>

    suspend fun add(expense: Expense): MetraResult<Long>

    suspend fun update(expense: Expense): MetraResult<Unit>

    suspend fun delete(expenseId: Long): MetraResult<Unit>
}

interface PaymentRuleRepository {

    fun observeRules(): Flow<List<PaymentRule>>

    suspend fun getRules(): List<PaymentRule>

    suspend fun getRule(id: Long): PaymentRule?

    /** The rule that applies on [epochDay]; seeds the default rule if none exists. */
    suspend fun applicableRuleOn(epochDay: Long): PaymentRule

    fun observeApplicableRuleOn(epochDay: Long): Flow<PaymentRule?>

    /**
     * Appends a new rule version. Existing rules are never edited in place, so
     * historical records keep the rate that was in force when they were logged.
     */
    suspend fun addRule(rule: PaymentRule): MetraResult<Long>

    suspend fun deleteRule(id: Long): MetraResult<Unit>
}

interface UserRepository {

    fun observeProfile(): Flow<UserProfile>

    suspend fun getProfile(): UserProfile

    suspend fun saveProfile(profile: UserProfile): MetraResult<Unit>

    suspend fun completeOnboarding(): MetraResult<Unit>
}

interface SettingsRepository {

    fun observeSettings(): Flow<AppSettings>

    suspend fun getSettings(): AppSettings

    suspend fun saveSettings(settings: AppSettings): MetraResult<Unit>

    suspend fun setUsePreviousWorkdayInfo(enabled: Boolean): MetraResult<Unit>

    suspend fun setReminder(enabled: Boolean, minuteOfDay: Int): MetraResult<Unit>
}

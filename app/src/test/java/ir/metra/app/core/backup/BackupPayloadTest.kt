package ir.metra.app.core.backup

import com.google.common.truth.Truth.assertThat
import ir.metra.app.domain.model.Expense
import ir.metra.app.domain.model.ExpenseCategory
import ir.metra.app.domain.model.PaymentRule
import ir.metra.app.domain.model.Project
import ir.metra.app.domain.model.WorkRecord
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * Backup payload serialization.
 *
 * The critical property is that a backup preserves the *snapshots*: threshold
 * and rate as they were on the day. If a restore recomputed them from today's
 * rule, every historical record would be silently re-priced.
 */
class BackupPayloadTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val rule = PaymentRule(
        id = 7,
        thresholdMeters = 400,
        ratePerMeter = 15_000L,
        effectiveFromEpochDay = 19_000L,
        createdAtEpochMilli = 1_700_000_000_000L,
    )

    private val project = Project(
        id = 3,
        name = "پروژهٔ خط انتقال آب",
        employer = "شرکت آب و فاضلاب",
        workArea = "منطقهٔ ۲",
        defaultSupervisor = "مهندس رضایی",
        defaultWorkerCount = 5,
        createdAtEpochMilli = 1_700_000_000_000L,
        updatedAtEpochMilli = 1_700_000_000_000L,
    )

    private val record = WorkRecord(
        id = 11,
        projectId = 3,
        projectName = "پروژهٔ خط انتقال آب",
        workArea = "منطقهٔ ۲",
        employer = "شرکت آب و فاضلاب",
        supervisor = "مهندس رضایی",
        workerCount = 5,
        workDateEpochDay = 19_700L,
        dailyMeters = 520,
        additionalMeters = 120,
        thresholdMetersSnapshot = 400,
        ratePerMeterSnapshot = 15_000L,
        additionalMeterPayment = 1_800_000L,
        expenseTotal = 530_000L,
        notes = "یادداشت فارسی با «گیومه» و ، ویرگول",
        workStartMinuteOfDay = 7 * 60,
        workEndMinuteOfDay = 17 * 60,
        createdAtEpochMilli = 1_700_000_000_000L,
        updatedAtEpochMilli = 1_700_000_000_000L,
    )

    private val payload = BackupPayload(
        appVersionName = "1.0.0",
        appVersionCode = 1,
        exportedAtEpochMilli = 1_700_000_000_000L,
        paymentRules = listOf(rule.toDto()),
        projects = listOf(project.toDto()),
        workRecords = listOf(record.toDto()),
        expenses = listOf(
            Expense(
                id = 21,
                workRecordId = 11,
                amount = 530_000L,
                category = ExpenseCategory.TRANSPORTATION,
                description = "هزینهٔ حمل",
                createdAtEpochMilli = 1_700_000_000_000L,
            ).toDto(workRecordIndex = 0),
        ),
    )

    @Test
    fun `payload round-trips through json`() {
        val encoded = json.encodeToString(BackupPayload.serializer(), payload)
        val decoded = json.decodeFromString(BackupPayload.serializer(), encoded)
        assertThat(decoded).isEqualTo(payload)
    }

    @Test
    fun `persian text survives the round-trip`() {
        val encoded = json.encodeToString(BackupPayload.serializer(), payload)
        val decoded = json.decodeFromString(BackupPayload.serializer(), encoded)
        assertThat(decoded.workRecords.first().notes).isEqualTo(record.notes)
        assertThat(decoded.projects.first().name).isEqualTo(project.name)
    }

    @Test
    fun `rule snapshots survive so history is never re-priced`() {
        val encoded = json.encodeToString(BackupPayload.serializer(), payload)
        val decoded = json.decodeFromString(BackupPayload.serializer(), encoded)
        val restored = decoded.workRecords.first()
        assertThat(restored.thresholdMetersSnapshot).isEqualTo(400)
        assertThat(restored.ratePerMeterSnapshot).isEqualTo(15_000L)
        assertThat(restored.additionalMeterPayment).isEqualTo(1_800_000L)
    }

    @Test
    fun `recorded company income is preserved separately from calculated income`() {
        val encoded = json.encodeToString(BackupPayload.serializer(), payload)
        val restored = json.decodeFromString(BackupPayload.serializer(), encoded).workRecords.first()
        assertThat(restored.expenseTotal).isEqualTo(530_000L)
    }

    @Test
    fun `expenses reference their work record by index`() {
        val encoded = json.encodeToString(BackupPayload.serializer(), payload)
        val decoded = json.decodeFromString(BackupPayload.serializer(), encoded)
        // Local database ids are meaningless in another install, so the link is
        // positional rather than by id.
        assertThat(decoded.expenses.first().workRecordIndex).isEqualTo(0)
        assertThat(decoded.expenses.first().category).isEqualTo(ExpenseCategory.TRANSPORTATION.name)
    }

    @Test
    fun `counts summarise the payload for the restore preview`() {
        val counts = payload.counts()
        assertThat(counts.projects).isEqualTo(1)
        assertThat(counts.workRecords).isEqualTo(1)
        assertThat(counts.expenses).isEqualTo(1)
        assertThat(counts.paymentRules).isEqualTo(1)
    }

    @Test
    fun `an older payload with unknown fields is still readable`() {
        // Forward compatibility: a future build must not refuse an older file.
        val encoded = json.encodeToString(BackupPayload.serializer(), payload)
            .replace("\"formatVersion\":1", "\"formatVersion\":1,\"someFutureField\":\"x\"")
        val decoded = json.decodeFromString(BackupPayload.serializer(), encoded)
        assertThat(decoded.workRecords).hasSize(1)
    }

    @Test
    fun `the format version is stamped explicitly`() {
        assertThat(payload.formatVersion).isEqualTo(BackupPayload.CURRENT_FORMAT_VERSION)
    }
}

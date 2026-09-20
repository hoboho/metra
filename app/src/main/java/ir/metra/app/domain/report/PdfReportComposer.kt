package ir.metra.app.domain.report

import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.core.format.DateFormatter
import ir.metra.app.core.format.NumberFormatter
import ir.metra.app.core.pdf.PdfAlign
import ir.metra.app.core.pdf.PdfBlock
import ir.metra.app.core.pdf.PdfColumn
import ir.metra.app.core.pdf.PdfDocumentSpec
import ir.metra.app.core.pdf.PdfTextStyle
import ir.metra.app.domain.model.ExpenseCategory
import ir.metra.app.domain.model.ReportType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns [ReportData] into the printable document model.
 *
 * Everything user-visible here is Persian. Numbers are rendered with Persian
 * digits because the report is read on paper or in a Persian UI; the CSV export
 * deliberately does the opposite so spreadsheets stay machine-readable.
 */
@Singleton
class PdfReportComposer @Inject constructor(
    private val dateFormatter: DateFormatter,
    private val numberFormatter: NumberFormatter,
) {

    fun compose(data: ReportData, includeDailyTable: Boolean = true, includeNotes: Boolean = true): PdfDocumentSpec {
        val blocks = ArrayList<PdfBlock>()

        blocks += PdfBlock.Paragraph(data.title, PdfTextStyle.Title, spaceAfterPt = 2f)
        blocks += PdfBlock.Paragraph(data.subtitle, PdfTextStyle.Subtitle, spaceAfterPt = 10f)

        // ------------------------------------------------------- report header
        val headerEntries = linkedMapOf(
            ReportBuilder.LABEL_NAME to data.profile.fullName.ifBlank { "—" },
            ReportBuilder.LABEL_COMPANY to data.profile.companyName.ifBlank { "—" },
        )
        data.profile.employeeCode.takeIf { it.isNotBlank() }?.let {
            headerEntries[ReportBuilder.LABEL_EMPLOYEE_CODE] = it
        }
        headerEntries[ReportBuilder.LABEL_PERIOD] =
            dateFormatter.formatRange(data.startEpochDay, data.endEpochDay)
        headerEntries[ReportBuilder.LABEL_GENERATED] =
            dateFormatter.formatLong(JalaliCalendar.today().toEpochDay())

        blocks += PdfBlock.KeyValueList(headerEntries.entries.map { it.key to it.value })
        blocks += PdfBlock.Divider()

        // ------------------------------------------------------------- summary
        blocks += PdfBlock.Paragraph("خلاصهٔ عملکرد", PdfTextStyle.SectionHeader, spaceAfterPt = 6f)
        val totals = data.totals
        val summaryEntries = linkedMapOf(
            "تعداد روزهای کاری" to numberFormatter.formatPersian(totals.workdays.toLong()),
            "مجموع کارکرد" to numberFormatter.formatMetersValue(totals.totalMeters) + " متر",
            "میانگین کارکرد روزانه" to numberFormatter.formatAverage(totals.averageMetersPerDay) + " متر",
            "مجموع متراژ مازاد" to numberFormatter.formatMetersValue(totals.totalAdditionalMeters) + " متر",
            "مبلغ متراژ مازاد (محاسبهٔ مترا)" to numberFormatter.formatToman(totals.totalAdditionalPayment),
        )
        blocks += PdfBlock.KeyValueList(summaryEntries.entries.map { it.key to it.value })

        blocks += PdfBlock.Spacer(6f)
        blocks += PdfBlock.Paragraph(
            "مطالبات از شرکت",
            PdfTextStyle.SectionHeader,
            spaceAfterPt = 6f,
        )
        // Expenses are reimbursable, so they are owed *to* the worker rather than
        // deducted. Company salary is not tracked by Metra at all.
        val recordedEntries = linkedMapOf(
            "جمع مبلغ متراژ اضافه" to numberFormatter.formatToman(totals.totalAdditionalPayment),
            "هزینه‌های قابل مطالبه" to numberFormatter.formatToman(totals.totalExpenses),
            "جمع مطالبات" to numberFormatter.formatToman(totals.totalReceivable),
        )
        blocks += PdfBlock.KeyValueList(
            entries = recordedEntries.entries.map { it.key to it.value },
            emphasisedLabels = setOf("جمع مطالبات"),
        )

        // ------------------------------------------------ project / expenses
        if (data.projectBreakdown.isNotEmpty()) {
            blocks += PdfBlock.Divider()
            blocks += PdfBlock.Paragraph("تفکیک بر اساس پروژه", PdfTextStyle.SectionHeader, spaceAfterPt = 6f)
            blocks += PdfBlock.Table(
                columns = listOf(
                    PdfColumn("پروژه", 2.2f),
                    PdfColumn("روز", 0.7f),
                    PdfColumn("کارکرد (متر)", 1.1f),
                    PdfColumn("مازاد (متر)", 1.1f),
                    PdfColumn("مبلغ مازاد", 1.3f),
                    PdfColumn("هزینه", 1.2f),
                ),
                header = listOf("پروژه", "روز", "کارکرد", "مازاد", "مبلغ مازاد", "هزینه"),
                rows = data.projectBreakdown.map { row ->
                    listOf(
                        row.projectName.ifBlank { "—" },
                        numberFormatter.formatPersian(row.workdays.toLong()),
                        numberFormatter.formatPersian(row.totalMeters.toLong()),
                        numberFormatter.formatPersian(row.totalAdditionalMeters.toLong()),
                        numberFormatter.formatPersian(row.totalAdditionalPayment),
                        numberFormatter.formatPersian(row.totalExpenses),
                    )
                },
                aligns = listOf(PdfAlign.START, PdfAlign.CENTER, PdfAlign.CENTER, PdfAlign.CENTER, PdfAlign.CENTER, PdfAlign.CENTER),
            )
        }

        if (data.expensesByCategory.isNotEmpty()) {
            blocks += PdfBlock.Spacer(8f)
            blocks += PdfBlock.Paragraph("تفکیک هزینه‌ها", PdfTextStyle.SectionHeader, spaceAfterPt = 6f)
            blocks += PdfBlock.KeyValueList(
                entries = data.expensesByCategory.entries
                    .sortedByDescending { it.value }
                    .map { (category, amount) ->
                        ReportBuilder.categoryLabel(category) to numberFormatter.formatToman(amount)
                    },
            )
        }

        // -------------------------------------------------------- daily table
        if (includeDailyTable && data.records.isNotEmpty()) {
            blocks += PdfBlock.Divider()
            blocks += PdfBlock.Paragraph("جزئیات روزانه", PdfTextStyle.SectionHeader, spaceAfterPt = 6f)
            blocks += PdfBlock.Table(
                columns = listOf(
                    PdfColumn("تاریخ", 1.15f, minWidthPt = 52f),
                    PdfColumn("پروژه", 1.5f, minWidthPt = 44f),
                    PdfColumn("محدوده", 1.1f, minWidthPt = 34f),
                    PdfColumn("کارفرما", 1.1f, minWidthPt = 34f),
                    PdfColumn("ناظر", 0.9f, minWidthPt = 30f),
                    PdfColumn("کارگر", 0.6f, minWidthPt = 22f),
                    PdfColumn("کارکرد", 0.8f, minWidthPt = 28f),
                    PdfColumn("مازاد", 0.8f, minWidthPt = 28f),
                    PdfColumn("مبلغ مازاد", 1.1f, minWidthPt = 38f),
                    PdfColumn("هزینه", 1.0f, minWidthPt = 34f),
                ),
                header = listOf(
                    "تاریخ", "پروژه", "محدوده", "کارفرما", "ناظر",
                    "کارگر", "کارکرد", "مازاد", "مبلغ مازاد", "هزینه",
                ),
                rows = data.records.map { record ->
                    listOf(
                        dateFormatter.format(record.workDateEpochDay),
                        record.projectName.ifBlank { "—" },
                        record.workArea.ifBlank { "—" },
                        record.employer.ifBlank { "—" },
                        record.supervisor.ifBlank { "—" },
                        numberFormatter.formatPersian(record.workerCount.toLong()),
                        numberFormatter.formatPersian(record.dailyMeters.toLong()),
                        numberFormatter.formatPersian(record.additionalMeters.toLong()),
                        numberFormatter.formatPersian(record.additionalMeterPayment),
                        numberFormatter.formatPersian(record.expenseTotal),
                    )
                },
                aligns = listOf(
                    PdfAlign.START, PdfAlign.START, PdfAlign.START, PdfAlign.START, PdfAlign.START,
                    PdfAlign.END, PdfAlign.END, PdfAlign.END, PdfAlign.END, PdfAlign.END,
                ),
            )
        }

        // ------------------------------------------------------------- notes
        if (includeNotes) {
            val notes = data.records.mapNotNull { record ->
                record.notes.trim().takeIf { it.isNotEmpty() }?.let { note ->
                    dateFormatter.format(record.workDateEpochDay) to note
                }
            }
            if (notes.isNotEmpty()) {
                blocks += PdfBlock.Divider()
                blocks += PdfBlock.Paragraph("یادداشت‌ها", PdfTextStyle.SectionHeader, spaceAfterPt = 6f)
                for ((date, note) in notes) {
                    blocks += PdfBlock.Paragraph("$date: $note", PdfTextStyle.Small, spaceAfterPt = 3f)
                }
            }
        }

        data.profile.reportFooterNote.takeIf { it.isNotBlank() }?.let { footer ->
            blocks += PdfBlock.Spacer(8f)
            blocks += PdfBlock.Paragraph(footer, PdfTextStyle.Small, spaceAfterPt = 4f)
        }

        return PdfDocumentSpec(
            title = data.title,
            subtitle = data.subtitle,
            blocks = blocks,
            footerText = "Generated by Metra",
            pageNumbers = true,
        )
    }
}

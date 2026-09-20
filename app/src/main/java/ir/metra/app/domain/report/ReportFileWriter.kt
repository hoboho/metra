package ir.metra.app.domain.report

import ir.metra.app.core.common.Clock
import ir.metra.app.core.common.MetraError
import ir.metra.app.core.common.MetraResult
import ir.metra.app.core.common.failure
import ir.metra.app.core.common.success
import ir.metra.app.core.format.DateFormatter
import ir.metra.app.core.format.NumberFormatter
import ir.metra.app.core.backup.ReportDirectoryProvider
import ir.metra.app.core.pdf.PdfReportEngine
import ir.metra.app.core.pdf.PdfRenderException
import ir.metra.app.domain.model.ReportType
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes reports to files and prepares them for sharing.
 *
 * Files land in the app's cache under `reports/`, which is exactly the path
 * exposed by the `FileProvider`, so the same [File] can be handed straight to an
 * `ACTION_SEND` intent.
 */
@Singleton
class ReportFileWriter @Inject constructor(
    private val reportBuilder: ReportBuilder,
    private val composer: PdfReportComposer,
    private val pdfEngine: PdfReportEngine,
    private val reportDirectoryProvider: ReportDirectoryProvider,
    private val dateFormatter: DateFormatter,
    private val numberFormatter: NumberFormatter,
    private val clock: Clock,
) {

    /** Generates a PDF report and returns the file to share. */
    suspend fun writePdf(request: ReportRequest, includeDailyTable: Boolean = true, includeNotes: Boolean = true):
        MetraResult<File> = runCatching {
        val data = reportBuilder.build(request).getOrThrow()
        val spec = composer.compose(data, includeDailyTable = includeDailyTable, includeNotes = includeNotes)
        val file = targetFile(prefixFor(request.type), "pdf")
        try {
            pdfEngine.render(spec, file)
        } catch (renderError: PdfRenderException) {
            return failure(MetraError.PdfGeneration(renderError.message ?: "unknown"))
        }
        success(file)
    }.fold({ it }, { failure(MetraError.PdfGeneration(it.message ?: "unknown")) })

    /** Writes the same report data as a UTF-8 CSV with Persian headers. */
    suspend fun writeCsv(request: ReportRequest): MetraResult<File> = runCatching {
        val data = reportBuilder.build(request).getOrThrow()
        val file = targetFile(prefixFor(request.type), "csv")
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            // The byte-order mark is what makes Excel detect UTF-8 correctly.
            writer.write(BYTE_ORDER_MARK)
            writer.write(CSV_HEADER.joinToString(CSV_SEPARATOR))
            writer.newLine()
            for (record in data.records) {
                writer.write(
                    listOf(
                        dateFormatter.format(record.workDateEpochDay, persianDigits = false),
                        record.projectName,
                        record.workArea,
                        record.employer,
                        record.supervisor,
                        record.workerCount.toString(),
                        record.dailyMeters.toString(),
                        record.additionalMeters.toString(),
                        record.ratePerMeterSnapshot.toString(),
                        record.additionalMeterPayment.toString(),
                        record.expenseTotal.toString(),
                        record.notes,
                    ).let { CsvEscaping.row(it, CSV_SEPARATOR) },
                )
                writer.newLine()
            }
        }
        success(file)
    }.fold({ it }, { failure(MetraError.Unknown(it.message ?: "csv")) })

    private fun prefixFor(type: ReportType): String = when (type) {
        ReportType.DAILY -> "metra-daily"
        ReportType.MONTHLY -> "metra-monthly"
        ReportType.RANGE -> "metra-range"
        ReportType.PROJECT -> "metra-project"
        ReportType.YEARLY -> "metra-yearly"
    }

    private fun targetFile(prefix: String, extension: String): File {
        val stamp = dateFormatter.format(
            clock.nowEpochMilli() / MILLIS_PER_DAY,
            persianDigits = false,
        ).replace('/', '-')
        return File(reportDirectoryProvider.reportsDirectory(), "$prefix-$stamp-$extension")
            .also { it.parentFile?.mkdirs() }
    }

    companion object {
        private const val MILLIS_PER_DAY = 86_400_000L
        const val CSV_SEPARATOR = ","

        /** UTF-8 byte-order mark: without it Excel misreads Persian text. */
        const val BYTE_ORDER_MARK = "\uFEFF"

        /** Persian column headers, as required for Excel-friendly output. */
        val CSV_HEADER = listOf(
            "تاریخ",
            "پروژه",
            "محدوده",
            "کارفرما",
            "ناظر",
            "تعداد کارگر",
            "کارکرد",
            "متراژ اضافه",
            "نرخ",
            "مبلغ متراژ اضافه",
            "هزینه قابل مطالبه",
            "جمع دریافتنی از شرکت",
            "توضیحات",
        )

    }
}

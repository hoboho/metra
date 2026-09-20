package ir.metra.app.core.pdf

import android.content.Context
import android.graphics.Canvas
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withTranslation
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Renders a [PdfDocumentSpec] to an A4 PDF.
 *
 * Two decisions matter here:
 *
 * 1. **RTL text** is shaped by [StaticLayout] rather than drawn glyph by glyph.
 *    Android's layout engine runs HarfBuzz shaping and the Unicode bidi
 *    algorithm, so Persian words join correctly and mixed Persian/ASCII runs
 *    ("۵۲۰ متر / 120 m") come out in the right order. Drawing raw strings with
 *    `Canvas.drawText` would not shape Arabic script at all.
 * 2. **The font is embedded** from `assets/fonts` (Vazirmatn, SIL Open Font
 *    License) so the PDF looks identical on any reader, including a desktop
 *    viewer without Persian fonts installed.
 *
 * The engine is the only Android-aware part of the reporting pipeline; the
 * document model and the report builders are plain Kotlin.
 */
@Singleton
class PdfReportEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Draws [spec] and writes the result to [outputFile].
     *
     * @throws PdfRenderException when the font cannot be loaded or the file
     *   cannot be written; callers translate that into a Persian message.
     */
    fun render(spec: PdfDocumentSpec, outputFile: File): File {
        val fonts = loadFonts()
        val document = PdfDocument()
        try {
            val painter = Painter(document, fonts, spec)
            while (painter.hasMoreContent()) {
                painter.renderNextPage()
            }
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { stream -> document.writeTo(stream) }
            return outputFile
        } catch (throwable: Throwable) {
            if (throwable is PdfRenderException) throw throwable
            throw PdfRenderException(
                throwable.message ?: "PDF rendering failed: $throwable",
                throwable,
            )
        } finally {
            document.close()
        }
    }

    // ------------------------------------------------------------------ fonts

    private data class Fonts(val regular: Typeface, val medium: Typeface, val bold: Typeface)

    private fun loadFonts(): Fonts {
        return try {
            Fonts(
                regular = typefaceFromAsset("fonts/Vazirmatn-Regular.ttf"),
                medium = typefaceFromAsset("fonts/Vazirmatn-Medium.ttf"),
                bold = typefaceFromAsset("fonts/Vazirmatn-Bold.ttf"),
            )
        } catch (throwable: Throwable) {
            // Fall back to the platform font: a report with a substitute font is
            // still far more useful than no report at all.
            Fonts(
                regular = Typeface.SANS_SERIF,
                medium = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD),
                bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD),
            )
        }
    }

    private fun typefaceFromAsset(path: String): Typeface {
        context.assets.open(path).use { input ->
            val temp = File.createTempFile("metra-font", ".ttf", context.cacheDir)
            temp.outputStream().use { output -> input.copyTo(output) }
            val face = Typeface.createFromFile(temp)
            temp.delete()
            return face
        }
    }

    // ---------------------------------------------------------------- painter

    /**
     * A page-at-a-time painter.
     *
     * Content is consumed sequentially through [cursor]; when a block would not
     * fit, the page is finished and a new one started. Tables split across pages
     * and repeat their header row.
     */
    private inner class Painter(
        private val document: PdfDocument,
        private val fonts: Fonts,
        private val spec: PdfDocumentSpec,
    ) {
        private var blockIndex = 0
        private var pageNumber = 0
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var cursorY = 0f

        /** Row index of the next row to draw for the table currently in flight. */
        private var pendingTableRow = 0

        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = "#D5DBE3".toColorInt()
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#EDF4F0".toColorInt() }

        /** Soft green band behind the total rows. */
        private val emphasisedBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = "#DCEDE5".toColorInt()
        }
        private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }

        fun hasMoreContent(): Boolean = blockIndex < spec.blocks.size || page != null

        fun renderNextPage() {
            pageNumber += 1
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val newPage = document.startPage(pageInfo)
            page = newPage
            canvas = newPage.canvas
            cursorY = MARGIN_TOP
            drawHeader()
            while (blockIndex < spec.blocks.size) {
                val consumed = drawBlock(spec.blocks[blockIndex])
                if (!consumed) break // needs a fresh page
                blockIndex += 1
                pendingTableRow = 0
            }
            drawFooter()
            document.finishPage(newPage)
            if (blockIndex >= spec.blocks.size) {
                page = null
                canvas = null
            }
        }

        // -------------------------------------------------------- decorations

        private fun drawHeader() {
            val brand = textPaint(PdfWeight.BOLD, 15f)
            val brandText = "METRA"
            val brandWidth = brand.measureText(brandText)
            // Latin brand sits on the left; the Persian app name on the right.
            canvas?.drawText(brandText, MARGIN_LEFT, cursorY + brand.textSize, brand)
            val persianBrand = textPaint(PdfWeight.BOLD, 13f)
            canvas?.drawText(
                "مترا",
                CONTENT_RIGHT - persianBrand.measureText("مترا"),
                cursorY + persianBrand.textSize,
                persianBrand,
            )
            cursorY += max(brand.textSize, persianBrand.textSize) + 6f
            canvas?.drawLine(MARGIN_LEFT, cursorY, CONTENT_RIGHT, cursorY, accentLinePaint())
            cursorY += 10f
        }

        private fun accentLinePaint(): Paint = Paint(linePaint).apply {
            color = ACCENT
            strokeWidth = 1.4f
        }

        private fun drawFooter() {
            val paint = textPaint(PdfWeight.REGULAR, 8.5f).apply { color = MUTED }
            val footerY = PAGE_HEIGHT - MARGIN_BOTTOM + 18f
            canvas?.drawLine(MARGIN_LEFT, footerY - 12f, CONTENT_RIGHT, footerY - 12f, linePaint)
            canvas?.drawText(
                spec.footerText,
                CONTENT_RIGHT - paint.measureText(spec.footerText),
                footerY,
                paint,
            )
            if (spec.pageNumbers) {
                val label = "$pageNumber"
                canvas?.drawText(label, MARGIN_LEFT, footerY, paint)
            }
        }

        // ------------------------------------------------------------- blocks

        /** @return true when the block was fully drawn, false when a new page is needed. */
        private fun drawBlock(block: PdfBlock): Boolean {
            val bottomLimit = PAGE_HEIGHT - MARGIN_BOTTOM
            return when (block) {
                is PdfBlock.Spacer -> {
                    if (cursorY + block.heightPt > bottomLimit) return false
                    cursorY += block.heightPt
                    true
                }

                is PdfBlock.Divider -> {
                    if (cursorY + block.spaceBeforePt + block.spaceAfterPt + 2f > bottomLimit) return false
                    cursorY += block.spaceBeforePt
                    canvas?.drawLine(MARGIN_LEFT, cursorY, CONTENT_RIGHT, cursorY, linePaint)
                    cursorY += block.spaceAfterPt
                    true
                }

                is PdfBlock.Paragraph -> {
                    val layout = layoutFor(block.text, block.style, CONTENT_WIDTH, block.rtl)
                    val height = layout.height + block.spaceBeforePt + block.spaceAfterPt
                    if (cursorY + height > bottomLimit) return false
                    cursorY += block.spaceBeforePt
                    drawLayout(layout, MARGIN_LEFT, cursorY, block.align, CONTENT_WIDTH)
                    cursorY += layout.height + block.spaceAfterPt
                    true
                }

                is PdfBlock.KeyValueList -> drawKeyValueList(block, bottomLimit)
                is PdfBlock.Table -> drawTable(block, bottomLimit)
            }
        }

        private fun drawKeyValueList(block: PdfBlock.KeyValueList, bottomLimit: Float): Boolean {
            val labelWidth = CONTENT_WIDTH * 0.45f
            val valueWidth = CONTENT_WIDTH - labelWidth
            val paint = textPaint(PdfWeight.REGULAR, 10f)
            val rowHeight = paint.textSize * 1.75f

            for ((label, value) in block.entries) {
                if (cursorY + rowHeight > bottomLimit) return false
                val emphasised = label in block.emphasisedLabels
                val labelPaint = textPaint(
                    if (emphasised) PdfWeight.BOLD else block.labelWeight,
                    if (emphasised) 10.5f else 10f,
                ).apply { if (emphasised) color = ACCENT_DARK }
                val valuePaint = textPaint(
                    if (emphasised) PdfWeight.BOLD else PdfWeight.REGULAR,
                    if (emphasised) 10.5f else 10f,
                )
                val baseline = cursorY + paint.textSize

                if (emphasised) {
                    canvas?.drawRect(
                        MARGIN_LEFT - 4f, cursorY - 4f,
                        CONTENT_RIGHT + 4f, cursorY + rowHeight - 4f,
                        emphasisedBandPaint,
                    )
                }

                // RTL: label hugs the right edge, value sits to its left.
                val labelLayout = layoutFor(label, PdfTextStyle.Body, labelWidth, rtl = true)
                canvas?.withTranslation(CONTENT_RIGHT - labelWidth, cursorY - 2f) {
                    labelLayout.draw(this)
                }
                valuePaint.textAlign = Paint.Align.RIGHT
                val valueRight = CONTENT_RIGHT - labelWidth - 12f
                val clippedValue = ellipsize(value, valuePaint, valueWidth - 8f)
                canvas?.drawText(clippedValue, valueRight, baseline, valuePaint)
                valuePaint.textAlign = Paint.Align.LEFT

                cursorY += rowHeight
                canvas?.drawLine(MARGIN_LEFT, cursorY - 4f, CONTENT_RIGHT, cursorY - 4f, linePaint)
            }
            return true
        }

        private fun drawTable(block: PdfBlock.Table, bottomLimit: Float): Boolean {
            val widths = resolveWidths(block.columns)
            val headerPaint = textPaint(PdfWeight.BOLD, 8.6f)
            val cellPaint = textPaint(PdfWeight.REGULAR, 8.6f)
            val headerHeight = headerPaint.textSize * 2.1f
            val rowHeight = cellPaint.textSize * 1.9f

            block.caption?.let { caption ->
                val layout = layoutFor(caption, PdfTextStyle.SectionHeader, CONTENT_WIDTH, rtl = block.rtl)
                if (cursorY + layout.height > bottomLimit) return false
                drawLayout(layout, MARGIN_LEFT, cursorY, PdfAlign.START, CONTENT_WIDTH)
                cursorY += layout.height + 4f
            }

            // Header row (repeated on every page the table spans).
            if (pendingTableRow == 0 || cursorY + headerHeight + rowHeight > bottomLimit) {
                if (cursorY + headerHeight > bottomLimit) return false
                canvas?.drawRect(MARGIN_LEFT, cursorY - 2f, CONTENT_RIGHT, cursorY + headerHeight - 2f, fillPaint)
                drawRow(block.header, widths, block.aligns, headerPaint, headerHeight, header = true)
                cursorY += headerHeight
            }

            while (pendingTableRow < block.rows.size) {
                if (cursorY + rowHeight > bottomLimit) return false
                val rowIndex = pendingTableRow
                if (rowIndex % 2 == 1) {
                    canvas?.drawRect(
                        MARGIN_LEFT,
                        cursorY - 1f,
                        CONTENT_RIGHT,
                        cursorY + rowHeight - 1f,
                        Paint(fillPaint).apply { color = "#F6FAF8".toColorInt() },
                    )
                }
                drawRow(block.rows[rowIndex], widths, block.aligns, cellPaint, rowHeight, header = false)
                canvas?.drawLine(MARGIN_LEFT, cursorY + rowHeight - 1f, CONTENT_RIGHT, cursorY + rowHeight - 1f, linePaint)
                cursorY += rowHeight
                pendingTableRow += 1
            }
            return true
        }

        /**
         * Draws one table row.
         *
         * Columns are laid out right-to-left: the first column occupies the
         * rightmost slot, which is what a Persian reader expects.
         */
        private fun drawRow(
            cells: List<String>,
            widths: List<Float>,
            aligns: List<PdfAlign>,
            paint: TextPaint,
            rowHeight: Float,
            header: Boolean,
        ) {
            var x = CONTENT_RIGHT
            for (index in cells.indices) {
                val width = widths.getOrElse(index) { CONTENT_WIDTH / cells.size }
                val align = aligns.getOrElse(index) { PdfAlign.START }
                val cellLeft = x - width
                val text = cells[index]
                val available = width - CELL_PADDING * 2
                val clipped = ellipsize(text, paint, available)
                val textWidth = paint.measureText(clipped)
                val textX = when (align) {
                    // In an RTL row "START" means the right side of the cell.
                    PdfAlign.START -> cellLeft + CELL_PADDING
                    PdfAlign.END -> x - CELL_PADDING - textWidth
                    PdfAlign.CENTER -> cellLeft + (width - textWidth) / 2f
                }
                canvas?.drawText(clipped, textX, cursorY + rowHeight * 0.72f, paint)
                if (header) {
                    canvas?.drawLine(cellLeft, cursorY - 2f, cellLeft, cursorY + rowHeight - 2f, linePaint)
                }
                x = cellLeft
            }
        }

        // ------------------------------------------------------------ helpers

        /** Scales column weights to the content width, honouring minimum widths. */
        private fun resolveWidths(columns: List<PdfColumn>): List<Float> {
            if (columns.isEmpty()) return emptyList()
            val totalWeight = columns.sumOf { it.weight.toDouble() }.toFloat()
            if (totalWeight <= 0f) return List(columns.size) { CONTENT_WIDTH / columns.size }
            val scaled = columns.map { column ->
                max(column.minWidthPt, CONTENT_WIDTH * (column.weight / totalWeight))
            }
            val totalScaled = scaled.sum()
            // Re-normalise so the row always spans exactly the content width.
            return scaled.map { it * (CONTENT_WIDTH / totalScaled) }
        }

        private fun textPaint(weight: PdfWeight, sizePt: Float): TextPaint {
            val face = when (weight) {
                PdfWeight.REGULAR -> fonts.regular
                PdfWeight.MEDIUM -> fonts.medium
                PdfWeight.BOLD -> fonts.bold
            }
            return TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                typeface = face
                textSize = sizePt * DENSITY
                color = if (weight == PdfWeight.BOLD) TEXT_STRONG else TEXT_PRIMARY
                isAntiAlias = true
            }
        }

        private fun stylePaint(style: PdfTextStyle): Pair<TextPaint, Float> {
            val weight: PdfWeight
            val size: Float
            val color: Int
            val spacing: Float
            when (style) {
                PdfTextStyle.Title -> {
                    weight = PdfWeight.BOLD; size = 17f; color = TEXT_STRONG; spacing = 8f
                }

                PdfTextStyle.Subtitle -> {
                    weight = PdfWeight.MEDIUM; size = 11.5f; color = MUTED; spacing = 6f
                }

                PdfTextStyle.SectionHeader -> {
                    weight = PdfWeight.BOLD; size = 12f; color = ACCENT_DARK; spacing = 7f
                }

                PdfTextStyle.Body -> {
                    weight = PdfWeight.REGULAR; size = 10f; color = TEXT_PRIMARY; spacing = 4f
                }

                PdfTextStyle.Small -> {
                    weight = PdfWeight.REGULAR; size = 8.6f; color = MUTED; spacing = 3f
                }

                PdfTextStyle.TableHeader -> {
                    weight = PdfWeight.BOLD; size = 8.6f; color = TEXT_STRONG; spacing = 3f
                }

                PdfTextStyle.TableCell -> {
                    weight = PdfWeight.REGULAR; size = 8.6f; color = TEXT_PRIMARY; spacing = 3f
                }
            }
            return textPaint(weight, size).apply { this.color = color } to spacing
        }

        /** Builds a shaped, bidi-correct layout for [text]. */
        private fun layoutFor(text: String, style: PdfTextStyle, width: Float, rtl: Boolean): StaticLayout {
            val (paint, _) = stylePaint(style)
            val alignment = Layout.Alignment.ALIGN_NORMAL
            return StaticLayout.Builder
                .obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
                .setAlignment(alignment)
                .setTextDirection(android.text.TextDirectionHeuristics.FIRSTSTRONG_RTL)
                .setLineSpacing(2f, 1f)
                .setIncludePad(false)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
        }

        private fun drawLayout(
            layout: StaticLayout,
            left: Float,
            top: Float,
            align: PdfAlign,
            width: Float,
        ) {
            val target = canvas ?: return
            target.withTranslation(left, top) { layout.draw(this) }
        }

        private fun ellipsize(text: String, paint: TextPaint, maxWidth: Float): String {
            if (maxWidth <= 0f) return ""
            if (paint.measureText(text) <= maxWidth) return text
            return TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString()
        }
    }

    companion object {
        // A4 in points (1/72 inch).
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        private const val MARGIN_LEFT = 34f
        private const val MARGIN_TOP = 40f
        private const val MARGIN_BOTTOM = 46f
        private const val CONTENT_RIGHT = PAGE_WIDTH - MARGIN_LEFT
        private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN_LEFT * 2
        private const val CELL_PADDING = 4f

        /** PDF is resolution-independent: 1pt maps to 1 unit in PdfDocument. */
        private const val DENSITY = 1f

        private val TEXT_PRIMARY = "#1B2430".toColorInt()
        private val TEXT_STRONG = "#0B1220".toColorInt()
        private val MUTED = "#5C6875".toColorInt()
        private val ACCENT = "#0F4C3A".toColorInt()
        private val ACCENT_DARK = "#0B3A2C".toColorInt()
    }
}

/** Raised when a PDF cannot be produced. */
class PdfRenderException(message: String, cause: Throwable? = null) : Exception(message, cause)

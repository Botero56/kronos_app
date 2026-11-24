package com.example.kronos_.report

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.kronos_.logic.AttendanceRow
import com.example.kronos_.logic.AttendanceRules
import com.example.kronos_.logic.Formats
import java.io.File
import java.time.LocalDate

enum class ReportFormat(val extension: String, val mimeType: String, val label: String) {
    PDF("pdf", "application/pdf", "PDF"),
    EXCEL("csv", "text/csv", "Excel (CSV)"),
}

/** Datos del reporte: filtros elegidos y filas de asistencia. */
data class AttendanceReport(
    val from: LocalDate,
    val to: LocalDate,
    val sedeLabel: String,
    val employeeLabel: String,
    val rows: List<AttendanceRow>,
) {
    val stats = AttendanceRules.stats(rows)
    val filters: String get() = "${Formats.dateRange(from, to)} · $sedeLabel · $employeeLabel"
    val fileBaseName: String get() = "Asistencia_${from}_$to"
}

data class GeneratedReport(val report: AttendanceReport, val format: ReportFormat, val file: File)

object ReportWriter {

    private const val SEPARATOR = ";"
    private val UTF8_MARK = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    /** CSV separado por punto y coma, como lo abre Excel en español. */
    fun toCsv(report: AttendanceReport): String = buildString {
        appendLine(listOf("Fecha", "Empleado", "Sede", "Entrada", "Salida", "Estado").joinToString(SEPARATOR))
        report.rows.forEach { row ->
            val cells = listOf(
                row.date.toString(),
                row.employeeName,
                row.sedeName,
                Formats.time(row.record?.checkIn),
                Formats.time(row.record?.checkOut),
                row.status.label,
            )
            appendLine(cells.joinToString(SEPARATOR) { "\"" + it.replace("\"", "\"\"") + "\"" })
        }
    }

    fun write(report: AttendanceReport, format: ReportFormat, folder: File): File {
        folder.mkdirs()
        val file = File(folder, "${report.fileBaseName}.${format.extension}")
        when (format) {
            // Los tres bytes iniciales (marca UTF-8) hacen que Excel muestre bien las tildes.
            ReportFormat.EXCEL -> file.writeBytes(UTF8_MARK + toCsv(report).toByteArray(Charsets.UTF_8))
            ReportFormat.PDF -> writePdf(report, file)
        }
        return file
    }

    private fun writePdf(report: AttendanceReport, file: File) {
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val columns = floatArrayOf(margin, 120f, 300f, 370f, 440f)
        val text = Paint().apply { textSize = 10f }
        val bold = Paint(text).apply { typeface = Typeface.DEFAULT_BOLD }
        val title = Paint(bold).apply { textSize = 16f }

        val document = PdfDocument()
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var y = margin

        fun header() {
            listOf("Fecha", "Empleado", "Entrada", "Salida", "Estado").forEachIndexed { i, label ->
                page.canvas.drawText(label, columns[i], y, bold)
            }
            y += 16f
        }

        page.canvas.drawText("KRONOS · Reporte de asistencia", margin, y, title)
        y += 20f
        page.canvas.drawText(report.filters, margin, y, text)
        y += 16f
        val stats = report.stats
        val punctuality = stats.punctuality?.let { "$it%" } ?: "—"
        page.canvas.drawText(
            "Presentes: ${stats.present}   Tarde: ${stats.late}   Ausentes: ${stats.absent}   Puntualidad: $punctuality",
            margin, y, text,
        )
        y += 28f
        header()

        for (row in report.rows) {
            if (y > pageHeight - margin) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                y = margin
                header()
            }
            val cells = listOf(
                Formats.shortDate(row.date),
                row.employeeName,
                Formats.time(row.record?.checkIn),
                Formats.time(row.record?.checkOut),
                row.status.label,
            )
            cells.forEachIndexed { i, value -> page.canvas.drawText(value, columns[i], y, text) }
            y += 14f
        }
        document.finishPage(page)
        file.outputStream().use { document.writeTo(it) }
        document.close()
    }
}

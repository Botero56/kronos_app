package com.example.kronos_.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.kronos_.MainActivity
import com.example.kronos_.R
import com.example.kronos_.data.KronosRepository
import com.example.kronos_.data.Session
import com.example.kronos_.data.UserProfile
import com.example.kronos_.logic.AttendanceRules
import com.example.kronos_.logic.Formats
import com.example.kronos_.report.AttendanceReport
import com.example.kronos_.report.GeneratedReport
import com.example.kronos_.report.ReportFormat
import com.example.kronos_.report.ReportWriter
import com.example.kronos_.ui.chooseOption
import com.example.kronos_.ui.color
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import com.example.kronos_.R.id as v
import com.example.kronos_.R.layout as s

private const val WHATSAPP_PACKAGE = "com.whatsapp"

/** Filtros del reporte: rango de fechas, sede, empleado y formato. */
internal fun MainActivity.setupReportScreen() {
    val rangeField = findViewById<TextView>(v.range)
    val sedeField = findViewById<TextView>(v.report_sede)
    val employeeField = findViewById<TextView>(v.report_employee)
    val pdfButton = findViewById<Button>(v.fmt_pdf)
    val excelButton = findViewById<Button>(v.fmt_excel)
    val generateButton = findViewById<Button>(v.generate)
    var from = AttendanceRules.mondayOf(LocalDate.now())
    var to = LocalDate.now()
    var sedeIndex = -1
    var employee: UserProfile? = null
    var employees = emptyList<UserProfile>()
    var format = ReportFormat.PDF

    rangeField.text = Formats.dateRange(from, to)
    fun showFormat() {
        pdfButton.alpha = if (format == ReportFormat.PDF) 1f else 0.5f
        excelButton.alpha = if (format == ReportFormat.EXCEL) 1f else 0.5f
    }
    showFormat()
    pdfButton.setOnClickListener { format = ReportFormat.PDF; showFormat() }
    excelButton.setOnClickListener { format = ReportFormat.EXCEL; showFormat() }

    rangeField.setOnClickListener {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Rango de fechas")
            .setSelection(androidx.core.util.Pair(from.toUtcMillis(), to.toUtcMillis()))
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            from = selection.first.toLocalDate()
            to = selection.second.toLocalDate()
            rangeField.text = Formats.dateRange(from, to)
        }
        picker.show(supportFragmentManager, "rango")
    }
    sedeField.setOnClickListener {
        chooseOption("Sede", listOf("Todas las sedes") + Session.sedes.map { it.name }) { index ->
            sedeIndex = index - 1
            sedeField.text = Session.sedes.getOrNull(sedeIndex)?.name ?: "Todas las sedes"
        }
    }
    employeeField.setOnClickListener {
        if (employees.isEmpty()) return@setOnClickListener showMessage("Cargando empleados, intenta en un momento.")
        chooseOption("Empleado", listOf("Todos los empleados") + employees.map { it.name }) { index ->
            employee = employees.getOrNull(index - 1)
            employeeField.text = employee?.name ?: "Todos los empleados"
        }
    }
    launchTask { employees = KronosRepository.loadEmployees(Session.requireProfile().companyId) }

    generateButton.setOnClickListener {
        launchTask(generateButton) {
            val companyId = Session.requireProfile().companyId
            val sede = Session.sedes.getOrNull(sedeIndex)
            val chosenEmployees = (employee?.let(::listOf) ?: KronosRepository.loadEmployees(companyId))
                .filter { sede == null || sede.id in it.sedeIds }
            val records = KronosRepository.attendanceBetween(companyId, from, to)
                .filter { sede == null || it.sedeId == sede.id }
            val rows = AttendanceRules.buildRows(chosenEmployees, records, Session.sedes, from, to, LocalDateTime.now())
                .filter { sede == null || it.sedeName == sede.name }
            val report = AttendanceReport(
                from = from,
                to = to,
                sedeLabel = sede?.name ?: "Todas las sedes",
                employeeLabel = employee?.name ?: "Todos los empleados",
                rows = rows,
            )
            val file = withContext(Dispatchers.IO) {
                ReportWriter.write(report, format, File(cacheDir, "reports"))
            }
            Session.report = GeneratedReport(report, format, file)
            goIfStillOn(s.screen_report, s.screen_report_done)
        }
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Nombre y tamaño del archivo generado. */
private fun MainActivity.showReportFile(report: GeneratedReport, detail: String) {
    findViewById<TextView>(v.file_name).text = report.file.name
    findViewById<TextView>(v.file_info).text = "${Formats.fileSize(report.file.length())} · $detail"
}

internal fun MainActivity.setupReportDoneScreen() {
    val report = Session.report ?: return
    showReportFile(report, "Generado hace instantes")
    findViewById<Button>(v.download).setOnClickListener {
        chooseReportDestination(report.format, report.file.name) { uri ->
            if (uri == null) return@chooseReportDestination
            try {
                contentResolver.openOutputStream(uri)?.use { output -> report.file.inputStream().use { it.copyTo(output) } }
                Session.savedReportUri = uri
                goIfStillOn(s.screen_report_done, s.screen_report_download)
            } catch (_: Exception) {
                showMessage("No se pudo guardar el reporte en esa ubicación.")
            }
        }
    }
}

internal fun MainActivity.setupReportDownloadScreen() {
    val report = Session.report ?: return
    showReportFile(report, report.format.label)
    findViewById<Button>(v.open).setOnClickListener {
        val uri = Session.savedReportUri ?: return@setOnClickListener go(s.screen_report_view)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, report.format.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Sin una app que abra el archivo, se muestra la vista previa de Kronos.
            go(s.screen_report_view)
        }
    }
}

/** Vista previa del reporte dentro de la app. */
internal fun MainActivity.setupReportViewScreen() {
    val generated = Session.report ?: return
    val report = generated.report
    findViewById<TextView>(v.file_name).text = generated.file.name
    findViewById<TextView>(v.report_filters).text = report.filters
    findViewById<TextView>(v.stat_present).text = report.stats.present.toString()
    findViewById<TextView>(v.stat_late).text = report.stats.late.toString()
    findViewById<TextView>(v.stat_absent).text = report.stats.absent.toString()
    findViewById<TextView>(v.stat_punctuality).text = report.stats.punctuality?.let { "$it%" } ?: "—"
    val table = findViewById<LinearLayout>(v.list)
    table.removeAllViews()
    report.rows.forEach { row ->
        val item = layoutInflater.inflate(R.layout.item_report_row, table, false)
        val name = if (report.from == report.to) row.employeeName else "${row.employeeName} (${Formats.shortDate(row.date)})"
        item.findViewById<TextView>(v.employee).text = name
        item.findViewById<TextView>(v.check_in).text = Formats.time(row.record?.checkIn)
        item.findViewById<TextView>(v.check_out).text = Formats.time(row.record?.checkOut)
        item.findViewById<TextView>(v.status).apply {
            text = row.status.label
            setTextColor(ContextCompat.getColor(context, row.status.color()))
        }
        table.addView(item)
    }
}

/** Comparte el archivo por correo, WhatsApp u otra app instalada. */
internal fun MainActivity.setupReportShareScreen() {
    val generated = Session.report ?: return
    showReportFile(generated, generated.format.label)
    val email = findViewById<EditText>(v.share_email)
    val message = findViewById<EditText>(v.share_message)
    val channels = listOf<View>(findViewById(v.channel_email), findViewById(v.channel_whatsapp), findViewById(v.channel_other))
    var channel = 0
    fun showChannel() = channels.forEachIndexed { index, view -> view.isSelected = index == channel }
    channels.forEachIndexed { index, view ->
        view.setOnClickListener {
            channel = index
            showChannel()
        }
    }
    showChannel()

    findViewById<Button>(v.send).setOnClickListener {
        val fileUri: Uri = FileProvider.getUriForFile(this, "$packageName.files", generated.file)
        val share = Intent(Intent.ACTION_SEND)
            .setType(generated.format.mimeType)
            .putExtra(Intent.EXTRA_STREAM, fileUri)
            .putExtra(Intent.EXTRA_SUBJECT, "Reporte de asistencia · ${generated.report.filters}")
            .putExtra(Intent.EXTRA_TEXT, message.trimmedText())
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (email.text.isNotBlank()) share.putExtra(Intent.EXTRA_EMAIL, arrayOf(email.trimmedText()))
        try {
            when (channel) {
                1 -> startActivity(share.setPackage(WHATSAPP_PACKAGE))
                else -> startActivity(Intent.createChooser(share, "Compartir reporte"))
            }
        } catch (_: ActivityNotFoundException) {
            showMessage("WhatsApp no está instalado en este teléfono.")
        }
    }
}

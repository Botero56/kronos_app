package com.example.kronos_.screens

import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.kronos_.MainActivity
import com.example.kronos_.MainActivity.Companion.BACK
import com.example.kronos_.R
import com.example.kronos_.auth.errorMessage
import com.example.kronos_.data.AttendanceRecord
import com.example.kronos_.data.KronosRepository
import com.example.kronos_.data.PermitRequest
import com.example.kronos_.data.RequestStatus
import com.example.kronos_.data.Sede
import com.example.kronos_.data.Session
import com.example.kronos_.logic.AttendanceRules
import com.example.kronos_.logic.Formats
import com.example.kronos_.ui.chooseDate
import com.example.kronos_.ui.chooseOption
import com.example.kronos_.ui.color
import com.example.kronos_.ui.confirm
import com.example.kronos_.ui.showRows
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import com.example.kronos_.R.id as v
import com.example.kronos_.R.layout as s

private val PERMIT_TYPES = listOf(
    "Cita médica", "Vacaciones", "Diligencia personal", "Calamidad doméstica", "Incapacidad", "Otro",
)

/** Días del historial y del resumen hacia atrás. */
private const val HISTORY_DAYS = 30L

private fun todayRecord(): AttendanceRecord? =
    Session.myRecords?.firstOrNull { it.date == LocalDate.now() }

/** Para empleados con más de una sede: elige en cuál marca hoy. */
internal fun MainActivity.setupSedeChoiceScreen() {
    val profile = Session.profile ?: return
    val sedes = Session.sedesOf(profile)
    var chosen = Session.currentSede ?: sedes.firstOrNull()
    val list = findViewById<LinearLayout>(v.list)

    fun render() {
        list.removeAllViews()
        sedes.forEach { sede ->
            val item = layoutInflater.inflate(R.layout.item_sede, list, false)
            item.findViewById<TextView>(v.sede_name).text = sede.name
            item.findViewById<TextView>(v.sede_address).text = sede.address
            item.isSelected = sede == chosen
            item.setOnClickListener {
                chosen = sede
                render()
            }
            list.addView(item)
        }
    }
    render()
    findViewById<Button>(v.next).setOnClickListener {
        Session.currentSede = chosen
        go(s.screen_home)
    }
}

internal fun MainActivity.setupHomeScreen() {
    val profile = Session.profile ?: return
    val sede = Session.currentSede
    findViewById<TextView>(v.today).text = Formats.longDate(LocalDate.now())
    findViewById<TextView>(v.greeting).text = "Hola, ${Formats.firstName(profile.name)}"
    val location = findViewById<TextView>(v.location)
    location.text = sede?.let { "${it.name} · verificar ubicación" } ?: "Sin sede asignada"
    location.setOnClickListener {
        val currentSede = sede ?: return@setOnClickListener showMessage("No tienes una sede asignada.")
        measureDistance(currentSede) { distance ->
            if (distance <= currentSede.radiusMeters) {
                showMessage("Estás dentro del rango autorizado de ${currentSede.name}.")
            } else {
                goIfStillOn(s.screen_home, s.screen_out_of_range)
            }
        }
    }

    findViewById<Button>(v.check_in).setOnClickListener {
        val record = todayRecord()
        when {
            sede == null -> showMessage("No tienes una sede asignada. Habla con tu administrador.")
            Session.myRecords == null -> showMessage("Cargando tus marcajes, intenta en un momento.")
            record != null -> showMessage("Ya marcaste entrada hoy a las ${Formats.time(record.checkIn)}.")
            else -> go(s.screen_biometric)
        }
    }
    findViewById<Button>(v.check_out).setOnClickListener {
        val record = todayRecord()
        when {
            Session.myRecords == null -> showMessage("Cargando tus marcajes, intenta en un momento.")
            record == null -> showMessage("Primero marca tu entrada.")
            record.checkOut != null -> showMessage("Ya marcaste salida hoy a las ${Formats.time(record.checkOut)}.")
            else -> go(s.screen_biometric_out)
        }
    }

    val shiftStatus = findViewById<TextView>(v.shift_status)
    val lastEntry = findViewById<TextView>(v.last_entry_time)
    val weekHours = findViewById<TextView>(v.week_hours_value)
    launchTask {
        val records = KronosRepository.attendanceOfUser(profile.companyId, profile.id)
        Session.myRecords = records
        val record = todayRecord()
        shiftStatus.text = when {
            record == null -> "Estás fuera de turno"
            record.checkOut == null -> "Turno en curso desde las ${Formats.time(record.checkIn)}"
            else -> "Turno terminado a las ${Formats.time(record.checkOut)}"
        }
        lastEntry.text = Formats.time(records.firstOrNull()?.checkIn)
        val monday = AttendanceRules.mondayOf(LocalDate.now())
        weekHours.text = Formats.duration(records.filter { !it.date.isBefore(monday) }.sumOf(AttendanceRules::workedMinutes))
    }
}

/** Mide la distancia a la sede; si no se puede, avisa al usuario. */
private fun MainActivity.measureDistance(sede: Sede, onMeasured: (Int) -> Unit) {
    if (!sede.hasLocation) return showMessage("La sede no tiene ubicación configurada. Avísale a tu administrador.")
    requestCurrentLocation { location ->
        if (location == null) return@requestCurrentLocation showMessage("No se pudo obtener tu ubicación. Inténtalo de nuevo.")
        val distance = AttendanceRules.distanceToSede(location.latitude, location.longitude, sede) ?: return@requestCurrentLocation
        Session.lastDistanceMeters = distance
        onMeasured(distance)
    }
}

/** Marcar entrada o salida: verifica la identidad, luego la ubicación, y guarda el marcaje. */
internal fun MainActivity.setupCheckScreen(isCheckIn: Boolean) {
    val screen = if (isCheckIn) s.screen_biometric else s.screen_biometric_out
    findViewById<Button>(v.scan).setOnClickListener {
        val profile = Session.profile ?: return@setOnClickListener signOut()
        val sede = Session.currentSede ?: return@setOnClickListener showMessage("No tienes una sede asignada.")
        verifyIdentity(if (isCheckIn) "Marcar entrada" else "Marcar salida") {
            measureDistance(sede) { distance ->
                if (!isShowing(screen)) return@measureDistance
                if (distance > sede.radiusMeters) {
                    KronosRepository.saveLocationAlert(profile.companyId, profile, sede, distance)
                    return@measureDistance go(s.screen_out_of_range)
                }
                val now = LocalDateTime.now()
                val saved = if (isCheckIn) {
                    val late = AttendanceRules.isLate(now.toLocalTime(), sede)
                    KronosRepository.saveCheckIn(profile.companyId, profile, sede, now, late, distance)
                } else {
                    val record = todayRecord() ?: return@measureDistance showMessage("Primero marca tu entrada.")
                    KronosRepository.saveCheckOut(profile.companyId, record.id, now)
                }
                saved.addOnFailureListener(this) { showMessage(errorMessage(it)) }
                Session.lastCheckTime = now
                Session.myRecords = null
                // Sin internet el marcaje queda guardado en el teléfono y se envía después.
                go(if (!isOnline()) s.screen_offline else if (isCheckIn) s.screen_confirm_in else s.screen_confirm_out)
            }
        }
    }
}

internal fun MainActivity.setupCheckConfirmedScreen() {
    findViewById<TextView>(v.time).text = Formats.time(Session.lastCheckTime)
    findViewById<TextView>(v.sede_name).text = Session.currentSede?.name.orEmpty()
}

internal fun MainActivity.setupOutOfRangeScreen() {
    val sede = Session.currentSede ?: return
    val message = findViewById<TextView>(v.distance_message)
    val details = findViewById<TextView>(v.map_info)

    fun render() {
        val distance = Session.lastDistanceMeters
        message.text = if (distance == null) {
            "No estás dentro del área de ${sede.name}. Acércate al lugar de trabajo para poder marcar."
        } else {
            "Te encuentras a $distance m de ${sede.name}. Acércate al lugar de trabajo para poder marcar."
        }
        details.text = "Radio autorizado: ${sede.radiusMeters} m · ${sede.address}"
    }
    render()
    findViewById<Button>(v.retry).setOnClickListener {
        measureDistance(sede) { distance ->
            if (distance <= sede.radiusMeters) {
                showMessage("Ya estás dentro del área autorizada. Puedes marcar.")
                goIfStillOn(s.screen_out_of_range, BACK)
            } else {
                render()
            }
        }
    }
}

/** Filas de asistencia del empleado desde que se registró (máximo [HISTORY_DAYS] días). */
private suspend fun myAttendanceRows(from: LocalDate, to: LocalDate) = Session.requireProfile().let { profile ->
    val records = KronosRepository.attendanceOfUser(profile.companyId, profile.id)
    AttendanceRules.buildRows(listOf(profile), records, Session.sedes, from, to, LocalDateTime.now())
}

internal fun MainActivity.setupHistoryScreen() {
    val list = findViewById<LinearLayout>(v.list)
    launchTask {
        val today = LocalDate.now()
        val rows = myAttendanceRows(today.minusDays(HISTORY_DAYS), today)
        list.showRows(rows, "Todavía no tienes marcajes.") { row, item ->
            row.title.text = Formats.shortDate(item.date)
            row.setSubtitle(Formats.timeRange(item.record?.checkIn, item.record?.checkOut))
            row.setStatus(item.status.label, item.status.color())
        }
    }
}

internal fun MainActivity.setupSummaryScreen() {
    val today = LocalDate.now()
    val monday = AttendanceRules.mondayOf(today)
    findViewById<TextView>(v.week_range).text = Formats.weekRange(monday)
    val hours = findViewById<TextView>(v.hours)
    val punctuality = findViewById<TextView>(v.punctuality)
    val lateCount = findViewById<TextView>(v.late_count)
    val absences = findViewById<TextView>(v.absences)
    val comparison = findViewById<TextView>(v.comparison)
    launchTask {
        val rows = myAttendanceRows(monday.minusWeeks(1), today)
        val thisWeek = AttendanceRules.weekSummary(rows.filter { !it.date.isBefore(monday) })
        val lastWeek = AttendanceRules.weekSummary(rows.filter { it.date.isBefore(monday) })
        hours.text = Formats.duration(thisWeek.workedMinutes)
        punctuality.text = thisWeek.stats.punctuality?.let { "$it%" } ?: "—"
        lateCount.text = thisWeek.stats.late.toString()
        absences.text = thisWeek.stats.absent.toString()
        val current = thisWeek.stats.punctuality
        val previous = lastWeek.stats.punctuality
        comparison.text = when {
            current == null || previous == null -> "Aún no hay datos suficientes para comparar."
            current > previous -> "+${current - previous}% más puntual que la semana pasada"
            current < previous -> "${current - previous}% menos puntual que la semana pasada"
            else -> "Igual de puntual que la semana pasada"
        }
    }
}

internal fun MainActivity.setupScheduleScreen() {
    val sede = Session.currentSede
    val title = findViewById<TextView>(v.schedule_sede)
    val list = findViewById<LinearLayout>(v.list)
    if (sede == null) {
        title.text = "No tienes una sede asignada."
        return
    }
    title.text = "Horario de ${sede.name}"
    findViewById<TextView>(v.tolerance).text = "Tolerancia de retraso: ${sede.toleranceMinutes} min"
    list.showRows(DayOfWeek.entries, "") { row, day ->
        row.title.text = Formats.dayName(day)
        if (day in sede.workDays) {
            row.setStatus("${Formats.time(sede.startTime)} – ${Formats.time(sede.endTime)}")
        } else {
            row.setStatus("Libre")
        }
    }
}

internal fun MainActivity.setupRequestPermitScreen() {
    val profile = Session.profile ?: return
    val typeField = findViewById<TextView>(v.permit_type)
    val startField = findViewById<TextView>(v.start_date)
    val endField = findViewById<TextView>(v.end_date)
    val reason = findViewById<EditText>(v.reason)
    val sendButton = findViewById<Button>(v.send)
    var type: String? = null
    var start: LocalDate? = null
    var end: LocalDate? = null

    typeField.setOnClickListener {
        chooseOption("Tipo de permiso", PERMIT_TYPES) { index ->
            type = PERMIT_TYPES[index]
            typeField.text = type
        }
    }
    startField.setOnClickListener {
        chooseDate(start ?: LocalDate.now()) { date ->
            start = date
            startField.text = Formats.date(date)
            if (end?.isBefore(date) != false) {
                end = date
                endField.text = Formats.date(date)
            }
        }
    }
    endField.setOnClickListener {
        chooseDate(end ?: start ?: LocalDate.now()) { date ->
            if (start?.isAfter(date) == true) return@chooseDate showMessage("La fecha de fin no puede ser anterior al inicio.")
            end = date
            endField.text = Formats.date(date)
        }
    }
    sendButton.setOnClickListener {
        val chosenType = type ?: return@setOnClickListener showMessage("Elige el tipo de permiso.")
        val from = start ?: return@setOnClickListener showMessage("Elige la fecha de inicio.")
        val to = end ?: from
        reason.error = if (reason.text.isBlank()) "Escribe el motivo." else null
        if (reason.error != null) return@setOnClickListener
        launchTask(sendButton) {
            val sedeName = Session.currentSede?.name.orEmpty()
            KronosRepository.sendRequest(profile.companyId, profile, sedeName, chosenType, from, to, reason.trimmedText())
            showMessage("Solicitud enviada.")
            goIfStillOn(s.screen_request_permit, s.screen_my_requests)
        }
    }
}

internal fun MainActivity.setupMyRequestsScreen() {
    val profile = Session.profile ?: return
    val list = findViewById<LinearLayout>(v.list)
    launchTask {
        val requests = KronosRepository.requestsOfUser(profile.companyId, profile.id)
        list.showRows(requests, "No has enviado solicitudes.") { row, request ->
            row.title.text = request.type
            row.setSubtitle(Formats.dateRange(request.startDate, request.endDate))
            row.setStatus(request.status.label, request.status.color())
            row.onClick {
                Session.selectedRequest = request
                go(s.screen_request_detail)
            }
        }
    }
}

internal fun MainActivity.setupRequestDetailScreen() {
    val request = Session.selectedRequest ?: return
    showRequestDetails(request)
    val cancelButton = findViewById<Button>(v.cancel)
    cancelButton.isVisible = request.status == RequestStatus.PENDING
    cancelButton.setOnClickListener {
        confirm("¿Cancelar esta solicitud?", "Sí, cancelar") {
            launchTask(cancelButton) {
                KronosRepository.cancelRequest(Session.requireProfile().companyId, request.id)
                showMessage("Solicitud cancelada.")
                goIfStillOn(s.screen_request_detail, BACK)
            }
        }
    }
}

/** Datos comunes del detalle de una solicitud (empleado y administrador). */
internal fun MainActivity.showRequestDetails(request: PermitRequest) {
    val status = findViewById<TextView>(v.status)
    status.text = if (request.status == RequestStatus.PENDING && isShowing(s.screen_review_request)) {
        "Pendiente de revisión"
    } else {
        request.status.label
    }
    status.setTextColor(ContextCompat.getColor(this, request.status.color()))
    findViewById<TextView>(v.type).text = request.type
    findViewById<TextView>(v.dates).text = Formats.dateRange(request.startDate, request.endDate)
    findViewById<TextView>(v.reason).text = request.reason
    findViewById<TextView>(v.sent_line).text = "● Solicitud enviada — ${Formats.dateTime(request.createdAt)}"
    val reviewedAt = request.reviewedAt
    findViewById<TextView>(v.review_line).text = when {
        request.status == RequestStatus.PENDING || reviewedAt == null -> "○ En revisión por el administrador"
        request.status == RequestStatus.APPROVED -> "● Aprobada — ${Formats.dateTime(reviewedAt)}"
        else -> "● Rechazada — ${Formats.dateTime(reviewedAt)}"
    }
}

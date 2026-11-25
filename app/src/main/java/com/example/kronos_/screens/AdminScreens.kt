package com.example.kronos_.screens

import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.example.kronos_.MainActivity
import com.example.kronos_.MainActivity.Companion.BACK
import com.example.kronos_.auth.CredentialValidator
import com.example.kronos_.data.AttendanceStatus
import com.example.kronos_.data.KronosRepository
import com.example.kronos_.data.RequestStatus
import com.example.kronos_.data.Sede
import com.example.kronos_.data.Session
import com.example.kronos_.data.UserProfile
import com.example.kronos_.logic.AttendanceRow
import com.example.kronos_.logic.AttendanceRules
import com.example.kronos_.logic.Formats
import com.example.kronos_.ui.chooseOption
import com.example.kronos_.ui.color
import com.example.kronos_.ui.showRows
import com.google.android.material.tabs.TabLayout
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeParseException
import com.example.kronos_.R.id as v
import com.example.kronos_.R.layout as s

private const val RECENT_ROWS = 10
private const val ALERT_DAYS = 7L
private const val MIN_RADIUS_METERS = 20
private const val MAX_RADIUS_METERS = 5000

/** Asistencia de todos los empleados entre dos fechas. */
private suspend fun companyAttendance(from: LocalDate, to: LocalDate): Pair<List<UserProfile>, List<AttendanceRow>> {
    val companyId = Session.requireProfile().companyId
    val employees = KronosRepository.loadEmployees(companyId)
    val records = KronosRepository.attendanceBetween(companyId, from, to)
    return employees to AttendanceRules.buildRows(employees, records, Session.sedes, from, to, LocalDateTime.now())
}

private fun sedeNames(employee: UserProfile) =
    Session.sedesOf(employee).joinToString { it.name }.ifBlank { "Sin sede" }

internal fun MainActivity.setupDashboardScreen() {
    findViewById<TextView>(v.sede).text = "Dashboard · ${Session.company?.name.orEmpty()}"
    val present = findViewById<TextView>(v.stat_present)
    val late = findViewById<TextView>(v.stat_late)
    val absent = findViewById<TextView>(v.stat_absent)
    val punctuality = findViewById<TextView>(v.stat_punctuality)
    val list = findViewById<LinearLayout>(v.list)
    launchTask {
        val today = LocalDate.now()
        val (employees, rows) = companyAttendance(today, today)
        val stats = AttendanceRules.stats(rows)
        present.text = stats.present.toString()
        late.text = stats.late.toString()
        absent.text = stats.absent.toString()
        punctuality.text = stats.punctuality?.let { "$it%" } ?: "—"
        val emptyText = if (employees.isEmpty()) "Agrega empleados para ver sus marcajes." else "Nadie ha marcado hoy."
        list.showRows(rows.take(RECENT_ROWS), emptyText) { row, item ->
            row.setAvatar(item.employeeName)
            row.title.text = item.employeeName
            row.setSubtitle(Formats.time(item.record?.checkIn))
            row.setStatus(item.status.label, item.status.color())
            if (item.status == AttendanceStatus.ABSENT) row.onClick { go(s.screen_alerts) }
        }
    }
}

internal fun MainActivity.setupEmployeesScreen() {
    val list = findViewById<LinearLayout>(v.list)
    val search = findViewById<EditText>(v.search)
    var employees = emptyList<UserProfile>()

    fun render() {
        val query = search.trimmedText()
        val visible = employees.filter {
            query.isEmpty() || listOf(it.name, it.position, it.email, it.document).any { field -> field.contains(query, ignoreCase = true) }
        }
        val emptyText = if (employees.isEmpty()) "Todavía no hay empleados." else "Ningún empleado coincide con la búsqueda."
        list.showRows(visible, emptyText) { row, employee ->
            row.setAvatar(employee.name)
            row.title.text = employee.name
            row.setSubtitle("${employee.position} · ${sedeNames(employee)}")
        }
    }
    search.doAfterTextChanged { render() }
    launchTask {
        employees = KronosRepository.loadEmployees(Session.requireProfile().companyId)
        render()
    }
}

/** El administrador crea la cuenta del empleado con una contraseña inicial que el empleado puede cambiar después. */
internal fun MainActivity.setupNewEmployeeScreen() {
    val name = findViewById<EditText>(v.name)
    val document = findViewById<EditText>(v.document)
    val email = findViewById<EditText>(v.email)
    val password = findViewById<EditText>(v.password)
    val position = findViewById<EditText>(v.position)
    val sedeField = findViewById<TextView>(v.sede_select)
    val saveButton = findViewById<Button>(v.save)
    var sede: Sede? = null

    sedeField.setOnClickListener {
        if (Session.sedes.isEmpty()) return@setOnClickListener showMessage("Primero crea una sede desde el Dashboard.")
        chooseOption("Sede", Session.sedes.map { it.name }) { index ->
            sede = Session.sedes[index]
            sedeField.text = Session.sedes[index].name
        }
    }
    saveButton.setOnClickListener {
        name.error = CredentialValidator.nameError(name.text.toString())
        document.error = CredentialValidator.documentError(document.text.toString())
        email.error = CredentialValidator.emailError(email.text.toString())
        password.error = CredentialValidator.newPasswordError(password.text.toString())
        position.error = CredentialValidator.requiredError(position.text.toString(), "Escribe el cargo.")
        if (listOf(name, document, email, password, position).any { it.error != null }) return@setOnClickListener
        val chosenSede = sede ?: return@setOnClickListener showMessage("Elige la sede del empleado.")

        launchTask(saveButton) {
            Session.createdEmployee = KronosRepository.createEmployee(
                context = this, companyId = Session.requireProfile().companyId,
                name = name.trimmedText(), document = document.trimmedText(), email = email.trimmedText(),
                password = password.text.toString(), position = position.trimmedText(), sedeId = chosenSede.id,
            )
            goIfStillOn(s.screen_new_employee, s.screen_employee_created)
        }
    }
}

internal fun MainActivity.setupEmployeeCreatedScreen() {
    val employee = Session.createdEmployee ?: return
    findViewById<TextView>(v.avatar).text = Formats.initials(employee.name)
    findViewById<TextView>(v.employee_name).text = employee.name
    findViewById<TextView>(v.employee_position).text = employee.position
}

/** Historial de asistencia con filtros de fecha, sede y estado. */
internal fun MainActivity.setupAdminHistoryScreen() {
    val dateFilter = findViewById<TextView>(v.filter_date)
    val sedeFilter = findViewById<TextView>(v.filter_sede)
    val statusFilter = findViewById<TextView>(v.filter_status)
    val list = findViewById<LinearLayout>(v.list)
    val periods = listOf("Hoy" to 0L, "Ayer" to 1L, "Últimos 7 días" to 6L, "Últimos 30 días" to 29L)
    val statuses = listOf<AttendanceStatus?>(null) + AttendanceStatus.entries
    var period = 0
    var sede: Sede? = null
    var status: AttendanceStatus? = null

    fun load() {
        launchTask {
            val today = LocalDate.now()
            val daysBack = periods[period].second
            val to = if (period == 1) today.minusDays(1) else today
            val (_, rows) = companyAttendance(today.minusDays(daysBack), to)
            val visible = rows.filter { row ->
                (sede == null || row.sedeName == sede?.name) && (status == null || row.status == status)
            }
            list.showRows(visible, "No hay registros con estos filtros.") { row, item ->
                row.title.text = item.employeeName
                val times = Formats.timeRange(item.record?.checkIn, item.record?.checkOut)
                row.setSubtitle(if (daysBack == 0L) times else "${Formats.shortDate(item.date)} · $times")
                row.setStatus(item.status.label, item.status.color())
            }
        }
    }
    dateFilter.setOnClickListener {
        chooseOption("Periodo", periods.map { it.first }) { index ->
            period = index
            dateFilter.text = periods[index].first
            load()
        }
    }
    sedeFilter.setOnClickListener {
        chooseOption("Sede", listOf("Todas las sedes") + Session.sedes.map { it.name }) { index ->
            sede = Session.sedes.getOrNull(index - 1)
            sedeFilter.text = sede?.name ?: "Todas las sedes"
            load()
        }
    }
    statusFilter.setOnClickListener {
        chooseOption("Estado", statuses.map { it?.label ?: "Todos" }) { index ->
            status = statuses[index]
            statusFilter.text = status?.label ?: "Todos"
            load()
        }
    }
    load()
}

/** Ausentes de hoy e intentos de marcaje fuera de la sede de los últimos días. */
internal fun MainActivity.setupAlertsScreen() {
    val list = findViewById<LinearLayout>(v.list)
    launchTask {
        val today = LocalDate.now()
        val (_, rows) = companyAttendance(today, today)
        val absents = rows.filter { it.status == AttendanceStatus.ABSENT }
            .map { LocalDateTime.of(today, LocalTime.MIN) to "${it.employeeName} no ha marcado entrada hoy (${it.sedeName})." }
        val since = today.minusDays(ALERT_DAYS).atStartOfDay()
        val locationAlerts = KronosRepository.alertsSince(Session.requireProfile().companyId, since)
            .map { it.createdAt to "${it.userName} intentó marcar a ${it.distanceMeters} m de ${it.sedeName}." }
        val alerts = absents + locationAlerts.sortedByDescending { it.first }
        list.showRows(alerts, "No hay alertas.") { row, alert ->
            row.title.text = alert.second
            row.setSubtitle(if (alert.first.toLocalTime() == LocalTime.MIN) "Hoy" else Formats.relative(alert.first))
        }
    }
}

internal fun MainActivity.setupAdminRequestsScreen() {
    val tabs = findViewById<TabLayout>(v.tabs)
    val list = findViewById<LinearLayout>(v.list)
    val statusByTab = listOf(RequestStatus.PENDING, RequestStatus.APPROVED, RequestStatus.REJECTED)

    fun load(tab: Int) {
        Session.selectedRequestTab = tab
        launchTask {
            val requests = KronosRepository.requestsWithStatus(Session.requireProfile().companyId, statusByTab[tab])
            list.showRows(requests, "No hay solicitudes en esta bandeja.") { row, request ->
                row.setAvatar(request.userName)
                row.title.text = request.userName
                row.setSubtitle("${request.type} · ${Formats.dateRange(request.startDate, request.endDate)}")
                row.setStatus(request.status.label, request.status.color())
                row.onClick {
                    Session.selectedRequest = request
                    go(s.screen_review_request)
                }
            }
        }
    }
    tabs.getTabAt(Session.selectedRequestTab)?.select()
    tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
        override fun onTabSelected(tab: TabLayout.Tab) = load(tab.position)
        override fun onTabUnselected(tab: TabLayout.Tab) = Unit
        override fun onTabReselected(tab: TabLayout.Tab) = Unit
    })
    load(Session.selectedRequestTab)
}

internal fun MainActivity.setupReviewRequestScreen() {
    val request = Session.selectedRequest ?: return
    showRequestDetails(request)
    findViewById<TextView>(v.employee).text =
        listOf(request.userName, request.userPosition, request.sedeName).filter { it.isNotBlank() }.joinToString(" · ")
    val approve = findViewById<Button>(v.approve)
    val reject = findViewById<Button>(v.reject)
    val pending = request.status == RequestStatus.PENDING
    approve.isVisible = pending
    reject.isVisible = pending

    fun review(status: RequestStatus, button: Button, message: String) {
        launchTask(button) {
            KronosRepository.reviewRequest(Session.requireProfile().companyId, request.id, status)
            showMessage(message)
            goIfStillOn(s.screen_review_request, BACK)
        }
    }
    approve.setOnClickListener { review(RequestStatus.APPROVED, approve, "Solicitud aprobada.") }
    reject.setOnClickListener { review(RequestStatus.REJECTED, reject, "Solicitud rechazada.") }
}

/** Crear o editar una sede: nombre, dirección, ubicación tomada del GPS, radio y horario. */
internal fun MainActivity.setupSedeEditScreen() {
    val title = findViewById<TextView>(v.title)
    val picker = findViewById<TextView>(v.sede_picker)
    val name = findViewById<EditText>(v.sede_name)
    val address = findViewById<EditText>(v.sede_address)
    val coordinates = findViewById<TextView>(v.coordinates)
    val radius = findViewById<EditText>(v.radius)
    val startTime = findViewById<EditText>(v.start_time)
    val endTime = findViewById<EditText>(v.end_time)
    val saveButton = findViewById<Button>(v.save)
    var editing: Sede? = null
    var latitude: Double? = null
    var longitude: Double? = null

    fun showCoordinates() {
        coordinates.text = if (latitude == null || longitude == null) {
            "Sin ubicación. Ve a la sede y toca el botón."
        } else {
            "Ubicación: %.5f, %.5f".format(latitude, longitude)
        }
    }

    fun fill(sede: Sede?) {
        editing = sede
        title.text = if (sede == null) "Nueva sede" else "Editar sede"
        picker.text = sede?.name ?: "Crear una sede nueva"
        name.setText(sede?.name.orEmpty())
        address.setText(sede?.address.orEmpty())
        radius.setText((sede?.radiusMeters ?: Sede.DEFAULT_RADIUS_METERS).toString())
        startTime.setText(sede?.startTime?.let(Formats::time).orEmpty())
        endTime.setText(sede?.endTime?.let(Formats::time).orEmpty())
        latitude = sede?.latitude
        longitude = sede?.longitude
        showCoordinates()
    }
    fill(null)

    picker.isVisible = Session.sedes.isNotEmpty()
    picker.setOnClickListener {
        chooseOption("Sede", listOf("Crear una sede nueva") + Session.sedes.map { it.name }) { index ->
            fill(Session.sedes.getOrNull(index - 1))
        }
    }
    findViewById<Button>(v.use_location).setOnClickListener {
        requestCurrentLocation { location ->
            if (location == null) return@requestCurrentLocation showMessage("No se pudo obtener la ubicación. Inténtalo de nuevo.")
            latitude = location.latitude
            longitude = location.longitude
            showCoordinates()
        }
    }
    saveButton.setOnClickListener {
        name.error = CredentialValidator.requiredError(name.text.toString(), "Escribe el nombre de la sede.")
        address.error = CredentialValidator.requiredError(address.text.toString(), "Escribe la dirección.")
        val radiusMeters = radius.trimmedText().toIntOrNull()
        radius.error = if (radiusMeters == null || radiusMeters !in MIN_RADIUS_METERS..MAX_RADIUS_METERS) {
            "Escribe un radio entre $MIN_RADIUS_METERS y $MAX_RADIUS_METERS metros."
        } else {
            null
        }
        val start = parseTime(startTime.trimmedText())
        val end = parseTime(endTime.trimmedText())
        startTime.error = if (start == null) "Usa el formato HH:mm, por ejemplo 07:30." else null
        endTime.error = when {
            end == null -> "Usa el formato HH:mm, por ejemplo 17:00."
            start != null && !end.isAfter(start) -> "La salida debe ser después de la entrada."
            else -> null
        }
        if (listOf(name, address, radius, startTime, endTime).any { it.error != null }) return@setOnClickListener
        if (latitude == null || longitude == null) return@setOnClickListener showMessage("Toca \"Usar mi ubicación actual\" estando en la sede.")

        val sede = Sede(
            id = editing?.id.orEmpty(),
            name = name.trimmedText(),
            address = address.trimmedText(),
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters!!,
            startTime = start!!,
            endTime = end!!,
            toleranceMinutes = editing?.toleranceMinutes ?: Sede.DEFAULT_TOLERANCE_MINUTES,
            workDays = editing?.workDays ?: Sede.DEFAULT_WORK_DAYS,
        )
        launchTask(saveButton) {
            val companyId = Session.requireProfile().companyId
            KronosRepository.saveSede(companyId, editing?.id, sede)
            Session.sedes = KronosRepository.loadSedes(companyId)
            showMessage("Sede guardada.")
            goIfStillOn(s.screen_sede_edit, BACK)
        }
    }
}

private fun parseTime(text: String): LocalTime? =
    try {
        LocalTime.parse(text.padStart(5, '0'))
    } catch (_: DateTimeParseException) {
        null
    }

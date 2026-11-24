package com.example.kronos_.logic

import com.example.kronos_.data.AttendanceRecord
import com.example.kronos_.data.AttendanceStatus
import com.example.kronos_.data.Sede
import com.example.kronos_.data.UserProfile
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Una fila de asistencia: un empleado en un día, con su marcaje o ausente. */
data class AttendanceRow(
    val date: LocalDate,
    val employeeId: String,
    val employeeName: String,
    val sedeName: String,
    val record: AttendanceRecord?,
) {
    val status: AttendanceStatus
        get() = when {
            record == null -> AttendanceStatus.ABSENT
            record.late -> AttendanceStatus.LATE
            else -> AttendanceStatus.ON_TIME
        }
}

data class AttendanceStats(val onTime: Int, val late: Int, val absent: Int) {
    val present: Int get() = onTime + late

    /** Porcentaje de entradas a tiempo sobre las entradas del periodo; null si no hubo entradas. */
    val punctuality: Int? get() = if (present == 0) null else onTime * 100 / present
}

data class WeekSummary(val workedMinutes: Long, val stats: AttendanceStats)

/** Reglas de asistencia: retrasos, ausencias, distancia a la sede y resúmenes. */
object AttendanceRules {

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun isLate(checkIn: LocalTime, sede: Sede): Boolean =
        checkIn.isAfter(sede.startTime.plusMinutes(sede.toleranceMinutes.toLong()))

    /** Distancia en metros entre dos coordenadas (fórmula del haversine). */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return (EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))).roundToInt()
    }

    /** Distancia a la sede; null si la sede todavía no tiene ubicación. */
    fun distanceToSede(latitude: Double, longitude: Double, sede: Sede): Int? {
        val sedeLat = sede.latitude ?: return null
        val sedeLon = sede.longitude ?: return null
        return distanceMeters(latitude, longitude, sedeLat, sedeLon)
    }

    fun workedMinutes(record: AttendanceRecord): Long =
        record.checkOut?.let { Duration.between(record.checkIn, it).toMinutes().coerceAtLeast(0) } ?: 0

    /** El día cuenta como ausencia si era laboral, ya pasó la hora de entrada más la tolerancia y no hubo marcaje. */
    private fun isMissed(date: LocalDate, sede: Sede, now: LocalDateTime): Boolean = when {
        date.dayOfWeek !in sede.workDays -> false
        date.isBefore(now.toLocalDate()) -> true
        date == now.toLocalDate() -> isLate(now.toLocalTime(), sede)
        else -> false
    }

    /**
     * Filas de asistencia de cada empleado para cada día del rango: su marcaje,
     * o una ausencia si el día era laboral en alguna de sus sedes. No cuenta días anteriores a su registro.
     */
    fun buildRows(
        employees: List<UserProfile>,
        records: List<AttendanceRecord>,
        sedes: List<Sede>,
        from: LocalDate,
        to: LocalDate,
        now: LocalDateTime,
    ): List<AttendanceRow> {
        val recordsByKey = records.associateBy { it.userId to it.date }
        val rows = mutableListOf<AttendanceRow>()
        var date = to
        while (!date.isBefore(from)) {
            for (employee in employees) {
                val record = recordsByKey[employee.id to date]
                val employeeSedes = sedes.filter { it.id in employee.sedeIds }
                if (record != null) {
                    rows += AttendanceRow(date, employee.id, employee.name, record.sedeName, record)
                } else if (!date.isBefore(employee.createdAt)) {
                    val missedSede = employeeSedes.firstOrNull { isMissed(date, it, now) }
                    if (missedSede != null) rows += AttendanceRow(date, employee.id, employee.name, missedSede.name, null)
                }
            }
            date = date.minusDays(1)
        }
        return rows.sortedWith(compareByDescending<AttendanceRow> { it.date }.thenByDescending { it.record?.checkIn })
    }

    fun stats(rows: List<AttendanceRow>): AttendanceStats = AttendanceStats(
        onTime = rows.count { it.status == AttendanceStatus.ON_TIME },
        late = rows.count { it.status == AttendanceStatus.LATE },
        absent = rows.count { it.status == AttendanceStatus.ABSENT },
    )

    fun weekSummary(rows: List<AttendanceRow>): WeekSummary =
        WeekSummary(rows.sumOf { row -> row.record?.let(::workedMinutes) ?: 0 }, stats(rows))

    fun mondayOf(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)
}

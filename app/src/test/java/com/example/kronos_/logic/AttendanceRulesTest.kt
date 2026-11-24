package com.example.kronos_.logic

import com.example.kronos_.data.AttendanceRecord
import com.example.kronos_.data.AttendanceStatus
import com.example.kronos_.data.Sede
import com.example.kronos_.data.UserProfile
import com.example.kronos_.report.AttendanceReport
import com.example.kronos_.report.ReportWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class AttendanceRulesTest {

    private val sede = Sede(
        id = "s1", name = "Sede Centro", address = "Calle 1", latitude = 6.2442, longitude = -75.5812,
        radiusMeters = 150, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(17, 0),
        toleranceMinutes = 10, workDays = Sede.DEFAULT_WORK_DAYS,
    )

    // Lunes 2 de noviembre de 2026.
    private val monday = LocalDate.of(2026, 11, 2)

    private val employee = UserProfile(
        id = "u1", name = "Laura Gómez", email = "laura@correo.com", document = "123456", position = "Operaria",
        role = UserProfile.ROLE_EMPLOYEE, companyId = "c1", sedeIds = listOf("s1"), createdAt = monday,
    )

    private fun record(date: LocalDate, checkIn: LocalTime, checkOut: LocalTime?) = AttendanceRecord(
        id = "u1_$date", userId = "u1", userName = employee.name, sedeId = "s1", sedeName = sede.name, date = date,
        checkIn = date.atTime(checkIn), checkOut = checkOut?.let(date::atTime),
        late = AttendanceRules.isLate(checkIn, sede),
    )

    @Test
    fun retrasoCuentaDespuesDeLaTolerancia() {
        assertFalse(AttendanceRules.isLate(LocalTime.of(8, 10), sede))
        assertTrue(AttendanceRules.isLate(LocalTime.of(8, 11), sede))
    }

    @Test
    fun distanciaEntreCoordenadasCercanas() {
        // 0,001 grados de latitud son unos 111 metros.
        val distance = AttendanceRules.distanceMeters(6.2442, -75.5812, 6.2452, -75.5812)
        assertTrue(distance in 105..117)
        assertEquals(0, AttendanceRules.distanceToSede(6.2442, -75.5812, sede))
    }

    @Test
    fun filasIncluyenAusenciasSoloEnDiasLaborales() {
        val records = listOf(
            record(monday, LocalTime.of(7, 55), LocalTime.of(17, 0)),
            record(monday.plusDays(2), LocalTime.of(8, 30), LocalTime.of(17, 0)),
        )
        // Jueves al mediodía: el jueves ya cuenta como ausencia porque pasó la hora de entrada.
        val now = LocalDateTime.of(monday.plusDays(3), LocalTime.NOON)
        val rows = AttendanceRules.buildRows(
            listOf(employee), records, listOf(sede), monday.minusDays(3), monday.plusDays(6), now,
        )

        assertEquals(
            listOf(AttendanceStatus.ABSENT, AttendanceStatus.LATE, AttendanceStatus.ABSENT, AttendanceStatus.ON_TIME),
            rows.map { it.status },
        )
        val stats = AttendanceRules.stats(rows)
        assertEquals(50, stats.punctuality)
        assertEquals(2, stats.absent)
    }

    @Test
    fun antesDeLaHoraDeEntradaNoHayAusencia() {
        val now = LocalDateTime.of(monday, LocalTime.of(7, 0))
        assertTrue(AttendanceRules.buildRows(listOf(employee), emptyList(), listOf(sede), monday, monday, now).isEmpty())
    }

    @Test
    fun resumenSumaLasHorasTrabajadas() {
        val records = listOf(
            record(monday, LocalTime.of(8, 0), LocalTime.of(17, 0)),
            record(monday.plusDays(1), LocalTime.of(8, 0), LocalTime.of(12, 30)),
        )
        val now = LocalDateTime.of(monday.plusDays(1), LocalTime.of(18, 0))
        val rows = AttendanceRules.buildRows(listOf(employee), records, listOf(sede), monday, monday.plusDays(1), now)
        assertEquals(13 * 60 + 30L, AttendanceRules.weekSummary(rows).workedMinutes)
    }

    @Test
    fun csvUsaPuntoYComaYEscapaComillas() {
        val rows = AttendanceRules.buildRows(
            listOf(employee.copy(name = "Ana \"La Jefa\"")),
            listOf(record(monday, LocalTime.of(8, 0), null)),
            listOf(sede), monday, monday, LocalDateTime.of(monday, LocalTime.NOON),
        )
        val report = AttendanceReport(monday, monday, "Todas las sedes", "Todos los empleados", rows)
        val lines = ReportWriter.toCsv(report).trim().lines()
        assertEquals("Fecha;Empleado;Sede;Entrada;Salida;Estado", lines[0])
        assertEquals("\"2026-11-02\";\"Ana \"\"La Jefa\"\"\";\"Sede Centro\";\"08:00\";\"—\";\"A tiempo\"", lines[1])
    }
}

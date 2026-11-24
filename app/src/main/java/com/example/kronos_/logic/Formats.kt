package com.example.kronos_.logic

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Textos de fechas, horas y duraciones tal como se muestran en la app. */
object Formats {

    private val shortDays = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
    private val longDays = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
    private val shortMonths = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    private val longMonths = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    )
    private val hourFormat = DateTimeFormatter.ofPattern("HH:mm")

    fun time(value: LocalTime): String = value.format(hourFormat)

    fun time(value: LocalDateTime?): String = value?.toLocalTime()?.let(::time) ?: "—"

    fun timeRange(start: LocalDateTime?, end: LocalDateTime?): String =
        if (start == null) "—" else "${time(start)} – ${time(end)}"

    fun dayName(day: DayOfWeek): String = longDays[day.value - 1]

    /** "Vie 5 sep" */
    fun shortDate(date: LocalDate): String =
        "${shortDays[date.dayOfWeek.value - 1]} ${date.dayOfMonth} ${shortMonths[date.monthValue - 1]}"

    /** "12 sep 2026" */
    fun date(date: LocalDate): String = "${date.dayOfMonth} ${shortMonths[date.monthValue - 1]} ${date.year}"

    /** "Lunes, 7 de septiembre" */
    fun longDate(date: LocalDate): String =
        "${dayName(date.dayOfWeek)}, ${date.dayOfMonth} de ${longMonths[date.monthValue - 1]}"

    /** "8 sep, 10:20" */
    fun dateTime(value: LocalDateTime): String =
        "${value.dayOfMonth} ${shortMonths[value.monthValue - 1]}, ${time(value)}"

    /** "12 sep 2026", "1 – 7 sep 2026" o "28 ago 2026 – 3 sep 2026". */
    fun dateRange(from: LocalDate, to: LocalDate): String = when {
        from == to -> date(from)
        from.year == to.year && from.month == to.month -> "${from.dayOfMonth} – ${date(to)}"
        else -> "${date(from)} – ${date(to)}"
    }

    /** "1 – 7 de septiembre" */
    fun weekRange(monday: LocalDate): String {
        val sunday = monday.plusDays(6)
        return if (monday.month == sunday.month) {
            "${monday.dayOfMonth} – ${sunday.dayOfMonth} de ${longMonths[sunday.monthValue - 1]}"
        } else {
            "${monday.dayOfMonth} de ${longMonths[monday.monthValue - 1]} – " +
                "${sunday.dayOfMonth} de ${longMonths[sunday.monthValue - 1]}"
        }
    }

    /** "32h 40m" */
    fun duration(minutes: Long): String = "${minutes / 60}h ${minutes % 60}m"

    /** "Hace 10 min", "Hace 3 h", "Ayer" o la fecha. */
    fun relative(value: LocalDateTime, now: LocalDateTime = LocalDateTime.now()): String {
        val minutes = Duration.between(value, now).toMinutes()
        return when {
            minutes < 1 -> "Hace un momento"
            minutes < 60 -> "Hace $minutes min"
            value.toLocalDate() == now.toLocalDate() -> "Hace ${minutes / 60} h"
            value.toLocalDate() == now.toLocalDate().minusDays(1) -> "Ayer"
            else -> date(value.toLocalDate())
        }
    }

    /** Iniciales para el avatar: "Juan Pérez" -> "JP". */
    fun initials(name: String): String =
        name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.first().uppercase() }

    fun firstName(name: String): String = name.trim().substringBefore(' ')

    fun fileSize(bytes: Long): String =
        if (bytes < 1024) "$bytes B" else "${(bytes + 1023) / 1024} KB"
}

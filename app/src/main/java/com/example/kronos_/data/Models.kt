package com.example.kronos_.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class UserProfile(
    val id: String,
    val name: String,
    val email: String,
    val document: String,
    val position: String,
    val role: String,
    val companyId: String,
    val sedeIds: List<String>,
    val createdAt: LocalDate,
) {
    val isAdmin: Boolean get() = role == ROLE_ADMIN

    companion object {
        const val ROLE_ADMIN = "admin"
        const val ROLE_EMPLOYEE = "employee"
    }
}

data class Company(val id: String, val name: String)

data class Sede(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val radiusMeters: Int,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val toleranceMinutes: Int,
    val workDays: Set<DayOfWeek>,
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null

    companion object {
        const val DEFAULT_RADIUS_METERS = 150
        const val DEFAULT_TOLERANCE_MINUTES = 10
        val DEFAULT_WORK_DAYS: Set<DayOfWeek> = DayOfWeek.entries.take(5).toSet()
    }
}

/** Un marcaje del día: entrada y, si ya la hubo, salida. */
data class AttendanceRecord(
    val id: String,
    val userId: String,
    val userName: String,
    val sedeId: String,
    val sedeName: String,
    val date: LocalDate,
    val checkIn: LocalDateTime,
    val checkOut: LocalDateTime?,
    val late: Boolean,
)

enum class AttendanceStatus(val label: String) {
    ON_TIME("A tiempo"),
    LATE("Tarde"),
    ABSENT("Ausente"),
}

enum class RequestStatus(val code: String, val label: String) {
    PENDING("pending", "Pendiente"),
    APPROVED("approved", "Aprobado"),
    REJECTED("rejected", "Rechazado");

    companion object {
        fun fromCode(code: String?): RequestStatus = entries.firstOrNull { it.code == code } ?: PENDING
    }
}

data class PermitRequest(
    val id: String,
    val userId: String,
    val userName: String,
    val userPosition: String,
    val sedeName: String,
    val type: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val reason: String,
    val status: RequestStatus,
    val createdAt: LocalDateTime,
    val reviewedAt: LocalDateTime?,
)

/** Intento de marcaje fuera del radio autorizado de la sede. */
data class LocationAlert(
    val id: String,
    val userId: String,
    val userName: String,
    val sedeName: String,
    val distanceMeters: Int,
    val createdAt: LocalDateTime,
)

package com.example.kronos_.data

import android.net.Uri
import com.example.kronos_.auth.UserFacingException
import com.example.kronos_.report.GeneratedReport
import com.google.firebase.auth.FirebaseUser
import java.time.LocalDateTime

/** Datos del usuario que inició sesión y lo que se pasa de una pantalla a otra. */
object Session {

    var profile: UserProfile? = null
    var company: Company? = null

    /** Todas las sedes de la empresa. */
    var sedes: List<Sede> = emptyList()

    /** Sede en la que el empleado marca hoy. */
    var currentSede: Sede? = null

    /** Marcajes del empleado, cargados en el inicio. */
    var myRecords: List<AttendanceRecord>? = null

    var lastCheckTime: LocalDateTime? = null
    var lastDistanceMeters: Int? = null
    var selectedRequest: PermitRequest? = null
    var selectedRequestTab: Int = 0
    var createdEmployee: UserProfile? = null
    var report: GeneratedReport? = null
    var savedReportUri: Uri? = null

    fun requireProfile(): UserProfile =
        profile ?: throw UserFacingException("Tu sesión terminó. Inicia sesión de nuevo.")

    fun sedesOf(user: UserProfile): List<Sede> = sedes.filter { it.id in user.sedeIds }

    /** Carga el perfil, la empresa y las sedes del usuario que inició sesión. */
    suspend fun load(account: FirebaseUser) {
        val user = KronosRepository.loadProfile(account.uid) ?: KronosRepository.createCompanyForAccount(account)
        val loadedSedes = KronosRepository.loadSedes(user.companyId)
        clear()
        profile = user
        company = KronosRepository.loadCompany(user.companyId)
        sedes = loadedSedes
        currentSede = sedesOf(user).firstOrNull()
    }

    fun clear() {
        profile = null
        company = null
        sedes = emptyList()
        currentSede = null
        myRecords = null
        lastCheckTime = null
        lastDistanceMeters = null
        selectedRequest = null
        selectedRequestTab = 0
        createdEmployee = null
        report = null
        savedReportUri = null
    }
}

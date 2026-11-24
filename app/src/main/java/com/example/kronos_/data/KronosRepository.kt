package com.example.kronos_.data

import android.content.Context
import com.example.kronos_.auth.UserFacingException
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import java.util.UUID

/**
 * Acceso a Firestore. Estructura:
 * users/{uid}, companies/{id} y dentro de cada empresa: sedes, attendance, requests y alerts.
 */
object KronosRepository {

    /** Tiempo máximo esperando al servidor; sin internet Firestore guarda el cambio y lo envía después. */
    private const val SAVE_TIMEOUT_MS = 10_000L
    private const val EMPLOYEE_CREATOR_APP = "creacion-empleados"

    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    private fun users() = db.collection("users")
    private fun company(id: String) = db.collection("companies").document(id)
    private fun sedes(companyId: String) = company(companyId).collection("sedes")
    private fun attendance(companyId: String) = company(companyId).collection("attendance")
    private fun requests(companyId: String) = company(companyId).collection("requests")
    private fun alerts(companyId: String) = company(companyId).collection("alerts")

    // ----- Cuentas -----

    suspend fun loadProfile(uid: String): UserProfile? =
        users().document(uid).get().await().takeIf { it.exists() }?.toProfile()

    /** Crea la cuenta del administrador y su empresa. */
    suspend fun registerCompany(name: String, email: String, password: String, companyName: String): UserProfile {
        auth.setLanguageCode("es")
        val user = auth.createUserWithEmailAndPassword(email, password).await().user
            ?: throw UserFacingException("No se pudo crear la cuenta. Inténtalo de nuevo.")
        try {
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build()).await()
            return createCompany(user.uid, name, email, companyName)
        } catch (error: Exception) {
            // Sin empresa la cuenta queda inservible: se borra para poder registrarse de nuevo.
            user.delete()
            throw error
        }
    }

    /**
     * Cuentas creadas desde la consola de Firebase (Authentication > Agregar usuario) no tienen perfil:
     * la primera vez que entran se les crea una empresa propia y quedan como administradores.
     */
    suspend fun createCompanyForAccount(user: FirebaseUser): UserProfile {
        val email = user.email.orEmpty()
        val name = user.displayName?.takeIf { it.isNotBlank() } ?: email.substringBefore('@')
        return createCompany(user.uid, name, email, "Empresa de $name")
    }

    private suspend fun createCompany(uid: String, name: String, email: String, companyName: String): UserProfile {
        val companyRef = db.collection("companies").document()
        val profile = UserProfile(
            id = uid, name = name, email = email, document = "", position = "Administrador",
            role = UserProfile.ROLE_ADMIN, companyId = companyRef.id, sedeIds = emptyList(), createdAt = LocalDate.now(),
        )
        db.batch()
            .set(companyRef, mapOf("name" to companyName, "ownerId" to uid))
            .set(users().document(uid), profile.toMap())
            .commit().await()
        return profile
    }

    /**
     * Crea la cuenta de un empleado sin cerrar la sesión del administrador (usa una instancia aparte de Firebase)
     * y le envía un correo para que elija su contraseña.
     */
    suspend fun createEmployee(
        context: Context,
        companyId: String,
        name: String,
        document: String,
        email: String,
        position: String,
        sedeId: String,
    ): UserProfile {
        val creatorApp = FirebaseApp.getApps(context).firstOrNull { it.name == EMPLOYEE_CREATOR_APP }
            ?: FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, EMPLOYEE_CREATOR_APP)
        val creatorAuth = FirebaseAuth.getInstance(creatorApp).apply { setLanguageCode("es") }
        val temporaryPassword = UUID.randomUUID().toString()
        val newUser = creatorAuth.createUserWithEmailAndPassword(email, temporaryPassword).await().user
            ?: throw UserFacingException("No se pudo crear la cuenta del empleado.")
        val profile = UserProfile(
            id = newUser.uid, name = name, email = email, document = document, position = position,
            role = UserProfile.ROLE_EMPLOYEE, companyId = companyId, sedeIds = listOf(sedeId), createdAt = LocalDate.now(),
        )
        try {
            users().document(newUser.uid).set(profile.toMap()).await()
        } catch (error: Exception) {
            newUser.delete()
            creatorAuth.signOut()
            throw error
        }
        try {
            creatorAuth.sendPasswordResetEmail(email).await()
        } catch (_: Exception) {
            throw UserFacingException(
                "El empleado quedó creado, pero no se pudo enviar el correo. Pídele que use \"¿Olvidaste tu contraseña?\".",
            )
        } finally {
            creatorAuth.signOut()
        }
        return profile
    }

    suspend fun loadCompany(id: String): Company =
        company(id).get().await().let { Company(it.id, it.getString("name").orEmpty()) }

    suspend fun loadEmployees(companyId: String): List<UserProfile> =
        users().whereEqualTo("companyId", companyId).get().await()
            .map { it.toProfile() }
            .filter { !it.isAdmin }
            .sortedBy { it.name.lowercase() }

    // ----- Sedes -----

    suspend fun loadSedes(companyId: String): List<Sede> =
        sedes(companyId).get().await().map { it.toSede() }.sortedBy { it.name.lowercase() }

    /** Crea la sede si [sedeId] es null; si no, la actualiza. */
    suspend fun saveSede(companyId: String, sedeId: String?, sede: Sede) {
        val data = mapOf(
            "name" to sede.name,
            "address" to sede.address,
            "latitude" to sede.latitude,
            "longitude" to sede.longitude,
            "radiusMeters" to sede.radiusMeters,
            "startTime" to sede.startTime.toString(),
            "endTime" to sede.endTime.toString(),
            "toleranceMinutes" to sede.toleranceMinutes,
            "workDays" to sede.workDays.map { it.value }.sorted(),
        )
        val ref = if (sedeId == null) sedes(companyId).document() else sedes(companyId).document(sedeId)
        ref.set(data).awaitSaved()
    }

    // ----- Marcajes -----

    /** Id del marcaje del día: uno por empleado y fecha. */
    fun attendanceId(userId: String, date: LocalDate) = "${userId}_$date"

    suspend fun attendanceOfUser(companyId: String, userId: String): List<AttendanceRecord> =
        attendance(companyId).whereEqualTo("userId", userId).get().await()
            .map { it.toAttendance() }
            .sortedByDescending { it.checkIn }

    suspend fun attendanceBetween(companyId: String, from: LocalDate, to: LocalDate): List<AttendanceRecord> =
        attendance(companyId)
            .whereGreaterThanOrEqualTo("date", from.toString())
            .whereLessThanOrEqualTo("date", to.toString())
            .get().await()
            .map { it.toAttendance() }

    /** No espera al servidor: el marcaje queda guardado en el teléfono y se sincroniza al haber internet. */
    fun saveCheckIn(companyId: String, user: UserProfile, sede: Sede, time: LocalDateTime, late: Boolean, distance: Int): Task<Void> =
        attendance(companyId).document(attendanceId(user.id, time.toLocalDate())).set(
            mapOf(
                "userId" to user.id,
                "userName" to user.name,
                "sedeId" to sede.id,
                "sedeName" to sede.name,
                "date" to time.toLocalDate().toString(),
                "checkIn" to time.toTimestamp(),
                "checkOut" to null,
                "late" to late,
                "distanceMeters" to distance,
            ),
        )

    fun saveCheckOut(companyId: String, recordId: String, time: LocalDateTime): Task<Void> =
        attendance(companyId).document(recordId).update("checkOut", time.toTimestamp())

    /** Marcajes del usuario que aún no llegan al servidor. */
    suspend fun pendingSyncCount(companyId: String, userId: String): Int =
        attendance(companyId).whereEqualTo("userId", userId).get(Source.CACHE).await()
            .count { it.metadata.hasPendingWrites() }

    // ----- Alertas de ubicación -----

    fun saveLocationAlert(companyId: String, user: UserProfile, sede: Sede, distance: Int): Task<*> =
        alerts(companyId).add(
            mapOf(
                "userId" to user.id,
                "userName" to user.name,
                "sedeName" to sede.name,
                "distanceMeters" to distance,
                "createdAt" to LocalDateTime.now().toTimestamp(),
            ),
        )

    suspend fun alertsOfUser(companyId: String, userId: String): List<LocationAlert> =
        alerts(companyId).whereEqualTo("userId", userId).get().await().map { it.toAlert() }

    suspend fun alertsSince(companyId: String, since: LocalDateTime): List<LocationAlert> =
        alerts(companyId).whereGreaterThanOrEqualTo("createdAt", since.toTimestamp()).get().await().map { it.toAlert() }

    // ----- Solicitudes de permiso -----

    suspend fun sendRequest(
        companyId: String,
        user: UserProfile,
        sedeName: String,
        type: String,
        startDate: LocalDate,
        endDate: LocalDate,
        reason: String,
    ) {
        requests(companyId).add(
            mapOf(
                "userId" to user.id,
                "userName" to user.name,
                "userPosition" to user.position,
                "sedeName" to sedeName,
                "type" to type,
                "startDate" to startDate.toString(),
                "endDate" to endDate.toString(),
                "reason" to reason,
                "status" to RequestStatus.PENDING.code,
                "createdAt" to LocalDateTime.now().toTimestamp(),
                "reviewedAt" to null,
            ),
        ).awaitSaved()
    }

    suspend fun requestsOfUser(companyId: String, userId: String): List<PermitRequest> =
        requests(companyId).whereEqualTo("userId", userId).get().await()
            .map { it.toRequest() }
            .sortedByDescending { it.createdAt }

    suspend fun requestsWithStatus(companyId: String, status: RequestStatus): List<PermitRequest> =
        requests(companyId).whereEqualTo("status", status.code).get().await()
            .map { it.toRequest() }
            .sortedByDescending { it.createdAt }

    suspend fun reviewRequest(companyId: String, requestId: String, status: RequestStatus) {
        requests(companyId).document(requestId)
            .update(mapOf("status" to status.code, "reviewedAt" to LocalDateTime.now().toTimestamp()))
            .awaitSaved()
    }

    suspend fun cancelRequest(companyId: String, requestId: String) {
        requests(companyId).document(requestId).delete().awaitSaved()
    }

    // ----- Conversión entre documentos y modelos -----

    private suspend fun Task<*>.awaitSaved() {
        withTimeoutOrNull(SAVE_TIMEOUT_MS) { await() }
    }

    private fun LocalDateTime.toTimestamp() = Timestamp(Date.from(atZone(ZoneId.systemDefault()).toInstant()))

    private fun Timestamp.toLocalDateTime(): LocalDateTime =
        LocalDateTime.ofInstant(toDate().toInstant(), ZoneId.systemDefault())

    private fun UserProfile.toMap() = mapOf(
        "name" to name,
        "email" to email,
        "document" to document,
        "position" to position,
        "role" to role,
        "companyId" to companyId,
        "sedeIds" to sedeIds,
        "createdAt" to createdAt.toString(),
    )

    private fun DocumentSnapshot.stringList(field: String): List<String> =
        (get(field) as? List<*>)?.filterIsInstance<String>().orEmpty()

    private fun DocumentSnapshot.toProfile() = UserProfile(
        id = id,
        name = getString("name").orEmpty(),
        email = getString("email").orEmpty(),
        document = getString("document").orEmpty(),
        position = getString("position").orEmpty(),
        role = getString("role") ?: UserProfile.ROLE_EMPLOYEE,
        companyId = getString("companyId").orEmpty(),
        sedeIds = stringList("sedeIds"),
        createdAt = getString("createdAt")?.let(LocalDate::parse) ?: LocalDate.now(),
    )

    private fun DocumentSnapshot.toSede() = Sede(
        id = id,
        name = getString("name").orEmpty(),
        address = getString("address").orEmpty(),
        latitude = getDouble("latitude"),
        longitude = getDouble("longitude"),
        radiusMeters = getLong("radiusMeters")?.toInt() ?: Sede.DEFAULT_RADIUS_METERS,
        startTime = getString("startTime")?.let(LocalTime::parse) ?: LocalTime.of(8, 0),
        endTime = getString("endTime")?.let(LocalTime::parse) ?: LocalTime.of(17, 0),
        toleranceMinutes = getLong("toleranceMinutes")?.toInt() ?: Sede.DEFAULT_TOLERANCE_MINUTES,
        workDays = (get("workDays") as? List<*>)
            ?.mapNotNull { (it as? Number)?.toInt()?.let(DayOfWeek::of) }?.toSet()
            ?: Sede.DEFAULT_WORK_DAYS,
    )

    private fun DocumentSnapshot.toAttendance(): AttendanceRecord {
        val checkIn = getTimestamp("checkIn")?.toLocalDateTime() ?: LocalDateTime.now()
        return AttendanceRecord(
            id = id,
            userId = getString("userId").orEmpty(),
            userName = getString("userName").orEmpty(),
            sedeId = getString("sedeId").orEmpty(),
            sedeName = getString("sedeName").orEmpty(),
            date = getString("date")?.let(LocalDate::parse) ?: checkIn.toLocalDate(),
            checkIn = checkIn,
            checkOut = getTimestamp("checkOut")?.toLocalDateTime(),
            late = getBoolean("late") ?: false,
        )
    }

    private fun DocumentSnapshot.toRequest(): PermitRequest {
        val start = getString("startDate")?.let(LocalDate::parse) ?: LocalDate.now()
        return PermitRequest(
            id = id,
            userId = getString("userId").orEmpty(),
            userName = getString("userName").orEmpty(),
            userPosition = getString("userPosition").orEmpty(),
            sedeName = getString("sedeName").orEmpty(),
            type = getString("type").orEmpty(),
            startDate = start,
            endDate = getString("endDate")?.let(LocalDate::parse) ?: start,
            reason = getString("reason").orEmpty(),
            status = RequestStatus.fromCode(getString("status")),
            createdAt = getTimestamp("createdAt")?.toLocalDateTime() ?: LocalDateTime.now(),
            reviewedAt = getTimestamp("reviewedAt")?.toLocalDateTime(),
        )
    }

    private fun DocumentSnapshot.toAlert() = LocationAlert(
        id = id,
        userId = getString("userId").orEmpty(),
        userName = getString("userName").orEmpty(),
        sedeName = getString("sedeName").orEmpty(),
        distanceMeters = getLong("distanceMeters")?.toInt() ?: 0,
        createdAt = getTimestamp("createdAt")?.toLocalDateTime() ?: LocalDateTime.now(),
    )
}

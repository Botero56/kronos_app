package com.example.kronos_.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.kronos_.MainActivity
import com.example.kronos_.MainActivity.Companion.BACK
import com.example.kronos_.auth.CredentialValidator
import com.example.kronos_.auth.UserFacingException
import com.example.kronos_.auth.errorMessage
import com.example.kronos_.data.KronosRepository
import com.example.kronos_.data.RequestStatus
import com.example.kronos_.data.Session
import com.example.kronos_.logic.Formats
import com.example.kronos_.ui.showRows
import com.google.firebase.auth.EmailAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalDateTime
import com.example.kronos_.R.id as v
import com.example.kronos_.R.layout as s

private const val SPLASH_DELAY_MS = 1500L

internal fun EditText.trimmedText(): String = text.toString().trim()

/** Si ya hay una sesión abierta, carga sus datos y pide verificar la identidad; si no, va al login. */
internal fun MainActivity.openSplashScreen() {
    window.decorView.postDelayed({
        if (!isShowing(s.screen_splash)) return@postDelayed
        val user = auth.currentUser ?: return@postDelayed startFrom(s.screen_login)
        lifecycleScope.launch {
            val loaded = runCatching { Session.load(user) }
            if (!isShowing(s.screen_splash)) return@launch
            if (loaded.isSuccess) {
                startFrom(s.screen_auth)
            } else {
                showMessage(errorMessage(loaded.exceptionOrNull()))
                signOut()
            }
        }
    }, SPLASH_DELAY_MS)
}

/** Inicio de sesión con correo y contraseña. El de administrador solo deja entrar cuentas de administrador. */
internal fun MainActivity.setupLoginScreen() {
    val screen = if (isShowing(s.screen_login_admin)) s.screen_login_admin else s.screen_login
    val email = findViewById<EditText>(v.email)
    val password = findViewById<EditText>(v.password)
    val loginButton = findViewById<Button>(v.login)
    loginButton.setOnClickListener {
        email.error = CredentialValidator.emailError(email.text.toString())
        password.error = CredentialValidator.loginPasswordError(password.text.toString())
        if (email.error != null || password.error != null) return@setOnClickListener

        launchTask(loginButton) {
            val user = auth.signInWithEmailAndPassword(email.trimmedText(), password.text.toString()).await().user
                ?: return@launchTask
            try {
                Session.load(user)
                if (screen == s.screen_login_admin && !Session.requireProfile().isAdmin) {
                    throw UserFacingException(
                        "Esta cuenta no es de administrador. Entra desde el inicio de sesión de empleados.",
                    )
                }
            } catch (error: Exception) {
                auth.signOut()
                Session.clear()
                throw error
            }
            goIfStillOn(screen, s.screen_auth)
        }
    }
}

/** Registro de una empresa nueva: crea la cuenta del administrador y la empresa. */
internal fun MainActivity.setupRegisterScreen() {
    val name = findViewById<EditText>(v.name)
    val email = findViewById<EditText>(v.email)
    val company = findViewById<EditText>(v.company)
    val password = findViewById<EditText>(v.password)
    val terms = findViewById<CheckBox>(v.terms)
    val createButton = findViewById<Button>(v.create)
    createButton.setOnClickListener {
        name.error = CredentialValidator.nameError(name.text.toString())
        email.error = CredentialValidator.emailError(email.text.toString())
        company.error = CredentialValidator.requiredError(company.text.toString(), "Escribe el nombre de la empresa.")
        password.error = CredentialValidator.newPasswordError(password.text.toString())
        if (listOf(name, email, company, password).any { it.error != null }) return@setOnClickListener
        if (!terms.isChecked) return@setOnClickListener showMessage("Debes aceptar los términos y la política de privacidad.")

        launchTask(createButton) {
            KronosRepository.registerCompany(
                name.trimmedText(), email.trimmedText(), password.text.toString(), company.trimmedText(),
            )
            Session.load(auth.currentUser ?: return@launchTask)
            goIfStillOn(s.screen_register, s.screen_auth)
        }
    }
}

internal fun MainActivity.setupRecoverScreen() {
    val email = findViewById<EditText>(v.email)
    val sendButton = findViewById<Button>(v.send)
    sendButton.setOnClickListener {
        email.error = CredentialValidator.emailError(email.text.toString())
        if (email.error != null) return@setOnClickListener
        launchTask(sendButton) {
            auth.setLanguageCode("es")
            auth.sendPasswordResetEmail(email.trimmedText()).await()
            showMessage("Si el correo está registrado, te llegará un enlace para restablecer la contraseña.")
            goIfStillOn(s.screen_recover, s.screen_login)
        }
    }
}

/** Después del login se confirma la identidad con el patrón o la huella del teléfono, según la pestaña elegida. */
internal fun MainActivity.setupIdentityCheckScreen() {
    val patternTab = findViewById<View>(v.tab_a)
    findViewById<Button>(v.verify).setOnClickListener {
        val method = if (patternTab.isSelected) {
            MainActivity.IdentityMethod.SCREEN_LOCK
        } else {
            MainActivity.IdentityMethod.FINGERPRINT
        }
        verifyIdentity("Verificación de identidad", method, continueIfUnavailable = true) {
            goIfStillOn(s.screen_auth, s.screen_auth_success)
        }
    }
}

internal fun MainActivity.setupIdentityConfirmedScreen() {
    findViewById<Button>(v.next).setOnClickListener {
        val profile = Session.profile ?: return@setOnClickListener signOut()
        when {
            profile.isAdmin -> go(s.screen_dashboard)
            Session.sedesOf(profile).size > 1 -> go(s.screen_sede)
            else -> go(s.screen_home)
        }
    }
}

internal fun MainActivity.setupProfileScreen() {
    val profile = Session.profile ?: return
    findViewById<TextView>(v.avatar).text = Formats.initials(profile.name)
    findViewById<TextView>(v.profile_name).text = profile.name
    findViewById<TextView>(v.profile_position).text = profile.position
    findViewById<TextView>(v.profile_email).text = profile.email
    findViewById<TextView>(v.profile_document).text = profile.document.ifBlank { "—" }
    findViewById<TextView>(v.profile_sede).text = if (profile.isAdmin) {
        Session.company?.name.orEmpty()
    } else {
        Session.sedesOf(profile).joinToString { it.name }.ifBlank { "Sin sede asignada" }
    }
}

internal fun MainActivity.setupChangePasswordScreen() {
    val current = findViewById<EditText>(v.current_password)
    val newPassword = findViewById<EditText>(v.new_password)
    val confirmation = findViewById<EditText>(v.confirm_password)
    val saveButton = findViewById<Button>(v.save)
    saveButton.setOnClickListener {
        current.error = CredentialValidator.loginPasswordError(current.text.toString())
        newPassword.error = CredentialValidator.newPasswordError(newPassword.text.toString())
        confirmation.error =
            if (confirmation.text.toString() != newPassword.text.toString()) "Las contraseñas no coinciden." else null
        if (listOf(current, newPassword, confirmation).any { it.error != null }) return@setOnClickListener

        launchTask(saveButton) {
            val user = auth.currentUser ?: return@launchTask signOut()
            // Firebase pide volver a confirmar la contraseña actual antes de cambiarla.
            user.reauthenticate(EmailAuthProvider.getCredential(user.email.orEmpty(), current.text.toString())).await()
            user.updatePassword(newPassword.text.toString()).await()
            showMessage("Contraseña actualizada.")
            goIfStillOn(s.screen_change_password, BACK)
        }
    }
}

internal fun MainActivity.setupHelpScreen() {
    findViewById<TextView>(v.app_version).text = packageManager.getPackageInfo(packageName, 0).versionName
}

internal fun MainActivity.setupNoLocationScreen() {
    findViewById<Button>(v.open_settings).setOnClickListener {
        // Sin permiso se abren los ajustes de la app; con permiso, falta encender la ubicación del teléfono.
        val intent = if (hasLocationPermission()) {
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        }
        startActivity(intent)
        go(BACK)
    }
}

internal fun MainActivity.setupOfflineScreen() {
    val profile = Session.profile ?: return
    val counter = findViewById<TextView>(v.pending_count)
    launchTask {
        counter.text = KronosRepository.pendingSyncCount(profile.companyId, profile.id).toString()
    }
}

/** Avisos del empleado: salida pendiente, permisos revisados y marcajes fuera de la sede. */
internal fun MainActivity.setupNotificationsScreen() {
    val profile = Session.profile ?: return
    val list = findViewById<LinearLayout>(v.list)
    launchTask {
        val today = LocalDate.now()
        val notices = mutableListOf<Pair<LocalDateTime, String>>()
        KronosRepository.attendanceOfUser(profile.companyId, profile.id)
            .firstOrNull { it.date == today && it.checkOut == null }
            ?.let { notices += it.checkIn to "Recuerda marcar tu salida." }
        KronosRepository.requestsOfUser(profile.companyId, profile.id)
            .filter { it.status != RequestStatus.PENDING && it.reviewedAt != null }
            .forEach { request ->
                val result = if (request.status == RequestStatus.APPROVED) "aprobado" else "rechazado"
                notices += request.reviewedAt!! to
                    "Tu permiso de ${request.type.lowercase()} del ${Formats.date(request.startDate)} fue $result."
            }
        KronosRepository.alertsOfUser(profile.companyId, profile.id).forEach { alert ->
            notices += alert.createdAt to "Marcaje fuera de la ubicación autorizada (${alert.sedeName})."
        }
        list.showRows(notices.sortedByDescending { it.first }.take(MAX_NOTICES), "No tienes notificaciones.") { row, notice ->
            row.title.text = notice.second
            row.setSubtitle(Formats.relative(notice.first))
        }
    }
}

private const val MAX_NOTICES = 20

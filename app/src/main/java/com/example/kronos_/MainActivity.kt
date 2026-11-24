package com.example.kronos_

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.lifecycleScope
import com.example.kronos_.auth.errorMessage
import com.example.kronos_.data.Session
import com.example.kronos_.report.ReportFormat
import com.example.kronos_.screens.*
import com.example.kronos_.ui.ThemePreference
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.example.kronos_.R.id as v
import com.example.kronos_.R.layout as s

/**
 * Única Activity: cada pantalla es un layout XML. Aquí están la navegación y lo que comparten las pantallas
 * (mensajes, verificación de identidad, ubicación y permisos). Lo propio de cada pantalla está en el paquete screens.
 */
class MainActivity : AppCompatActivity() {

    internal val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    /** Ir a una pantalla raíz borra el historial (no se puede volver atrás a lo anterior). */
    private val roots = setOf(s.screen_login, s.screen_home, s.screen_dashboard)

    /** Pantallas que no necesitan una sesión cargada. */
    private val publicScreens = setOf(
        s.screen_splash, s.screen_login, s.screen_login_admin, s.screen_register, s.screen_recover, s.screen_reset,
    )

    /** Barra inferior del administrador: ítem -> pantalla. */
    private val adminBar = mapOf(
        v.nav_dashboard to s.screen_dashboard,
        v.nav_employees to s.screen_employees,
        v.nav_requests to s.screen_admin_requests,
        v.nav_history to s.screen_admin_history,
    )

    /** Botones que solo navegan: pantalla -> (vista que se toca -> pantalla destino). */
    private val links: Map<Int, Map<Int, Int>> = mapOf(
        // Comunes
        s.screen_login to mapOf(
            v.forgot to s.screen_recover, v.register to s.screen_register, v.admin to s.screen_login_admin,
        ),
        s.screen_login_admin to mapOf(v.forgot to s.screen_recover, v.register to s.screen_register),
        s.screen_reset to mapOf(v.save to s.screen_login),
        s.screen_profile to mapOf(v.settings to s.screen_settings, v.edit to s.screen_change_password),
        s.screen_settings to mapOf(v.privacy to s.screen_help),
        s.screen_help to mapOf(v.support to s.screen_offline),

        // Empleado
        s.screen_home to mapOf(
            v.notifications to s.screen_notifications, v.settings to s.screen_settings,
            v.profile to s.screen_profile, v.last_entry to s.screen_history, v.week_hours to s.screen_summary,
            v.schedule to s.screen_schedule, v.request_permit to s.screen_request_permit,
        ),
        s.screen_confirm_in to mapOf(v.home to s.screen_home, v.profile to s.screen_profile),
        s.screen_confirm_out to mapOf(v.home to s.screen_home),
        s.screen_out_of_range to mapOf(v.cancel to BACK),
        s.screen_my_requests to mapOf(v.new_request to s.screen_request_permit),

        // Administrador
        s.screen_dashboard to adminBar + mapOf(
            v.sede to s.screen_sede_edit, v.report to s.screen_report,
            v.stat1 to s.screen_admin_history, v.stat2 to s.screen_admin_history,
            v.stat3 to s.screen_alerts, v.stat4 to s.screen_admin_history,
        ),
        s.screen_employees to adminBar + mapOf(v.add to s.screen_new_employee),
        s.screen_admin_history to adminBar,
        s.screen_admin_requests to adminBar,
        s.screen_employee_created to mapOf(v.view_employees to s.screen_employees),
        s.screen_report_done to mapOf(v.home to s.screen_dashboard, v.share to s.screen_report_share),
        s.screen_report_download to mapOf(v.home to s.screen_dashboard),
        s.screen_report_view to mapOf(v.share to s.screen_report_share, v.home to s.screen_dashboard),
    )

    private val stack = ArrayDeque<Int>()

    private var onLocationPermissionResult: ((Boolean) -> Unit)? = null
    private val locationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            onLocationPermissionResult?.invoke(result.values.any { it })
            onLocationPermissionResult = null
        }

    private var onReportSaved: ((Uri?) -> Unit)? = null
    private val savePdfReport =
        registerForActivityResult(ActivityResultContracts.CreateDocument(ReportFormat.PDF.mimeType)) { onReportSaved?.invoke(it) }
    private val saveCsvReport =
        registerForActivityResult(ActivityResultContracts.CreateDocument(ReportFormat.EXCEL.mimeType)) { onReportSaved?.invoke(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Deja espacio para la barra de estado y la de navegación.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        onBackPressedDispatcher.addCallback(this) { go(BACK) }
        // Al cambiar el tema la Activity se recrea: se recupera la pila para seguir en la misma pantalla.
        // Si Android cerró la app, la sesión se vuelve a cargar desde el inicio.
        val saved = savedInstanceState?.getIntArray(STACK)
        val canRestore = saved != null && saved.isNotEmpty() && (Session.profile != null || saved.last() in publicScreens)
        if (canRestore) {
            stack.addAll(saved.toList())
            show(stack.last())
        } else {
            go(s.screen_splash)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntArray(STACK, stack.toIntArray())
    }

    // ----- Navegación -----

    internal fun go(screen: Int) {
        when {
            screen == BACK -> if (stack.size > 1) stack.removeLast() else return finish()
            screen in roots -> { stack.clear(); stack.addLast(screen) }
            screen in stack -> while (stack.last() != screen) stack.removeLast() // volver a una pantalla anterior
            else -> stack.addLast(screen)
        }
        show(stack.last())
    }

    /** Empieza de cero en [screen]: al volver atrás desde ahí se cierra la app. */
    internal fun startFrom(screen: Int) {
        stack.clear()
        go(screen)
    }

    internal fun isShowing(screen: Int) = stack.lastOrNull() == screen

    /** Navega solo si el usuario sigue en [from] (útil al terminar una tarea en segundo plano). */
    internal fun goIfStillOn(from: Int, to: Int) {
        if (isShowing(from)) go(to)
    }

    private fun show(screen: Int) {
        setContentView(screen)
        findViewById<View?>(v.back)?.setOnClickListener { go(BACK) }
        findViewById<View?>(v.logout)?.setOnClickListener { signOut() }
        links[screen]?.forEach { (view, to) -> findViewById<View>(view).setOnClickListener { go(to) } }
        highlightAdminBar(screen)
        setupTabs()
        setupDarkModeSwitch()
        when (screen) {
            // Comunes
            s.screen_splash -> openSplashScreen()
            s.screen_login, s.screen_login_admin -> setupLoginScreen()
            s.screen_register -> setupRegisterScreen()
            s.screen_recover -> setupRecoverScreen()
            s.screen_auth -> setupIdentityCheckScreen()
            s.screen_auth_success -> setupIdentityConfirmedScreen()
            s.screen_profile -> setupProfileScreen()
            s.screen_change_password -> setupChangePasswordScreen()
            s.screen_help -> setupHelpScreen()
            s.screen_no_location -> setupNoLocationScreen()
            s.screen_offline -> setupOfflineScreen()
            s.screen_notifications -> setupNotificationsScreen()

            // Empleado
            s.screen_sede -> setupSedeChoiceScreen()
            s.screen_home -> setupHomeScreen()
            s.screen_biometric -> setupCheckScreen(isCheckIn = true)
            s.screen_biometric_out -> setupCheckScreen(isCheckIn = false)
            s.screen_confirm_in, s.screen_confirm_out -> setupCheckConfirmedScreen()
            s.screen_out_of_range -> setupOutOfRangeScreen()
            s.screen_history -> setupHistoryScreen()
            s.screen_summary -> setupSummaryScreen()
            s.screen_schedule -> setupScheduleScreen()
            s.screen_request_permit -> setupRequestPermitScreen()
            s.screen_my_requests -> setupMyRequestsScreen()
            s.screen_request_detail -> setupRequestDetailScreen()

            // Administrador
            s.screen_dashboard -> setupDashboardScreen()
            s.screen_employees -> setupEmployeesScreen()
            s.screen_new_employee -> setupNewEmployeeScreen()
            s.screen_employee_created -> setupEmployeeCreatedScreen()
            s.screen_admin_history -> setupAdminHistoryScreen()
            s.screen_alerts -> setupAlertsScreen()
            s.screen_admin_requests -> setupAdminRequestsScreen()
            s.screen_review_request -> setupReviewRequestScreen()
            s.screen_sede_edit -> setupSedeEditScreen()

            // Reportes
            s.screen_report -> setupReportScreen()
            s.screen_report_done -> setupReportDoneScreen()
            s.screen_report_download -> setupReportDownloadScreen()
            s.screen_report_view -> setupReportViewScreen()
            s.screen_report_share -> setupReportShareScreen()
        }
    }

    // ----- Utilidades compartidas por las pantallas -----

    internal fun showMessage(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    /**
     * Ejecuta una tarea que usa Firebase. Mientras corre desactiva [button] para evitar dobles envíos;
     * si falla, muestra un mensaje claro.
     */
    internal fun launchTask(button: View? = null, task: suspend () -> Unit) {
        button?.isEnabled = false
        lifecycleScope.launch {
            try {
                task()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showMessage(errorMessage(error))
            } finally {
                button?.isEnabled = true
            }
        }
    }

    internal fun signOut() {
        auth.signOut()
        Session.clear()
        go(s.screen_login)
    }

    internal fun isOnline(): Boolean {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Pide la huella, el rostro o el bloqueo de pantalla del teléfono. */
    internal fun verifyIdentity(title: String, onVerified: () -> Unit) {
        val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            return showMessage("Configura una huella, un rostro o un bloqueo de pantalla en el teléfono para verificar tu identidad.")
        }
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onVerified()

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val cancelledByUser = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                if (!cancelledByUser) showMessage(errString.toString())
            }
        }
        val prompt = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Usa tu huella, tu rostro o el bloqueo de pantalla")
            .setAllowedAuthenticators(authenticators)
            .build()
        BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback).authenticate(prompt)
    }

    /**
     * Obtiene la ubicación actual. Si no hay permiso o el GPS está apagado lleva a la pantalla
     * "Necesitamos tu ubicación"; si no se pudo leer, entrega null.
     */
    @SuppressLint("MissingPermission")
    internal fun requestCurrentLocation(onLocation: (Location?) -> Unit) {
        withLocationPermission { granted ->
            val manager = getSystemService(LocationManager::class.java)
            if (!granted || manager == null || !LocationManagerCompat.isLocationEnabled(manager)) {
                return@withLocationPermission go(s.screen_no_location)
            }
            val provider = if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                LocationManager.GPS_PROVIDER
            } else {
                LocationManager.NETWORK_PROVIDER
            }
            showMessage("Obteniendo tu ubicación…")
            LocationManagerCompat.getCurrentLocation(
                manager, provider, null as android.os.CancellationSignal?, ContextCompat.getMainExecutor(this),
            ) { location -> onLocation(location) }
        }
    }

    internal fun hasLocationPermission(): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun withLocationPermission(onResult: (Boolean) -> Unit) {
        if (hasLocationPermission()) return onResult(true)
        onLocationPermissionResult = onResult
        locationPermissionRequest.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        )
    }

    /** Abre el selector de Android para guardar una copia del reporte donde el usuario elija. */
    internal fun chooseReportDestination(format: ReportFormat, fileName: String, onSaved: (Uri?) -> Unit) {
        onReportSaved = onSaved
        when (format) {
            ReportFormat.PDF -> savePdfReport.launch(fileName)
            ReportFormat.EXCEL -> saveCsvReport.launch(fileName)
        }
    }

    /** Marca en azul el ítem de la barra inferior de la pantalla actual. */
    private fun highlightAdminBar(screen: Int) {
        val item = adminBar.entries.firstOrNull { it.value == screen }?.key ?: return
        val blue = ContextCompat.getColorStateList(this, R.color.primary)
        findViewById<TextView>(item).apply {
            setTextColor(blue)
            TextViewCompat.setCompoundDrawableTintList(this, blue)
        }
    }

    /** Dos opciones donde solo una queda marcada (rostro/huella, patrón/huella).
     *  Si la pantalla tiene area_a / area_b, también cambia cuál se muestra. */
    private fun setupTabs() {
        val tabA = findViewById<View?>(v.tab_a) ?: return
        val tabB = findViewById<View>(v.tab_b)
        val pick = { first: Boolean ->
            tabA.isSelected = first
            tabB.isSelected = !first
            findViewById<View?>(v.area_a)?.isVisible = first
            findViewById<View?>(v.area_b)?.isVisible = !first
        }
        tabA.setOnClickListener { pick(true) }
        tabB.setOnClickListener { pick(false) }
        pick(true)
    }

    private fun setupDarkModeSwitch() {
        findViewById<CompoundButton?>(v.dark_mode)?.apply {
            isChecked = ThemePreference.isDark(context)
            setOnCheckedChangeListener { _, on -> ThemePreference.setDark(context, on) }
        }
    }

    internal companion object {
        const val BACK = 0
        private const val STACK = "stack"
    }
}

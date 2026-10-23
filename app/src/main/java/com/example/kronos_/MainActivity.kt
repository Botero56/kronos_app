package com.example.kronos_

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import com.example.kronos_.R.id as v
import com.example.kronos_.R.layout as s

/**
 * Prototipo visual: cada pantalla es un layout XML y aquí solo se conectan los botones.
 * Sin lógica: no valida campos ni guarda datos.
 */
class MainActivity : AppCompatActivity() {

    /** Ir a una pantalla raíz borra el historial (no se puede volver atrás a lo anterior). */
    private val roots = setOf(s.screen_login, s.screen_home, s.screen_dashboard)

    /** Barra inferior del administrador: ítem -> pantalla. */
    private val adminBar = mapOf(
        v.nav_dashboard to s.screen_dashboard,
        v.nav_employees to s.screen_employees,
        v.nav_requests to s.screen_admin_requests,
        v.nav_history to s.screen_admin_history,
    )

    /** Conexiones: pantalla -> (vista que se toca -> pantalla destino). */
    private val links: Map<Int, Map<Int, Int>> = mapOf(
        // Comunes
        s.screen_login to mapOf(
            v.forgot to s.screen_recover, v.login to s.screen_auth,
            v.register to s.screen_register, v.admin to s.screen_login_admin,
        ),
        s.screen_login_admin to mapOf(
            v.forgot to s.screen_recover, v.login to s.screen_auth, v.register to s.screen_register,
        ),
        s.screen_auth to mapOf(v.verify to s.screen_auth_success),
        s.screen_auth_success to mapOf(v.next to s.screen_sede),
        s.screen_register to mapOf(v.create to BACK),
        s.screen_recover to mapOf(v.send to s.screen_reset),
        s.screen_reset to mapOf(v.save to s.screen_login),
        s.screen_sede to mapOf(v.next to s.screen_home),
        s.screen_profile to mapOf(v.settings to s.screen_settings, v.edit to s.screen_change_password),
        s.screen_change_password to mapOf(v.save to BACK),
        s.screen_settings to mapOf(v.privacy to s.screen_help, v.logout to s.screen_login),
        s.screen_notifications to mapOf(
            v.n1 to BACK, v.n2 to s.screen_my_requests, v.n3 to s.screen_out_of_range,
        ),
        s.screen_help to mapOf(v.support to s.screen_offline),
        s.screen_no_location to mapOf(v.open_settings to BACK),

        // Empleado
        s.screen_home to mapOf(
            v.notifications to s.screen_notifications, v.settings to s.screen_settings,
            v.profile to s.screen_profile, v.check_in to s.screen_biometric,
            v.check_out to s.screen_biometric_out, v.location to s.screen_out_of_range,
            v.last_entry to s.screen_history, v.week_hours to s.screen_summary,
            v.schedule to s.screen_schedule, v.request_permit to s.screen_request_permit,
        ),
        s.screen_biometric to mapOf(v.scan to s.screen_confirm_in),
        s.screen_biometric_out to mapOf(v.scan to s.screen_confirm_out),
        s.screen_confirm_in to mapOf(v.home to s.screen_home, v.profile to s.screen_profile),
        s.screen_confirm_out to mapOf(v.home to s.screen_home),
        s.screen_out_of_range to mapOf(v.retry to s.screen_no_location, v.cancel to BACK),
        s.screen_request_permit to mapOf(v.send to s.screen_my_requests),
        s.screen_my_requests to mapOf(
            v.r1 to s.screen_request_detail, v.r2 to s.screen_request_detail,
            v.r3 to s.screen_request_detail, v.new_request to s.screen_request_permit,
        ),
        s.screen_request_detail to mapOf(v.cancel to BACK),

        // Administrador
        s.screen_dashboard to adminBar + mapOf(
            v.logout to s.screen_login, v.sede to s.screen_sede_edit,
            v.stat1 to s.screen_admin_history, v.stat2 to s.screen_admin_history,
            v.stat3 to s.screen_admin_history, v.stat4 to s.screen_admin_history,
            v.absent to s.screen_alerts, v.report to s.screen_report,
        ),
        s.screen_employees to adminBar + mapOf(v.add to s.screen_new_employee),
        s.screen_admin_history to adminBar + mapOf(v.absent to s.screen_alerts),
        s.screen_admin_requests to adminBar + mapOf(
            v.r1 to s.screen_review_request, v.r2 to s.screen_review_request,
            v.r3 to s.screen_review_request, v.r4 to s.screen_review_request,
        ),
        s.screen_sede_edit to mapOf(v.save to BACK),
        s.screen_report to mapOf(v.generate to s.screen_report_done),
        s.screen_report_done to mapOf(
            v.home to s.screen_dashboard, v.download to s.screen_report_download, v.share to s.screen_report_share,
        ),
        s.screen_report_download to mapOf(v.open to s.screen_report_view, v.home to s.screen_dashboard),
        s.screen_report_view to mapOf(v.share to s.screen_report_share, v.home to s.screen_dashboard),
        s.screen_report_share to mapOf(v.send to BACK),
        s.screen_new_employee to mapOf(v.save to s.screen_employee_created),
        s.screen_employee_created to mapOf(v.view_employees to s.screen_employees),
        s.screen_review_request to mapOf(v.reject to BACK, v.approve to BACK),
    )

    private val stack = ArrayDeque<Int>()

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
        go(s.screen_splash)
    }

    private fun go(screen: Int) {
        when {
            screen == BACK -> if (stack.size > 1) stack.removeLast() else return finish()
            screen in roots -> { stack.clear(); stack.addLast(screen) }
            screen in stack -> while (stack.last() != screen) stack.removeLast() // volver a una pantalla anterior
            else -> stack.addLast(screen)
        }
        show(stack.last())
    }

    private fun show(screen: Int) {
        setContentView(screen)
        findViewById<View?>(v.back)?.setOnClickListener { go(BACK) }
        links[screen]?.forEach { (view, to) -> findViewById<View>(view).setOnClickListener { go(to) } }
        // Si entró por el login de administrador, la verificación termina en el Dashboard.
        if (screen == s.screen_auth_success && s.screen_login_admin in stack) {
            findViewById<View>(v.next).setOnClickListener { go(s.screen_dashboard) }
        }
        highlightAdminBar(screen)
        setupTabs()
        if (screen == s.screen_splash) {
            window.decorView.postDelayed({ if (stack.lastOrNull() == s.screen_splash) go(s.screen_login) }, 1500)
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

    /** Dos opciones donde solo una queda marcada (rostro/huella, patrón/huella, sedes).
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

    private companion object {
        const val BACK = 0
    }
}

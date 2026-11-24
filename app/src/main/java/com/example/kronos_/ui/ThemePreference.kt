package com.example.kronos_.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** Tema claro u oscuro elegido en Ajustes (única preferencia que se recuerda). */
object ThemePreference {

    private const val FILE = "ajustes"
    private const val DARK = "tema_oscuro"

    fun isDark(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(DARK, false)

    fun setDark(context: Context, dark: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(DARK, dark).apply()
        apply(dark)
    }

    /** Se llama al abrir la app, antes de crear la ventana, para que también las barras del sistema tomen el tema. */
    fun applySaved(context: Context) = apply(isDark(context))

    private fun apply(dark: Boolean) = AppCompatDelegate.setDefaultNightMode(
        if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO,
    )
}

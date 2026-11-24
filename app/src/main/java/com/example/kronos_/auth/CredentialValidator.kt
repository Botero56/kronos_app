package com.example.kronos_.auth

/** Revisa los campos de los formularios antes de enviarlos a Firebase. Devuelve el error o null. */
object CredentialValidator {

    const val MIN_PASSWORD_LENGTH = 6

    private val emailFormat = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun nameError(name: String): String? =
        if (name.isBlank()) "Escribe tu nombre completo." else null

    fun emailError(email: String): String? = when {
        email.isBlank() -> "Escribe tu correo electrónico."
        !emailFormat.matches(email.trim()) -> "El correo no tiene un formato válido."
        else -> null
    }

    fun loginPasswordError(password: String): String? =
        if (password.isEmpty()) "Escribe tu contraseña." else null

    fun newPasswordError(password: String): String? = when {
        password.isEmpty() -> "Escribe una contraseña."
        password.length < MIN_PASSWORD_LENGTH ->
            "La contraseña debe tener al menos $MIN_PASSWORD_LENGTH caracteres."
        else -> null
    }
}

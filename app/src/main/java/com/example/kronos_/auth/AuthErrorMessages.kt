package com.example.kronos_.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

/** Convierte un error de Firebase Authentication en un mensaje claro para el usuario. */
fun authErrorMessage(error: Exception?): String = when (error) {
    // La contraseña débil va antes porque también es un error de credenciales.
    is FirebaseAuthWeakPasswordException ->
        "La contraseña es muy débil. Usa al menos ${CredentialValidator.MIN_PASSWORD_LENGTH} caracteres."
    is FirebaseAuthUserCollisionException -> "Ya existe una cuenta con ese correo."
    is FirebaseAuthInvalidUserException -> "La cuenta no existe o está deshabilitada."
    is FirebaseAuthInvalidCredentialsException -> "Correo o contraseña incorrectos."
    is FirebaseNetworkException -> "No hay conexión a internet. Revisa tu red e inténtalo de nuevo."
    is FirebaseTooManyRequestsException -> "Demasiados intentos. Espera un momento y vuelve a intentarlo."
    is FirebaseAuthException if error.errorCode == "ERROR_OPERATION_NOT_ALLOWED" ->
        "El acceso con correo y contraseña no está habilitado en Firebase."
    else -> "No se pudo completar la operación. Inténtalo de nuevo."
}

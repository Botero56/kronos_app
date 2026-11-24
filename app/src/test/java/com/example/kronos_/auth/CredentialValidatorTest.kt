package com.example.kronos_.auth

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CredentialValidatorTest {

    @Test
    fun emailValidoNoTieneError() {
        assertNull(CredentialValidator.emailError("camila@empresa.com"))
        assertNull(CredentialValidator.emailError("  camila@empresa.com  "))
    }

    @Test
    fun emailVacioOMalFormadoTieneError() {
        assertNotNull(CredentialValidator.emailError(""))
        assertNotNull(CredentialValidator.emailError("camila"))
        assertNotNull(CredentialValidator.emailError("camila@empresa"))
    }

    @Test
    fun contrasenaNuevaNecesitaSeisCaracteres() {
        assertNotNull(CredentialValidator.newPasswordError(""))
        assertNotNull(CredentialValidator.newPasswordError("12345"))
        assertNull(CredentialValidator.newPasswordError("123456"))
    }

    @Test
    fun loginSoloExigeQueHayaContrasenaYNombre() {
        assertNotNull(CredentialValidator.loginPasswordError(""))
        assertNull(CredentialValidator.loginPasswordError("1"))
        assertNotNull(CredentialValidator.nameError("   "))
        assertNull(CredentialValidator.nameError("Camila Restrepo"))
    }
}

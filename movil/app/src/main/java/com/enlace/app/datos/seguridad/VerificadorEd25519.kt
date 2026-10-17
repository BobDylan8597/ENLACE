package com.enlace.app.datos.seguridad

import com.enlace.app.dominio.VerificadorFirma
import com.google.crypto.tink.subtle.Ed25519Verify
import java.security.GeneralSecurityException
import java.util.Base64

/**
 * Verificación Ed25519 con Tink: Android 8–12 no trae Ed25519 nativo.
 * `clavePublicaBase64`: los 32 bytes de GET /alertas/clave-publica.
 */
class VerificadorEd25519(clavePublicaBase64: String) : VerificadorFirma {
    private val verificador = Ed25519Verify(Base64.getDecoder().decode(clavePublicaBase64))

    override fun esValida(contenido: String, firmaBase64: String): Boolean = try {
        verificador.verify(Base64.getDecoder().decode(firmaBase64), contenido.toByteArray(Charsets.UTF_8))
        true
    } catch (_: GeneralSecurityException) {
        false
    } catch (_: IllegalArgumentException) {
        false // firma que no es base64
    }
}

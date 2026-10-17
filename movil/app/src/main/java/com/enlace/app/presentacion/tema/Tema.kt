package com.enlace.app.presentacion.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Colores con significado fijo (CLAUDE.md, sección 16). Siempre con ícono o texto al lado. */
object ColoresEnlace {
    val actuar = Color(0xFFC62828) // rojo: actuar / pedir ayuda
    val explorar = Color(0xFFB45309) // ámbar: explorar / sin confirmar
    val estable = Color(0xFF2E7D32) // verde: estable / a salvo
    val sinDatos = Color(0xFF6B7280) // gris: sin datos
    val accion = Color(0xFF1D4ED8) // azul: acción principal, brigadas, rutas
}

private val Claro = lightColorScheme(
    primary = ColoresEnlace.accion,
    error = ColoresEnlace.actuar,
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFF93B4FF),
    error = Color(0xFFFF8A80),
)

/** `oscuro` = true fuerza el modo oscuro (durante una alerta activa). */
@Composable
fun TemaEnlace(oscuro: Boolean = isSystemInDarkTheme(), contenido: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (oscuro) Oscuro else Claro, content = contenido)
}

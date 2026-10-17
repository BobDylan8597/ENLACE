package com.enlace.app.presentacion.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Identidad de ENLACE (paleta oficial). El durazno es acento de marca, nunca un estado. */
object MarcaEnlace {
    val turquesa = Color(0xFF29AEAD) // del logo: realces, íconos, sobre fondos oscuros
    val turquesaOscuro = Color(0xFF1A7D7C) // botones y texto sobre blanco (contraste 4,9:1)
    val turquesaSuave = Color(0xFFE0F2F1)
    val noche = Color(0xFF173C56) // estructura: encabezados
    val nocheOscuro = Color(0xFF0F2A3D)
    val nocheClaro = Color(0xFF22506F)
    val durazno = Color(0xFFF0A262)
    val fondo = Color(0xFFF3F6F8)
}

/** Colores con significado fijo (CLAUDE.md, sección 16). Siempre con ícono o texto al lado. */
object ColoresEnlace {
    val actuar = Color(0xFFC62828) // rojo: actuar / pedir ayuda
    val explorar = Color(0xFFB45309) // ámbar: explorar / sin confirmar
    val estable = Color(0xFF2E7D32) // verde: estable / a salvo
    val sinDatos = Color(0xFF6B7280) // gris: sin datos
    val accion = MarcaEnlace.turquesaOscuro // turquesa: acción principal, brigadas, rutas
}

private val Claro = lightColorScheme(
    primary = MarcaEnlace.turquesaOscuro,
    onPrimary = Color.White,
    primaryContainer = MarcaEnlace.turquesaSuave,
    onPrimaryContainer = MarcaEnlace.nocheOscuro,
    secondary = MarcaEnlace.noche,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE6EE),
    onSecondaryContainer = MarcaEnlace.noche,
    tertiary = MarcaEnlace.durazno,
    background = MarcaEnlace.fondo,
    onBackground = MarcaEnlace.nocheOscuro,
    surface = Color.White,
    onSurface = MarcaEnlace.nocheOscuro,
    surfaceVariant = Color(0xFFEAF0F3),
    onSurfaceVariant = Color(0xFF4A5B68),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF7F9FA),
    outline = Color(0xFFC5D0D8),
    outlineVariant = Color(0xFFE2E8ED),
    error = ColoresEnlace.actuar,
)

/** Modo oscuro (por defecto durante una alerta): el azul noche de la marca como fondo. */
private val Oscuro = darkColorScheme(
    primary = MarcaEnlace.turquesa,
    onPrimary = MarcaEnlace.nocheOscuro,
    primaryContainer = Color(0xFF14504F),
    onPrimaryContainer = Color(0xFFCDEDEC),
    secondary = Color(0xFF9DB9CF),
    tertiary = MarcaEnlace.durazno,
    background = Color(0xFF0B2132),
    onBackground = Color(0xFFE8EEF2),
    surface = MarcaEnlace.nocheOscuro,
    onSurface = Color(0xFFE8EEF2),
    surfaceVariant = MarcaEnlace.noche,
    onSurfaceVariant = Color(0xFFB6C6D2),
    surfaceContainerLowest = Color(0xFF0B2132),
    surfaceContainerLow = MarcaEnlace.nocheOscuro,
    surfaceContainer = MarcaEnlace.nocheOscuro,
    surfaceContainerHigh = MarcaEnlace.noche,
    outline = Color(0xFF3B5A72),
    outlineVariant = Color(0xFF274861),
    error = Color(0xFFFF8A80),
)

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Jerarquía clara: títulos firmes, cuerpo legible (mínimo 14 sp, sección 16). */
private val Tipografia = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(lineHeight = 24.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Título de sección en mayúsculas pequeñas (como en la web). */
val EstiloSeccion = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)

/** `oscuro` = true fuerza el modo oscuro (durante una alerta activa). */
@Composable
fun TemaEnlace(oscuro: Boolean = isSystemInDarkTheme(), contenido: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (oscuro) Oscuro else Claro,
        shapes = Formas,
        typography = Tipografia,
        content = contenido,
    )
}

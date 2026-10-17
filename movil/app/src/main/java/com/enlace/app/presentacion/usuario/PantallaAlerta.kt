package com.enlace.app.presentacion.usuario

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.enlace.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.enlace.app.presentacion.componentes.FilaAjuste
import com.enlace.app.presentacion.tema.MarcaEnlace
import com.enlace.app.datos.GestorAlertas
import com.enlace.app.datos.textoAlerta
import com.enlace.app.dominio.AlertaVigente
import com.enlace.app.dominio.EnvioZona
import com.enlace.app.presentacion.componentes.BotonPrincipal
import com.enlace.app.presentacion.tema.ColoresEnlace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** `null` mientras se lee lo guardado; `Leida(null)` si no hay alerta activa. */
data class Leida(val vigente: AlertaVigente?)

@HiltViewModel
class AlertaViewModel @Inject constructor(private val gestor: GestorAlertas) : ViewModel() {
    val alerta: StateFlow<Leida?> =
        gestor.vigente.map(::Leida).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun enviarZona() = viewModelScope.launch { gestor.enviarZona() }

    fun noEnviarZona() = viewModelScope.launch { gestor.noEnviarZona() }

    fun estoyBien() = viewModelScope.launch { gestor.estoyBien() }
}

private val TEXTO_ZONA = mapOf(
    EnvioZona.ENVIANDO to R.string.zona_enviando,
    EnvioZona.ENVIADA to R.string.zona_enviada,
    EnvioZona.UBICACION_ENVIADA to R.string.zona_ubicacion_enviada,
    EnvioZona.NO_COMPARTIDA to R.string.zona_no_compartida,
    EnvioZona.SIN_UBICACION to R.string.zona_sin_ubicacion,
    EnvioZona.FUERA_DE_ZONA to R.string.zona_fuera,
    EnvioZona.SIN_CONEXION to R.string.zona_sin_conexion,
)

/** U8: alerta activa. Modo oscuro y botones de ancho completo (sección 16). */
@Composable
fun PantallaAlerta(
    alVolver: () -> Unit,
    alPedirAyuda: () -> Unit,
    alQueHacer: () -> Unit,
    viewModel: AlertaViewModel = hiltViewModel(),
) {
    val leida by viewModel.alerta.collectAsStateWithLifecycle()
    val cargada = leida ?: return // todavía leyendo lo guardado
    val actual = cargada.vigente
    if (actual == null) {
        // El evento se cerró (o no hay alerta): vuelve al inicio
        LaunchedEffect(Unit) { alVolver() }
        return
    }
    val contexto = LocalContext.current

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = relleno.calculateBottomPadding())
                .verticalScroll(rememberScrollState()),
        ) {
            // Encabezado rojo: la alerta oficial, verificada (firma Ed25519)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF8E1C1C), ColoresEnlace.actuar)),
                        RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                    )
                    .statusBarsPadding()
                    .padding(start = 8.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconButton(onClick = alVolver) {
                    Icon(painterResource(R.drawable.ic_atras), contentDescription = stringResource(R.string.volver), tint = Color.White)
                }
                Row(
                    modifier = Modifier.padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        Modifier.size(56.dp).background(Color.White.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_alerta), contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Text(
                        textoAlerta(contexto, actual.alerta),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_bien), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.alerta_verificada), color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }

            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.alerta_consejo), style = MaterialTheme.typography.bodyLarge)

                if (actual.envioZona == EnvioZona.PENDIENTE_CONFIRMACION) {
                    Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.alerta_preguntar_zona), style = MaterialTheme.typography.titleMedium)
                            BotonPrincipal(stringResource(R.string.alerta_enviar_zona), { viewModel.enviarZona() })
                            TextButton(onClick = { viewModel.noEnviarZona() }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.alerta_no_enviar_zona))
                            }
                        }
                    }
                } else {
                    TEXTO_ZONA[actual.envioZona]?.let { texto ->
                        val enviada = actual.envioZona == EnvioZona.ENVIADA || actual.envioZona == EnvioZona.UBICACION_ENVIADA
                        val color = if (enviada) MarcaEnlace.turquesa else MaterialTheme.colorScheme.onSurfaceVariant
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(painterResource(R.drawable.ic_ubicacion), contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                            Text(stringResource(texto), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                if (actual.estoyBien) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(painterResource(R.drawable.ic_bien), contentDescription = null, tint = ColoresEnlace.estable)
                        Text(stringResource(R.string.alerta_gracias), color = ColoresEnlace.estable, style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    BotonPrincipal(
                        texto = stringResource(R.string.alerta_estoy_bien),
                        alTocar = { viewModel.estoyBien() },
                        color = ColoresEnlace.estable,
                        icono = R.drawable.ic_bien,
                    )
                }
                BotonPrincipal(
                    texto = stringResource(R.string.ayuda_boton),
                    alTocar = alPedirAyuda,
                    color = ColoresEnlace.actuar,
                    icono = R.drawable.ic_alerta,
                )
                FilaAjuste(R.drawable.ic_lista_consejos, stringResource(R.string.que_hacer_titulo), alQueHacer)
            }
        }
    }
}

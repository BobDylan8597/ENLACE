package com.enlace.receptor

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val ROJO = Color(0xFFC62828)
private val AMBAR = Color(0xFFB45309)
private val VERDE = Color(0xFF2E7D32)
private val AZUL = Color(0xFF1D4ED8)
private const val CODIGO_OK = 200
private const val NO_AUTORIZADO = 401

/** Mensaje de prueba de la versión de desarrollo: ningún SMS real se envía para probar. */
private const val REMITENTE_PRUEBA = "+50500000000"
private const val TEXTO_PRUEBA = "ENL 12.1300,-86.2500 P3 A1 H1 RM"

class ReceptorViewModel(app: Application) : AndroidViewModel(app) {
    private val almacen = Almacen(app)
    private val reenvio = Reenvio(app)

    val cola: StateFlow<List<SmsRecibido>> = almacen.cola.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val resultadoPrueba = mutableStateOf<Int?>(null)

    suspend fun direccionGuardada(): String = almacen.direccion.first().orEmpty()
        .removePrefix("http://").removeSuffix("/")

    suspend fun claveGuardada(): String = almacen.clave.first().orEmpty()

    fun guardarYProbar(direccion: String, clave: String) {
        val normalizada = normalizarDireccion(direccion) ?: run {
            resultadoPrueba.value = R.string.direccion_invalida
            return
        }
        viewModelScope.launch {
            almacen.guardarConexion(normalizada, clave.trim())
            resultadoPrueba.value = when (reenvio.probar(normalizada, clave.trim())) {
                CODIGO_OK -> R.string.prueba_ok
                NO_AUTORIZADO -> R.string.error_clave
                null -> R.string.error_sin_servidor
                else -> R.string.prueba_error
            }
            // Con la conexión corregida, se entrega lo que haya quedado en la cola
            if (!reenvio.enviarPendientes()) reenvio.programar()
        }
    }

    fun reintentar() = viewModelScope.launch { if (!reenvio.enviarPendientes()) reenvio.programar() }

    fun simular() = viewModelScope.launch {
        ReceptorSms.recibir(getApplication(), listOf(REMITENTE_PRUEBA to TEXTO_PRUEBA), System.currentTimeMillis())
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                PantallaReceptor()
            }
        }
    }
}

@Composable
private fun PantallaReceptor(viewModel: ReceptorViewModel = viewModel()) {
    val contexto = LocalContext.current
    val cola by viewModel.cola.collectAsStateWithLifecycle()
    val resultado by viewModel.resultadoPrueba
    var direccion by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var permiso by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(contexto, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED)
    }
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permiso = it }
    LaunchedEffect(Unit) {
        direccion = viewModel.direccionGuardada()
        clave = viewModel.claveGuardada()
        if (!permiso) pedirPermiso.launch(Manifest.permission.RECEIVE_SMS)
    }
    val pendientes = cola.count { it.estado == EstadoSms.PENDIENTE }
    val entregados = cola.count { it.estado == EstadoSms.ENTREGADO }

    Scaffold { relleno ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(relleno),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(R.string.app_nombre), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = AZUL)
                Text(stringResource(R.string.explicacion), style = MaterialTheme.typography.bodyLarge)
            }
            item {
                // El color siempre va con texto (sección 16)
                Text(
                    stringResource(if (permiso) R.string.permiso_ok else R.string.permiso_falta),
                    color = if (permiso) VERDE else ROJO,
                    fontWeight = FontWeight.Bold,
                )
                if (!permiso) {
                    OutlinedButton(
                        onClick = { pedirPermiso.launch(Manifest.permission.RECEIVE_SMS) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.permiso_pedir)) }
                }
            }
            item {
                OutlinedTextField(
                    value = direccion,
                    onValueChange = { direccion = it },
                    label = { Text(stringResource(R.string.direccion)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = clave,
                    onValueChange = { clave = it },
                    label = { Text(stringResource(R.string.clave)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Button(
                    onClick = { viewModel.guardarYProbar(direccion, clave) },
                    colors = ButtonDefaults.buttonColors(containerColor = AZUL),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp),
                ) { Text(stringResource(R.string.guardar_probar)) }
                resultado?.let {
                    Text(stringResource(it), color = if (it == R.string.prueba_ok) VERDE else ROJO, fontWeight = FontWeight.Bold)
                }
            }
            item {
                Text(stringResource(R.string.resumen, pendientes, entregados), style = MaterialTheme.typography.titleMedium)
                if (pendientes > 0) {
                    OutlinedButton(onClick = { viewModel.reintentar() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.reintentar))
                    }
                }
                if (BuildConfig.DEBUG) {
                    OutlinedButton(onClick = { viewModel.simular() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.simular))
                    }
                }
            }
            if (cola.isEmpty()) item { Text(stringResource(R.string.vacio), style = MaterialTheme.typography.bodyMedium) }
            items(cola, key = { it.id }) { sms -> FilaSms(sms) }
        }
    }
}

@Composable
private fun FilaSms(sms: SmsRecibido) {
    val (color, estado) = when (sms.estado) {
        EstadoSms.ENTREGADO -> VERDE to stringResource(R.string.estado_entregado)
        EstadoSms.RECHAZADO -> ROJO to stringResource(R.string.estado_rechazado)
        EstadoSms.PENDIENTE -> AMBAR to stringResource(R.string.estado_pendiente)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(estado, color = color, fontWeight = FontWeight.Bold)
            Text("${ocultarNumero(sms.remitente)} · ${sms.hora.substringAfter('T').take(5)}", style = MaterialTheme.typography.labelLarge)
            Text(sms.texto, style = MaterialTheme.typography.bodyMedium)
            sms.error?.let { Text(it, color = color, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

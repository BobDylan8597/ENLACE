package com.enlace.app.presentacion.componentes

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.enlace.app.R
import com.enlace.app.dominio.AjustesRepositorio
import com.enlace.app.dominio.necesitaInicioAutomatico
import com.enlace.app.presentacion.tema.ColoresEnlace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InicioAutomaticoViewModel @Inject constructor(private val ajustes: AjustesRepositorio) : ViewModel() {
    val visto: StateFlow<Boolean> =
        ajustes.avisoInicioAutomaticoVisto.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun listo() = viewModelScope.launch { ajustes.marcarAvisoInicioAutomatico() }
}

/**
 * Xiaomi, Honor, Huawei, Oppo y Vivo bloquean que una app arranque sola al encender el celular
 * salvo que la persona lo permita ("Inicio automático"). Sin eso, tras reiniciar el celular no
 * llegan alertas hasta abrir ENLACE. Se muestra hasta que la persona toca "Listo", solo en esas marcas.
 */
@Composable
fun AvisoInicioAutomatico(viewModel: InicioAutomaticoViewModel = hiltViewModel()) {
    val visto by viewModel.visto.collectAsStateWithLifecycle()
    if (visto || !necesitaInicioAutomatico(Build.MANUFACTURER)) return
    val contexto = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = ColoresEnlace.explorar), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.inicio_automatico_titulo), color = Color.White, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.inicio_automatico_detalle), color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { abrirAjustes(contexto) }) {
                    Text(stringResource(R.string.inicio_automatico_abrir), color = Color.White, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { viewModel.listo() }) {
                    Text(stringResource(R.string.inicio_automatico_listo), color = Color.White)
                }
            }
        }
    }
}

/** La pantalla de "Inicio automático" de cada marca; si no existe, los ajustes de la app. */
private fun abrirAjustes(contexto: Context) {
    val propias = listOf(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
    )
    for (componente in propias) {
        val intent = Intent().setComponent(componente).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { contexto.startActivity(intent) }.isSuccess) return
    }
    contexto.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${contexto.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

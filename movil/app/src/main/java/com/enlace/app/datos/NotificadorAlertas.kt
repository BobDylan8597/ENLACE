package com.enlace.app.datos

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.enlace.app.MainActivity
import com.enlace.app.R
import com.enlace.app.dominio.Alerta
import com.enlace.app.dominio.AsignacionUnidad
import com.enlace.app.dominio.minutosRedondeados
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val CANAL_ALERTAS = "alertas"
private const val CANAL_SERVICIO = "servicio"
private const val CANAL_ASIGNACIONES = "asignaciones"
private const val ID_ASIGNACION = 4

/** Extra del intent que abre la app desde el aviso de asignación (sobre la pantalla bloqueada). */
const val EXTRA_ASIGNACION = "com.enlace.app.ASIGNACION"
private const val ID_ALERTA = 1
private const val ID_AYUDA_EN_CAMINO = 3
const val ID_NOTIFICACION_SERVICIO = 2

@Singleton
class NotificadorAlertas @Inject constructor(@param:ApplicationContext private val contexto: Context) {

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val gestor = contexto.getSystemService(NotificationManager::class.java)
            gestor.createNotificationChannel(
                NotificationChannel(
                    CANAL_ALERTAS,
                    contexto.getString(R.string.canal_alertas),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
            gestor.createNotificationChannel(
                NotificationChannel(
                    CANAL_ASIGNACIONES,
                    contexto.getString(R.string.canal_asignaciones),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
            gestor.createNotificationChannel(
                NotificationChannel(
                    CANAL_SERVICIO,
                    contexto.getString(R.string.canal_servicio),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    private fun abrirApp(): PendingIntent = PendingIntent.getActivity(
        contexto,
        0,
        Intent(contexto, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Notificación fija del servicio que mantiene la conexión para recibir alertas. */
    fun servicio() = NotificationCompat.Builder(contexto, CANAL_SERVICIO)
        .setSmallIcon(R.drawable.ic_senal)
        .setContentTitle(contexto.getString(R.string.servicio_titulo))
        .setContentText(contexto.getString(R.string.servicio_texto))
        .setContentIntent(abrirApp())
        .setOngoing(true)
        .build()

    fun mostrarAlerta(alerta: Alerta) {
        if (!puedeNotificar()) return // sin permiso: la persona la verá al abrir la app
        val notificacion = NotificationCompat.Builder(contexto, CANAL_ALERTAS)
            .setSmallIcon(R.drawable.ic_alerta)
            .setContentTitle(textoAlerta(contexto, alerta))
            .setContentText(contexto.getString(R.string.alerta_notificacion_texto))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(abrirApp())
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(contexto).notify(ID_ALERTA, notificacion)
    }

    fun quitarAlerta() = NotificationManagerCompat.from(contexto).cancel(ID_ALERTA)

    /** U12: el centro asignó un equipo al pedido de ayuda de esta persona. */
    fun mostrarAyudaEnCamino(equipo: String, minutos: Double) {
        if (!puedeNotificar()) return
        val notificacion = NotificationCompat.Builder(contexto, CANAL_ALERTAS)
            .setSmallIcon(R.drawable.ic_equipo)
            .setContentTitle(contexto.getString(R.string.seguimiento_en_camino))
            .setContentText(contexto.getString(R.string.seguimiento_equipo, equipo, minutos.toInt()))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(abrirApp())
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(contexto).notify(ID_AYUDA_EN_CAMINO, notificacion)
    }

    /** B3: asignación nueva para la unidad, a pantalla completa aunque el celular esté bloqueado. */
    fun mostrarAsignacion(asignacion: AsignacionUnidad) {
        if (!puedeNotificar()) return // la unidad la ve al abrir la app
        val abrir = PendingIntent.getActivity(
            contexto,
            ID_ASIGNACION,
            Intent(contexto, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_ASIGNACION, asignacion.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val destino = asignacion.nombreZona.ifEmpty { asignacion.idH3 }
        val notificacion = NotificationCompat.Builder(contexto, CANAL_ASIGNACIONES)
            .setSmallIcon(R.drawable.ic_equipo)
            .setContentTitle(contexto.getString(R.string.asignacion_nueva))
            .setContentText(contexto.getString(R.string.asignacion_notificacion, destino, minutosRedondeados(asignacion.minutos)))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(abrir)
            .setFullScreenIntent(abrir, true)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(contexto).notify(ID_ASIGNACION, notificacion)
    }

    fun quitarAsignacion() = NotificationManagerCompat.from(contexto).cancel(ID_ASIGNACION)

    private fun puedeNotificar(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}

/** "Alerta oficial: sismo M6.2" */
fun textoAlerta(contexto: Context, alerta: Alerta): String {
    val tipo = when (alerta.tipo) {
        "sismo" -> R.string.evento_sismo
        "inundacion" -> R.string.evento_inundacion
        "huracan" -> R.string.evento_huracan
        else -> R.string.evento_otro
    }
    val magnitud = alerta.magnitud?.let { " M$it" } ?: ""
    return contexto.getString(R.string.alerta_titulo, contexto.getString(tipo) + magnitud)
}

package com.enlace.receptor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Recibe cada SMS que llega al celular y se queda solo con los de formato ENL: los guarda en la
 * cola y pide el reenvío. Los demás mensajes no se leen ni se guardan.
 */
class ReceptorSms : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val mensajes = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val hora = mensajes.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
        val reportes = unirPartes(mensajes.map { it.originatingAddress.orEmpty() to it.messageBody.orEmpty() })
            .filter { (_, texto) -> esReporteEnlace(texto) }
        if (reportes.isEmpty()) return

        val pendiente = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                recibir(contexto, reportes, hora)
            } finally {
                pendiente.finish()
            }
        }
    }

    companion object {
        /** También lo usa el botón de prueba (versión de desarrollo) para no enviar SMS reales. */
        suspend fun recibir(contexto: Context, reportes: List<Pair<String, String>>, horaMs: Long) {
            val almacen = Almacen(contexto)
            val hora = OffsetDateTime.ofInstant(Instant.ofEpochMilli(horaMs), ZoneId.systemDefault()).toString()
            reportes.forEach { (remitente, texto) -> almacen.agregar(remitente, texto.trim(), hora) }
            val reenvio = Reenvio(contexto)
            // Se intenta ya; si no hay servidor, WorkManager reintenta después
            if (!reenvio.enviarPendientes()) reenvio.programar()
        }
    }
}

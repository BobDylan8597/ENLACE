package com.enlace.app.datos

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class TrabajoEnvioOutbox @AssistedInject constructor(
    @Assisted contexto: Context,
    @Assisted parametros: WorkerParameters,
    private val outbox: Outbox,
) : CoroutineWorker(contexto, parametros) {
    override suspend fun doWork(): Result = if (outbox.enviarPendientes()) Result.success() else Result.retry()
}

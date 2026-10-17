package com.enlace.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class EnlaceApp : Application(), Configuration.Provider {
    @Inject lateinit var fabricaTrabajos: HiltWorkerFactory

    /** WorkManager con Hilt: el trabajo del Outbox recibe sus dependencias inyectadas. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(fabricaTrabajos).build()
}

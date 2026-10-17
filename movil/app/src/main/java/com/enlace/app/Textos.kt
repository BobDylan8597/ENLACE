package com.enlace.app

import com.enlace.app.dominio.Condicion
import com.enlace.app.dominio.Necesidad
import com.enlace.app.dominio.TipoBloqueo

/** Texto (en strings.xml) de cada valor del dominio: las pantallas y los envíos usan los mismos. */
val TEXTO_CONDICION = mapOf(
    Condicion.MOVILIDAD_REDUCIDA to R.string.condicion_movilidad_reducida,
    Condicion.ADULTO_MAYOR to R.string.condicion_adulto_mayor,
    Condicion.EMBARAZO to R.string.condicion_embarazo,
    Condicion.NINOS to R.string.condicion_ninos,
    Condicion.MEDICACION to R.string.condicion_medicacion,
    Condicion.DISCAPACIDAD to R.string.condicion_discapacidad,
)

val TEXTO_NECESIDAD = mapOf(
    Necesidad.RESCATE to R.string.necesidad_rescate,
    Necesidad.MEDICO to R.string.necesidad_medico,
    Necesidad.AGUA to R.string.necesidad_agua,
    Necesidad.REFUGIO to R.string.necesidad_refugio,
)

val TEXTO_BLOQUEO = mapOf(
    TipoBloqueo.DERRUMBE to R.string.bloqueo_derrumbe,
    TipoBloqueo.AGUA to R.string.bloqueo_agua,
    TipoBloqueo.ESCOMBROS to R.string.bloqueo_escombros,
    TipoBloqueo.CABLES to R.string.bloqueo_cables,
    TipoBloqueo.VIA_ROTA to R.string.bloqueo_via_rota,
    TipoBloqueo.OTRO to R.string.bloqueo_otro,
)

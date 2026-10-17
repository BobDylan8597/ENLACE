package com.enlace.app.dominio

/** Celda h3 con su contorno en (lat, lon), tal como la entrega GET /celdas/geometria. */
data class CeldaH3(val id: String, val contorno: List<Pair<Double, Double>>)

/**
 * Calcula en el celular en qué celda está una persona, para que el check-in lleve solo la celda
 * y nunca coordenadas: los check-ins viajan también por celulares ajenos (red Nearby).
 */
class LocalizadorCelda(private val celdas: List<CeldaH3>) {

    /** Id de la celda que contiene el punto, o null si está fuera de la zona preparada. */
    fun celdaDe(lat: Double, lon: Double): String? = celdas.firstOrNull { contiene(it.contorno, lat, lon) }?.id

    companion object {
        /** Punto dentro de polígono por cruce de rayos (las celdas de una ciudad son casi planas). */
        fun contiene(contorno: List<Pair<Double, Double>>, lat: Double, lon: Double): Boolean {
            var dentro = false
            var j = contorno.lastIndex
            for (i in contorno.indices) {
                val (latI, lonI) = contorno[i]
                val (latJ, lonJ) = contorno[j]
                val cruza = (latI > lat) != (latJ > lat) &&
                    lon < (lonJ - lonI) * (lat - latI) / (latJ - latI) + lonI
                if (cruza) dentro = !dentro
                j = i
            }
            return dentro
        }
    }
}

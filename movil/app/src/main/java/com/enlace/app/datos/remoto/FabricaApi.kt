package com.enlace.app.datos.remoto

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * La dirección del servidor se configura en el celular, así que el cliente se crea por
 * dirección (y se reutiliza mientras no cambie).
 */
@Singleton
class FabricaApi @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json,
) {
    private var actual: Pair<String, ApiEnlace>? = null

    @Synchronized
    fun para(direccion: String): ApiEnlace {
        actual?.let { (base, api) -> if (base == direccion) return api }
        val api = Retrofit.Builder()
            .baseUrl(direccion)
            .client(http)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiEnlace::class.java)
        actual = direccion to api
        return api
    }
}

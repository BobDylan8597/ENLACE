package com.enlace.app.datos.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.enlace.app.dominio.EstadoAtencion
import com.enlace.app.dominio.EstadoEnvio
import com.enlace.app.dominio.TipoMensaje
import kotlinx.coroutines.flow.Flow

/** Mensaje guardado en el celular hasta que el servidor lo confirme (patrón Outbox). */
@Entity(tableName = "outbox")
data class MensajeSaliente(
    @PrimaryKey val idMensaje: String,
    val tipo: TipoMensaje,
    /** JSON listo para enviar (ReporteDto, BloqueoDto o CheckInDto). */
    val cuerpo: String,
    val creado: Long,
    val intentos: Int = 0,
    val estado: EstadoEnvio = EstadoEnvio.PENDIENTE,
    val error: String? = null,
)

/** Pedido de ayuda hecho desde este celular, para el seguimiento (U12). */
@Entity(tableName = "pedidos")
data class PedidoPropio(
    @PrimaryKey val idMensaje: String,
    val creado: Long,
    val resumen: String,
    val atencion: EstadoAtencion? = null,
    val equipo: String? = null,
    val minutos: Double? = null,
)

/** id_mensaje ya visto por la red cercana (gossip): lo repetido se ignora. */
@Entity(tableName = "vistos")
data class MensajeVisto(@PrimaryKey val idMensaje: String, val saltos: Int, val recibido: Long)

@Dao
interface VistosDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun guardar(visto: MensajeVisto): Long

    @Query("SELECT saltos FROM vistos WHERE idMensaje = :id")
    suspend fun saltos(id: String): Int?
}

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(mensaje: MensajeSaliente)

    @Query("SELECT * FROM outbox WHERE estado = 'PENDIENTE' ORDER BY creado")
    suspend fun pendientes(): List<MensajeSaliente>

    @Query("SELECT * FROM outbox WHERE idMensaje = :id")
    fun observar(id: String): Flow<MensajeSaliente?>

    @Query("SELECT COUNT(*) FROM outbox WHERE idMensaje = :id")
    suspend fun existe(id: String): Int

    @Query("SELECT * FROM outbox WHERE idMensaje IN (:ids)")
    fun observarVarios(ids: List<String>): Flow<List<MensajeSaliente>>

    @Query("UPDATE outbox SET estado = :estado, error = :error, intentos = intentos + 1 WHERE idMensaje = :id")
    suspend fun marcar(id: String, estado: EstadoEnvio, error: String?)

    @Query("UPDATE outbox SET intentos = intentos + 1 WHERE idMensaje IN (:ids)")
    suspend fun sumarIntento(ids: List<String>)
}

@Dao
interface PedidosDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(pedido: PedidoPropio)

    @Query("SELECT * FROM pedidos ORDER BY creado DESC")
    fun todos(): Flow<List<PedidoPropio>>

    @Query("SELECT * FROM pedidos")
    suspend fun lista(): List<PedidoPropio>

    @Query("UPDATE pedidos SET atencion = :atencion, equipo = :equipo, minutos = :minutos WHERE idMensaje = :id")
    suspend fun actualizar(id: String, atencion: EstadoAtencion?, equipo: String?, minutos: Double?)
}

class Conversores {
    @TypeConverter fun tipo(valor: TipoMensaje): String = valor.name
    @TypeConverter fun tipo(valor: String): TipoMensaje = TipoMensaje.valueOf(valor)
    @TypeConverter fun envio(valor: EstadoEnvio): String = valor.name
    @TypeConverter fun envio(valor: String): EstadoEnvio = EstadoEnvio.valueOf(valor)
    @TypeConverter fun atencion(valor: EstadoAtencion?): String? = valor?.name
    @TypeConverter fun atencion(valor: String?): EstadoAtencion? = valor?.let(EstadoAtencion::valueOf)
}

@Database(
    entities = [MensajeSaliente::class, PedidoPropio::class, MensajeVisto::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Conversores::class)
abstract class BaseLocal : RoomDatabase() {
    abstract fun outbox(): OutboxDao
    abstract fun pedidos(): PedidosDao
    abstract fun vistos(): VistosDao
}

/** Versión 2 agrega la tabla de mensajes vistos sin borrar el Outbox pendiente. */
val MIGRACION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS vistos " +
                "(idMensaje TEXT NOT NULL PRIMARY KEY, saltos INTEGER NOT NULL, recibido INTEGER NOT NULL)",
        )
    }
}

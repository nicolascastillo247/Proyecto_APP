package com.example.sqlite.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UsuarioDao {
    @Query("SELECT * FROM usuarios ORDER BY nombre")
    fun todos(): Flow<List<Usuario>>

    @Query("SELECT * FROM usuarios WHERE username = :username LIMIT 1")
    suspend fun porUsername(username: String): Usuario?

    @Query("SELECT * FROM usuarios WHERE id = :id LIMIT 1")
    suspend fun porId(id: Long): Usuario?

    @Query("SELECT COUNT(*) FROM usuarios")
    suspend fun contar(): Int

    @Insert
    suspend fun insertar(u: Usuario): Long

    @Update
    suspend fun actualizar(u: Usuario)
}

@Dao
interface VehiculoDao {
    @Query("SELECT * FROM vehiculos ORDER BY patente")
    fun todos(): Flow<List<Vehiculo>>

    @Query("SELECT * FROM vehiculos WHERE id = :id LIMIT 1")
    suspend fun porId(id: Long): Vehiculo?

    @Query("SELECT * FROM vehiculos WHERE patente = :patente LIMIT 1")
    suspend fun porPatente(patente: String): Vehiculo?

    @Insert
    suspend fun insertar(v: Vehiculo): Long

    @Update
    suspend fun actualizar(v: Vehiculo)
}

@Dao
interface CentroDao {
    @Query("SELECT * FROM centros ORDER BY esBase DESC, nombre")
    fun todos(): Flow<List<Centro>>

    @Query("SELECT * FROM centros WHERE id = :id LIMIT 1")
    suspend fun porId(id: Long): Centro?

    @Insert
    suspend fun insertar(c: Centro): Long

    @Update
    suspend fun actualizar(c: Centro)
}

@Dao
interface TramoDao {
    @Query("SELECT * FROM tramos ORDER BY origenId, destinoId")
    fun todos(): Flow<List<Tramo>>

    /** Busca el tramo sin importar el sentido. */
    @Query(
        "SELECT * FROM tramos WHERE (origenId = :a AND destinoId = :b) " +
            "OR (origenId = :b AND destinoId = :a) LIMIT 1"
    )
    suspend fun entre(a: Long, b: Long): Tramo?

    @Insert
    suspend fun insertar(t: Tramo): Long

    @Update
    suspend fun actualizar(t: Tramo)
}

@Dao
interface TarifaDao {
    @Query("SELECT * FROM tarifas ORDER BY tipo, vigenteDesde DESC")
    fun todas(): Flow<List<Tarifa>>

    /** Tarifa vigente en un instante dado (RF-21). */
    @Query(
        "SELECT * FROM tarifas WHERE tipo = :tipo AND vigenteDesde <= :ts " +
            "AND (vigenteHasta IS NULL OR vigenteHasta >= :ts) " +
            "ORDER BY vigenteDesde DESC LIMIT 1"
    )
    suspend fun vigenteEn(tipo: String, ts: Long): Tarifa?

    @Query("SELECT * FROM tarifas WHERE tipo = :tipo AND vigenteHasta IS NULL ORDER BY vigenteDesde DESC LIMIT 1")
    suspend fun abierta(tipo: String): Tarifa?

    @Insert
    suspend fun insertar(t: Tarifa): Long

    @Update
    suspend fun actualizar(t: Tarifa)
}

@Dao
interface VisitaDao {
    @Query("SELECT * FROM visitas WHERE tecnicoId = :tecnicoId AND fecha = :fecha ORDER BY id")
    fun delDia(tecnicoId: Long, fecha: String): Flow<List<Visita>>

    @Query("SELECT * FROM visitas ORDER BY fecha DESC, id DESC")
    fun todas(): Flow<List<Visita>>

    @Query("SELECT COUNT(*) FROM visitas WHERE fecha = :fecha")
    suspend fun contarDelDia(fecha: String): Int

    @Insert
    suspend fun insertar(v: Visita): Long

    @Update
    suspend fun actualizar(v: Visita)

    @Query("UPDATE visitas SET atendida = 1 WHERE id = :id")
    suspend fun marcarAtendida(id: Long)
}

@Dao
interface TrasladoDao {
    @Insert
    suspend fun insertar(t: Traslado): Long

    @Update
    suspend fun actualizar(t: Traslado)

    @Query("SELECT * FROM traslados WHERE id = :id LIMIT 1")
    suspend fun porId(id: Long): Traslado?

    @Query("SELECT * FROM traslados WHERE tecnicoId = :tecnicoId AND estado = 'EN_CURSO' ORDER BY inicioTs DESC LIMIT 1")
    fun activo(tecnicoId: Long): Flow<Traslado?>

    @Query("SELECT * FROM traslados WHERE tecnicoId = :tecnicoId AND estado = 'EN_CURSO' LIMIT 1")
    suspend fun activoUnaVez(tecnicoId: Long): Traslado?

    @Query("SELECT * FROM traslados WHERE tecnicoId = :tecnicoId AND estado != 'EN_CURSO' ORDER BY inicioTs DESC")
    fun historial(tecnicoId: Long): Flow<List<Traslado>>

    @Query("SELECT * FROM traslados WHERE estado != 'EN_CURSO' ORDER BY inicioTs DESC")
    fun todosCerrados(): Flow<List<Traslado>>

    @Query("SELECT * FROM traslados WHERE estado != 'EN_CURSO' AND syncEstado != 'SINCRONIZADO' ORDER BY inicioTs")
    suspend fun pendientesSync(): List<Traslado>

    @Query("SELECT COUNT(*) FROM traslados WHERE tecnicoId = :tecnicoId AND estado != 'EN_CURSO' AND syncEstado != 'SINCRONIZADO'")
    fun contarPendientes(tecnicoId: Long): Flow<Int>
}

@Dao
interface ParadaDao {
    @Query("SELECT * FROM paradas WHERE trasladoId = :trasladoId ORDER BY orden")
    fun observar(trasladoId: Long): Flow<List<Parada>>

    @Query("SELECT * FROM paradas WHERE trasladoId = :trasladoId ORDER BY orden")
    suspend fun deTraslado(trasladoId: Long): List<Parada>

    @Insert
    suspend fun insertar(p: Parada): Long
}

/** Solo inserción y lectura: la bitácora no se edita ni se borra. */
@Dao
interface BitacoraDao {
    @Insert
    suspend fun insertar(b: Bitacora): Long

    @Query("SELECT * FROM bitacora ORDER BY id DESC LIMIT 300")
    fun recientes(): Flow<List<Bitacora>>
}

@Dao
interface ParametroDao {
    @Query("SELECT valor FROM parametros WHERE clave = :clave LIMIT 1")
    suspend fun obtener(clave: String): String?

    @Query("SELECT valor FROM parametros WHERE clave = :clave LIMIT 1")
    fun observar(clave: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(p: Parametro)
}

package com.example.sqlite.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de datos local (Room / SQLite) de TeqRuta.
 * Es la fuente de verdad en el dispositivo: permite trabajar sin conexión (RNF-02, RNF-03).
 * Usa un archivo nuevo ("teqruta.db") para no mezclarse con el antiguo "productos.db".
 */
@Database(
    entities = [
        Usuario::class, Vehiculo::class, Centro::class, Tramo::class, Tarifa::class,
        Visita::class, Traslado::class, Parada::class, Bitacora::class, Parametro::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usuarioDao(): UsuarioDao
    abstract fun vehiculoDao(): VehiculoDao
    abstract fun centroDao(): CentroDao
    abstract fun tramoDao(): TramoDao
    abstract fun tarifaDao(): TarifaDao
    abstract fun visitaDao(): VisitaDao
    abstract fun trasladoDao(): TrasladoDao
    abstract fun paradaDao(): ParadaDao
    abstract fun bitacoraDao(): BitacoraDao
    abstract fun parametroDao(): ParametroDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "teqruta.db"
                ).build().also { INSTANCE = it }
            }
    }
}

/** Atajo para escribir en la bitácora inmutable. */
suspend fun AppDatabase.registrar(usuarioId: Long?, accion: String, detalle: String) {
    bitacoraDao().insertar(
        Bitacora(ts = System.currentTimeMillis(), usuarioId = usuarioId, accion = accion, detalle = detalle)
    )
}

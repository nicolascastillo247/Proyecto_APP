package com.example.sqlite.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// ---------- Constantes de dominio (se guardan como texto en Room) ----------

object Rol {
    const val TECNICO = "TECNICO"
    const val JEFE = "JEFE"
    const val GERENCIA = "GERENCIA"
    const val ADMIN = "ADMIN"

    val todos = listOf(
        TECNICO to "Técnico",
        JEFE to "Jefe de servicio técnico",
        GERENCIA to "Gerencia / Finanzas",
        ADMIN to "Administrador"
    )

    fun etiqueta(rol: String) = todos.firstOrNull { it.first == rol }?.second ?: rol
}

object TipoVehiculo {
    const val PARTICULAR = "PARTICULAR"
    const val EMPRESA = "EMPRESA"
    val todos = listOf(PARTICULAR to "Particular", EMPRESA to "De la empresa")
}

object EstadoTraslado {
    const val EN_CURSO = "EN_CURSO"
    const val PENDIENTE = "PENDIENTE"
    const val VALIDADO = "VALIDADO"
    const val OBSERVADO = "OBSERVADO"
    const val RECHAZADO = "RECHAZADO"

    /** Estados que puede ver un filtro (EN_CURSO no se envía a validación). */
    val filtrables = listOf(
        PENDIENTE to "Pendiente",
        VALIDADO to "Validado",
        OBSERVADO to "Observado",
        RECHAZADO to "Rechazado"
    )

    fun etiqueta(e: String) = when (e) {
        EN_CURSO -> "En curso"
        PENDIENTE -> "Pendiente"
        VALIDADO -> "Validado"
        OBSERVADO -> "Observado"
        RECHAZADO -> "Rechazado"
        else -> e
    }
}

object EstadoSync {
    const val PENDIENTE = "PENDIENTE"
    const val SINCRONIZADO = "SINCRONIZADO"
    const val ERROR = "ERROR"

    fun etiqueta(e: String) = when (e) {
        PENDIENTE -> "Sin sincronizar"
        SINCRONIZADO -> "Sincronizado"
        ERROR -> "Error de sincronización"
        else -> e
    }
}

object TipoTarifa {
    const val COMBUSTIBLE = "COMBUSTIBLE"
    const val KM_PARTICULAR = "KM_PARTICULAR"
    val todos = listOf(
        COMBUSTIBLE to "Combustible ($ por litro)",
        KM_PARTICULAR to "Vehículo particular ($ por km)"
    )

    fun etiqueta(t: String) = todos.firstOrNull { it.first == t }?.second ?: t
}

object Param {
    const val UMBRAL_DESVIACION = "umbral_desviacion_pct"
}

// ---------- Entidades ----------

@Entity(tableName = "usuarios", indices = [Index(value = ["username"], unique = true)])
data class Usuario(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val username: String,
    val passwordHash: String,
    val rol: String,
    val activo: Boolean = true
)

@Entity(tableName = "vehiculos", indices = [Index(value = ["patente"], unique = true)])
data class Vehiculo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patente: String,
    val descripcion: String,
    val tipo: String,
    val consumoKmPorLitro: Double,
    val activo: Boolean = true
)

/** Clínica, hospital o la base de la empresa (esBase = true). */
@Entity(tableName = "centros")
data class Centro(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val cliente: String,
    val contrato: String,
    val direccion: String = "",
    val latitud: Double? = null,
    val longitud: Double? = null,
    val esBase: Boolean = false,
    val activo: Boolean = true
)

/** Distancia oficial y peajes entre dos centros (vale en ambos sentidos). */
@Entity(tableName = "tramos", indices = [Index(value = ["origenId", "destinoId"], unique = true)])
data class Tramo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val origenId: Long,
    val destinoId: Long,
    val distanciaKm: Double,
    val peajes: Int
)

/** Tarifa con vigencia. Nunca se borra: al crear una nueva se cierra la anterior. */
@Entity(tableName = "tarifas")
data class Tarifa(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tipo: String,
    val valor: Double,
    val vigenteDesde: Long,
    val vigenteHasta: Long? = null
)

/** Llamado o visita asignada a un técnico para un día (fecha yyyy-MM-dd). */
@Entity(tableName = "visitas", indices = [Index(value = ["tecnicoId", "fecha"])])
data class Visita(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tecnicoId: Long,
    val centroId: Long,
    val fecha: String,
    val descripcion: String = "",
    val atendida: Boolean = false
)

@Entity(
    tableName = "traslados",
    indices = [Index(value = ["uuid"], unique = true), Index(value = ["tecnicoId"])]
)
data class Traslado(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Clave de idempotencia: evita duplicados al sincronizar (RF-27). */
    val uuid: String,
    val tecnicoId: Long,
    val vehiculoId: Long,
    val origenId: Long,
    val destinoId: Long,
    val visitaId: Long? = null,
    val inicioTs: Long,
    val finTs: Long? = null,
    val latIni: Double? = null,
    val lonIni: Double? = null,
    val latFin: Double? = null,
    val lonFin: Double? = null,
    val kmInicio: Int,
    val kmFin: Int? = null,
    /** Total de peajes pagados en todo el traslado. */
    val peajes: Int = 0,
    val estado: String = EstadoTraslado.EN_CURSO,
    val syncEstado: String = EstadoSync.PENDIENTE,
    val syncError: String? = null,
    // --- Los rellena el "servidor" al sincronizar (RNF-06) ---
    val distanciaOficial: Double? = null,
    val desviacionPct: Double? = null,
    val desviado: Boolean = false,
    val costoTotal: Double? = null,
    // --- Revisión del jefe ---
    val comentarioJefe: String? = null,
    val revisadoPor: Long? = null,
    val revisadoTs: Long? = null
)

/** Parada en una clínica intermedia dentro de un mismo recorrido (RF-08). */
@Entity(tableName = "paradas", indices = [Index(value = ["trasladoId"])])
data class Parada(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trasladoId: Long,
    val orden: Int,
    val centroId: Long,
    val kmLlegada: Int,
    val peajes: Int,
    val ts: Long
)

/** Bitácora inmutable: el DAO solo permite insertar y consultar (RNF-07). */
@Entity(tableName = "bitacora")
data class Bitacora(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val usuarioId: Long?,
    val accion: String,
    val detalle: String
)

@Entity(tableName = "parametros")
data class Parametro(
    @PrimaryKey val clave: String,
    val valor: String
)

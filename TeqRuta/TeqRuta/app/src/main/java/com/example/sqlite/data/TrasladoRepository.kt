package com.example.sqlite.data

import com.example.sqlite.data.remote.ServidorSimulado
import com.example.sqlite.util.Fmt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Fila de un reporte agrupado (clientes, contratos, técnicos o vehículos). */
data class FilaReporte(val clave: String, val traslados: Int, val km: Double, val costo: Double)

data class Reporte(
    val total: FilaReporte,
    val porCliente: List<FilaReporte>,
    val porContrato: List<FilaReporte>,
    val porTecnico: List<FilaReporte>,
    val porVehiculo: List<FilaReporte>
) {
    /** CSV con separador ';' para abrir directo en Excel en configuración regional chilena. */
    fun aCsv(desde: String, hasta: String): String {
        val sb = StringBuilder("Reporte de costos de traslado;$desde;$hasta\n")
        sb.append("Agrupación;Clave;Traslados;Km;Costo CLP\n")
        fun bloque(nombre: String, filas: List<FilaReporte>) = filas.forEach {
            sb.append("$nombre;${it.clave.replace(';', ',')};${it.traslados};${"%.1f".format(java.util.Locale.US, it.km)};${Math.round(it.costo)}\n")
        }
        bloque("Cliente", porCliente)
        bloque("Contrato", porContrato)
        bloque("Técnico", porTecnico)
        bloque("Vehículo", porVehiculo)
        sb.append("TOTAL;;${total.traslados};${"%.1f".format(java.util.Locale.US, total.km)};${Math.round(total.costo)}\n")
        return sb.toString()
    }
}

/**
 * Traslados: flujo del técnico (RF-03 a RF-09), revisión del jefe (RF-10 a RF-12),
 * reportes de gerencia (RF-13 a RF-15) y sincronización (RF-25 a RF-27).
 */
class TrasladoRepository(private val db: AppDatabase) {

    private val servidor = ServidorSimulado(db)
    private val mutexSync = Mutex()

    // ---------- Técnico ----------

    fun visitasDelDia(tecnicoId: Long, fecha: String): Flow<List<Visita>> = db.visitaDao().delDia(tecnicoId, fecha)
    fun activo(tecnicoId: Long): Flow<Traslado?> = db.trasladoDao().activo(tecnicoId)
    fun historial(tecnicoId: Long): Flow<List<Traslado>> = db.trasladoDao().historial(tecnicoId)
    fun paradas(trasladoId: Long): Flow<List<Parada>> = db.paradaDao().observar(trasladoId)
    fun pendientesSync(tecnicoId: Long): Flow<Int> = db.trasladoDao().contarPendientes(tecnicoId)

    suspend fun iniciar(
        tecnicoId: Long, vehiculoId: Long?, origenId: Long?, destinoId: Long?,
        visitaId: Long?, kmInicio: Int?, ubicacion: Pair<Double, Double>?
    ) {
        require(vehiculoId != null) { "Selecciona el vehículo" }
        require(origenId != null) { "Selecciona el origen" }
        require(destinoId != null) { "Selecciona la clínica u hospital de destino" }
        require(kmInicio != null && kmInicio >= 0) { "Ingresa el kilometraje de inicio" }
        check(db.trasladoDao().activoUnaVez(tecnicoId) == null) { "Ya tienes un traslado en curso" }

        val t = Traslado(
            uuid = UUID.randomUUID().toString(),
            tecnicoId = tecnicoId, vehiculoId = vehiculoId, origenId = origenId, destinoId = destinoId,
            visitaId = visitaId, inicioTs = System.currentTimeMillis(),
            latIni = ubicacion?.first, lonIni = ubicacion?.second, kmInicio = kmInicio
        )
        db.trasladoDao().insertar(t)
        db.registrar(tecnicoId, "TRASLADO_INICIADO", "traslado=${t.uuid}; km_inicio=$kmInicio; con_ubicacion=${ubicacion != null}")
    }

    suspend fun agregarParada(trasladoId: Long, centroId: Long?, kmLlegada: Int?, peajes: Int?) {
        require(centroId != null) { "Selecciona la clínica intermedia" }
        require(kmLlegada != null) { "Ingresa el kilometraje al llegar" }
        require(peajes == null || peajes >= 0) { "Los peajes no pueden ser negativos" }
        val t = db.trasladoDao().porId(trasladoId) ?: error("Traslado no encontrado")
        check(t.estado == EstadoTraslado.EN_CURSO) { "El traslado ya fue finalizado" }
        val paradas = db.paradaDao().deTraslado(trasladoId)
        val kmPrevio = paradas.lastOrNull()?.kmLlegada ?: t.kmInicio
        require(kmLlegada >= kmPrevio) { "El kilometraje no puede ser menor a $kmPrevio" }
        db.paradaDao().insertar(
            Parada(
                trasladoId = trasladoId, orden = paradas.size + 1, centroId = centroId,
                kmLlegada = kmLlegada, peajes = peajes ?: 0, ts = System.currentTimeMillis()
            )
        )
        db.registrar(t.tecnicoId, "PARADA_REGISTRADA", "traslado=${t.uuid}; centro=$centroId; km=$kmLlegada")
    }

    /** Cierra el traslado y lo deja listo para validar y sincronizar (RF-07). */
    suspend fun finalizar(trasladoId: Long, kmFin: Int?, peajesUltimoTramo: Int?, ubicacion: Pair<Double, Double>?) {
        require(kmFin != null) { "Ingresa el kilometraje final" }
        require(peajesUltimoTramo == null || peajesUltimoTramo >= 0) { "Los peajes no pueden ser negativos" }
        val t = db.trasladoDao().porId(trasladoId) ?: error("Traslado no encontrado")
        check(t.estado == EstadoTraslado.EN_CURSO) { "El traslado ya fue finalizado" }
        val paradas = db.paradaDao().deTraslado(trasladoId)
        val kmPrevio = paradas.lastOrNull()?.kmLlegada ?: t.kmInicio
        require(kmFin >= kmPrevio) { "El kilometraje final no puede ser menor a $kmPrevio" }

        val peajesTotal = paradas.sumOf { it.peajes } + (peajesUltimoTramo ?: 0)
        db.trasladoDao().actualizar(
            t.copy(
                finTs = System.currentTimeMillis(), kmFin = kmFin, peajes = peajesTotal,
                latFin = ubicacion?.first, lonFin = ubicacion?.second,
                estado = EstadoTraslado.PENDIENTE, syncEstado = EstadoSync.PENDIENTE, syncError = null
            )
        )
        t.visitaId?.let { db.visitaDao().marcarAtendida(it) }
        db.registrar(t.tecnicoId, "TRASLADO_FINALIZADO", "traslado=${t.uuid}; km_fin=$kmFin; peajes=$peajesTotal")
    }

    // ---------- Jefe de servicio ----------

    fun todosCerrados(): Flow<List<Traslado>> = db.trasladoDao().todosCerrados()

    /** Valida, observa o rechaza un traslado dejando trazabilidad (RF-11). */
    suspend fun revisar(trasladoId: Long, jefeId: Long, nuevoEstado: String, comentario: String) {
        require(nuevoEstado == EstadoTraslado.VALIDADO || nuevoEstado == EstadoTraslado.OBSERVADO || nuevoEstado == EstadoTraslado.RECHAZADO) {
            "Estado inválido"
        }
        if (nuevoEstado != EstadoTraslado.VALIDADO) require(comentario.isNotBlank()) { "Escribe un comentario para observar o rechazar" }
        val t = db.trasladoDao().porId(trasladoId) ?: error("Traslado no encontrado")
        check(t.estado != EstadoTraslado.EN_CURSO) { "El traslado sigue en curso" }
        check(t.syncEstado == EstadoSync.SINCRONIZADO) { "El traslado aún no se sincroniza: no tiene costo ni desviación calculados" }
        db.trasladoDao().actualizar(
            t.copy(
                estado = nuevoEstado, comentarioJefe = comentario.trim().ifBlank { null },
                revisadoPor = jefeId, revisadoTs = System.currentTimeMillis()
            )
        )
        db.registrar(jefeId, "TRASLADO_$nuevoEstado", "traslado=${t.uuid}; comentario=${comentario.trim()}")
    }

    // ---------- Gerencia / Finanzas ----------

    /** Solo cuentan traslados VALIDADOS con costo calculado: el mismo período da el mismo resultado (RNF-14). */
    fun construirReporte(
        traslados: List<Traslado>, cat: Catalogo, desdeTs: Long, hastaTs: Long
    ): Reporte {
        val validos = traslados.filter {
            it.estado == EstadoTraslado.VALIDADO && it.costoTotal != null && it.inicioTs in desdeTs..hastaTs
        }
        fun km(t: Traslado) = t.distanciaOficial ?: ((t.kmFin ?: t.kmInicio) - t.kmInicio).toDouble()
        fun agrupar(clave: (Traslado) -> String): List<FilaReporte> =
            validos.groupBy(clave).map { (k, lista) ->
                FilaReporte(k, lista.size, lista.sumOf { km(it) }, lista.sumOf { it.costoTotal ?: 0.0 })
            }.sortedByDescending { it.costo }

        return Reporte(
            total = FilaReporte("Total", validos.size, validos.sumOf { km(it) }, validos.sumOf { it.costoTotal ?: 0.0 }),
            porCliente = agrupar { cat.cliente(it.destinoId) },
            porContrato = agrupar { cat.contrato(it.destinoId) },
            porTecnico = agrupar { cat.usuario(it.tecnicoId) },
            porVehiculo = agrupar { cat.vehiculo(it.vehiculoId) }
        )
    }

    // ---------- Sincronización (RF-25 a RF-27) ----------

    /**
     * Envía al servidor los traslados cerrados que aún no se sincronizan.
     * Si falla, el traslado queda en ERROR con el motivo y se reintenta en la próxima pasada (RNF-14).
     * @return cantidad de traslados sincronizados.
     */
    suspend fun sincronizarPendientes(): Int = mutexSync.withLock {
        var exitosos = 0
        for (t in db.trasladoDao().pendientesSync()) {
            try {
                val r = servidor.procesar(t, db.paradaDao().deTraslado(t.id))
                db.trasladoDao().actualizar(
                    t.copy(
                        syncEstado = EstadoSync.SINCRONIZADO, syncError = null,
                        distanciaOficial = r.distanciaOficial, desviacionPct = r.desviacionPct,
                        desviado = r.desviado, costoTotal = r.costo
                    )
                )
                db.registrar(null, "SYNC_OK", "traslado=${t.uuid}; costo=${Fmt.dinero(r.costo)}")
                exitosos++
            } catch (e: Exception) {
                db.trasladoDao().actualizar(t.copy(syncEstado = EstadoSync.ERROR, syncError = e.message ?: "Error desconocido"))
                db.registrar(null, "SYNC_ERROR", "traslado=${t.uuid}; motivo=${e.message}")
            }
        }
        exitosos
    }
}

package com.example.sqlite.data.remote

import com.example.sqlite.data.AppDatabase
import com.example.sqlite.data.Param
import com.example.sqlite.data.Parada
import com.example.sqlite.data.TipoTarifa
import com.example.sqlite.data.TipoVehiculo
import com.example.sqlite.data.Traslado
import com.example.sqlite.data.registrar
import com.example.sqlite.util.Fmt
import kotlin.math.abs

/**
 * Simula el servidor de TeqRuta mientras no exista la API real.
 *
 * Aquí vive TODO el cálculo de costos (RNF-06): el dispositivo nunca lo hace.
 * Para conectar un backend real basta reemplazar [procesar] por una llamada HTTPS
 * (Retrofit, por ejemplo) que envíe el traslado y devuelva un [Resultado].
 * El `uuid` del traslado es la clave de idempotencia: reenviarlo no genera duplicados (RF-27).
 */
class ServidorSimulado(private val db: AppDatabase) {

    data class Resultado(
        val distanciaOficial: Double?,
        val desviacionPct: Double?,
        val desviado: Boolean,
        val costo: Double
    )

    suspend fun procesar(t: Traslado, paradas: List<Parada>): Resultado {
        val kmFin = t.kmFin ?: error("El traslado no tiene kilometraje final")
        val declarada = (kmFin - t.kmInicio).toDouble()
        val vehiculo = db.vehiculoDao().porId(t.vehiculoId) ?: error("El vehículo del traslado no existe")

        // Distancia oficial: suma de tramos origen -> destino -> paradas (RF-22).
        val cadena = listOf(t.origenId, t.destinoId) + paradas.sortedBy { it.orden }.map { it.centroId }
        var oficial = 0.0
        var completa = true
        for ((a, b) in cadena.zipWithNext()) {
            if (a == b) continue
            val tramo = db.tramoDao().entre(a, b)
            if (tramo == null) {
                completa = false
                break
            }
            oficial += tramo.distanciaKm
        }
        val distOficial = if (completa) oficial else null

        // Desviación respecto del umbral configurable.
        val umbral = db.parametroDao().obtener(Param.UMBRAL_DESVIACION)?.toDoubleOrNull() ?: 10.0
        val desviacion = if (distOficial != null && distOficial > 0) abs(declarada - distOficial) / distOficial * 100 else null
        val desviado = desviacion != null && desviacion > umbral

        // Costo con la tarifa vigente en la FECHA DEL TRASLADO, no la actual (RF-21).
        val distanciaCosto = distOficial ?: declarada
        val base: Double
        val detalleTarifa: String
        if (vehiculo.tipo == TipoVehiculo.EMPRESA) {
            val tarifa = db.tarifaDao().vigenteEn(TipoTarifa.COMBUSTIBLE, t.inicioTs)
                ?: error("No hay tarifa de combustible vigente al ${Fmt.fecha(t.inicioTs)}")
            base = distanciaCosto / vehiculo.consumoKmPorLitro * tarifa.valor
            detalleTarifa = "combustible=${tarifa.valor}/L (tarifa #${tarifa.id}); consumo=${vehiculo.consumoKmPorLitro}km/L"
        } else {
            val tarifa = db.tarifaDao().vigenteEn(TipoTarifa.KM_PARTICULAR, t.inicioTs)
                ?: error("No hay tarifa por km (vehículo particular) vigente al ${Fmt.fecha(t.inicioTs)}")
            base = distanciaCosto * tarifa.valor
            detalleTarifa = "km_particular=${tarifa.valor}/km (tarifa #${tarifa.id})"
        }
        val costo = base + t.peajes

        // Registro auditable de cada cálculo: parámetros, resultado y fecha (RF-23, RNF-07).
        db.registrar(
            null, "CALCULO_COSTO",
            "traslado=${t.uuid}; distancia_costo=$distanciaCosto; distancia_declarada=$declarada; " +
                "distancia_oficial=$distOficial; desviacion_pct=$desviacion; umbral=$umbral; " +
                "$detalleTarifa; peajes=${t.peajes}; costo=$costo"
        )
        return Resultado(distOficial, desviacion, desviado, costo)
    }
}

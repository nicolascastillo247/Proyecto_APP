package com.example.sqlite.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sqlite.data.AdminRepository
import com.example.sqlite.data.AuthRepository
import com.example.sqlite.data.Bitacora
import com.example.sqlite.data.Catalogo
import com.example.sqlite.data.Centro
import com.example.sqlite.data.Tarifa
import com.example.sqlite.data.Tramo
import com.example.sqlite.data.Usuario
import com.example.sqlite.data.Vehiculo
import com.example.sqlite.data.Visita
import com.example.sqlite.util.Fmt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Mantenedores del administrador (RF-16 a RF-19). Los formularios llegan como mapas clave -> texto. */
class AdminViewModel(
    private val auth: AuthRepository,
    private val repo: AdminRepository
) : ViewModel() {

    private val compartir = SharingStarted.WhileSubscribed(5_000)

    val catalogo: StateFlow<Catalogo> = repo.catalogo.stateIn(viewModelScope, compartir, Catalogo())
    val usuarios: StateFlow<List<Usuario>> = repo.usuarios.stateIn(viewModelScope, compartir, emptyList())
    val vehiculos: StateFlow<List<Vehiculo>> = repo.vehiculos.stateIn(viewModelScope, compartir, emptyList())
    val centros: StateFlow<List<Centro>> = repo.centros.stateIn(viewModelScope, compartir, emptyList())
    val tramos: StateFlow<List<Tramo>> = repo.tramos.stateIn(viewModelScope, compartir, emptyList())
    val tarifas: StateFlow<List<Tarifa>> = repo.tarifas.stateIn(viewModelScope, compartir, emptyList())
    val visitas: StateFlow<List<Visita>> = repo.visitas.stateIn(viewModelScope, compartir, emptyList())
    val bitacora: StateFlow<List<Bitacora>> = repo.bitacora.stateIn(viewModelScope, compartir, emptyList())
    val umbral: StateFlow<String> = repo.umbral.map { it ?: "10" }.stateIn(viewModelScope, compartir, "10")

    private fun actor(): Long? = auth.usuario.value?.id

    /** Ejecuta la operación y avisa al formulario: null = éxito, texto = mensaje de error. */
    private fun ejecutar(alTerminar: (String?) -> Unit, bloque: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                bloque()
                alTerminar(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                alTerminar(e.message ?: "Ocurrió un error")
            }
        }
    }

    private fun texto(m: Map<String, String>, k: String) = m[k]?.trim().orEmpty()

    private fun decimal(m: Map<String, String>, k: String, etiqueta: String): Double =
        texto(m, k).replace(",", ".").toDoubleOrNull() ?: throw IllegalArgumentException("$etiqueta inválido")

    private fun decimalOpcional(m: Map<String, String>, k: String, etiqueta: String): Double? {
        val t = texto(m, k).replace(",", ".")
        if (t.isEmpty()) return null
        return t.toDoubleOrNull() ?: throw IllegalArgumentException("$etiqueta inválida")
    }

    private fun entero(m: Map<String, String>, k: String, etiqueta: String): Int =
        texto(m, k).toIntOrNull() ?: throw IllegalArgumentException("$etiqueta inválido")

    private fun idDe(m: Map<String, String>, k: String, etiqueta: String): Long =
        texto(m, k).toLongOrNull() ?: throw IllegalArgumentException("Selecciona $etiqueta")

    fun guardarUsuario(id: Long?, m: Map<String, String>, cb: (String?) -> Unit) = ejecutar(cb) {
        repo.guardarUsuario(actor(), id, texto(m, "nombre"), texto(m, "username"), m["password"].orEmpty(), texto(m, "rol"))
    }

    fun cambiarActivo(u: Usuario, cb: (String?) -> Unit) = ejecutar(cb) { repo.cambiarActivoUsuario(actor(), u) }

    fun guardarVehiculo(id: Long?, m: Map<String, String>, cb: (String?) -> Unit) = ejecutar(cb) {
        repo.guardarVehiculo(
            actor(), id, texto(m, "patente"), texto(m, "descripcion"), texto(m, "tipo"),
            decimal(m, "consumo", "Consumo")
        )
    }

    fun cambiarActivo(v: Vehiculo, cb: (String?) -> Unit) = ejecutar(cb) { repo.cambiarActivoVehiculo(actor(), v) }

    fun guardarCentro(id: Long?, m: Map<String, String>, cb: (String?) -> Unit) = ejecutar(cb) {
        repo.guardarCentro(
            actor(), id, texto(m, "nombre"), texto(m, "cliente"), texto(m, "contrato"), texto(m, "direccion"),
            decimalOpcional(m, "latitud", "Latitud"), decimalOpcional(m, "longitud", "Longitud"),
            texto(m, "esBase") == "SI"
        )
    }

    fun cambiarActivo(c: Centro, cb: (String?) -> Unit) = ejecutar(cb) { repo.cambiarActivoCentro(actor(), c) }

    fun guardarTramo(id: Long?, m: Map<String, String>, cb: (String?) -> Unit) = ejecutar(cb) {
        repo.guardarTramo(
            actor(), id, idDe(m, "origen", "el origen"), idDe(m, "destino", "el destino"),
            decimal(m, "distancia", "Distancia"), entero(m, "peajes", "Peajes")
        )
    }

    fun nuevaTarifa(m: Map<String, String>, cb: (String?) -> Unit) = ejecutar(cb) {
        repo.nuevaTarifa(actor(), texto(m, "tipo"), decimal(m, "valor", "Valor"), texto(m, "desde"))
    }

    fun guardarVisita(m: Map<String, String>, cb: (String?) -> Unit) = ejecutar(cb) {
        repo.guardarVisita(actor(), idDe(m, "tecnico", "el técnico"), idDe(m, "centro", "la clínica u hospital"), texto(m, "fecha"), texto(m, "descripcion"))
    }

    fun guardarUmbral(m: Map<String, String>, cb: (String?) -> Unit) = ejecutar(cb) {
        repo.guardarUmbral(actor(), decimal(m, "umbral", "Umbral"))
    }

    fun hoy(): String = Fmt.hoy()
}

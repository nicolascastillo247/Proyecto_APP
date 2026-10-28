package com.example.sqlite.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sqlite.data.AdminRepository
import com.example.sqlite.data.AuthRepository
import com.example.sqlite.data.Catalogo
import com.example.sqlite.data.Parada
import com.example.sqlite.data.SyncManager
import com.example.sqlite.data.Traslado
import com.example.sqlite.data.TrasladoRepository
import com.example.sqlite.data.Vehiculo
import com.example.sqlite.data.Centro
import com.example.sqlite.data.Visita
import com.example.sqlite.util.Fmt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Lógica de la pantalla del técnico (RF-03 a RF-09, RF-26). */
@OptIn(ExperimentalCoroutinesApi::class)
class TecnicoViewModel(
    private val auth: AuthRepository,
    private val repo: TrasladoRepository,
    admin: AdminRepository,
    private val sync: SyncManager
) : ViewModel() {

    private val compartir = SharingStarted.WhileSubscribed(5_000)
    private val tecnicoId = auth.usuario.map { it?.id ?: -1L }.distinctUntilChanged()

    val catalogo: StateFlow<Catalogo> = admin.catalogo.stateIn(viewModelScope, compartir, Catalogo())

    val vehiculos: StateFlow<List<Vehiculo>> =
        admin.vehiculos.map { l -> l.filter { it.activo } }.stateIn(viewModelScope, compartir, emptyList())

    val centros: StateFlow<List<Centro>> =
        admin.centros.map { l -> l.filter { it.activo } }.stateIn(viewModelScope, compartir, emptyList())

    val visitasHoy: StateFlow<List<Visita>> =
        tecnicoId.flatMapLatest { repo.visitasDelDia(it, Fmt.hoy()) }.stateIn(viewModelScope, compartir, emptyList())

    val activo: StateFlow<Traslado?> =
        tecnicoId.flatMapLatest { repo.activo(it) }.stateIn(viewModelScope, compartir, null)

    val paradasActivo: StateFlow<List<Parada>> =
        activo.flatMapLatest { t -> if (t == null) flowOf(emptyList()) else repo.paradas(t.id) }
            .stateIn(viewModelScope, compartir, emptyList())

    val historial: StateFlow<List<Traslado>> =
        tecnicoId.flatMapLatest { repo.historial(it) }.stateIn(viewModelScope, compartir, emptyList())

    val pendientesSync: StateFlow<Int> =
        tecnicoId.flatMapLatest { repo.pendientesSync(it) }.stateIn(viewModelScope, compartir, 0)

    private val _mensaje = MutableStateFlow<String?>(null)
    val mensaje: StateFlow<String?> = _mensaje.asStateFlow()

    fun limpiarMensaje() {
        _mensaje.value = null
    }

    // ---------- Consentimiento de ubicación (RNF-10) ----------

    private fun uid(): Long = auth.usuario.value?.id ?: -1L

    fun consentimientoRespondido() = auth.consentimientoRespondido(uid())
    fun ubicacionConsentida() = auth.ubicacionConsentida(uid())
    fun responderConsentimiento(acepta: Boolean) = auth.guardarConsentimiento(uid(), acepta)

    // ---------- Acciones ----------

    private fun ejecutar(exito: String? = null, bloque: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                bloque()
                if (exito != null) _mensaje.value = exito
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _mensaje.value = e.message ?: "Ocurrió un error"
            }
        }
    }

    private fun entero(texto: String, etiqueta: String): Int? {
        val t = texto.trim()
        if (t.isEmpty()) return null
        return t.toIntOrNull() ?: throw IllegalArgumentException("$etiqueta inválido")
    }

    fun iniciar(
        vehiculoId: Long?, origenId: Long?, destinoId: Long?, visitaId: Long?,
        kmInicio: String, ubicacion: Pair<Double, Double>?
    ) = ejecutar("Traslado iniciado") {
        repo.iniciar(uid(), vehiculoId, origenId, destinoId, visitaId, entero(kmInicio, "Kilometraje"), ubicacion)
    }

    fun agregarParada(trasladoId: Long, centroId: Long?, kmLlegada: String, peajes: String) =
        ejecutar("Parada registrada") {
            repo.agregarParada(trasladoId, centroId, entero(kmLlegada, "Kilometraje"), entero(peajes, "Peajes"))
        }

    fun finalizar(trasladoId: Long, kmFin: String, peajes: String, ubicacion: Pair<Double, Double>?) =
        ejecutar("Traslado enviado a validación") {
            repo.finalizar(trasladoId, entero(kmFin, "Kilometraje"), entero(peajes, "Peajes"), ubicacion)
            sync.sincronizarEnSegundoPlano() // si hay señal, se envía de inmediato
        }

    fun sincronizarAhora() {
        viewModelScope.launch { _mensaje.value = sync.sincronizarAhora() }
    }
}

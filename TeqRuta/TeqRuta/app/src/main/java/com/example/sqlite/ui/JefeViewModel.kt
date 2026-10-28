package com.example.sqlite.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sqlite.data.AdminRepository
import com.example.sqlite.data.AuthRepository
import com.example.sqlite.data.Catalogo
import com.example.sqlite.data.Parada
import com.example.sqlite.data.Rol
import com.example.sqlite.data.Traslado
import com.example.sqlite.data.TrasladoRepository
import com.example.sqlite.data.Usuario
import com.example.sqlite.util.Fmt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Lógica de la pantalla del jefe de servicio técnico (RF-10 a RF-12). */
class JefeViewModel(
    private val auth: AuthRepository,
    private val repo: TrasladoRepository,
    admin: AdminRepository
) : ViewModel() {

    private data class Filtros(val tecnico: Long?, val fecha: String, val estado: String?, val soloDesviados: Boolean)

    private val compartir = SharingStarted.WhileSubscribed(5_000)

    val catalogo: StateFlow<Catalogo> = admin.catalogo.stateIn(viewModelScope, compartir, Catalogo())

    val tecnicos: StateFlow<List<Usuario>> =
        admin.usuarios.map { l -> l.filter { it.rol == Rol.TECNICO } }.stateIn(viewModelScope, compartir, emptyList())

    val tecnicoFiltro = MutableStateFlow<Long?>(null)
    val fechaFiltro = MutableStateFlow("")
    val estadoFiltro = MutableStateFlow<String?>(null)
    val soloDesviados = MutableStateFlow(false)

    private val filtros = combine(tecnicoFiltro, fechaFiltro, estadoFiltro, soloDesviados) { t, f, e, d ->
        Filtros(t, f.trim(), e, d)
    }

    val traslados: StateFlow<List<Traslado>> =
        combine(repo.todosCerrados(), filtros) { lista, f ->
            lista.filter { t ->
                (f.tecnico == null || t.tecnicoId == f.tecnico) &&
                    (f.fecha.isEmpty() || Fmt.fecha(t.inicioTs).startsWith(f.fecha)) &&
                    (f.estado == null || t.estado == f.estado) &&
                    (!f.soloDesviados || t.desviado)
            }
        }.stateIn(viewModelScope, compartir, emptyList())

    private val _mensaje = MutableStateFlow<String?>(null)
    val mensaje: StateFlow<String?> = _mensaje.asStateFlow()

    fun limpiarMensaje() {
        _mensaje.value = null
    }

    fun paradas(trasladoId: Long): Flow<List<Parada>> = repo.paradas(trasladoId)

    fun revisar(trasladoId: Long, estado: String, comentario: String, alTerminar: () -> Unit) {
        viewModelScope.launch {
            try {
                val jefeId = auth.usuario.value?.id ?: error("Sin sesión")
                repo.revisar(trasladoId, jefeId, estado, comentario)
                _mensaje.value = "Traslado actualizado"
                alTerminar()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _mensaje.value = e.message ?: "Ocurrió un error"
            }
        }
    }
}

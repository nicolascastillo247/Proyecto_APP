package com.example.sqlite.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sqlite.data.AdminRepository
import com.example.sqlite.data.Reporte
import com.example.sqlite.data.TrasladoRepository
import com.example.sqlite.util.Fmt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Reportes de costo para Gerencia y Finanzas (RF-13 a RF-15). */
class GerenciaViewModel(
    private val repo: TrasladoRepository,
    admin: AdminRepository
) : ViewModel() {

    val desde = MutableStateFlow(Fmt.primerDiaMes())
    val hasta = MutableStateFlow(Fmt.hoy())

    /** null mientras las fechas no sean válidas (AAAA-MM-DD). */
    val reporte: StateFlow<Reporte?> =
        combine(repo.todosCerrados(), admin.catalogo, desde, hasta) { lista, cat, d, h ->
            val desdeTs = Fmt.inicioDia(d)
            val hastaTs = Fmt.finDia(h)
            if (desdeTs == null || hastaTs == null) null else repo.construirReporte(lista, cat, desdeTs, hastaTs)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun csv(): String? = reporte.value?.aCsv(desde.value, hasta.value)
}

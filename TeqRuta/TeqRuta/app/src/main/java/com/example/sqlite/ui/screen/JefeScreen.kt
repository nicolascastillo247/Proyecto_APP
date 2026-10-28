package com.example.sqlite.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sqlite.data.EstadoSync
import com.example.sqlite.data.EstadoTraslado
import com.example.sqlite.data.Traslado
import com.example.sqlite.data.Usuario
import com.example.sqlite.ui.JefeViewModel
import com.example.sqlite.util.Fmt

/** Pantalla del jefe de servicio técnico (RF-10, RF-11, RF-12). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JefeScreen(vm: JefeViewModel, usuario: Usuario, alSalir: () -> Unit) {
    val cat by vm.catalogo.collectAsState()
    val tecnicos by vm.tecnicos.collectAsState()
    val traslados by vm.traslados.collectAsState()
    val tecnicoFiltro by vm.tecnicoFiltro.collectAsState()
    val fechaFiltro by vm.fechaFiltro.collectAsState()
    val estadoFiltro by vm.estadoFiltro.collectAsState()
    val soloDesviados by vm.soloDesviados.collectAsState()
    val mensaje by vm.mensaje.collectAsState()

    val snack = remember { SnackbarHostState() }
    var seleccionadoId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(mensaje) {
        mensaje?.let {
            snack.showSnackbar(it)
            vm.limpiarMensaje()
        }
    }

    // Se busca por id para que el detalle se actualice si el traslado cambia.
    val seleccionado = traslados.firstOrNull { it.id == seleccionadoId }
    if (seleccionado != null) {
        DetalleTraslado(
            vm = vm, t = seleccionado, cat = cat,
            alCerrar = { seleccionadoId = null }
        )
    }

    Scaffold(
        topBar = { BarraSuperior("TeqRuta · Jefe de servicio", usuario, alSalir) },
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Desplegable(
                        etiqueta = "Técnico", opciones = tecnicos.map { it.id to it.nombre },
                        seleccion = tecnicoFiltro, alElegir = { vm.tecnicoFiltro.value = it },
                        textoNinguno = "Todos"
                    )
                    OutlinedTextField(
                        value = fechaFiltro, onValueChange = { vm.fechaFiltro.value = it },
                        label = { Text("Fecha (AAAA-MM-DD, puede ser parcial)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Desplegable(
                        etiqueta = "Estado", opciones = EstadoTraslado.filtrables,
                        seleccion = estadoFiltro, alElegir = { vm.estadoFiltro.value = it },
                        textoNinguno = "Todos"
                    )
                    FilterChip(
                        selected = soloDesviados, onClick = { vm.soloDesviados.value = !soloDesviados },
                        label = { Text("Solo con desviación de distancia") }
                    )
                }
            }
            item { Titulo("Traslados (${traslados.size})") }
            if (traslados.isEmpty()) item { Vacio("No hay traslados con esos filtros.") }
            items(traslados, key = { it.id }) { t ->
                TrasladoCard(t = t, cat = cat, mostrarTecnico = true, alTocar = { seleccionadoId = t.id }) {
                    Text("Costo: ${Fmt.dinero(t.costoTotal)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Detalle con desviación y acciones de validar / observar / rechazar con comentario. */
@Composable
private fun DetalleTraslado(vm: JefeViewModel, t: Traslado, cat: com.example.sqlite.data.Catalogo, alCerrar: () -> Unit) {
    val paradas by remember(t.id) { vm.paradas(t.id) }.collectAsState(initial = emptyList())
    var comentario by remember(t.id) { mutableStateOf(t.comentarioJefe.orEmpty()) }
    val sincronizado = t.syncEstado == EstadoSync.SINCRONIZADO

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Traslado de ${cat.usuario(t.tecnicoId)}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${Fmt.fechaHora(t.inicioTs)} → ${Fmt.fechaHora(t.finTs)}")
                Text("${cat.centro(t.origenId)} → ${cat.centro(t.destinoId)}")
                paradas.forEach { Text("  Parada ${it.orden}: ${cat.centro(it.centroId)} (km ${it.kmLlegada})") }
                Text("Vehículo: ${cat.vehiculo(t.vehiculoId)}")
                Text("Estado: ${EstadoTraslado.etiqueta(t.estado)}", fontWeight = FontWeight.SemiBold)
                Text("Km declarados: ${Fmt.km(t.kmFin?.let { (it - t.kmInicio).toDouble() })}")
                Text("Km oficiales: ${Fmt.km(t.distanciaOficial)}")
                Text(
                    "Desviación: ${Fmt.pct(t.desviacionPct)}" + if (t.desviado) "  ⚠ supera el umbral" else "",
                    color = if (t.desviado) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Text("Peajes: ${Fmt.dinero(t.peajes.toDouble())} · Costo: ${Fmt.dinero(t.costoTotal)}")
                Text(
                    if (t.latIni != null) "Ubicación de inicio: ${"%.5f".format(java.util.Locale.US, t.latIni)}, ${"%.5f".format(java.util.Locale.US, t.lonIni)}"
                    else "Sin ubicación de inicio"
                )
                if (t.revisadoTs != null) {
                    Text("Revisado por ${t.revisadoPor?.let { cat.usuario(it) } ?: "—"} el ${Fmt.fechaHora(t.revisadoTs)}")
                }
                if (!sincronizado) {
                    Text("Aún no se sincroniza: no se puede revisar hasta tener costo y desviación.", color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(
                    value = comentario, onValueChange = { comentario = it },
                    label = { Text("Comentario (obligatorio al observar o rechazar)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        enabled = sincronizado,
                        onClick = { vm.revisar(t.id, EstadoTraslado.VALIDADO, comentario, alCerrar) }
                    ) { Text("Validar") }
                    OutlinedButton(
                        enabled = sincronizado,
                        onClick = { vm.revisar(t.id, EstadoTraslado.OBSERVADO, comentario, alCerrar) }
                    ) { Text("Observar") }
                }
                OutlinedButton(
                    enabled = sincronizado,
                    onClick = { vm.revisar(t.id, EstadoTraslado.RECHAZADO, comentario, alCerrar) }
                ) { Text("Rechazar") }
            }
        },
        confirmButton = { TextButton(onClick = alCerrar) { Text("Cerrar") } }
    )
}

package com.example.sqlite.ui.screen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.sqlite.data.Catalogo
import com.example.sqlite.data.Parada
import com.example.sqlite.data.Traslado
import com.example.sqlite.data.Usuario
import com.example.sqlite.data.Visita
import com.example.sqlite.ui.TecnicoViewModel
import com.example.sqlite.util.Fmt
import com.example.sqlite.util.LocationHelper

/** Pantalla del técnico: visitas del día, traslado en curso e historial (RF-03 a RF-09, RF-26). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TecnicoScreen(vm: TecnicoViewModel, usuario: Usuario, alSalir: () -> Unit) {
    val ctx = LocalContext.current
    val cat by vm.catalogo.collectAsState()
    val vehiculos by vm.vehiculos.collectAsState()
    val centros by vm.centros.collectAsState()
    val visitas by vm.visitasHoy.collectAsState()
    val activo by vm.activo.collectAsState()
    val paradas by vm.paradasActivo.collectAsState()
    val historial by vm.historial.collectAsState()
    val pendientes by vm.pendientesSync.collectAsState()
    val mensaje by vm.mensaje.collectAsState()

    var tab by remember { mutableIntStateOf(0) }
    val snack = remember { SnackbarHostState() }
    var mostrarInicio by remember { mutableStateOf(false) }
    var visitaElegida by remember { mutableStateOf<Visita?>(null) }
    var mostrarParada by remember { mutableStateOf(false) }
    var consentimientoOk by remember { mutableStateOf(vm.consentimientoRespondido()) }
    var obteniendoUbicacion by remember { mutableStateOf(false) }

    LaunchedEffect(mensaje) {
        mensaje?.let {
            snack.showSnackbar(it)
            vm.limpiarMensaje()
        }
    }
    // Al haber un traslado en curso, se muestra su pestaña.
    LaunchedEffect(activo?.id) { if (activo != null) tab = 1 }

    // --- Ubicación solo al iniciar/finalizar, con consentimiento y permiso (RNF-10, RNF-11) ---
    var pendiente by remember { mutableStateOf<(() -> Unit)?>(null) }
    val lanzadorPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        pendiente?.invoke()
        pendiente = null
    }
    fun conUbicacion(accion: (Pair<Double, Double>?) -> Unit) {
        if (!vm.ubicacionConsentida()) {
            accion(null)
            return
        }
        val ejecutar = {
            obteniendoUbicacion = true
            LocationHelper.capturar(ctx) { loc ->
                obteniendoUbicacion = false
                accion(loc)
            }
        }
        if (LocationHelper.tienePermiso(ctx)) {
            ejecutar()
        } else {
            pendiente = ejecutar
            lanzadorPermiso.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    if (!consentimientoOk) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Uso de tu ubicación") },
            text = {
                Text(
                    "TeqRuta registra tu ubicación solo dos veces por traslado: al iniciarlo y al finalizarlo. " +
                        "No te sigue de forma continua. Se usa para validar los recorridos y se conserva por tiempo limitado.\n\n" +
                        "Si no aceptas, puedes registrar traslados igual, sin ubicación."
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.responderConsentimiento(true); consentimientoOk = true }) { Text("Acepto") }
            },
            dismissButton = {
                TextButton(onClick = { vm.responderConsentimiento(false); consentimientoOk = true }) { Text("No acepto") }
            }
        )
    }

    if (mostrarInicio) {
        val base = centros.firstOrNull { it.esBase }?.id
        FormularioDialog(
            titulo = "Iniciar traslado",
            campos = listOf(
                Campo("vehiculo", "Vehículo", TipoCampo.OPCION, vehiculos.map { it.id.toString() to "${it.patente} · ${it.descripcion}" }),
                Campo("origen", "Origen", TipoCampo.OPCION, centros.map { it.id.toString() to it.nombre }),
                Campo("destino", "Clínica / hospital de destino", TipoCampo.OPCION, centros.map { it.id.toString() to it.nombre }),
                Campo("km", "Kilometraje de inicio", TipoCampo.NUMERO)
            ),
            inicial = mapOf(
                "vehiculo" to (vehiculos.singleOrNull()?.id?.toString() ?: ""),
                "origen" to (base?.toString() ?: ""),
                "destino" to (visitaElegida?.centroId?.toString() ?: "")
            ),
            textoGuardar = "Iniciar",
            alCerrar = { mostrarInicio = false },
            alGuardar = { v, cb ->
                val error = when {
                    v["vehiculo"].isNullOrBlank() -> "Selecciona el vehículo"
                    v["origen"].isNullOrBlank() -> "Selecciona el origen"
                    v["destino"].isNullOrBlank() -> "Selecciona el destino"
                    v["km"].isNullOrBlank() -> "Ingresa el kilometraje de inicio"
                    else -> null
                }
                if (error != null) {
                    cb(error)
                } else {
                    val visitaId = visitaElegida?.id
                    conUbicacion { loc ->
                        vm.iniciar(
                            v["vehiculo"]?.toLongOrNull(), v["origen"]?.toLongOrNull(),
                            v["destino"]?.toLongOrNull(), visitaId, v["km"].orEmpty(), loc
                        )
                    }
                    cb(null)
                }
            }
        )
    }

    val trasladoActivo = activo
    if (mostrarParada && trasladoActivo != null) {
        FormularioDialog(
            titulo = "Parada intermedia",
            campos = listOf(
                Campo("centro", "Clínica / hospital", TipoCampo.OPCION, centros.map { it.id.toString() to it.nombre }),
                Campo("km", "Kilometraje al llegar", TipoCampo.NUMERO),
                Campo("peajes", "Peajes pagados en este tramo (opcional)", TipoCampo.NUMERO)
            ),
            inicial = emptyMap(),
            alCerrar = { mostrarParada = false },
            alGuardar = { v, cb ->
                when {
                    v["centro"].isNullOrBlank() -> cb("Selecciona la clínica u hospital")
                    v["km"].isNullOrBlank() -> cb("Ingresa el kilometraje")
                    else -> {
                        vm.agregarParada(trasladoActivo.id, v["centro"]?.toLongOrNull(), v["km"].orEmpty(), v["peajes"].orEmpty())
                        cb(null)
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = { BarraSuperior("TeqRuta · Técnico", usuario, alSalir) },
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Hoy") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(if (activo != null) "Traslado ●" else "Traslado") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Historial") })
            }
            if (obteniendoUbicacion) Text("Obteniendo ubicación…", modifier = Modifier.padding(12.dp))

            when (tab) {
                0 -> TabHoy(
                    visitas = visitas, cat = cat, hayActivo = activo != null, pendientes = pendientes,
                    alSincronizar = vm::sincronizarAhora,
                    alIniciar = { visita -> visitaElegida = visita; mostrarInicio = true }
                )
                1 -> TabTraslado(
                    activo = activo, paradas = paradas, cat = cat,
                    alAgregarParada = { mostrarParada = true },
                    alFinalizar = { id, kmFin, peajes ->
                        conUbicacion { loc -> vm.finalizar(id, kmFin, peajes, loc) }
                    },
                    alIniciarSinVisita = { visitaElegida = null; mostrarInicio = true }
                )
                else -> TabHistorial(historial = historial, cat = cat)
            }
        }
    }
}

@Composable
private fun TabHoy(
    visitas: List<Visita>,
    cat: Catalogo,
    hayActivo: Boolean,
    pendientes: Int,
    alSincronizar: () -> Unit,
    alIniciar: (Visita?) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Card(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        if (pendientes == 0) "Todo sincronizado" else "Pendientes de sincronizar: $pendientes",
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(onClick = alSincronizar) { Text("Sincronizar") }
                }
            }
        }
        item { Titulo("Visitas de hoy (${Fmt.hoy()})") }
        if (visitas.isEmpty()) item { Vacio("No tienes visitas asignadas para hoy.") }
        items(visitas, key = { it.id }) { v ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(cat.centro(v.centroId), fontWeight = FontWeight.Bold)
                    Text("${cat.cliente(v.centroId)} · contrato ${cat.contrato(v.centroId)}", style = MaterialTheme.typography.bodyMedium)
                    if (v.descripcion.isNotBlank()) Text(v.descripcion, style = MaterialTheme.typography.bodyMedium)
                    if (v.atendida) {
                        Etiqueta("Atendida", androidx.compose.ui.graphics.Color(0xFF2E7D32))
                    } else {
                        Button(
                            onClick = { alIniciar(v) }, enabled = !hayActivo,
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) { Text(if (hayActivo) "Ya tienes un traslado en curso" else "Iniciar traslado") }
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { alIniciar(null) }, enabled = !hayActivo,
                modifier = Modifier.fillMaxWidth().padding(12.dp).height(56.dp)
            ) { Text("Iniciar traslado sin visita asignada") }
        }
    }
}

@Composable
private fun TabTraslado(
    activo: Traslado?,
    paradas: List<Parada>,
    cat: Catalogo,
    alAgregarParada: () -> Unit,
    alFinalizar: (Long, String, String) -> Unit,
    alIniciarSinVisita: () -> Unit
) {
    if (activo == null) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Vacio("No tienes un traslado en curso. Inicia uno desde tus visitas de hoy.")
            OutlinedButton(onClick = alIniciarSinVisita, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Iniciar traslado")
            }
        }
        return
    }

    var kmFin by remember(activo.id) { mutableStateOf("") }
    var peajes by remember(activo.id) { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Traslado en curso", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Inicio: ${Fmt.fechaHora(activo.inicioTs)}")
                Text("${cat.centro(activo.origenId)} → ${cat.centro(activo.destinoId)}")
                Text("Vehículo: ${cat.vehiculo(activo.vehiculoId)}")
                Text("Km de inicio: ${activo.kmInicio}")
                Text(if (activo.latIni != null) "Ubicación de inicio registrada" else "Sin ubicación de inicio", style = MaterialTheme.typography.bodySmall)
            }
        }

        if (paradas.isNotEmpty()) {
            Text("Paradas intermedias", fontWeight = FontWeight.Bold)
            paradas.forEach { p ->
                Text("${p.orden}. ${cat.centro(p.centroId)} · km ${p.kmLlegada} · peajes ${Fmt.dinero(p.peajes.toDouble())}")
            }
        }

        OutlinedButton(onClick = alAgregarParada, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Agregar parada en otra clínica")
        }

        Text("Finalizar traslado", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = kmFin, onValueChange = { kmFin = it }, label = { Text("Kilometraje final") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = peajes, onValueChange = { peajes = it }, label = { Text("Peajes pagados desde la última parada (opcional)") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { alFinalizar(activo.id, kmFin, peajes) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Finalizar y enviar a validación", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun TabHistorial(historial: List<Traslado>, cat: Catalogo) {
    if (historial.isEmpty()) {
        Vacio("Aún no tienes traslados finalizados.")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(historial, key = { it.id }) { t ->
            TrasladoCard(t = t, cat = cat, mostrarTecnico = false) {
                // El costo lo calcula el servidor: se ve recién al sincronizar (RNF-06).
                Text(
                    if (t.costoTotal != null) "Costo calculado: ${Fmt.dinero(t.costoTotal)}" else "Costo: se calcula al sincronizar",
                    style = MaterialTheme.typography.bodyMedium
                )
                t.comentarioJefe?.let { Text("Comentario del jefe: $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

package com.example.sqlite.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.sqlite.data.Rol
import com.example.sqlite.data.TipoTarifa
import com.example.sqlite.data.TipoVehiculo
import com.example.sqlite.data.Usuario
import com.example.sqlite.ui.AdminViewModel
import com.example.sqlite.util.Fmt

private val SECCIONES = listOf("Técnicos", "Vehículos", "Centros", "Tramos", "Tarifas", "Visitas", "Parámetros", "Bitácora")

/** Mantenedores del administrador (RF-16 a RF-19) + asignación de visitas y consulta de la bitácora. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(vm: AdminViewModel, usuario: Usuario, alSalir: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(topBar = { BarraSuperior("TeqRuta · Administración", usuario, alSalir) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                SECCIONES.forEachIndexed { i, nombre ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(nombre) })
                }
            }
            when (tab) {
                0 -> TabUsuarios(vm)
                1 -> TabVehiculos(vm)
                2 -> TabCentros(vm)
                3 -> TabTramos(vm)
                4 -> TabTarifas(vm)
                5 -> TabVisitas(vm)
                6 -> TabParametros(vm)
                else -> TabBitacora(vm)
            }
        }
    }
}

/** Lista con botones Nuevo / Editar / Activar-Desactivar y su formulario. */
@Composable
private fun <T> Mantenedor(
    datos: List<T>,
    clave: (T) -> Long,
    titulo: (T) -> String,
    detalle: (T) -> String,
    activo: (T) -> Boolean?,
    entidad: String,
    campos: List<Campo>,
    inicial: (T?) -> Map<String, String>,
    guardar: (T?, Map<String, String>, (String?) -> Unit) -> Unit,
    cambiarActivo: ((T, (String?) -> Unit) -> Unit)? = null,
    permitirEditar: Boolean = true
) {
    var editando by remember { mutableStateOf<T?>(null) }
    var creando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }

    if (creando || editando != null) {
        val actual = editando
        FormularioDialog(
            titulo = if (actual == null) "Nuevo $entidad" else "Editar $entidad",
            campos = campos,
            inicial = inicial(actual),
            alCerrar = { creando = false; editando = null },
            alGuardar = { v, cb -> guardar(actual, v, cb) }
        )
    }
    aviso?.let {
        AlertDialog(
            onDismissRequest = { aviso = null },
            confirmButton = { TextButton(onClick = { aviso = null }) { Text("OK") } },
            text = { Text(it) }
        )
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Button(
                onClick = { creando = true },
                modifier = Modifier.fillMaxWidth().padding(12.dp).height(52.dp)
            ) { Text("Nuevo $entidad") }
        }
        if (datos.isEmpty()) item { Vacio("Sin registros.") }
        items(datos, key = { clave(it) }) { item ->
            val estaActivo = activo(item)
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(titulo(item), fontWeight = FontWeight.Bold)
                    Text(detalle(item), style = MaterialTheme.typography.bodyMedium)
                    Row {
                        if (permitirEditar) TextButton(onClick = { editando = item }) { Text("Editar") }
                        if (cambiarActivo != null && estaActivo != null) {
                            TextButton(onClick = { cambiarActivo(item) { e -> aviso = e } }) {
                                Text(if (estaActivo) "Desactivar" else "Activar")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun estado(activo: Boolean) = if (activo) "Activo" else "Inactivo"

@Composable
private fun TabUsuarios(vm: AdminViewModel) {
    val lista by vm.usuarios.collectAsState()
    Mantenedor(
        datos = lista, clave = { it.id }, titulo = { it.nombre },
        detalle = { "${it.username} · ${Rol.etiqueta(it.rol)} · ${estado(it.activo)}" },
        activo = { it.activo }, entidad = "usuario",
        campos = listOf(
            Campo("nombre", "Nombre completo"),
            Campo("username", "Usuario de acceso"),
            Campo("password", "Contraseña (al editar, vacía = no cambiar)", TipoCampo.CLAVE),
            Campo("rol", "Perfil", TipoCampo.OPCION, Rol.todos)
        ),
        inicial = { u -> if (u == null) mapOf("rol" to Rol.TECNICO) else mapOf("nombre" to u.nombre, "username" to u.username, "rol" to u.rol) },
        guardar = { u, v, cb -> vm.guardarUsuario(u?.id, v, cb) },
        cambiarActivo = { u, cb -> vm.cambiarActivo(u, cb) }
    )
}

@Composable
private fun TabVehiculos(vm: AdminViewModel) {
    val lista by vm.vehiculos.collectAsState()
    Mantenedor(
        datos = lista, clave = { it.id }, titulo = { "${it.patente} · ${it.descripcion}" },
        detalle = { v -> "${TipoVehiculo.todos.first { it.first == v.tipo }.second} · ${v.consumoKmPorLitro} km/L · ${estado(v.activo)}" },
        activo = { it.activo }, entidad = "vehículo",
        campos = listOf(
            Campo("patente", "Patente"),
            Campo("descripcion", "Descripción"),
            Campo("tipo", "Tipo", TipoCampo.OPCION, TipoVehiculo.todos),
            Campo("consumo", "Consumo (km por litro)", TipoCampo.DECIMAL)
        ),
        inicial = { v -> if (v == null) mapOf("tipo" to TipoVehiculo.EMPRESA) else mapOf("patente" to v.patente, "descripcion" to v.descripcion, "tipo" to v.tipo, "consumo" to v.consumoKmPorLitro.toString()) },
        guardar = { v, m, cb -> vm.guardarVehiculo(v?.id, m, cb) },
        cambiarActivo = { v, cb -> vm.cambiarActivo(v, cb) }
    )
}

@Composable
private fun TabCentros(vm: AdminViewModel) {
    val lista by vm.centros.collectAsState()
    Mantenedor(
        datos = lista, clave = { it.id }, titulo = { it.nombre + if (it.esBase) " (base)" else "" },
        detalle = { "${it.cliente} · contrato ${it.contrato} · ${if (it.latitud != null) "con ubicación" else "sin ubicación"} · ${estado(it.activo)}" },
        activo = { it.activo }, entidad = "centro",
        campos = listOf(
            Campo("nombre", "Nombre de la clínica / hospital"),
            Campo("cliente", "Cliente"),
            Campo("contrato", "Contrato"),
            Campo("direccion", "Dirección"),
            Campo("latitud", "Latitud (opcional)", TipoCampo.DECIMAL),
            Campo("longitud", "Longitud (opcional)", TipoCampo.DECIMAL),
            Campo("esBase", "¿Es la base de la empresa?", TipoCampo.OPCION, listOf("NO" to "No", "SI" to "Sí"))
        ),
        inicial = { c ->
            if (c == null) mapOf("esBase" to "NO") else mapOf(
                "nombre" to c.nombre, "cliente" to c.cliente, "contrato" to c.contrato, "direccion" to c.direccion,
                "latitud" to (c.latitud?.toString() ?: ""), "longitud" to (c.longitud?.toString() ?: ""),
                "esBase" to if (c.esBase) "SI" else "NO"
            )
        },
        guardar = { c, m, cb -> vm.guardarCentro(c?.id, m, cb) },
        cambiarActivo = { c, cb -> vm.cambiarActivo(c, cb) }
    )
}

@Composable
private fun TabTramos(vm: AdminViewModel) {
    val lista by vm.tramos.collectAsState()
    val cat by vm.catalogo.collectAsState()
    val centros = cat.centros.values.sortedBy { it.nombre }.map { it.id.toString() to it.nombre }
    Mantenedor(
        datos = lista, clave = { it.id }, titulo = { "${cat.centro(it.origenId)} ↔ ${cat.centro(it.destinoId)}" },
        detalle = { "${it.distanciaKm} km oficiales · peajes ${Fmt.dinero(it.peajes.toDouble())}" },
        activo = { null }, entidad = "tramo",
        campos = listOf(
            Campo("origen", "Origen", TipoCampo.OPCION, centros),
            Campo("destino", "Destino", TipoCampo.OPCION, centros),
            Campo("distancia", "Distancia oficial (km)", TipoCampo.DECIMAL),
            Campo("peajes", "Peajes ($)", TipoCampo.NUMERO)
        ),
        inicial = { t -> if (t == null) mapOf("peajes" to "0") else mapOf("origen" to t.origenId.toString(), "destino" to t.destinoId.toString(), "distancia" to t.distanciaKm.toString(), "peajes" to t.peajes.toString()) },
        guardar = { t, m, cb -> vm.guardarTramo(t?.id, m, cb) }
    )
}

@Composable
private fun TabTarifas(vm: AdminViewModel) {
    val lista by vm.tarifas.collectAsState()
    Mantenedor(
        datos = lista, clave = { it.id },
        titulo = { "${TipoTarifa.etiqueta(it.tipo)}: ${Fmt.dinero(it.valor)}" },
        detalle = { "Desde ${Fmt.fecha(it.vigenteDesde)} · " + (it.vigenteHasta?.let { h -> "hasta ${Fmt.fecha(h)}" } ?: "vigente") },
        activo = { null }, entidad = "valor de tarifa",
        campos = listOf(
            Campo("tipo", "Tipo de tarifa", TipoCampo.OPCION, TipoTarifa.todos),
            Campo("valor", "Valor ($)", TipoCampo.DECIMAL),
            Campo("desde", "Vigente desde (AAAA-MM-DD)")
        ),
        inicial = { mapOf("desde" to vm.hoy()) },
        guardar = { _, m, cb -> vm.nuevaTarifa(m, cb) },
        permitirEditar = false
    )
}

@Composable
private fun TabVisitas(vm: AdminViewModel) {
    val lista by vm.visitas.collectAsState()
    val cat by vm.catalogo.collectAsState()
    val tecnicos = cat.usuarios.values.filter { it.rol == Rol.TECNICO && it.activo }.map { it.id.toString() to it.nombre }
    val centros = cat.centros.values.filter { it.activo && !it.esBase }.sortedBy { it.nombre }.map { it.id.toString() to it.nombre }
    Mantenedor(
        datos = lista, clave = { it.id },
        titulo = { "${it.fecha} · ${cat.centro(it.centroId)}" },
        detalle = { "${cat.usuario(it.tecnicoId)} · ${if (it.atendida) "atendida" else "pendiente"}" + if (it.descripcion.isNotBlank()) " · ${it.descripcion}" else "" },
        activo = { null }, entidad = "visita",
        campos = listOf(
            Campo("tecnico", "Técnico", TipoCampo.OPCION, tecnicos),
            Campo("centro", "Clínica / hospital", TipoCampo.OPCION, centros),
            Campo("fecha", "Fecha (AAAA-MM-DD)"),
            Campo("descripcion", "Descripción del llamado")
        ),
        inicial = { mapOf("fecha" to vm.hoy()) },
        guardar = { _, m, cb -> vm.guardarVisita(m, cb) },
        permitirEditar = false
    )
}

@Composable
private fun TabParametros(vm: AdminViewModel) {
    val actual by vm.umbral.collectAsState()
    var texto by remember(actual) { mutableStateOf(actual) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Se marca un traslado cuando la distancia declarada se aleja de la oficial más que este porcentaje. Cambia sin recompilar la app.")
        OutlinedTextField(
            value = texto, onValueChange = { texto = it; mensaje = null },
            label = { Text("Umbral de desviación (%)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { vm.guardarUmbral(mapOf("umbral" to texto)) { e -> mensaje = e ?: "Guardado" } },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Guardar") }
        mensaje?.let { Text(it) }
    }
}

@Composable
private fun TabBitacora(vm: AdminViewModel) {
    val lista by vm.bitacora.collectAsState()
    val cat by vm.catalogo.collectAsState()
    LazyColumn(Modifier.fillMaxSize()) {
        item { Titulo("Bitácora inmutable (últimos ${lista.size})") }
        items(lista, key = { it.id }) { b ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text("${Fmt.fechaHora(b.ts)} · ${b.accion} · ${b.usuarioId?.let { cat.usuario(it) } ?: "sistema"}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text(b.detalle, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

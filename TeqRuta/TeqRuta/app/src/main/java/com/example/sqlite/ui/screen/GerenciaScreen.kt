package com.example.sqlite.ui.screen

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sqlite.data.FilaReporte
import com.example.sqlite.data.Usuario
import com.example.sqlite.ui.GerenciaViewModel
import com.example.sqlite.util.Fmt

/** Pantalla de Gerencia y Finanzas: costo por cliente, contrato, técnico y vehículo (RF-13 a RF-15). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GerenciaScreen(vm: GerenciaViewModel, usuario: Usuario, alSalir: () -> Unit) {
    val ctx = LocalContext.current
    val desde by vm.desde.collectAsState()
    val hasta by vm.hasta.collectAsState()
    val reporte by vm.reporte.collectAsState()

    Scaffold(topBar = { BarraSuperior("TeqRuta · Gerencia y Finanzas", usuario, alSalir) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = desde, onValueChange = { vm.desde.value = it }, label = { Text("Desde (AAAA-MM-DD)") },
                    singleLine = true, modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = hasta, onValueChange = { vm.hasta.value = it }, label = { Text("Hasta (AAAA-MM-DD)") },
                    singleLine = true, modifier = Modifier.weight(1f)
                )
            }
            val r = reporte
            if (r == null) {
                Text("Ingresa un período válido con formato AAAA-MM-DD.", color = MaterialTheme.colorScheme.error)
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Costo total del período", style = MaterialTheme.typography.bodyMedium)
                        Text(Fmt.dinero(r.total.costo), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("${r.total.traslados} traslados validados · ${Fmt.km(r.total.km)}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Solo incluye traslados validados y calculados por el servidor.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Button(
                    onClick = {
                        val csv = vm.csv() ?: return@Button
                        val envio = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_SUBJECT, "Reporte de costos de traslado $desde a $hasta")
                            putExtra(Intent.EXTRA_TEXT, csv)
                        }
                        ctx.startActivity(Intent.createChooser(envio, "Exportar reporte (CSV)"))
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text("Exportar reporte (CSV / Excel)") }

                Seccion("Por cliente", r.porCliente)
                Seccion("Por contrato (rentabilidad)", r.porContrato)
                Seccion("Por técnico", r.porTecnico)
                Seccion("Por vehículo", r.porVehiculo)
            }
        }
    }
}

@Composable
private fun Seccion(titulo: String, filas: List<FilaReporte>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (filas.isEmpty()) Text("Sin datos en el período.", style = MaterialTheme.typography.bodyMedium)
            filas.forEach { f ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(f.clave, fontWeight = FontWeight.SemiBold)
                        Text("${f.traslados} traslados · ${Fmt.km(f.km)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(Fmt.dinero(f.costo), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

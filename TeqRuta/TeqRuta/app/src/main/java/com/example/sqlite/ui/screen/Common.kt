package com.example.sqlite.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.sqlite.data.Catalogo
import com.example.sqlite.data.EstadoSync
import com.example.sqlite.data.EstadoTraslado
import com.example.sqlite.data.Rol
import com.example.sqlite.data.Traslado
import com.example.sqlite.data.Usuario
import com.example.sqlite.util.Fmt

private val Verde = Color(0xFF2E7D32)
private val Ambar = Color(0xFFB26A00)
private val Rojo = Color(0xFFC62828)
private val Gris = Color(0xFF546E7A)
private val Azul = Color(0xFF1565C0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarraSuperior(titulo: String, usuario: Usuario, alSalir: () -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${usuario.nombre} · ${Rol.etiqueta(usuario.rol)}", style = MaterialTheme.typography.bodySmall)
            }
        },
        actions = {
            TextButton(onClick = alSalir) {
                Text("Salir")
            }
        },
    )
}

@Composable
fun Etiqueta(texto: String, color: Color) {
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
        Text(
            text = texto,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

fun colorEstado(estado: String): Color = when (estado) {
    EstadoTraslado.VALIDADO -> Verde
    EstadoTraslado.OBSERVADO -> Ambar
    EstadoTraslado.RECHAZADO -> Rojo
    EstadoTraslado.EN_CURSO -> Azul
    else -> Gris
}

fun colorSync(sync: String): Color = when (sync) {
    EstadoSync.SINCRONIZADO -> Verde
    EstadoSync.ERROR -> Rojo
    else -> Ambar
}

@Composable
fun Vacio(texto: String) {
    Text(
        text = texto,
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
fun Titulo(texto: String) {
    Text(
        text = texto,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** Tarjeta de un traslado, reutilizada por técnico y jefe. */
@Composable
fun TrasladoCard(
    t: Traslado,
    cat: Catalogo,
    mostrarTecnico: Boolean,
    alTocar: (() -> Unit)? = null,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val base = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
    Card(if (alTocar != null) base.clickable { alTocar() } else base) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(Fmt.fechaHora(t.inicioTs), fontWeight = FontWeight.Bold)
                Etiqueta(EstadoTraslado.etiqueta(t.estado), colorEstado(t.estado))
            }
            Text("${cat.centro(t.origenId)} → ${cat.centro(t.destinoId)}")
            if (mostrarTecnico) Text("Técnico: ${cat.usuario(t.tecnicoId)}", style = MaterialTheme.typography.bodyMedium)
            Text("Vehículo: ${cat.vehiculo(t.vehiculoId)}", style = MaterialTheme.typography.bodyMedium)
            val declarada = t.kmFin?.let { (it - t.kmInicio).toDouble() }
            Text("Km declarados: ${Fmt.km(declarada)} · Peajes: ${Fmt.dinero(t.peajes.toDouble())}", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Etiqueta(EstadoSync.etiqueta(t.syncEstado), colorSync(t.syncEstado))
                if (t.desviado) Etiqueta("Desviación ${Fmt.pct(t.desviacionPct)}", Rojo)
            }
            if ((t.syncEstado == EstadoSync.ERROR) && (t.syncError != null)) {
                Text("Motivo: ${t.syncError}", color = Rojo, style = MaterialTheme.typography.bodySmall)
            }
            extra()
        }
    }
}

/** Lista desplegable genérica. Si [textoNinguno] no es null se agrega una opción para limpiar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Desplegable(
    etiqueta: String,
    opciones: List<Pair<T, String>>,
    seleccion: T?,
    alElegir: (T?) -> Unit,
    modifier: Modifier = Modifier,
    textoNinguno: String? = null,
) {
    var abierto by remember { mutableStateOf(value = false) }
    val texto = opciones.firstOrNull { it.first == seleccion }?.second ?: textoNinguno ?: ""
    ExposedDropdownMenuBox(
        expanded = abierto,
        onExpandedChange = { abierto = !abierto },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = texto,
            onValueChange = {},
            readOnly = true,
            label = { Text(etiqueta) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = abierto) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            if (textoNinguno != null) {
                DropdownMenuItem(text = { Text(textoNinguno) }, onClick = { alElegir(null); abierto = false })
            }
            opciones.forEach { (valor, nombre) ->
                DropdownMenuItem(text = { Text(nombre) }, onClick = { alElegir(valor); abierto = false })
            }
        }
    }
}

// ---------- Formularios genéricos (usados por el administrador y varios diálogos) ----------

enum class TipoCampo { TEXTO, NUMERO, DECIMAL, CLAVE, OPCION }

data class Campo(
    val clave: String,
    val etiqueta: String,
    val tipo: TipoCampo = TipoCampo.TEXTO,
    val opciones: List<Pair<String, String>> = emptyList(),
)

/**
 * Diálogo de formulario. [alGuardar] recibe los valores y un callback: null = guardado,
 * texto = mensaje de error que se muestra dentro del diálogo.
 */
@Composable
fun FormularioDialog(
    titulo: String,
    campos: List<Campo>,
    inicial: Map<String, String>,
    textoGuardar: String = "Guardar",
    alCerrar: () -> Unit,
    alGuardar: (Map<String, String>, (String?) -> Unit) -> Unit,
) {
    val valores = remember { mutableStateMapOf<String, String>().apply { putAll(inicial) } }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(titulo) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                campos.forEach { c ->
                    if (c.tipo == TipoCampo.OPCION) {
                        Desplegable(
                            etiqueta = c.etiqueta, opciones = c.opciones, seleccion = valores[c.clave],
                            alElegir = { valores[c.clave] = it ?: "" }
                        )
                    } else {
                        OutlinedTextField(
                            value = valores[c.clave].orEmpty(),
                            onValueChange = { valores[c.clave] = it },
                            label = { Text(c.etiqueta) },
                            singleLine = true,
                            visualTransformation = if (c.tipo == TipoCampo.CLAVE) PasswordVisualTransformation() else VisualTransformation.None,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = when (c.tipo) {
                                    TipoCampo.NUMERO -> KeyboardType.Number
                                    TipoCampo.DECIMAL -> KeyboardType.Decimal
                                    TipoCampo.CLAVE -> KeyboardType.Password
                                    else -> KeyboardType.Text
                                },
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    alGuardar(valores.toMap()) { e ->
                        if (e == null) alCerrar() else error = e
                    }
                }
            ) {
                Text(textoGuardar)
            }
        },
        dismissButton = {
            TextButton(onClick = alCerrar) {
                Text("Cancelar")
            }
        },
    )
}

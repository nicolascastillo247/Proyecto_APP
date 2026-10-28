package com.example.sqlite.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.sqlite.ui.AuthViewModel

@Composable
fun LoginScreen(vm: AuthViewModel) {
    val error by vm.error.collectAsState()
    var usuario by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("TeqRuta", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text("Traslados en terreno · TEQMED SpA", style = MaterialTheme.typography.bodyLarge)

        OutlinedTextField(
            value = usuario, onValueChange = { usuario = it; vm.limpiarError() },
            label = { Text("Usuario") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = clave, onValueChange = { clave = it; vm.limpiarError() },
            label = { Text("Contraseña") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(onClick = { vm.ingresar(usuario, clave) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Ingresar", style = MaterialTheme.typography.titleMedium)
        }

        Text(
            "Demo (clave 1234): tecnico1, tecnico2, jefe, gerencia, admin",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

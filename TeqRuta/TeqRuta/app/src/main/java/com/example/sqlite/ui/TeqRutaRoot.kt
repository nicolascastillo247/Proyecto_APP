package com.example.sqlite.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.sqlite.data.Rol
import com.example.sqlite.ui.screen.AdminScreen
import com.example.sqlite.ui.screen.GerenciaScreen
import com.example.sqlite.ui.screen.JefeScreen
import com.example.sqlite.ui.screen.LoginScreen
import com.example.sqlite.ui.screen.TecnicoScreen

/** Cada perfil ve solo su propia pantalla (RF-02). */
@Composable
fun TeqRutaRoot(
    auth: AuthViewModel,
    tecnico: TecnicoViewModel,
    jefe: JefeViewModel,
    gerencia: GerenciaViewModel,
    admin: AdminViewModel
) {
    val iniciando by auth.iniciando.collectAsState()
    val usuario by auth.usuario.collectAsState()
    val u = usuario

    when {
        iniciando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        u == null -> LoginScreen(auth)
        else -> {
            val salir = { auth.salir() }
            when (u.rol) {
                Rol.TECNICO -> TecnicoScreen(tecnico, u, salir)
                Rol.JEFE -> JefeScreen(jefe, u, salir)
                Rol.GERENCIA -> GerenciaScreen(gerencia, u, salir)
                Rol.ADMIN -> AdminScreen(admin, u, salir)
                else -> LoginScreen(auth)
            }
        }
    }
}

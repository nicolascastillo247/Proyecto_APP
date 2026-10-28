package com.example.sqlite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.sqlite.ui.AdminViewModel
import com.example.sqlite.ui.AuthViewModel
import com.example.sqlite.ui.GerenciaViewModel
import com.example.sqlite.ui.JefeViewModel
import com.example.sqlite.ui.TecnicoViewModel
import com.example.sqlite.ui.TeqRutaRoot
import com.example.sqlite.ui.ViewModelFactory
import com.example.sqlite.ui.theme.SqliteTheme

class MainActivity : ComponentActivity() {
    private val factory by lazy { ViewModelFactory((application as TeqRutaApp).container) }

    private val authVm: AuthViewModel by viewModels { factory }
    private val tecnicoVm: TecnicoViewModel by viewModels { factory }
    private val jefeVm: JefeViewModel by viewModels { factory }
    private val gerenciaVm: GerenciaViewModel by viewModels { factory }
    private val adminVm: AdminViewModel by viewModels { factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SqliteTheme {
                TeqRutaRoot(authVm, tecnicoVm, jefeVm, gerenciaVm, adminVm)
            }
        }
    }
}

package com.example.sqlite.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.sqlite.AppContainer

class ViewModelFactory(private val c: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(AuthViewModel::class.java) -> AuthViewModel(c.auth)
        modelClass.isAssignableFrom(TecnicoViewModel::class.java) -> TecnicoViewModel(c.auth, c.traslados, c.admin, c.sync)
        modelClass.isAssignableFrom(JefeViewModel::class.java) -> JefeViewModel(c.auth, c.traslados, c.admin)
        modelClass.isAssignableFrom(GerenciaViewModel::class.java) -> GerenciaViewModel(c.traslados, c.admin)
        modelClass.isAssignableFrom(AdminViewModel::class.java) -> AdminViewModel(c.auth, c.admin)
        else -> throw IllegalArgumentException("ViewModel desconocido: ${modelClass.name}")
    } as T
}

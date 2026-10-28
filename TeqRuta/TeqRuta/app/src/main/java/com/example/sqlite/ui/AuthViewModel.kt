package com.example.sqlite.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sqlite.data.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val auth: AuthRepository) : ViewModel() {

    val usuario = auth.usuario
    val iniciando = auth.iniciando

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun ingresar(username: String, password: String) {
        viewModelScope.launch {
            _error.value = if (username.isBlank() || password.isEmpty()) {
                "Ingresa usuario y contraseña"
            } else {
                auth.ingresar(username, password)
            }
        }
    }

    fun salir() {
        viewModelScope.launch { auth.salir() }
    }

    fun limpiarError() {
        _error.value = null
    }
}

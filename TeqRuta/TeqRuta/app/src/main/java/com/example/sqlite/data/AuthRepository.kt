package com.example.sqlite.data

import com.example.sqlite.util.Fmt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Autenticación local y perfiles (RF-01). */
class AuthRepository(private val db: AppDatabase, private val session: SessionStore) {

    private val _usuario = MutableStateFlow<Usuario?>(null)
    val usuario: StateFlow<Usuario?> = _usuario.asStateFlow()

    private val _iniciando = MutableStateFlow(true)
    /** true mientras se restaura la sesión guardada. */
    val iniciando: StateFlow<Boolean> = _iniciando.asStateFlow()

    suspend fun restaurar() {
        val id = session.usuarioVigente()
        val u = id?.let { db.usuarioDao().porId(it) }
        _usuario.value = if (u != null && u.activo) u else null
        if (_usuario.value == null) session.cerrar()
        _iniciando.value = false
    }

    /** @return mensaje de error o null si el ingreso fue exitoso. */
    suspend fun ingresar(username: String, password: String): String? {
        val u = db.usuarioDao().porUsername(username.trim().lowercase())
        if (u == null || u.passwordHash != Fmt.sha256(password)) return "Usuario o contraseña incorrectos"
        if (!u.activo) return "El usuario está desactivado"
        session.guardar(u.id)
        _usuario.value = u
        db.registrar(u.id, "LOGIN", "usuario=${u.username}")
        return null
    }

    suspend fun salir() {
        val u = _usuario.value
        session.cerrar()
        _usuario.value = null
        if (u != null) db.registrar(u.id, "LOGOUT", "usuario=${u.username}")
    }

    // --- Consentimiento de ubicación (RNF-10) ---
    fun consentimientoRespondido(userId: Long) = session.consentimientoRespondido(userId)
    fun ubicacionConsentida(userId: Long) = session.ubicacionConsentida(userId)
    fun guardarConsentimiento(userId: Long, acepta: Boolean) = session.guardarConsentimiento(userId, acepta)
}

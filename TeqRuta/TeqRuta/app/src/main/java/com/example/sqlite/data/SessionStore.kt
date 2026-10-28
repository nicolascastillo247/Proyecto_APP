package com.example.sqlite.data

import android.content.Context

/** Sesión persistente (sobrevive a cierres de la app) con expiración (RNF-09). */
class SessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("teqruta_sesion", Context.MODE_PRIVATE)

    fun guardar(userId: Long) {
        prefs.edit().putLong(KEY_USER, userId).putLong(KEY_TS, System.currentTimeMillis()).apply()
    }

    /** Devuelve el usuario de la sesión si no ha expirado. */
    fun usuarioVigente(): Long? {
        val id = prefs.getLong(KEY_USER, -1L)
        val ts = prefs.getLong(KEY_TS, 0L)
        if (id <= 0 || System.currentTimeMillis() - ts > DURACION_MS) return null
        return id
    }

    fun cerrar() {
        prefs.edit().remove(KEY_USER).remove(KEY_TS).apply()
    }

    // Consentimiento para capturar la ubicación (RNF-10), por usuario.
    fun ubicacionConsentida(userId: Long): Boolean = prefs.getBoolean("consent_$userId", false)

    fun consentimientoRespondido(userId: Long): Boolean = prefs.contains("consent_$userId")

    fun guardarConsentimiento(userId: Long, acepta: Boolean) {
        prefs.edit().putBoolean("consent_$userId", acepta).apply()
    }

    private companion object {
        const val KEY_USER = "user_id"
        const val KEY_TS = "login_ts"
        const val DURACION_MS = 12L * 60 * 60 * 1000 // 12 horas
    }
}

package com.example.sqlite.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Sincroniza automáticamente al recuperar señal y reintenta cada 5 minutos (RF-25, RNF-14).
 * No bloquea la interfaz: todo corre en el scope de la aplicación (RNF-12).
 */
class SyncManager(
    context: Context,
    private val repo: TrasladoRepository,
    private val scope: CoroutineScope
) {
    private val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun hayConexion(): Boolean {
        val red = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(red) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun iniciar() {
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    sincronizarEnSegundoPlano()
                }
            })
        }
        scope.launch {
            while (isActive) {
                delay(5 * 60_000L)
                sincronizarEnSegundoPlano()
            }
        }
        sincronizarEnSegundoPlano()
    }

    fun sincronizarEnSegundoPlano() {
        scope.launch { if (hayConexion()) repo.sincronizarPendientes() }
    }

    /** Sincronización pedida por el técnico. Devuelve un mensaje para mostrar. */
    suspend fun sincronizarAhora(): String {
        if (!hayConexion()) return "Sin conexión: tus traslados siguen guardados en el dispositivo"
        val n = repo.sincronizarPendientes()
        return if (n == 0) "No había traslados por sincronizar (o quedaron con error, revisa el historial)" else "Sincronizados: $n"
    }
}

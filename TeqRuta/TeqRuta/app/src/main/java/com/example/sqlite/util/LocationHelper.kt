package com.example.sqlite.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Captura puntual de la ubicación (sin servicios en segundo plano, RNF-11):
 * solo se llama al iniciar y al finalizar un traslado. Usa LocationManager para no
 * depender de Google Play Services. Si no hay permiso o señal, entrega null.
 */
object LocationHelper {
    private const val ESPERA_MS = 10_000L
    private const val EDAD_MAXIMA_MS = 2 * 60_000L

    fun tienePermiso(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun capturar(ctx: Context, alTerminar: (Pair<Double, Double>?) -> Unit) {
        if (!tienePermiso(ctx)) {
            alTerminar(null)
            return
        }
        val lm = ctx.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val proveedores = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())

        fun ultimaConocida(): Location? =
            proveedores.mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
                .maxByOrNull { it.time }

        val ultima = ultimaConocida()
        if (ultima != null && System.currentTimeMillis() - ultima.time < EDAD_MAXIMA_MS) {
            alTerminar(ultima.latitude to ultima.longitude)
            return
        }

        val proveedor = when {
            LocationManager.GPS_PROVIDER in proveedores -> LocationManager.GPS_PROVIDER
            LocationManager.NETWORK_PROVIDER in proveedores -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (proveedor == null) {
            alTerminar(ultima?.let { it.latitude to it.longitude })
            return
        }

        val handler = Handler(Looper.getMainLooper())
        var entregado = false
        lateinit var listener: LocationListener
        val entregar: (Location?) -> Unit = { loc ->
            if (!entregado) {
                entregado = true
                handler.removeCallbacksAndMessages(null)
                runCatching { lm.removeUpdates(listener) }
                alTerminar(loc?.let { it.latitude to it.longitude })
            }
        }
        listener = object : LocationListener {
            override fun onLocationChanged(location: Location) = entregar(location)
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            @Deprecated("Requerido en API < 30")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        try {
            lm.requestLocationUpdates(proveedor, 0L, 0f, listener, Looper.getMainLooper())
            handler.postDelayed({ entregar(ultima) }, ESPERA_MS)
        } catch (e: Exception) {
            entregar(ultima)
        }
    }
}

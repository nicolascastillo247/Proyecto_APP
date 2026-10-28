package com.example.sqlite.util

import java.security.MessageDigest
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Utilidades de formato y fechas (zona horaria del dispositivo). */
object Fmt {
    private val localeCL: Locale = Locale.forLanguageTag("es-CL")

    private fun df(patron: String) = SimpleDateFormat(patron, Locale.US)

    fun hoy(): String = df("yyyy-MM-dd").format(Date())

    fun primerDiaMes(): String = df("yyyy-MM-01").format(Date())

    fun fechaHora(ts: Long?): String = if (ts == null) "—" else df("dd-MM-yyyy HH:mm").format(Date(ts))

    fun fecha(ts: Long?): String = if (ts == null) "—" else df("yyyy-MM-dd").format(Date(ts))

    fun dinero(v: Double?): String =
        if (v == null) "—" else "$" + NumberFormat.getIntegerInstance(localeCL).format(Math.round(v))

    fun km(v: Double?): String = if (v == null) "—" else String.format(Locale.US, "%.1f km", v)

    fun pct(v: Double?): String = if (v == null) "—" else String.format(Locale.US, "%.1f%%", v)

    /** Inicio del día (00:00) para un texto yyyy-MM-dd, o null si el formato es inválido. */
    fun inicioDia(fecha: String): Long? = try {
        df("yyyy-MM-dd").apply { isLenient = false }.parse(fecha.trim())?.time
    } catch (e: Exception) {
        null
    }

    /** Último milisegundo del día para un texto yyyy-MM-dd. */
    fun finDia(fecha: String): Long? = inicioDia(fecha)?.plus(86_400_000L - 1)

    fun sha256(texto: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(texto.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

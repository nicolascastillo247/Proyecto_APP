package com.example.sqlite.data

import com.example.sqlite.util.Fmt

/**
 * Datos iniciales de demostración para poder probar la app sin servidor.
 * Contraseña de todos los usuarios demo: 1234
 * (Las coordenadas son aproximadas y los nombres de clientes/contratos son de ejemplo.)
 */
object DemoData {

    suspend fun cargar(db: AppDatabase) {
        if (db.usuarioDao().contar() == 0) sembrarBase(db)
        asegurarVisitasDeHoy(db)
    }

    private suspend fun sembrarBase(db: AppDatabase) {
        val clave = Fmt.sha256("1234")
        val u = db.usuarioDao()
        u.insertar(Usuario(nombre = "Carlos Técnico", username = "tecnico1", passwordHash = clave, rol = Rol.TECNICO))
        u.insertar(Usuario(nombre = "Marta Técnica", username = "tecnico2", passwordHash = clave, rol = Rol.TECNICO))
        u.insertar(Usuario(nombre = "Jorge Jefe de Servicio", username = "jefe", passwordHash = clave, rol = Rol.JEFE))
        u.insertar(Usuario(nombre = "Gabriela Gerencia", username = "gerencia", passwordHash = clave, rol = Rol.GERENCIA))
        u.insertar(Usuario(nombre = "Admin del Sistema", username = "admin", passwordHash = clave, rol = Rol.ADMIN))

        val v = db.vehiculoDao()
        v.insertar(Vehiculo(patente = "AB1234", descripcion = "Camioneta empresa", tipo = TipoVehiculo.EMPRESA, consumoKmPorLitro = 9.5))
        v.insertar(Vehiculo(patente = "CD5678", descripcion = "Auto particular", tipo = TipoVehiculo.PARTICULAR, consumoKmPorLitro = 12.0))

        val c = db.centroDao()
        val base = c.insertar(Centro(nombre = "Base TEQMED Concepción", cliente = "TEQMED", contrato = "Interno", direccion = "Concepción", latitud = -36.827, longitud = -73.050, esBase = true))
        val regional = c.insertar(Centro(nombre = "Hospital Regional Concepción", cliente = "Servicio de Salud (demo)", contrato = "C-001", latitud = -36.836, longitud = -73.048))
        val sanatorio = c.insertar(Centro(nombre = "Clínica Sanatorio (demo)", cliente = "Clínica Privada (demo)", contrato = "C-002", latitud = -36.834, longitud = -73.033))
        val talcahuano = c.insertar(Centro(nombre = "Hospital Talcahuano", cliente = "Servicio de Salud (demo)", contrato = "C-001", latitud = -36.739, longitud = -73.113))
        val coronel = c.insertar(Centro(nombre = "Hospital Coronel", cliente = "Servicio de Salud (demo)", contrato = "C-003", latitud = -37.017, longitud = -73.143))

        val t = db.tramoDao()
        t.insertar(Tramo(origenId = base, destinoId = regional, distanciaKm = 6.0, peajes = 0))
        t.insertar(Tramo(origenId = base, destinoId = sanatorio, distanciaKm = 5.0, peajes = 0))
        t.insertar(Tramo(origenId = regional, destinoId = sanatorio, distanciaKm = 3.0, peajes = 0))
        t.insertar(Tramo(origenId = base, destinoId = talcahuano, distanciaKm = 18.0, peajes = 1500))
        t.insertar(Tramo(origenId = regional, destinoId = talcahuano, distanciaKm = 17.0, peajes = 1500))
        t.insertar(Tramo(origenId = base, destinoId = coronel, distanciaKm = 32.0, peajes = 2400))

        val desde = Fmt.inicioDia("2026-01-01") ?: 0L
        db.tarifaDao().insertar(Tarifa(tipo = TipoTarifa.COMBUSTIBLE, valor = 1250.0, vigenteDesde = desde))
        db.tarifaDao().insertar(Tarifa(tipo = TipoTarifa.KM_PARTICULAR, valor = 220.0, vigenteDesde = desde))
        db.parametroDao().guardar(Parametro(Param.UMBRAL_DESVIACION, "10"))
        db.registrar(null, "DATOS_DEMO", "Carga inicial de datos de demostración")
    }

    /** Si hoy no hay visitas asignadas, crea algunas de ejemplo para los técnicos demo. */
    private suspend fun asegurarVisitasDeHoy(db: AppDatabase) {
        val hoy = Fmt.hoy()
        if (db.visitaDao().contarDelDia(hoy) > 0) return
        val t1 = db.usuarioDao().porUsername("tecnico1")
        val t2 = db.usuarioDao().porUsername("tecnico2")
        // Centros por nombre no es estable; se toman por id creciente (1 = base).
        val v = db.visitaDao()
        if (t1 != null) {
            v.insertar(Visita(tecnicoId = t1.id, centroId = 2, fecha = hoy, descripcion = "Mantención preventiva de equipo"))
            v.insertar(Visita(tecnicoId = t1.id, centroId = 3, fecha = hoy, descripcion = "Llamado correctivo"))
        }
        if (t2 != null) v.insertar(Visita(tecnicoId = t2.id, centroId = 4, fecha = hoy, descripcion = "Calibración de equipo"))
    }
}

package com.example.sqlite.data

import com.example.sqlite.util.Fmt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Catálogos indexados por id para mostrar nombres en las pantallas. */
data class Catalogo(
    val usuarios: Map<Long, Usuario> = emptyMap(),
    val vehiculos: Map<Long, Vehiculo> = emptyMap(),
    val centros: Map<Long, Centro> = emptyMap()
) {
    fun usuario(id: Long) = usuarios[id]?.nombre ?: "Usuario #$id"
    fun vehiculo(id: Long) = vehiculos[id]?.let { "${it.patente} · ${it.descripcion}" } ?: "Vehículo #$id"
    fun centro(id: Long) = centros[id]?.nombre ?: "Centro #$id"
    fun cliente(centroId: Long) = centros[centroId]?.cliente ?: "—"
    fun contrato(centroId: Long) = centros[centroId]?.contrato ?: "—"
}

/** Mantenedores del administrador (RF-16 a RF-19) y asignación de visitas. */
class AdminRepository(private val db: AppDatabase) {

    val usuarios = db.usuarioDao().todos()
    val vehiculos = db.vehiculoDao().todos()
    val centros = db.centroDao().todos()
    val tramos = db.tramoDao().todos()
    val tarifas = db.tarifaDao().todas()
    val visitas = db.visitaDao().todas()
    val bitacora = db.bitacoraDao().recientes()
    val umbral: Flow<String?> = db.parametroDao().observar(Param.UMBRAL_DESVIACION)

    val catalogo: Flow<Catalogo> = combine(usuarios, vehiculos, centros) { u, v, c ->
        Catalogo(u.associateBy { it.id }, v.associateBy { it.id }, c.associateBy { it.id })
    }

    // ---------- Usuarios / técnicos ----------

    suspend fun guardarUsuario(
        actor: Long?, id: Long?, nombre: String, username: String, password: String, rol: String
    ) {
        require(nombre.isNotBlank()) { "El nombre es obligatorio" }
        val user = username.trim().lowercase()
        require(user.isNotBlank()) { "El usuario es obligatorio" }
        require(Rol.todos.any { it.first == rol }) { "Selecciona un perfil" }
        val repetido = db.usuarioDao().porUsername(user)
        require(repetido == null || repetido.id == id) { "Ya existe un usuario con ese nombre de acceso" }

        if (id == null) {
            require(password.length >= 4) { "La contraseña debe tener al menos 4 caracteres" }
            db.usuarioDao().insertar(
                Usuario(nombre = nombre.trim(), username = user, passwordHash = Fmt.sha256(password), rol = rol)
            )
            db.registrar(actor, "USUARIO_CREADO", "usuario=$user; rol=$rol")
        } else {
            val actual = db.usuarioDao().porId(id) ?: error("El usuario no existe")
            require(password.isEmpty() || password.length >= 4) { "La contraseña debe tener al menos 4 caracteres" }
            db.usuarioDao().actualizar(
                actual.copy(
                    nombre = nombre.trim(), username = user, rol = rol,
                    passwordHash = if (password.isEmpty()) actual.passwordHash else Fmt.sha256(password)
                )
            )
            db.registrar(actor, "USUARIO_EDITADO", "usuario=$user; rol=$rol")
        }
    }

    suspend fun cambiarActivoUsuario(actor: Long?, u: Usuario) {
        require(u.id != actor) { "No puedes desactivar tu propio usuario" }
        db.usuarioDao().actualizar(u.copy(activo = !u.activo))
        db.registrar(actor, if (u.activo) "USUARIO_DESACTIVADO" else "USUARIO_ACTIVADO", "usuario=${u.username}")
    }

    // ---------- Vehículos ----------

    suspend fun guardarVehiculo(
        actor: Long?, id: Long?, patente: String, descripcion: String, tipo: String, consumo: Double
    ) {
        val pat = patente.trim().uppercase()
        require(pat.isNotBlank()) { "La patente es obligatoria" }
        require(tipo == TipoVehiculo.PARTICULAR || tipo == TipoVehiculo.EMPRESA) { "Selecciona el tipo de vehículo" }
        require(consumo > 0) { "El consumo (km por litro) debe ser mayor que 0" }
        val repetido = db.vehiculoDao().porPatente(pat)
        require(repetido == null || repetido.id == id) { "Ya existe un vehículo con esa patente" }

        if (id == null) {
            db.vehiculoDao().insertar(
                Vehiculo(patente = pat, descripcion = descripcion.trim(), tipo = tipo, consumoKmPorLitro = consumo)
            )
            db.registrar(actor, "VEHICULO_CREADO", "patente=$pat; tipo=$tipo")
        } else {
            val actual = db.vehiculoDao().porId(id) ?: error("El vehículo no existe")
            db.vehiculoDao().actualizar(
                actual.copy(patente = pat, descripcion = descripcion.trim(), tipo = tipo, consumoKmPorLitro = consumo)
            )
            db.registrar(actor, "VEHICULO_EDITADO", "patente=$pat; tipo=$tipo")
        }
    }

    suspend fun cambiarActivoVehiculo(actor: Long?, v: Vehiculo) {
        db.vehiculoDao().actualizar(v.copy(activo = !v.activo))
        db.registrar(actor, if (v.activo) "VEHICULO_DESACTIVADO" else "VEHICULO_ACTIVADO", "patente=${v.patente}")
    }

    // ---------- Centros (clínicas / hospitales) ----------

    suspend fun guardarCentro(
        actor: Long?, id: Long?, nombre: String, cliente: String, contrato: String,
        direccion: String, latitud: Double?, longitud: Double?, esBase: Boolean
    ) {
        require(nombre.isNotBlank()) { "El nombre es obligatorio" }
        require(cliente.isNotBlank()) { "El cliente es obligatorio" }
        require(contrato.isNotBlank()) { "El contrato es obligatorio" }
        require(latitud == null || latitud in -90.0..90.0) { "Latitud inválida" }
        require(longitud == null || longitud in -180.0..180.0) { "Longitud inválida" }

        if (id == null) {
            db.centroDao().insertar(
                Centro(
                    nombre = nombre.trim(), cliente = cliente.trim(), contrato = contrato.trim(),
                    direccion = direccion.trim(), latitud = latitud, longitud = longitud, esBase = esBase
                )
            )
            db.registrar(actor, "CENTRO_CREADO", "nombre=${nombre.trim()}")
        } else {
            val actual = db.centroDao().porId(id) ?: error("El centro no existe")
            db.centroDao().actualizar(
                actual.copy(
                    nombre = nombre.trim(), cliente = cliente.trim(), contrato = contrato.trim(),
                    direccion = direccion.trim(), latitud = latitud, longitud = longitud, esBase = esBase
                )
            )
            db.registrar(actor, "CENTRO_EDITADO", "nombre=${nombre.trim()}")
        }
    }

    suspend fun cambiarActivoCentro(actor: Long?, c: Centro) {
        db.centroDao().actualizar(c.copy(activo = !c.activo))
        db.registrar(actor, if (c.activo) "CENTRO_DESACTIVADO" else "CENTRO_ACTIVADO", "nombre=${c.nombre}")
    }

    // ---------- Distancias oficiales y peajes por tramo ----------

    suspend fun guardarTramo(actor: Long?, id: Long?, origenId: Long, destinoId: Long, distanciaKm: Double, peajes: Int) {
        require(origenId != destinoId) { "El origen y el destino deben ser distintos" }
        require(distanciaKm > 0) { "La distancia debe ser mayor que 0" }
        require(peajes >= 0) { "Los peajes no pueden ser negativos" }
        val repetido = db.tramoDao().entre(origenId, destinoId)
        require(repetido == null || repetido.id == id) { "Ya existe un tramo entre esos dos centros" }

        if (id == null) {
            db.tramoDao().insertar(Tramo(origenId = origenId, destinoId = destinoId, distanciaKm = distanciaKm, peajes = peajes))
            db.registrar(actor, "TRAMO_CREADO", "origen=$origenId; destino=$destinoId; km=$distanciaKm; peajes=$peajes")
        } else {
            db.tramoDao().actualizar(Tramo(id, origenId, destinoId, distanciaKm, peajes))
            db.registrar(actor, "TRAMO_EDITADO", "origen=$origenId; destino=$destinoId; km=$distanciaKm; peajes=$peajes")
        }
    }

    // ---------- Tarifas con historial de vigencia ----------

    /** Crea una tarifa nueva y cierra la vigente; nunca borra valores antiguos (RF-19). */
    suspend fun nuevaTarifa(actor: Long?, tipo: String, valor: Double, desdeTexto: String) {
        require(TipoTarifa.todos.any { it.first == tipo }) { "Selecciona el tipo de tarifa" }
        require(valor > 0) { "El valor debe ser mayor que 0" }
        val desde = Fmt.inicioDia(desdeTexto) ?: throw IllegalArgumentException("Fecha inválida (usa AAAA-MM-DD)")
        val abierta = db.tarifaDao().abierta(tipo)
        if (abierta != null) {
            require(desde > abierta.vigenteDesde) { "La vigencia debe ser posterior a la tarifa actual (${Fmt.fecha(abierta.vigenteDesde)})" }
            db.tarifaDao().actualizar(abierta.copy(vigenteHasta = desde - 1))
        }
        db.tarifaDao().insertar(Tarifa(tipo = tipo, valor = valor, vigenteDesde = desde))
        db.registrar(actor, "TARIFA_CAMBIADA", "tipo=$tipo; valor=$valor; desde=${Fmt.fecha(desde)}; anterior=${abierta?.valor}")
    }

    // ---------- Visitas asignadas ----------

    suspend fun guardarVisita(actor: Long?, tecnicoId: Long, centroId: Long, fecha: String, descripcion: String) {
        val tecnico = db.usuarioDao().porId(tecnicoId)
        require(tecnico != null && tecnico.rol == Rol.TECNICO) { "Selecciona un técnico" }
        require(db.centroDao().porId(centroId) != null) { "Selecciona la clínica u hospital" }
        require(Fmt.inicioDia(fecha) != null) { "Fecha inválida (usa AAAA-MM-DD)" }
        db.visitaDao().insertar(Visita(tecnicoId = tecnicoId, centroId = centroId, fecha = fecha.trim(), descripcion = descripcion.trim()))
        db.registrar(actor, "VISITA_ASIGNADA", "tecnico=${tecnico.username}; centro=$centroId; fecha=${fecha.trim()}")
    }

    // ---------- Parámetros ----------

    suspend fun guardarUmbral(actor: Long?, valor: Double) {
        require(valor > 0 && valor <= 100) { "El umbral debe estar entre 0 y 100" }
        db.parametroDao().guardar(Parametro(Param.UMBRAL_DESVIACION, valor.toString()))
        db.registrar(actor, "PARAMETRO_CAMBIADO", "${Param.UMBRAL_DESVIACION}=$valor")
    }
}

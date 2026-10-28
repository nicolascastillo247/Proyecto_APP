TEQRUTA – CÓMO FUNCIONA EL CÓDIGO Y EL FLUJO

1. IDEA GENERAL
TeqRuta es una app Android (Kotlin + Jetpack Compose) para que los técnicos de TEQMED registren traslados en terreno sin conexión. El costo de cada traslado lo calcula un "servidor" central (simulado) para que sea uniforme y auditable. Hay cuatro roles: técnico, jefe de servicio técnico, gerencia/finanzas y administrador.

2. ARQUITECTURA (MVVM, local-first)
- UI (Compose): pantallas que solo muestran estado y envían eventos.
- ViewModel: guarda el estado en StateFlow, combina flujos y llama a los repositorios. No hay lógica de pantalla en la UI.
- Repository: reglas de negocio y validaciones (AuthRepository, AdminRepository, TrasladoRepository).
- Room (SQLite): fuente de verdad local. Todo se escribe primero acá, por eso funciona offline.
- AppContainer (TeqRutaApp.kt): inyección de dependencias manual. Crea la base de datos, la sesión, los repositorios y el SyncManager una sola vez. ViewModelFactory entrega esas dependencias a los 5 ViewModels (Auth, Técnico, Jefe, Gerencia, Admin).

3. DATOS (data/)
- Entities.kt: tablas Usuario, Vehiculo, Centro, Tramo, Tarifa, Visita, Traslado, Parada, Bitacora y Parametro.
- Daos.kt: consultas. Destacan TramoDao.entre(a,b) (busca el tramo en cualquier sentido), TarifaDao.vigenteEn(tipo, fecha), TrasladoDao.pendientesSync y BitacoraDao, que solo inserta y lee.
- AppDatabase: base "teqruta.db" y la función registrar() que escribe en la bitácora.
- SessionStore: guarda la sesión en SharedPreferences con vencimiento de 12 horas y el consentimiento de ubicación por usuario.
- DemoData: carga usuarios de prueba (clave 1234), vehículos, centros, tramos, tarifas y umbral de desviación.

4. FLUJO COMPLETO
a) Inicio: TeqRutaApp crea el AppContainer, carga datos demo, restaura la sesión e inicia el SyncManager. TeqRutaRoot muestra un spinner mientras se restaura la sesión, luego el Login o la pantalla según el rol.
b) Login: AuthRepository compara el hash SHA-256 de la clave, verifica que el usuario esté activo, guarda la sesión y registra LOGIN en la bitácora.
c) Técnico (3 pestañas):
   - Hoy: visitas programadas del día.
   - Traslado: iniciar (vehículo, origen, destino, km inicial); si no hay consentimiento de ubicación se muestra un diálogo y luego se captura el GPS solo al inicio y al final. Se pueden agregar paradas (el km no puede bajar). Al finalizar se ingresa km final y peajes.
   - Historial: sus traslados con estado y costo.
   Un técnico no puede tener dos traslados en curso a la vez.
   Al finalizar, el traslado queda en estado PENDIENTE y syncEstado PENDIENTE, y la visita se marca como atendida.
d) Sincronización (SyncManager): se dispara al finalizar, cuando vuelve la conexión (callback de red) y cada 5 minutos. Usa un Mutex para no correr dos veces. Por cada traslado pendiente llama al ServidorSimulado. Si resulta, guarda el resultado y marca SINCRONIZADO; si falla, marca ERROR con el mensaje, lo registra en la bitácora y reintenta después. El uuid evita duplicados.
e) ServidorSimulado (cálculo central):
   - km declarado = km final − km inicial.
   - km oficial = suma de los tramos de la ruta origen → paradas → destino (si falta algún tramo, queda sin dato).
   - desviación % = diferencia entre declarado y oficial; se marca "desviado" si supera el umbral configurable (10 % por defecto).
   - costo: vehículo de empresa = km / rendimiento × precio del combustible vigente; vehículo particular = km × tarifa por km vigente; más peajes. Se usa el km oficial si existe, si no el declarado.
   - Deja registro CALCULO_COSTO en la bitácora.
f) Jefe de servicio técnico: ve todos los traslados cerrados y sincronizados, filtra por técnico, fecha, estado o solo desviados, abre el detalle y decide Validar, Observar o Rechazar. El comentario es obligatorio al observar o rechazar. Solo se puede revisar un traslado ya sincronizado (con costo calculado).
g) Gerencia/Finanzas: elige un período y ve el costo total solo de traslados VALIDADOS, agrupado por cliente, contrato, técnico y vehículo. Puede exportar un CSV (separador ;) y compartirlo con un Intent.
h) Administrador: mantenedores de usuarios, vehículos, centros, tramos, tarifas, visitas y parámetros, y consulta de la bitácora. Una nueva tarifa cierra la anterior (vigenteHasta = día anterior), por lo que el historial de costos se conserva. También define el umbral de desviación (0–100).

5. ESTADOS DE UN TRASLADO
EN_CURSO → PENDIENTE (al finalizar) → VALIDADO / OBSERVADO / RECHAZADO (decisión del jefe).
Aparte, syncEstado: PENDIENTE → SINCRONIZADO (o ERROR).

6. REQUISITOS QUE CUBRE
- Offline: Room + sincronización diferida.
- Cálculo centralizado y auditable: ServidorSimulado + bitácora inmutable.
- Roles: pantallas separadas por rol.
- Ubicación con consentimiento: diálogo y captura solo al inicio y fin.
- Tarifas con vigencia: se resuelven según la fecha de inicio del traslado.

7. LIMITACIONES DEL MVP (decirlas honestamente)
- El servidor es simulado (misma base local), no una API real.
- El precio del combustible es un valor de ejemplo, no se consulta una fuente pública.
- La sincronización corre mientras la app está viva (no usa WorkManager).
- El hash de clave no lleva sal.
- Sin integración con el ERP, según el caso.

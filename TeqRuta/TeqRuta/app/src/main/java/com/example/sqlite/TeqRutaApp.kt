package com.example.sqlite

import android.app.Application
import com.example.sqlite.data.AdminRepository
import com.example.sqlite.data.AppDatabase
import com.example.sqlite.data.AuthRepository
import com.example.sqlite.data.DemoData
import com.example.sqlite.data.SessionStore
import com.example.sqlite.data.SyncManager
import com.example.sqlite.data.TrasladoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Contenedor de dependencias (inyección manual, sin librerías extra). */
class AppContainer(app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val db: AppDatabase = AppDatabase.get(app)
    val session = SessionStore(app)
    val auth = AuthRepository(db, session)
    val admin = AdminRepository(db)
    val traslados = TrasladoRepository(db)
    val sync = SyncManager(app, traslados, scope)

    init {
        scope.launch {
            DemoData.cargar(db)
            auth.restaurar()
            sync.iniciar()
        }
    }
}

class TeqRutaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

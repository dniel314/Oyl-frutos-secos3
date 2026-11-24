package cl.oyl.frutossecos

import android.app.Application
import cl.oyl.frutossecos.core.di.ApplicationScope
import cl.oyl.frutossecos.data.local.seed.DatabaseSeeder
import cl.oyl.frutossecos.data.remote.RemoteResult
import cl.oyl.frutossecos.data.repository.AuthRepository
import cl.oyl.frutossecos.data.repository.remote.RemoteNotificationRepository
import cl.oyl.frutossecos.data.repository.remote.RemoteOrderRepository
import cl.oyl.frutossecos.data.repository.remote.RemoteProductRepository
import cl.oyl.frutossecos.data.repository.remote.RemoteStoreRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration

@HiltAndroidApp
class OylApplication : Application() {
    @Inject lateinit var seeder: DatabaseSeeder
    @Inject lateinit var auth: AuthRepository
    @Inject lateinit var products: RemoteProductRepository
    @Inject lateinit var stores: RemoteStoreRepository
    @Inject lateinit var orders: RemoteOrderRepository
    @Inject lateinit var notifications: RemoteNotificationRepository
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // osmdroid exige un User-Agent propio para usar los tiles de OpenStreetMap.
        Configuration.getInstance().userAgentValue = packageName
        appScope.launch {
            // 1) Caché local mínima para que la app abra al instante aunque no haya red.
            seeder.seedIfNeeded()
            // 2) Si hay backend configurado, se sincroniza al iniciar y la caché se
            //    reemplaza/actualiza con los datos oficiales de PostgreSQL.
            if (BuildConfig.USE_REMOTE_REPOSITORIES) syncWithBackend()
        }
    }

    /**
     * Sincronización inicial best-effort: cualquier fallo de red deja intactos los
     * datos locales, por lo que la app sigue siendo utilizable sin conexión.
     */
    private suspend fun syncWithBackend() {
        val catalogue = products.refresh()
        if (catalogue is RemoteResult.Failure) return
        stores.refresh()
        val session = auth.currentSession() ?: return
        orders.refresh(session.role)
        notifications.refresh(session.userId)
    }
}

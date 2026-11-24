package cl.oyl.frutossecos.core.di

import cl.oyl.frutossecos.BuildConfig
import cl.oyl.frutossecos.core.notifications.AndroidSystemNotifier
import cl.oyl.frutossecos.core.notifications.SystemNotifier
import cl.oyl.frutossecos.data.repository.AuthRepository
import cl.oyl.frutossecos.data.repository.CartRepository
import cl.oyl.frutossecos.data.repository.InventoryRepository
import cl.oyl.frutossecos.data.repository.NotificationRepository
import cl.oyl.frutossecos.data.repository.OrderRepository
import cl.oyl.frutossecos.data.repository.ProductRepository
import cl.oyl.frutossecos.data.repository.ProfileRepository
import cl.oyl.frutossecos.data.repository.StoreRepository
import cl.oyl.frutossecos.data.repository.local.LocalAuthRepository
import cl.oyl.frutossecos.data.repository.local.LocalCartRepository
import cl.oyl.frutossecos.data.repository.local.LocalInventoryRepository
import cl.oyl.frutossecos.data.repository.local.LocalNotificationRepository
import cl.oyl.frutossecos.data.repository.local.LocalOrderRepository
import cl.oyl.frutossecos.data.repository.local.LocalProductRepository
import cl.oyl.frutossecos.data.repository.local.LocalProfileRepository
import cl.oyl.frutossecos.data.repository.local.LocalStoreRepository
import cl.oyl.frutossecos.data.repository.remote.RemoteAuthRepository
import cl.oyl.frutossecos.data.repository.remote.RemoteInventoryRepository
import cl.oyl.frutossecos.data.repository.remote.RemoteNotificationRepository
import cl.oyl.frutossecos.data.repository.remote.RemoteOrderRepository
import cl.oyl.frutossecos.data.repository.remote.RemoteProductRepository
import cl.oyl.frutossecos.data.repository.remote.RemoteStoreRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Elige la implementación de cada repositorio:
 * - `USE_REMOTE_REPOSITORIES=true` (por defecto): backend real vía Retrofit,
 *   con Room como caché offline. Las pantallas y ViewModels no cambian.
 * - `false`: modo 100 % local con Room/SQLite (comportamiento original).
 *
 * El carrito siempre es local: es un borrador de compra por usuario.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    fun auth(local: LocalAuthRepository, remoteImpl: RemoteAuthRepository): AuthRepository =
        if (BuildConfig.USE_REMOTE_REPOSITORIES) remoteImpl else local

    @Provides
    fun products(local: LocalProductRepository, remoteImpl: RemoteProductRepository): ProductRepository =
        if (BuildConfig.USE_REMOTE_REPOSITORIES) remoteImpl else local

    @Provides
    fun cart(local: LocalCartRepository): CartRepository = local

    @Provides
    fun orders(local: LocalOrderRepository, remoteImpl: RemoteOrderRepository): OrderRepository =
        if (BuildConfig.USE_REMOTE_REPOSITORIES) remoteImpl else local

    @Provides
    fun inventory(local: LocalInventoryRepository, remoteImpl: RemoteInventoryRepository): InventoryRepository =
        if (BuildConfig.USE_REMOTE_REPOSITORIES) remoteImpl else local

    @Provides
    fun stores(local: LocalStoreRepository, remoteImpl: RemoteStoreRepository): StoreRepository =
        if (BuildConfig.USE_REMOTE_REPOSITORIES) remoteImpl else local

    @Provides
    fun notifications(local: LocalNotificationRepository, remoteImpl: RemoteNotificationRepository): NotificationRepository =
        if (BuildConfig.USE_REMOTE_REPOSITORIES) remoteImpl else local

    @Provides
    fun profile(local: LocalProfileRepository): ProfileRepository = local

    @Provides
    fun systemNotifier(impl: AndroidSystemNotifier): SystemNotifier = impl
}

package cl.oyl.frutossecos.core.di

import cl.oyl.frutossecos.data.remote.interceptor.AuthInterceptor
import cl.oyl.frutossecos.data.remote.interceptor.TokenHolder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/**
 * Configuración de red única.
 * Instala: Ms-core (fuente principal), y clientes Retrofit de ms-usuarios,
 * ms-productos y ms-ventas. Ver [RemoteServiceFactory] y [cl.oyl.frutossecos.data.remote.ApiEndpoints].
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides @Singleton
    fun provideTokenHolder(): TokenHolder = TokenHolder()

    @Provides @Singleton
    fun provideAuthInterceptor(holder: TokenHolder): AuthInterceptor = AuthInterceptor(holder)
}

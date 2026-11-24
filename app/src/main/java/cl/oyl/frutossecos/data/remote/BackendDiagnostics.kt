package cl.oyl.frutossecos.data.remote

import cl.oyl.frutossecos.data.remote.api.HealthApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/** Estado de un microservicio para la pantalla de diagnóstico. */
data class ServiceStatus(
    val endpoint: ServiceEndpoint,
    val online: Boolean,
    val latencyMs: Long,
    val detail: String,
)

/**
 * Comprueba, en paralelo, cada uno de los cuatro microservicios consultando su
 * `/actuator/health`. Sirve para validar desde el emulador o un teléfono físico
 * que el backend Docker está accesible y qué servicio falla si algo va mal.
 */
@Singleton
class BackendDiagnostics @Inject constructor(
    private val factory: RemoteServiceFactory,
) {
    private suspend fun check(endpoint: ServiceEndpoint): ServiceStatus = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        val result = factory.health(endpoint).ping()
        val elapsed = (System.nanoTime() - started) / 1_000_000
        when (result) {
            is RemoteResult.Success -> ServiceStatus(endpoint, true, elapsed, "En línea")
            is RemoteResult.Failure -> ServiceStatus(endpoint, false, elapsed, result.error.userMessage)
        }
    }

    suspend fun checkAll(): List<ServiceStatus> = withContext(Dispatchers.IO) {
        ApiEndpoints.all.map { endpoint -> async { check(endpoint) } }.awaitAll()
    }

    /** URL completa de health de un servicio (se muestra en la pantalla). */
    fun healthUrl(endpoint: ServiceEndpoint): String = endpoint.baseUrl + "actuator/health"

    /** Acceso usado por la prueba de integración de Retrofit. */
    fun createHealthApi(endpoint: ServiceEndpoint): HealthApi = factory.health(endpoint)
}

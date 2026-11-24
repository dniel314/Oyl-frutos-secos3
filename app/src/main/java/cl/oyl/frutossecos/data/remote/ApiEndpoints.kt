package cl.oyl.frutossecos.data.remote

import cl.oyl.frutossecos.BuildConfig

/**
 * Direcciones de los cuatro microservicios, centralizadas en un solo lugar.
 *
 * - [usuario] / [producto] / [venta] / [core] permiten probar cada servicio por
 *   separado; por defecto pasan por el API Gateway, que es el único punto de
 *   entrada publicado (puerto 8080) y enruta a cada microservicio.
 * - Sirve tanto para el emulador (10.0.2.2) como para un dispositivo físico
 *   (IP LAN del computador). La IP se configura en gradle.properties
 *   (OYL_DEV_HOST) o en local.properties (oylDevHost) sin tocar el código.
 */
data class ServiceEndpoint(val name: String, val baseUrl: String)

object ApiEndpoints {
    /** Host del computador que ejecuta Docker. */
    val host: String get() = BuildConfig.API_HOST

    private fun url(port: Int) = "http://$host:$port/"

    val gateway: ServiceEndpoint get() = ServiceEndpoint("api-gateway", url(BuildConfig.API_GATEWAY_PORT))
    val usuario: ServiceEndpoint get() = ServiceEndpoint("ms-usuarios", url(BuildConfig.API_USUARIOS_PORT))
    val producto: ServiceEndpoint get() = ServiceEndpoint("ms-productos", url(BuildConfig.API_PRODUCTOS_PORT))
    val venta: ServiceEndpoint get() = ServiceEndpoint("ms-ventas", url(BuildConfig.API_VENTAS_PORT))
    val core: ServiceEndpoint get() = ServiceEndpoint("ms-core", url(BuildConfig.API_CORE_PORT))

    /** Servicios que la app puede probar directamente (diagnóstico y sincronización). */
    val all: List<ServiceEndpoint> get() = listOf(usuario, producto, venta, core)
}

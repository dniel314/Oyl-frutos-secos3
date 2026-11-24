package cl.oyl.frutossecos.data.remote

import cl.oyl.frutossecos.BuildConfig
import cl.oyl.frutossecos.data.remote.api.CoreApi
import cl.oyl.frutossecos.data.remote.api.HealthApi
import cl.oyl.frutossecos.data.remote.api.ProductosApi
import cl.oyl.frutossecos.data.remote.api.UsuariosApi
import cl.oyl.frutossecos.data.remote.api.VentasApi
import cl.oyl.frutossecos.data.remote.dto.ApiErrorDto
import cl.oyl.frutossecos.data.remote.interceptor.AuthInterceptor
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Resultado de una llamada al backend.
 * [RemoteError] distingue errores de red (sin conexión, timeout), HTTP (4xx/5xx)
 * y de datos, para que la UI muestre un mensaje útil y la app siga funcionando
 * con los datos locales (Room) cuando el servidor no está disponible.
 */
sealed interface RemoteResult<out T> {
    data class Success<T>(val value: T) : RemoteResult<T>
    data class Failure(val error: RemoteError) : RemoteResult<Nothing>
}

enum class RemoteErrorKind { NETWORK, TIMEOUT, UNAUTHORIZED, CONFLICT, NOT_FOUND, SERVER, BAD_RESPONSE, UNKNOWN }

data class RemoteError(
    val kind: RemoteErrorKind,
    val httpCode: Int? = null,
    val code: String? = null,
    val message: String? = null,
) {
    /** Texto apto para el usuario, sin detalles internos del servidor. */
    val userMessage: String
        get() = when (kind) {
            RemoteErrorKind.NETWORK -> "No se pudo conectar con el servidor. Se muestran los datos guardados en el teléfono."
            RemoteErrorKind.TIMEOUT -> "El servidor tardó demasiado en responder."
            RemoteErrorKind.UNAUTHORIZED -> "Sesión expirada o credenciales inválidas."
            RemoteErrorKind.CONFLICT -> message ?: "El recurso ya existe."
            RemoteErrorKind.NOT_FOUND -> message ?: "El recurso no existe en el servidor."
            RemoteErrorKind.SERVER -> "El servidor tuvo un problema. Intenta nuevamente."
            RemoteErrorKind.BAD_RESPONSE -> "Respuesta inesperada del servidor."
            RemoteErrorKind.UNKNOWN -> "Ocurrió un error inesperado."
        }
}

/**
 * Única fábrica de Retrofit y OkHttp de la app.
 * Los cuatro servicios comparten el mismo cliente (interceptores, timeouts y
 * serialización) y solo cambian la URL base, evitando configuraciones duplicadas.
 */
@Singleton
class RemoteServiceFactory @Inject constructor(
    private val authInterceptor: AuthInterceptor,
    private val json: Json,
) {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            })
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private fun retrofit(endpoint: ServiceEndpoint, client: OkHttpClient = this.client): Retrofit = Retrofit.Builder()
        .baseUrl(endpoint.baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val core: CoreApi by lazy { retrofit(ApiEndpoints.gateway).create(CoreApi::class.java) }
    val usuarios: UsuariosApi by lazy { retrofit(ApiEndpoints.gateway).create(UsuariosApi::class.java) }
    val productos: ProductosApi by lazy { retrofit(ApiEndpoints.gateway).create(ProductosApi::class.java) }
    val ventas: VentasApi by lazy { retrofit(ApiEndpoints.gateway).create(VentasApi::class.java) }

    /** Cliente sin JWT para diagnósticos de cada servicio directo. */
    val health: (ServiceEndpoint) -> HealthApi = { endpoint ->
        retrofit(endpoint, client.newBuilder().build()).create(HealthApi::class.java)
    }
}

/** Convierte cualquier fallo de red/HTTP en un [RemoteError] uniforme. */
object RemoteErrors {
    suspend fun <T> call(block: suspend () -> Response<T>): RemoteResult<T> = try {
        val response = block()
        if (response.isSuccessful) {
            @Suppress("UNCHECKED_CAST")
            val body = response.body() ?: Unit as T
            RemoteResult.Success(body)
        } else {
            RemoteResult.Failure(http(response.code(), response.errorBody()?.string()))
        }
    } catch (e: SocketTimeoutException) {
        RemoteResult.Failure(RemoteError(RemoteErrorKind.TIMEOUT, message = e.message))
    } catch (e: IOException) {
        RemoteResult.Failure(RemoteError(RemoteErrorKind.NETWORK, message = e.message))
    } catch (e: SerializationException) {
        RemoteResult.Failure(RemoteError(RemoteErrorKind.BAD_RESPONSE, message = e.message))
    } catch (e: Exception) {
        RemoteResult.Failure(RemoteError(RemoteErrorKind.UNKNOWN, message = e.message))
    }

    fun http(code: Int, body: String?): RemoteError {
        val parsed = body?.takeIf { it.isNotBlank() }?.let {
            runCatching { Json { ignoreUnknownKeys = true }.decodeFromString(ApiErrorDto.serializer(), it) }.getOrNull()
        }
        val kind = when (code) {
            401, 403 -> RemoteErrorKind.UNAUTHORIZED
            404 -> RemoteErrorKind.NOT_FOUND
            409 -> RemoteErrorKind.CONFLICT
            in 400..499 -> RemoteErrorKind.BAD_RESPONSE
            else -> RemoteErrorKind.SERVER
        }
        return RemoteError(kind, code, parsed?.code, parsed?.message ?: parsed?.error)
    }
}

/** Ping de un servicio para la pantalla de diagnóstico (no lanza excepciones). */
suspend fun HealthApi.ping(): RemoteResult<Unit> = RemoteErrors.call { health() }.let {
    when (it) {
        is RemoteResult.Success -> RemoteResult.Success(Unit)
        is RemoteResult.Failure -> it
    }
}

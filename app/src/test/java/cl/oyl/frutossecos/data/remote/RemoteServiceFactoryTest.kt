package cl.oyl.frutossecos.data.remote

import cl.oyl.frutossecos.data.remote.api.CoreApi
import cl.oyl.frutossecos.data.remote.dto.LoginRequestDto
import cl.oyl.frutossecos.data.remote.dto.ProductDto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Prueba de la capa de red real (Retrofit + kotlinx.serialization) contra un
 * servidor HTTP simulado, sin necesidad de emulador ni backend.
 *
 * Verifica que:
 *  - las rutas y los DTOs coinciden con los del backend;
 *  - los errores HTTP y de conexión se traducen a mensajes para el usuario.
 */
class RemoteServiceFactoryTest {
    private lateinit var server: MockWebServer
    private lateinit var api: CoreApi

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        api = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CoreApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `login envia el contrato esperado y lee el JWT`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"token":"jwt-de-prueba","user":{"id":1,"name":"Administrador",
                    "alias":"admin","email":"admin@admin.cl","phone":"+56 9 1234 1234",
                    "role":"ADMINISTRADOR"}}
                    """.trimIndent()
                )
        )
        val result = RemoteErrors.call {
            api.login(LoginRequestDto(alias = "admin", password = "secreta", role = "ADMINISTRADOR"))
        }

        val request = server.takeRequest()
        assertEquals("/api/v1/auth/login", request.path)
        assertEquals("POST", request.method)
        val body = request.body.readUtf8()
        assertTrue("el alias debe viajar en el cuerpo", body.contains("\"alias\":\"admin\""))
        assertTrue("el rol debe viajar en el cuerpo", body.contains("\"role\":\"ADMINISTRADOR\""))

        assertTrue("Respuesta 200 debe ser Success", result is RemoteResult.Success)
        val auth = (result as RemoteResult.Success).value
        assertEquals("jwt-de-prueba", auth.token)
        assertEquals("admin", auth.user?.alias)
    }

    @Test
    fun `serializa productos con campos desconocidos sin fallar`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    [{"id":1,"name":"Almendras","categoryId":1,"price":2990,"stock":25,
                    "minStock":5,"active":true,"qrCode":"OYL-P-0001","campoNuevoDelFuturo":true}]
                    """.trimIndent()
                )
        )
        val result = RemoteErrors.call { api.products() }
        assertTrue(result is RemoteResult.Success)
        val products = (result as RemoteResult.Success<List<ProductDto>>).value
        assertEquals(1, products.size)
        assertEquals("Almendras", products.first().name)
        assertEquals(2990L, products.first().price)
    }

    @Test
    fun `un 401 se informa como sesion o credenciales invalidas`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"code":"INVALID_CREDENTIALS","message":"Credenciales inválidas"}""")
        )
        val result = RemoteErrors.call { api.login(LoginRequestDto("admin", "mala", "ADMINISTRADOR")) }
        assertTrue(result is RemoteResult.Failure)
        val error = (result as RemoteResult.Failure).error
        assertEquals(RemoteErrorKind.UNAUTHORIZED, error.kind)
        assertEquals(401, error.httpCode)
        assertEquals("INVALID_CREDENTIALS", error.code)
    }

    @Test
    fun `un 409 de stock se informa como conflicto con el mensaje del servidor`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(409)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"code":"INSUFFICIENT_STOCK","message":"Stock insuficiente para Almendras"}""")
        )
        val result = RemoteErrors.call { api.products() }
        val error = (result as RemoteResult.Failure).error
        assertEquals(RemoteErrorKind.CONFLICT, error.kind)
        assertEquals("Stock insuficiente para Almendras", error.message)
    }

    @Test
    fun `sin servidor se informa como error de red y la app puede usar la cache`() = runTest {
        server.shutdown() // puerto cerrado: simula que no hay backend
        val result = RemoteErrors.call { api.products() }
        assertTrue(result is RemoteResult.Failure)
        val error = (result as RemoteResult.Failure).error
        assertEquals(RemoteErrorKind.NETWORK, error.kind)
        assertTrue("Debe sugerir usar datos locales", error.userMessage.contains("guardados"))
    }

    @Test
    fun `un cuerpo que no es JSON valido se informa como respuesta invalida`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("esto no es json"))
        val result = RemoteErrors.call { api.products() }
        assertTrue(result is RemoteResult.Failure)
        assertEquals(RemoteErrorKind.BAD_RESPONSE, (result as RemoteResult.Failure).error.kind)
    }
}

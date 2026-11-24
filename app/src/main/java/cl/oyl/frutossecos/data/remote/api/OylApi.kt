package cl.oyl.frutossecos.data.remote.api

import cl.oyl.frutossecos.data.remote.dto.ActiveRequestDto
import cl.oyl.frutossecos.data.remote.dto.ApiErrorDto
import cl.oyl.frutossecos.data.remote.dto.AuthResponseDto
import cl.oyl.frutossecos.data.remote.dto.CartValidateResponseDto
import cl.oyl.frutossecos.data.remote.dto.CreateOrderRequestDto
import cl.oyl.frutossecos.data.remote.dto.LoginRequestDto
import cl.oyl.frutossecos.data.remote.dto.MovementRequestDto
import cl.oyl.frutossecos.data.remote.dto.MovementResponseDto
import cl.oyl.frutossecos.data.remote.dto.NotificationDto
import cl.oyl.frutossecos.data.remote.dto.OrderDto
import cl.oyl.frutossecos.data.remote.dto.ProductDto
import cl.oyl.frutossecos.data.remote.dto.ProductRequestDto
import cl.oyl.frutossecos.data.remote.dto.RegisterRequestDto
import cl.oyl.frutossecos.data.remote.dto.StatusRequestDto
import cl.oyl.frutossecos.data.remote.dto.StoreDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * ms-core (backend integral, base /api/v1/).
 * Es el servicio que cubre autenticación, pedidos, inventario, tiendas y avisos,
 * por lo que la app lo usa como fuente principal de esas operaciones.
 */
interface CoreApi {
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): Response<AuthResponseDto>

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequestDto): Response<AuthResponseDto>

    @GET("products")
    suspend fun products(
        @Query("category") categoryId: Long? = null,
        @Query("q") query: String? = null,
        @Query("active") active: Boolean? = null,
    ): Response<List<ProductDto>>

    @GET("products/{id}")
    suspend fun product(@Path("id") id: Long): Response<ProductDto>

    @GET("products/by-qr/{code}")
    suspend fun productByQr(@Path("code") code: String): Response<ProductDto>

    @POST("products")
    suspend fun createProduct(@Body body: ProductRequestDto): Response<ProductDto>

    @PUT("products/{id}")
    suspend fun updateProduct(@Path("id") id: Long, @Body body: ProductRequestDto): Response<ProductDto>

    @PATCH("products/{id}/active")
    suspend fun setProductActive(@Path("id") id: Long, @Body body: ActiveRequestDto): Response<ProductDto>

    @GET("stores")
    suspend fun stores(): Response<List<StoreDto>>

    @POST("cart/validate")
    suspend fun validateCart(@Body body: CreateOrderRequestDto): Response<CartValidateResponseDto>

    @POST("orders")
    suspend fun createOrder(@Body body: CreateOrderRequestDto): Response<OrderDto>

    @GET("orders")
    suspend fun orders(@Query("status") status: String? = null): Response<List<OrderDto>>

    @GET("orders/{id}")
    suspend fun order(@Path("id") id: Long): Response<OrderDto>

    @PATCH("orders/{id}/status")
    suspend fun updateOrderStatus(@Path("id") id: Long, @Body body: StatusRequestDto): Response<OrderDto>

    @GET("inventory")
    suspend fun inventory(@Query("lowStock") lowStock: Boolean = false): Response<List<ProductDto>>

    @POST("inventory/movements")
    suspend fun postMovement(@Body body: MovementRequestDto): Response<MovementResponseDto>

    @GET("notifications")
    suspend fun notifications(): Response<List<NotificationDto>>

    @POST("notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: Long): Response<Unit>
}

/** ms-usuarios (base /api/usuarios/). */
interface UsuariosApi {
    @POST("login")
    suspend fun login(@Body body: cl.oyl.frutossecos.data.remote.dto.UsuariosLoginRequestDto):
        Response<cl.oyl.frutossecos.data.remote.dto.UsuariosLoginResponseDto>

    @POST("register")
    suspend fun register(@Body body: cl.oyl.frutossecos.data.remote.dto.UsuariosRegistroRequestDto):
        Response<cl.oyl.frutossecos.data.remote.dto.UsuariosUsuarioDto>

    @GET("{id}")
    suspend fun byId(@Path("id") id: Long): Response<cl.oyl.frutossecos.data.remote.dto.UsuariosUsuarioDto>

    @GET
    suspend fun all(): Response<List<cl.oyl.frutossecos.data.remote.dto.UsuariosUsuarioDto>>
}

/** ms-productos (bases /api/productos/ y /api/categorias/). */
interface ProductosApi {
    @GET("api/productos")
    suspend fun products(): Response<List<cl.oyl.frutossecos.data.remote.dto.ProductoDto>>

    @GET("api/productos/{id}")
    suspend fun product(@Path("id") id: Long): Response<cl.oyl.frutossecos.data.remote.dto.ProductoDto>

    @GET("api/productos/categoria/{categoriaId}")
    suspend fun byCategory(@Path("categoriaId") categoriaId: Long): Response<List<cl.oyl.frutossecos.data.remote.dto.ProductoDto>>

    @GET("api/productos/buscar")
    suspend fun search(@Query("q") query: String): Response<List<cl.oyl.frutossecos.data.remote.dto.ProductoDto>>

    @PATCH("api/productos/{id}/stock")
    suspend fun updateStock(
        @Path("id") id: Long,
        @Body body: cl.oyl.frutossecos.data.remote.dto.StockUpdateDto,
    ): Response<cl.oyl.frutossecos.data.remote.dto.ProductoDto>

    @GET("api/categorias")
    suspend fun categories(): Response<List<cl.oyl.frutossecos.data.remote.dto.CategoriaDto>>
}

/** ms-ventas (base /api/ventas/). */
interface VentasApi {
    @POST("api/ventas")
    suspend fun create(@Body body: cl.oyl.frutossecos.data.remote.dto.VentaRequestDto):
        Response<cl.oyl.frutossecos.data.remote.dto.VentaResponseDto>

    @GET("api/ventas/{id}")
    suspend fun byId(@Path("id") id: Long): Response<cl.oyl.frutossecos.data.remote.dto.VentaResponseDto>

    @GET("api/ventas/usuario/{usuarioId}")
    suspend fun byUser(@Path("usuarioId") usuarioId: Long):
        Response<List<cl.oyl.frutossecos.data.remote.dto.VentaResponseDto>>
}

/** Diagnóstico: consulta el actuator de un servicio sin pasar por los interceptores. */
interface HealthApi {
    @GET("actuator/health")
    suspend fun health(): Response<Map<String, String>>
}

/** Tipo compartido para documentar el formato de error del backend. */
typealias BackendError = ApiErrorDto

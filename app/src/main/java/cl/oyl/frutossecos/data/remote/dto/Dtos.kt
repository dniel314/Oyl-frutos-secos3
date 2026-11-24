package cl.oyl.frutossecos.data.remote.dto

import kotlinx.serialization.Serializable

// =====================================================================
// DTOs del backend real. Se serializan con kotlinx.serialization y
// toleran campos extra (el backend puede evolucionar sin romper la app).
// Montos en CLP enteros (ms-core) o BigDecimal (ms-productos/ms-ventas).
// =====================================================================

// ---------- ms-core (/api/v1) ----------
@Serializable data class LoginRequestDto(val alias: String, val password: String, val role: String)
@Serializable data class AuthResponseDto(
    val token: String,
    val user: CoreUserDto? = null,
    // Compatibilidad con el contrato antiguo plano.
    val userId: Long? = null,
    val name: String? = null,
    val role: String? = null,
)

@Serializable data class CoreUserDto(
    val id: Long, val name: String, val alias: String, val email: String, val phone: String,
    val address: String? = null, val avatarId: Int? = null, val role: String,
)

@Serializable data class RegisterRequestDto(
    val name: String,
    val alias: String,
    val email: String,
    val password: String,
    val phone: String,
    val address: String? = null,
    val avatarId: Int? = null,
)

@Serializable data class ProductDto(
    val id: Long,
    val name: String,
    val categoryId: Long,
    val category: String? = null,
    val description: String = "",
    val price: Long,
    val presentation: String = "",
    val weightGrams: Int = 0,
    val origin: String = "",
    val imageUrl: String? = null,
    val stock: Int,
    val minStock: Int = 0,
    val active: Boolean = true,
    val qrCode: String? = null,
)

@Serializable data class ProductRequestDto(
    val name: String,
    val categoryId: Long,
    val description: String,
    val price: Long,
    val presentation: String,
    val weightGrams: Int,
    val origin: String,
    val stock: Int,
    val minStock: Int,
    val active: Boolean = true,
    val imageUrl: String? = null,
)

@Serializable data class ActiveRequestDto(val active: Boolean)

@Serializable data class StoreDto(
    val id: Long, val name: String, val address: String, val lat: Double, val lng: Double,
    val hours: String, val type: String,
)

@Serializable data class LineRequestDto(val productId: Long, val quantity: Int)

@Serializable data class CreateOrderRequestDto(val items: List<LineRequestDto>, val deliveryMode: String)
@Serializable data class StatusRequestDto(val status: String)

@Serializable data class OrderItemDto(
    val productId: Long, val name: String, val unitPrice: Long, val quantity: Int,
)

@Serializable data class OrderDto(
    val id: Long,
    val number: String,
    val userId: Long,
    val customerName: String? = null,
    val status: String,
    val subtotal: Long = 0,
    val shipping: Long = 0,
    val total: Long,
    val deliveryMode: String,
    val createdAt: String,
    val items: List<OrderItemDto> = emptyList(),
)

@Serializable data class CartValidateResponseDto(
    val valid: Boolean = true,
    val issues: List<StockIssueDto> = emptyList(),
)

@Serializable data class StockIssueDto(val productId: Long, val name: String = "", val requested: Int = 0, val available: Int = 0)

@Serializable data class MovementRequestDto(
    val localId: String, val productId: Long, val type: String, val quantity: Int,
)

@Serializable data class MovementResponseDto(
    val localId: String, val productId: Long, val type: String, val quantity: Int,
    val resultingStock: Int, val duplicated: Boolean = false,
)

@Serializable data class NotificationDto(
    val id: Long, val type: String, val title: String, val message: String,
    val createdAt: String, val read: Boolean = false, val targetRoute: String? = null, val targetId: Long? = null,
)

// ---------- ms-usuarios (/api/usuarios) ----------
@Serializable data class UsuariosLoginRequestDto(val email: String, val password: String)

@Serializable data class UsuariosRegistroRequestDto(
    val nombre: String, val apellido: String, val email: String, val password: String,
    val telefono: String? = null, val direccion: String? = null,
)

@Serializable data class UsuariosUsuarioDto(
    val id: Long, val nombre: String, val apellido: String, val email: String,
    val telefono: String? = null, val direccion: String? = null, val rol: String, val activo: Boolean = true,
)

@Serializable data class UsuariosLoginResponseDto(
    val token: String, val tipo: String? = null, val usuario: UsuariosUsuarioDto? = null,
)

// ---------- ms-productos (/api/productos, /api/categorias) ----------
@Serializable data class CategoriaDto(
    val id: Long, val nombre: String, val descripcion: String? = null, val activo: Boolean = true,
)

@Serializable data class CategoriaCreateDto(
    val nombre: String, val descripcion: String? = null,
)

@Serializable data class ProductoDto(
    val id: Long, val nombre: String, val descripcion: String? = null, val precio: Double,
    val stock: Int, val imagenUrl: String? = null, val categoriaId: Long? = null,
    val categoriaNombre: String? = null, val activo: Boolean = true,
)

@Serializable data class ProductoCreateDto(
    val nombre: String, val descripcion: String? = null, val precio: Double, val stock: Int,
    val imagenUrl: String? = null, val categoriaId: Long,
)

@Serializable data class StockUpdateDto(val cantidad: Int, val operacion: String)

// ---------- ms-ventas (/api/ventas) ----------
@Serializable data class DetalleVentaRequestDto(
    val productoId: Long, val nombreProducto: String, val precioUnitario: Double, val cantidad: Int,
)

@Serializable data class VentaRequestDto(
    val usuarioId: Long, val metodoPago: String, val direccionEntrega: String? = null,
    val detalles: List<DetalleVentaRequestDto>,
)

@Serializable data class DetalleVentaResponseDto(
    val id: Long, val productoId: Long, val nombreProducto: String,
    val precioUnitario: Double, val cantidad: Int, val subtotal: Double,
)

@Serializable data class VentaResponseDto(
    val id: Long, val usuarioId: Long, val total: Double, val estado: String,
    val metodoPago: String, val direccionEntrega: String? = null, val createdAt: String? = null,
    val detalles: List<DetalleVentaResponseDto> = emptyList(),
)

@Serializable data class CambioEstadoDto(val nuevoEstado: String)

/** Respuesta de error estándar del backend. */
@Serializable data class ApiErrorDto(
    val code: String? = null, val message: String? = null, val error: String? = null,
    val timestamp: String? = null, val status: Int? = null, val details: String? = null,
)

package cl.oyl.frutossecos.data.repository.remote

import androidx.room.withTransaction
import cl.oyl.frutossecos.data.local.OylDatabase
import cl.oyl.frutossecos.data.local.entity.OrderEntity
import cl.oyl.frutossecos.data.local.entity.OrderItemEntity
import cl.oyl.frutossecos.data.local.entity.PendingStockMovementEntity
import cl.oyl.frutossecos.data.local.mapper.toDomain
import cl.oyl.frutossecos.data.remote.RemoteErrors
import cl.oyl.frutossecos.data.remote.RemoteResult
import cl.oyl.frutossecos.data.remote.RemoteServiceFactory
import cl.oyl.frutossecos.data.remote.dto.CreateOrderRequestDto
import cl.oyl.frutossecos.data.remote.dto.LineRequestDto
import cl.oyl.frutossecos.data.remote.dto.MovementRequestDto
import cl.oyl.frutossecos.data.remote.dto.NotificationDto
import cl.oyl.frutossecos.data.remote.dto.OrderDto
import cl.oyl.frutossecos.data.remote.dto.StatusRequestDto
import cl.oyl.frutossecos.data.repository.EmptyCartException
import cl.oyl.frutossecos.data.repository.InvalidTransitionException
import cl.oyl.frutossecos.data.repository.InventoryRepository
import cl.oyl.frutossecos.data.repository.NotificationRepository
import cl.oyl.frutossecos.data.repository.OrderRepository
import cl.oyl.frutossecos.data.repository.StockException
import cl.oyl.frutossecos.data.repository.local.LocalInventoryRepository
import cl.oyl.frutossecos.data.repository.local.NotificationPublisher
import cl.oyl.frutossecos.domain.logic.CartRules
import cl.oyl.frutossecos.domain.logic.OrderRules
import cl.oyl.frutossecos.domain.logic.StockIssue
import cl.oyl.frutossecos.domain.model.AppNotification
import cl.oyl.frutossecos.domain.model.CartLine
import cl.oyl.frutossecos.domain.model.DeliveryMode
import cl.oyl.frutossecos.domain.model.MovementType
import cl.oyl.frutossecos.domain.model.NotificationType
import cl.oyl.frutossecos.domain.model.Order
import cl.oyl.frutossecos.domain.model.OrderItem
import cl.oyl.frutossecos.domain.model.OrderStatus
import cl.oyl.frutossecos.domain.model.Role
import cl.oyl.frutossecos.domain.model.SyncStatus
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Pedidos contra ms-core, con Room como espejo local.
 *
 * Crear un pedido:
 *  1. Valida el carrito local (reglas compartidas con el backend).
 *  2. Envía el pedido al backend, que descuenta stock de forma transaccional.
 *  3. Guarda en Room el pedido y sus líneas, y vacía el carrito.
 *  4. Si no hay red, se registra el pedido igual en local (pendiente de sincronizar)
 *     usando la misma maquinaria local que ya existía, sin inventar endpoints.
 */
@Singleton
class RemoteOrderRepository @Inject constructor(
    private val db: OylDatabase,
    private val remote: RemoteServiceFactory,
    private val local: cl.oyl.frutossecos.data.repository.local.LocalOrderRepository,
) : OrderRepository {
    private val dao = db.orderDao()
    private val syncMutex = Mutex()

    override suspend fun placeOrder(userId: Long, mode: DeliveryMode): Order {
        val lines = db.cartDao().getLines(userId).map { it.toDomain() }
        if (lines.isEmpty()) throw EmptyCartException()
        CartRules.issues(lines).takeIf { it.isNotEmpty() }?.let { throw StockException(it) }

        val request = CreateOrderRequestDto(
            items = lines.map { LineRequestDto(it.productId, it.quantity) },
            deliveryMode = mode.name,
        )
        return when (val res = RemoteErrors.call { remote.core.createOrder(request) }) {
            is RemoteResult.Success -> {
                persist(res.value, lines)
                db.cartDao().clear(userId)
                refreshProductsFromServer()
                db.orderDao().getById(res.value.id)?.toDomain() ?: local.placeOrder(userId, mode)
            }
            is RemoteResult.Failure -> {
                if (res.error.httpCode == 409 || res.error.code == "INSUFFICIENT_STOCK") {
                    refreshProductsFromServer()
                    throw StockException(
                        lines.map { StockIssue(it.productId, it.name, it.quantity, db.productDao().getById(it.productId)?.stock ?: 0) }
                    )
                }
                // Sin servidor: el pedido queda registrado localmente (mismo flujo de siempre).
                local.placeOrder(userId, mode)
            }
        }
    }

    override fun observeOrders(userId: Long, role: Role, status: OrderStatus?): Flow<List<Order>> =
        (if (role == Role.ADMINISTRADOR) dao.observeAll(status) else dao.observeForUser(userId, status))
            .map { l -> l.map { it.toDomain() } }

    override fun observeOrder(orderId: Long): Flow<Order?> = dao.observeById(orderId).map { it?.toDomain() }
    override fun observeItems(orderId: Long): Flow<List<OrderItem>> = dao.observeItems(orderId).map { l -> l.map { it.toDomain() } }
    override fun observePendingCount(): Flow<Int> = dao.observeCountByStatus(OrderStatus.PENDIENTE_VALIDACION)

    override suspend fun updateStatus(orderId: Long, status: OrderStatus) {
        val order = dao.getById(orderId) ?: throw InvalidTransitionException()
        if (!OrderRules.canTransition(order.status, status)) throw InvalidTransitionException()
        val res = RemoteErrors.call { remote.core.updateOrderStatus(orderId, StatusRequestDto(status.name)) }
        dao.updateStatus(orderId, status)
        if (res is RemoteResult.Success) cacheOrders(listOf(res.value))
        refreshProductsFromServer()
    }

    /** Descarga los pedidos visibles para el rol actual y los guarda en Room. */
    suspend fun refresh(role: Role): RemoteResult<Unit> = syncMutex.withLock {
        val res = RemoteErrors.call { remote.core.orders() }
        if (res is RemoteResult.Success) cacheOrders(res.value)
        if (res is RemoteResult.Success) RemoteResult.Success(Unit) else res as RemoteResult<Unit>
    }

    private suspend fun persist(dto: OrderDto, lines: List<CartLine>) = cacheOrders(listOf(dto), lines)

    private suspend fun cacheOrders(orders: List<OrderDto>, lines: List<CartLine> = emptyList()) {
        if (orders.isEmpty()) return
        db.withTransaction {
            orders.forEach { o ->
                val entity = OrderEntity(
                    id = o.id, userId = o.userId, number = o.number,
                    status = runCatching { OrderStatus.valueOf(o.status) }.getOrDefault(OrderStatus.PENDIENTE_VALIDACION),
                    total = o.total, shipping = o.shipping,
                    deliveryMode = runCatching { DeliveryMode.valueOf(o.deliveryMode) }.getOrDefault(DeliveryMode.RETIRO),
                    createdAt = runCatching { Instant.parse(o.createdAt).toEpochMilli() }.getOrDefault(System.currentTimeMillis()),
                    syncedAt = System.currentTimeMillis(),
                )
                dao.upsertOrder(entity)
                val items = o.items.map { OrderItemEntity(o.id, it.productId, it.name, it.unitPrice, it.quantity) }
                    .ifEmpty { lines.map { OrderItemEntity(o.id, it.productId, it.name, it.unitPrice, it.quantity) } }
                if (items.isNotEmpty()) dao.insertItems(items)
            }
        }
    }

    private suspend fun refreshProductsFromServer() {
        when (val res = RemoteErrors.call { remote.core.products() }) {
            is RemoteResult.Success -> {
                val now = System.currentTimeMillis()
                val entities = res.value.mapNotNull { dto ->
                    db.productDao().getById(dto.id)?.copy(
                        name = dto.name, price = dto.price, stock = dto.stock, minStock = dto.minStock,
                        active = dto.active, imageUrl = dto.imageUrl, updatedAt = now,
                    )
                }
                if (entities.isNotEmpty()) db.productDao().upsertAll(entities)
            }
            is RemoteResult.Failure -> Unit
        }
    }
}

/**
 * Inventario: intenta registrar el movimiento en el backend con idempotencia
 * (localId UUID) y siempre deja la cola local para reintentos/sincronización.
 */
@Singleton
class RemoteInventoryRepository @Inject constructor(
    private val db: OylDatabase,
    private val remote: RemoteServiceFactory,
    private val local: LocalInventoryRepository,
) : InventoryRepository {
    override suspend fun adjust(productId: Long, type: MovementType, quantity: Int): Int {
        require(quantity > 0) { "La cantidad debe ser mayor a 0" }
        val localId = UUID.randomUUID().toString()
        val res = RemoteErrors.call {
            remote.core.postMovement(MovementRequestDto(localId, productId, type.name, quantity))
        }
        val newStock = local.adjust(productId, type, quantity)
        // Marca como sincronizado el movimiento si el backend lo aceptó.
        if (res is RemoteResult.Success) {
            runCatching {
                db.pendingStockMovementDao().insert(
                    PendingStockMovementEntity(localId, productId, type, quantity, System.currentTimeMillis(), SyncStatus.SINCRONIZADO)
                )
            }
        }
        return newStock
    }

    override fun observePendingMovements(): Flow<Int> = local.observePendingMovements()

    /** Reintenta los movimientos pendientes (cola local) contra el backend. */
    suspend fun flush(): Int {
        val pending = db.pendingStockMovementDao().getPending()
        var sent = 0
        pending.forEach { m ->
            val res = RemoteErrors.call { remote.core.postMovement(MovementRequestDto(m.localId, m.productId, m.type.name, m.quantity)) }
            if (res is RemoteResult.Success) sent++
        }
        return sent
    }
}

/**
 * Notificaciones: las del backend (ms-core) son la fuente oficial y se guardan
 * en la bandeja local, que sigue funcionando sin red.
 */
@Singleton
class RemoteNotificationRepository @Inject constructor(
    private val db: OylDatabase,
    private val remote: RemoteServiceFactory,
    private val publisher: NotificationPublisher,
) : NotificationRepository {
    private val dao = db.notificationDao()

    override fun observe(userId: Long): Flow<List<AppNotification>> = dao.observeForUser(userId).map { l -> l.map { it.toDomain() } }
    override fun observeUnreadCount(userId: Long): Flow<Int> = dao.observeUnreadCount(userId)

    override suspend fun markRead(userId: Long, id: Long) {
        RemoteErrors.call { remote.core.markNotificationRead(id) }
        dao.markRead(userId, id)
    }

    override suspend fun markAllRead(userId: Long) {
        // El backend expone el marcado individual; la bandeja local se marca completa
        // y las lecturas individuales se sincronizan cuando el usuario abre cada aviso.
        dao.markAllRead(userId)
    }

    suspend fun refresh(userId: Long): RemoteResult<Unit> {
        val res = RemoteErrors.call { remote.core.notifications() }
        return when (res) {
            is RemoteResult.Success -> {
                val pending = res.value.map { it.toPending(userId) }
                if (pending.isNotEmpty()) publisher.insert(pending)
                RemoteResult.Success(Unit)
            }
            is RemoteResult.Failure -> res
        }
    }

    private fun NotificationDto.toPending(userId: Long) = NotificationPublisher.Pending(
        userId,
        cl.oyl.frutossecos.data.local.entity.NotificationEntity(
            id = id, userId = userId,
            type = runCatching { NotificationType.valueOf(type) }.getOrDefault(NotificationType.GENERAL),
            title = title, message = message,
            createdAt = runCatching { Instant.parse(createdAt).toEpochMilli() }.getOrDefault(System.currentTimeMillis()),
            read = read, targetRoute = targetRoute, targetId = targetId,
        ),
    )
}

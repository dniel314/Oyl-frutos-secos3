package cl.oyl.frutossecos.data.repository.remote

import cl.oyl.frutossecos.data.local.OylDatabase
import cl.oyl.frutossecos.data.local.entity.CategoryEntity
import cl.oyl.frutossecos.data.local.entity.ProductEntity
import cl.oyl.frutossecos.data.local.entity.StoreEntity
import cl.oyl.frutossecos.data.local.mapper.toDomain
import cl.oyl.frutossecos.data.remote.RemoteErrors
import cl.oyl.frutossecos.data.remote.RemoteResult
import cl.oyl.frutossecos.data.remote.RemoteServiceFactory
import cl.oyl.frutossecos.data.remote.dto.ActiveRequestDto
import cl.oyl.frutossecos.data.remote.dto.ProductDto
import cl.oyl.frutossecos.data.remote.dto.ProductRequestDto
import cl.oyl.frutossecos.data.remote.dto.StoreDto
import cl.oyl.frutossecos.data.repository.ProductDraft
import cl.oyl.frutossecos.data.repository.ProductRepository
import cl.oyl.frutossecos.data.repository.StoreRepository
import cl.oyl.frutossecos.domain.logic.QrParser
import cl.oyl.frutossecos.domain.model.Category
import cl.oyl.frutossecos.domain.model.Product
import cl.oyl.frutossecos.domain.model.Store
import cl.oyl.frutossecos.domain.model.StoreType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Catálogo con el backend (ms-core) como fuente oficial y Room como caché offline.
 *
 * - Las pantallas siguen observando Room (Flows): no cambia ninguna UI existente.
 * - [refresh] descarga catálogo y tiendas, y actualiza la caché sin borrar datos
 *   locales que el servidor no conoce (p. ej. creados sin red).
 * - Si no hay red, se conserva lo último guardado.
 * - Las altas/ediciones de administrador se envían al backend real y se reflejan
 *   también en Room, de modo que el modo offline sigue siendo coherente.
 */
@Singleton
class RemoteProductRepository @Inject constructor(
    private val db: OylDatabase,
    private val remote: RemoteServiceFactory,
) : ProductRepository {
    private val dao = db.productDao()
    private val refreshMutex = Mutex()

    override fun observeCatalog(query: String, categoryId: Long?, onlyActive: Boolean): Flow<List<Product>> =
        dao.observe(query.trim(), categoryId, onlyActive).map { l -> l.map { it.toDomain() } }

    override fun observeAll(): Flow<List<Product>> = dao.observeAll().map { l -> l.map { it.toDomain() } }
    override fun observeCategories(): Flow<List<Category>> =
        db.categoryDao().observeAll().map { l -> l.map { it.toDomain() } }

    override fun observeProduct(id: Long): Flow<Product?> = dao.observeById(id).map { it?.toDomain() }
    override fun observeLowStock(): Flow<List<Product>> = dao.observeLowStock().map { l -> l.map { it.toDomain() } }

    override suspend fun findByQr(raw: String): Product? {
        val code = QrParser.normalize(raw) ?: return null
        // El backend entrega stock/precio oficiales; si no hay red se usa la caché.
        when (val res = RemoteErrors.call { remote.core.productByQr(code) }) {
            is RemoteResult.Success -> cacheProducts(listOf(res.value))
            is RemoteResult.Failure -> Unit
        }
        return dao.findByQr(code)?.toDomain()
    }

    override suspend fun save(draft: ProductDraft): Long {
        val body = ProductRequestDto(
            name = draft.name.trim(), categoryId = draft.categoryId, description = draft.description.trim(),
            price = draft.price, presentation = draft.presentation.trim(), weightGrams = draft.weightGrams,
            origin = draft.origin.trim(), stock = draft.stock, minStock = draft.minStock, active = draft.active,
        )
        val existing = draft.id?.let { dao.getById(it) }
        val result = if (existing != null && existing.id > 0) {
            RemoteErrors.call { remote.core.updateProduct(existing.id, body) }
        } else {
            RemoteErrors.call { remote.core.createProduct(body) }
        }
        val saved = (result as? RemoteResult.Success)?.value
        if (saved != null) cacheProducts(listOf(saved))

        // Se persiste también localmente para que el cambio se vea al instante.
        val id = existing?.id ?: saved?.id ?: (dao.maxId() + 1)
        dao.upsert(
            ProductEntity(
                id = id, name = body.name, categoryId = body.categoryId, description = body.description,
                price = body.price, presentation = body.presentation, weightGrams = body.weightGrams,
                origin = body.origin, imageUrl = saved?.imageUrl ?: existing?.imageUrl,
                imagePath = draft.imagePath ?: existing?.imagePath, stock = body.stock, minStock = body.minStock,
                active = body.active, qrCode = saved?.qrCode ?: existing?.qrCode ?: "OYL-P-%04d".format(id),
                updatedAt = System.currentTimeMillis(),
            )
        )
        return id
    }

    override suspend fun setActive(id: Long, active: Boolean) {
        RemoteErrors.call { remote.core.setProductActive(id, ActiveRequestDto(active)) }
        dao.setActive(id, active, System.currentTimeMillis())
    }

    /** Descarga catálogo y tiendas del backend y actualiza la caché local. */
    suspend fun refresh(): RemoteResult<Unit> = refreshMutex.withLock {
        val products = RemoteErrors.call { remote.core.products() }
        if (products is RemoteResult.Failure) return@withLock products
        cacheProducts((products as RemoteResult.Success).value)
        val stores = RemoteErrors.call { remote.core.stores() }
        if (stores is RemoteResult.Success) db.storeDao().upsertAll(stores.value.map { it.toEntity() })
        RemoteResult.Success(Unit)
    }

    private suspend fun cacheProducts(items: List<ProductDto>) {
        if (items.isEmpty()) return
        // Categorías: se crean las que falten para respetar la clave foránea local.
        items.map { it.categoryId to (it.category ?: "Categoría ${it.categoryId}") }
            .distinctBy { it.first }
            .forEach { (id, name) ->
                if (db.categoryDao().getById(id) == null) db.categoryDao().upsertAll(listOf(CategoryEntity(id, name)))
            }
        val now = System.currentTimeMillis()
        val entities = items.mapNotNull { dto ->
            if (db.categoryDao().getById(dto.categoryId) == null) return@mapNotNull null
            ProductEntity(
                id = dto.id, name = dto.name, categoryId = dto.categoryId, description = dto.description,
                price = dto.price, presentation = dto.presentation, weightGrams = dto.weightGrams,
                origin = dto.origin, imageUrl = dto.imageUrl, stock = dto.stock, minStock = dto.minStock,
                active = dto.active, qrCode = dto.qrCode ?: "OYL-P-%04d".format(dto.id), updatedAt = now,
            )
        }
        if (entities.isNotEmpty()) dao.upsertAll(entities)
    }
}

@Singleton
class RemoteStoreRepository @Inject constructor(
    private val db: OylDatabase,
    private val remote: RemoteServiceFactory,
) : StoreRepository {
    override fun observeStores(): Flow<List<Store>> = db.storeDao().observeAll().map { l -> l.map { it.toDomain() } }

    suspend fun refresh(): RemoteResult<Unit> {
        val res = RemoteErrors.call { remote.core.stores() }
        return when (res) {
            is RemoteResult.Success -> {
                db.storeDao().upsertAll(res.value.map { it.toEntity() })
                RemoteResult.Success(Unit)
            }
            is RemoteResult.Failure -> res
        }
    }
}

private fun StoreDto.toEntity() = StoreEntity(
    id = id, name = name, address = address, lat = lat, lng = lng, hours = hours,
    type = runCatching { StoreType.valueOf(type) }.getOrDefault(StoreType.TIENDA),
)

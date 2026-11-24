package cl.oyl.frutossecos.data.repository.remote

import cl.oyl.frutossecos.core.di.ApplicationScope
import cl.oyl.frutossecos.data.local.OylDatabase
import cl.oyl.frutossecos.data.local.entity.SessionEntity
import cl.oyl.frutossecos.data.local.entity.UserEntity
import cl.oyl.frutossecos.data.remote.ApiEndpoints
import cl.oyl.frutossecos.data.remote.RemoteErrors
import cl.oyl.frutossecos.data.remote.RemoteResult
import cl.oyl.frutossecos.data.remote.RemoteServiceFactory
import cl.oyl.frutossecos.data.remote.dto.CoreUserDto
import cl.oyl.frutossecos.data.remote.dto.LoginRequestDto
import cl.oyl.frutossecos.data.remote.dto.RegisterRequestDto
import cl.oyl.frutossecos.data.remote.interceptor.TokenHolder
import cl.oyl.frutossecos.data.repository.AuthError
import cl.oyl.frutossecos.data.repository.AuthException
import cl.oyl.frutossecos.data.repository.AuthRepository
import cl.oyl.frutossecos.data.repository.local.LocalAuthRepository
import cl.oyl.frutossecos.domain.logic.RegisterForm
import cl.oyl.frutossecos.domain.model.Role
import cl.oyl.frutossecos.domain.model.Session
import cl.oyl.frutossecos.domain.model.User
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Autenticación contra el backend real (ms-core) con caché local en Room.
 *
 * - Login: valida contra /api/v1/auth/login y guarda en Room el usuario + JWT,
 *   para que la sesión sobreviva reinicios y la app funcione sin red.
 * - Registro: crea el usuario en el backend y deja la sesión iniciada.
 * - Sin conexión: se permite entrar con las credenciales ya sincronizadas en el
 *   teléfono (hash PBKDF2 guardado localmente), manteniendo el modo offline.
 */
@Singleton
class RemoteAuthRepository @Inject constructor(
    private val db: OylDatabase,
    private val remote: RemoteServiceFactory,
    private val tokenHolder: TokenHolder,
    private val local: LocalAuthRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : AuthRepository {
    private val sessionDao = db.sessionDao()
    private val userDao = db.userDao()
    private val syncMutex = Mutex()

    override val session: Flow<Session?> = sessionDao.observe().map { e ->
        tokenHolder.token = e?.token
        e?.let { Session(it.userId, it.role, it.token, it.loggedAt) }
    }

    override suspend fun currentSession(): Session? = sessionDao.get()?.let { s ->
        tokenHolder.token = s.token
        Session(s.userId, s.role, s.token, s.loggedAt)
    }

    override suspend fun login(login: String, password: String, role: Role): User {
        val response = RemoteErrors.call {
            remote.core.login(LoginRequestDto(alias = login.trim(), password = password, role = role.name))
        }
        when (response) {
            is RemoteResult.Success -> {
                val body = response.value
                val dto = body.user ?: CoreUserDto(
                    id = body.userId ?: -1, name = body.name ?: login, alias = login,
                    email = login, phone = "", role = body.role ?: role.name,
                )
                val entity = cacheUser(dto, password)
                tokenHolder.token = body.token
                sessionDao.save(SessionEntity(userId = entity.id, role = entity.role, token = body.token, loggedAt = System.currentTimeMillis()))
                cacheProfileOnServicioUsuarios(entity)
                return User(entity.id, entity.name, entity.alias, entity.email, entity.phone, entity.address, entity.avatarId, entity.avatarUri, entity.role, entity.notificationsEnabled)
            }
            is RemoteResult.Failure -> {
                if (response.error.kind == cl.oyl.frutossecos.data.remote.RemoteErrorKind.NETWORK ||
                    response.error.kind == cl.oyl.frutossecos.data.remote.RemoteErrorKind.TIMEOUT
                ) {
                    // Sin red: validar contra la copia local ya sincronizada.
                    return local.login(login, password, role)
                }
                if (response.error.httpCode == 401 || response.error.httpCode == 400) {
                    throw AuthException(AuthError.INVALID_CREDENTIALS)
                }
                throw AuthException(AuthError.CONNECTION)
            }
        }
    }

    override suspend fun register(form: RegisterForm, avatarId: Int?): User {
        val response = RemoteErrors.call {
            remote.core.register(
                RegisterRequestDto(
                    name = form.name.trim(), alias = form.alias.trim(), email = form.email.trim().lowercase(),
                    password = form.password, phone = form.phone.trim(), address = form.address.trim().ifBlank { null },
                    avatarId = avatarId,
                )
            )
        }
        return when (response) {
            is RemoteResult.Success -> {
                val body = response.value
                val dto = body.user ?: throw AuthException(AuthError.CONNECTION)
                val entity = cacheUser(dto, form.password)
                tokenHolder.token = body.token
                sessionDao.save(SessionEntity(userId = entity.id, role = entity.role, token = body.token, loggedAt = System.currentTimeMillis()))
                cacheProfileOnServicioUsuarios(entity)
                User(entity.id, entity.name, entity.alias, entity.email, entity.phone, entity.address, entity.avatarId, entity.avatarUri, entity.role, entity.notificationsEnabled)
            }
            is RemoteResult.Failure -> {
                val code = response.error.code
                when {
                    code == "ALIAS_TAKEN" -> throw AuthException(AuthError.ALIAS_TAKEN)
                    code == "EMAIL_TAKEN" -> throw AuthException(AuthError.EMAIL_TAKEN)
                    response.error.kind == cl.oyl.frutossecos.data.remote.RemoteErrorKind.NETWORK ||
                        response.error.kind == cl.oyl.frutossecos.data.remote.RemoteErrorKind.TIMEOUT -> {
                        // Sin red solo se permite el alta local si el backend no está disponible.
                        local.register(form, avatarId)
                    }
                    else -> throw AuthException(AuthError.CONNECTION)
                }
            }
        }
    }

    override suspend fun logout() {
        tokenHolder.token = null
        sessionDao.clear()
    }

    /** Crea o actualiza la copia local del usuario y devuelve la entidad persistida. */
    private suspend fun cacheUser(dto: CoreUserDto, plainPassword: String): UserEntity {
        val role = runCatching { Role.valueOf(dto.role) }.getOrDefault(Role.CLIENTE)
        val existing = userDao.getById(dto.id) ?: userDao.findByEmail(dto.email)
        val hasher = cl.oyl.frutossecos.core.util.PasswordHasher.hash(plainPassword)
        val entity = UserEntity(
            id = existing?.id ?: dto.id,
            name = dto.name, alias = dto.alias, email = dto.email.lowercase(),
            phone = dto.phone, address = dto.address, avatarId = dto.avatarId, avatarUri = existing?.avatarUri,
            passwordHash = hasher.hash, salt = hasher.salt, role = role,
            notificationsEnabled = existing?.notificationsEnabled ?: true,
        )
        return if (existing == null) {
            val id = userDao.insert(entity)
            entity.copy(id = id)
        } else {
            userDao.update(entity)
            entity
        }
    }

    /**
     * Mantiene sincronizado el usuario también en ms-usuarios (su propio contrato).
     * Es una llamada best-effort: si falla, la app sigue funcionando.
     */
    private fun cacheProfileOnServicioUsuarios(entity: UserEntity) {
        scope.launch {
            syncMutex.withLock {
                runCatching {
                    val exists = RemoteErrors.call { remote.usuarios.byId(entity.id) }
                    if (exists is RemoteResult.Failure && exists.error.httpCode == 404) {
                        RemoteErrors.call {
                            remote.usuarios.register(
                                cl.oyl.frutossecos.data.remote.dto.UsuariosRegistroRequestDto(
                                    nombre = entity.name, apellido = "-", email = entity.email,
                                    password = "Oyl-" + UUID.randomUUID(), telefono = entity.phone, direccion = entity.address,
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    /** Servicio usado por la pantalla de diagnóstico. */
    val endpoints get() = ApiEndpoints.all
}

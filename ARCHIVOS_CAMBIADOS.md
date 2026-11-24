# Archivos creados y modificados

Resumen de la entrega `oyl_sktt1-transformado-postgresql`. La ruta es relativa a esta
carpeta. Las líneas indican el tamaño final del archivo.

## Documentación

| Archivo | Estado |
|---|---|
| `README.md` | Reescrito: arquitectura, ejecución con Docker, pruebas, Android Studio, teléfono físico, limitaciones |
| `COMPARACION_PROYECTOS.md` | **Nuevo**: comparación original / referencia / esta entrega |
| `ARCHIVOS_CAMBIADOS.md` | **Nuevo**: este archivo |
| `backend/README.md` | Corregido: 4 bases independientes, pruebas con PostgreSQL, sin `init-databases.sql` |
| `backend/INTEGRACION_OYL_SKTT1.md` | Reescrito: estado real (antes decía H2 y bases compartidas) |

## Infraestructura

| Archivo | Estado |
|---|---|
| `docker-compose.yml` | Reescrito: 4 PostgreSQL independientes, healthchecks, orden por salud, builds desde código |
| `.env.example` | **Nuevo**: plantilla de puertos, credenciales y secretos |
| `.gitignore` | Ampliado: `**/target/`, `**/build/`, `**/.gradle/`, `.env`, storage de emulador |
| `backend/Dockerfile.maven` | **Nuevo**: build multi-stage compartido (`ARG MODULE`) para los 5 módulos Maven |
| `backend/.dockerignore` | **Nuevo** |
| `backend/ms-core/Dockerfile` | Reescrito: Gradle wrapper, usuario sin privilegios, caché de dependencias |
| `backend/ms-core/.dockerignore` | **Nuevo** |
| `backend/{eureka-server,api-gateway,ms-usuarios,ms-productos,ms-ventas}/Dockerfile` | **Eliminados** (los reemplaza `Dockerfile.maven`) |
| `backend/init-databases.sql` | **Eliminado** (obsoleto al separar las bases) |

## Backend · ms-core (H2 → PostgreSQL)

| Archivo | Estado |
|---|---|
| `backend/ms-core/build.gradle.kts` | PostgreSQL, Flyway, actuator; fuera H2 y la consola H2; `embedded-postgres` en pruebas |
| `backend/ms-core/src/main/resources/application.yml` | Datasource PostgreSQL, `ddl-auto: validate`, Flyway, endpoints de salud |
| `backend/ms-core/src/main/resources/db/migration/V1__init_core.sql` | **Nuevo**: esquema inicial (9 tablas + índices) |
| `backend/ms-core/src/main/kotlin/cl/oyl/backend/domain/Entities.kt` | `@Column` explícitos, índices nombrados |
| `backend/ms-core/src/main/kotlin/cl/oyl/backend/config/Seeder.kt` | Sembrado idempotente + `OYL_DEMO_SEED` |
| `backend/ms-core/src/main/kotlin/cl/oyl/backend/security/Security.kt` | Fuera `/h2-console`; permisos de `/actuator/health/**` |
| `backend/ms-core/src/test/kotlin/cl/oyl/backend/EmbeddedPostgresConfig.kt` | **Nuevo**: `@PostgresTest` (PostgreSQL real, base nueva por contexto) |
| `backend/ms-core/src/test/kotlin/cl/oyl/backend/ExternalPostgresTest.kt` | **Nuevo**: verificación contra un PostgreSQL externo (`-Doyl.external.pg=true`); ejecutada y aprobada |
| `backend/ms-core/src/test/kotlin/cl/oyl/backend/EmbeddedPostgresSmokeTest.kt` | **Nuevo** |
| `backend/ms-core/src/test/kotlin/cl/oyl/backend/{ApiIntegrationTest,ConcurrencyTest}.kt` | `@PostgresTest` en lugar de `@SpringBootTest` con H2 |
| `backend/ms-core/src/test/resources/application.yml` | Perfil de pruebas sin H2 |

## Backend · servicios Maven

| Archivo | Estado |
|---|---|
| `backend/{ms-usuarios,ms-productos,ms-ventas}/pom.xml` | `embedded-postgres` y `commons-lang3` (solo pruebas) en lugar de H2 |
| `backend/{ms-usuarios,ms-productos,ms-ventas}/src/test/resources/application.yml` | PostgreSQL real, `ddl-auto: validate`, Flyway activo |
| `backend/{ms-usuarios,ms-productos,ms-ventas}/src/test/java/cl/oyl/*/EmbeddedPg.java` | **Nuevo**: PostgreSQL embebido con base nueva por clase |
| `backend/{ms-usuarios,ms-productos,ms-ventas}/src/test/java/cl/oyl/*/Ms*ApplicationTests.java` | Datasource dinámico al PostgreSQL embebido |

## App Android · capa de red (nueva)

| Archivo | Estado |
|---|---|
| `app/src/main/java/cl/oyl/frutossecos/data/remote/ApiEndpoints.kt` | **Nuevo**: URLs de los 4 servicios + gateway |
| `app/src/main/java/cl/oyl/frutossecos/data/remote/dto/Dtos.kt` | **Nuevo**: DTOs de los 4 contratos |
| `app/src/main/java/cl/oyl/frutossecos/data/remote/api/OylApi.kt` | **Nuevo**: `CoreApi`, `UsuariosApi`, `ProductosApi`, `VentasApi`, `HealthApi` |
| `app/src/main/java/cl/oyl/frutossecos/data/remote/RemoteServiceFactory.kt` | **Nuevo**: fábrica única de Retrofit/OkHttp, `RemoteResult`, `RemoteError` |
| `app/src/main/java/cl/oyl/frutossecos/data/remote/BackendDiagnostics.kt` | **Nuevo**: comprobación en paralelo de los 4 servicios |
| `app/src/main/java/cl/oyl/frutossecos/data/repository/remote/RemoteAuthRepository.kt` | **Nuevo**: login/registro contra `ms-core` con caché Room |
| `app/src/main/java/cl/oyl/frutossecos/data/repository/remote/RemoteProductRepository.kt` | **Nuevo**: catálogo + tiendas (`RemoteStoreRepository`) |
| `app/src/main/java/cl/oyl/frutossecos/data/repository/remote/RemoteOrderRepository.kt` | **Nuevo**: pedidos, inventario y notificaciones |
| `app/src/main/java/cl/oyl/frutossecos/core/di/NetworkModule.kt` | **Nuevo**: Json, TokenHolder, AuthInterceptor |
| `app/src/main/java/cl/oyl/frutossecos/core/di/RepositoryModule.kt` | **Nuevo**: elige remoto u local según `BuildConfig` |

## App Android · archivos modificados

| Archivo | Cambio |
|---|---|
| `app/build.gradle.kts` | Host/puertos configurables, `API_BASE_URL` dev y release, `USE_REMOTE_REPOSITORIES` por defecto `true` |
| `app/src/main/java/cl/oyl/frutossecos/OylApplication.kt` | Sincronización inicial con el backend (best-effort) |
| `app/src/main/java/cl/oyl/frutossecos/feature/profile/ProfileFeature.kt` | Tarjeta "Servidor y base de datos" con estado por servicio |
| `app/src/main/java/cl/oyl/frutossecos/feature/auth/AuthScreens.kt` | Mensaje para `AuthError.CONNECTION` |
| `app/src/main/java/cl/oyl/frutossecos/data/repository/Repositories.kt` | Nuevo valor `AuthError.CONNECTION` |
| `app/src/main/java/cl/oyl/frutossecos/data/local/dao/{CategoryDao,StoreDao,ProductDao}.kt` | Consultas de apoyo para la sincronización |
| `app/src/main/res/xml/network_security_config.xml` | Sin HTTP en claro salvo `10.0.2.2`/localhost |
| `app/src/debug/res/xml/network_security_config.xml` | **Nuevo**: HTTP en claro solo en debug (IP LAN) |
| `app/src/main/res/values/strings_screens.xml` | Textos del estado del backend |
| `app/src/main/res/values/strings_features.xml` | `error_no_connection` |
| `app/src/test/java/cl/oyl/frutossecos/data/remote/RemoteServiceFactoryTest.kt` | **Nuevo**: 6 pruebas de Retrofit con `MockWebServer` |

## Sin cambios (se conservan)

- Las 15 pantallas, navegación, reglas de negocio, validaciones, carrito, QR, mapa,
  notificaciones y su suite de pruebas original.
- Contratos HTTP de los tres servicios Maven y del gateway.
- Suite de pruebas Android existente (39 pruebas), que sigue pasando.

# OYL Frutos Secos — copia transformada con PostgreSQL independiente

Esta carpeta es una **copia independiente** del proyecto OYL Frutos Secos. No modifica ni
depende de los repositorios originales (`Oyl-frutos-secos3` ni
`Predeterminado-93/oyl-frutos-secos`); se puede entregar, versionar y ejecutar por sí sola.

## Qué cambió respecto del proyecto original

| Área | Antes | Ahora |
|---|---|---|
| Base de datos de `ms-core` | H2 en memoria (`ddl-auto=create-drop`) | PostgreSQL 16 real, esquema versionado con Flyway |
| Bases de datos | Una sola instancia compartida | **4 instancias independientes**, una por microservicio |
| Persistencia | Se perdía al reiniciar `ms-core` | Persiste en volúmenes Docker (`docker compose down` conserva los datos) |
| Arranque de contenedores | Dependía de que el jar estuviera compilado | `docker compose up --build` compila desde el código fuente |
| Salud de contenedores | Sin comprobaciones reales | `healthcheck` real por servicio + `depends_on: service_healthy` |
| App Android | 100 % local (Room/SQLite) | Backend real vía Retrofit (4 servicios) + Room como caché offline |
| Pruebas de backend | H2 en memoria | PostgreSQL real embebido (Zonky) |
| Secretos | Valores fijos en `docker-compose.yml` | Plantilla `.env.example`; `.env` no se versiona |

## Arquitectura

```
                        ┌───────────────────────┐
   App Android ────────▶│  api-gateway  :8080   │
   (Retrofit)           └───────────┬───────────┘
                                    │
     ┌──────────────┬───────────────┼────────────────┬───────────────┐
     ▼              ▼               ▼                ▼               │
 ms-usuarios    ms-productos     ms-ventas         ms-core      eureka-server
   :8081           :8082          :8083            :8084          :8761
     │              │               │                │
     ▼              ▼               ▼                ▼
 postgres-      postgres-       postgres-        postgres-
 usuarios       productos        ventas            core
  :5433          :5434           :5435            :5436
 (oyl_usuarios) (oyl_productos) (oyl_ventas)     (oyl_core)
```

Cada microservicio tiene **su propio contenedor PostgreSQL, su propio usuario, su
propia contraseña y su propio volumen**. No comparten base ni esquema.

- `ms-core` es el backend integral original (Kotlin + Spring Boot) y ahora usa
  **exclusivamente** la base `oyl_core`. Su esquema lo crea Flyway
  (`db/migration/V1__init_core.sql`) y Hibernate solo valida (`ddl-auto: validate`).
- Los tres servicios Java mantienen sus contratos originales y su propio Flyway
  (`db/migration/V1__init_*.sql`).
- La app habla con los cuatro servicios **a través del gateway** (un solo puerto,
  `8080`), y el gateway enruta `/api/v1/**` a `ms-core` y `/api/*` a cada servicio.

## Requisitos

- Docker Engine o Docker Desktop con Docker Compose v2.
- Para desarrollo sin Docker: JDK 21, Maven 3.9+, Gradle (wrapper incluido) y PostgreSQL 15+.
- Para la app: Android Studio (AGP 9.4.1, Kotlin 2.4.21, `compileSdk 37`, `minSdk 26`).

## Ejecutar el backend

```bash
cp .env.example .env          # 1. ajustar contraseñas y secretos
docker compose up --build     # 2. compilar y levantar todo
```

Servicios publicados en el host:

| Servicio | URL |
|---|---|
| Gateway (punto de entrada) | http://localhost:8080 |
| Eureka | http://localhost:8761 |
| ms-usuarios | http://localhost:8081 |
| ms-productos | http://localhost:8082 |
| ms-ventas | http://localhost:8083 |
| ms-core | http://localhost:8084 |
| PostgreSQL usuarios / productos / ventas / core | 5433 / 5434 / 5435 / 5436 |

Comprobaciones:

```bash
curl http://localhost:8080/api/v1/products          # catálogo (vía gateway)
curl http://localhost:8084/actuator/health          # salud de ms-core
docker compose ps                                   # estados y healthchecks
```

Detener conservando datos y reanudar:

```bash
docker compose down        # los volúmenes se conservan
docker compose up -d       # los datos siguen ahí
```

Borrar todo (incluidos los datos): `docker compose down -v`.

### Solo las bases de datos

```bash
docker compose up -d postgres-usuarios postgres-productos postgres-ventas postgres-core
```

### Usuario de demostración

Con `OYL_DEMO_SEED=true` (por defecto) `ms-core` siembra una sola vez:

- Alias: `admin` · contraseña: la de `OYL_ADMIN_PASSWORD`
- 3 categorías, 3 tiendas y 11 productos

El sembrado es idempotente: en el segundo arranque detecta los datos y no repite nada.
Con `OYL_DEMO_SEED=false` no se siembra nada.

## Ejecutar las pruebas

Backend (Maven, PostgreSQL real embebido, no requiere Docker):

```bash
cd backend
mvn test                       # eureka-server, api-gateway, ms-usuarios, ms-productos, ms-ventas
cd ms-core && ./gradlew test   # ms-core (Gradle)
```

Para probar `ms-core` contra un PostgreSQL **externo** (el mismo que usa Docker):

```bash
cd backend/ms-core
DB_URL=jdbc:postgresql://localhost:5436/oyl_core \
DB_USERNAME=oyl_core DB_PASSWORD=... \
./gradlew test --tests '*ExternalPostgresTest' -Doyl.external.pg=true
```

Sin `-Doyl.external.pg=true`, esa prueba se omite y el resto usa PostgreSQL embebido.

Android (sin emulador, pruebas JVM):

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Resultados de esta entrega (Windows: `gradlew.bat` / `mvn` equivalente):

| Conjunto | Resultado |
|---|---|
| `eureka-server` + `api-gateway` | 2 / 2 |
| `ms-usuarios` | 5 / 5 |
| `ms-productos` | 4 / 4 |
| `ms-ventas` | 3 / 3 |
| `ms-core` (embebido) | 9 / 9 |
| `ms-core` `ExternalPostgresTest` (PostgreSQL externo) | 1 / 1 |
| Android `:app:testDebugUnitTest` | 45 / 45 |
| Android `:app:assembleDebug` | APK generado |

Verificación en vivo (PostgreSQL real, `ms-core` arrancado con `java -jar`):

| Comprobación | Resultado |
|---|---|
| `GET /actuator/health` | `UP` |
| Login `admin` | JWT emitido (204 caracteres) |
| `GET /api/v1/products` | 11 productos |
| `POST /api/v1/orders` | `OYL-000001` creado |
| Stock del producto 1 | 25 → 23 |
| `GET /api/v1/stores` | 3 tiendas |
| **Reinicio** de `ms-core` | pedido `OYL-000001` y stock 23 se conservan; sembrado omitido |
| `current_database()` | `oyl_core` (solo sus 10 tablas) |

## Conectar la app Android

La URL del backend no está fija en el código: se configura en `local.properties`
(que no se versiona) o en `gradle.properties`.

**Emulador de Android Studio (por defecto):** no hay que hacer nada. La app usa
`http://10.0.2.2:8080`, que es el `localhost` del computador visto desde el emulador.

**Teléfono físico en la misma red WiFi:**

```properties
# local.properties
oylDevHost=192.168.1.50      # IP LAN del computador con Docker
```

y en el computador:

```bash
docker compose up --build
hostname -I                   # confirmar la IP
```

La variante *debug* permite HTTP en claro hacia cualquier IP (el teléfono cambia de red);
la variante *release* usa HTTPS y solo permite HTTP hacia `10.0.2.2`/`localhost`.

Puertos alternativos (si hay conflictos): `oylGatewayPort`, `oylUsuariosPort`,
`oylProductosPort`, `oylVentasPort`, `oylCorePort`.

### Cómo funciona el modo remoto

- `USE_REMOTE_REPOSITORIES=true` (por defecto) usa el backend con **Room como caché
  offline**: si el servidor no responde, las pantallas siguen mostrando lo último
  sincronizado y los pedidos sin red se registran localmente.
- Al arrancar, la app sincroniza catálogo, tiendas y (si hay sesión) pedidos y avisos.
- `USE_REMOTE_REPOSITORIES=false` vuelve al modo 100 % local original (`oylUseRemote=false`).
- En **Perfil → Servidor y base de datos** se puede comprobar, uno por uno, si los
  cuatro servicios responden y cuánto tardan.

## Abrir la app en Android Studio

1. *File → Open* y seleccionar la carpeta **raíz** del proyecto (la que contiene
   `settings.gradle.kts`), no `app/`.
2. Esperar la sincronización de Gradle (descarga dependencias la primera vez).
3. Crear o elegir un emulador y ejecutar la configuración `app`.
4. Con el backend levantado, iniciar sesión con `admin` y la contraseña configurada.

## Variables de entorno

Todas están documentadas en [`.env.example`](.env.example). Las importantes:

| Variable | Uso |
|---|---|
| `PORT_GATEWAY`, `PORT_MS_*`, `PORT_DB_*` | Puertos publicados en el host |
| `DB_USUARIOS_*`, `DB_PRODUCTOS_*`, `DB_VENTAS_*`, `DB_CORE_*` | Nombre, usuario, contraseña y URL JDBC de cada base |
| `OYL_JWT_SECRET` | Secreto HMAC de `ms-core` (32 caracteres o más) |
| `OYL_ADMIN_PASSWORD` | Contraseña del administrador de demostración |
| `OYL_DEMO_SEED` | Siembra inicial de datos (true/false) |

`.env` está en `.gitignore`: **nunca** se versiona. No hay contraseñas reales en el
repositorio; el compose usa valores por defecto solo para desarrollo local.

## Estructura

```
.
├── docker-compose.yml              # 4 PostgreSQL + Eureka + gateway + 4 microservicios
├── .env.example                    # plantilla de variables (copiar a .env)
├── backend/
│   ├── Dockerfile.maven            # build multi-stage compartido (Maven)
│   ├── pom.xml                     # reactor Maven
│   ├── eureka-server/              # descubrimiento
│   ├── api-gateway/                # punto de entrada, enruta a los 4 servicios
│   ├── ms-usuarios/                # PostgreSQL propio + Flyway
│   ├── ms-productos/               # PostgreSQL propio + Flyway
│   ├── ms-ventas/                  # PostgreSQL propio + Flyway
│   └── ms-core/                    # Kotlin+Spring Boot, migrado de H2 a PostgreSQL
│       ├── Dockerfile
│       └── src/main/resources/db/migration/V1__init_core.sql
└── app/                            # app Android (Compose + Hilt + Room + Retrofit)
    └── src/main/java/cl/oyl/frutossecos/
        ├── data/remote/            # Retrofit de los 4 servicios, errores, diagnóstico
        ├── data/repository/remote/ # repositorios híbridos (backend + caché Room)
        └── core/di/                # Hilt: NetworkModule, RepositoryModule
```

## Limitaciones conocidas

- **Docker no se pudo ejecutar en el entorno de construcción**: el `docker-compose.yml`
  se validó sintácticamente (parseo YAML y verificación de servicios, dependencias,
  healthchecks y volúmenes) y cada servicio se probó por separado contra PostgreSQL real,
  pero el `docker compose up` completo no se ejecutó aquí. Ver `COMPARACION_PROYECTOS.md`.
- `ms-core` sigue siendo el backend integral: no se dividió en microdominios. Es
  intencional, para no perder endpoints ni reglas de negocio; los tres servicios Java
  se mantienen con sus contratos independientes.
- La app conserva las 15 pantallas y el carrito local; el carrito es un borrador por
  usuario y no se sincroniza con el servidor.
- Los pedidos creados sin conexión quedan en el teléfono y no se reenvían
  automáticamente al servidor (no se inventó un endpoint de reintento que el backend
  no expone).
- No se configuró firma de release ni FCM: requieren un keystore y un
  `google-services.json` reales, que no se versionan.
- El *workspace* de Freecode se suspende por inactividad: los contenedores Docker y la
  app deben ejecutarse en la máquina del usuario, no aquí.

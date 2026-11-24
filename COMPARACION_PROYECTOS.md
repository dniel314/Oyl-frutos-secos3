# Comparación de proyectos

Este documento compara, con hechos verificables en el código, tres entregas del
proyecto OYL Frutos Secos:

| # | Proyecto | Ubicación de referencia |
|---|---|---|
| A | Proyecto original del curso (`Oyl-frutos-secos3`) | repositorio base de la app Android + backend Kotlin |
| B | Repositorio de referencia (`Predeterminado-93/oyl-frutos-secos`) | `github.com/Predeterminado-93/oyl-frutos-secos` |
| C | **Este proyecto**: `oyl_sktt1-transformado-postgresql` | copia independiente en esta entrega |

> Los puntos de A y B se describieron revisando su código y su `docker-compose.yml`,
> no solo su documentación.

---

## 1. Resumen ejecutivo

| Criterio | A · Original | B · Referencia | **C · Esta entrega** |
|---|---|---|---|
| Bases de datos | 1 instancia PostgreSQL | **1** instancia con 4 bases creadas por script | **4 instancias independientes**, una por servicio |
| Contenedor por base | No | No (`postgres` único) | **Sí**: `postgres-usuarios/productos/ventas/core` |
| Usuario y contraseña por servicio | Compartidos (`postgres`/`postgres`) | Compartidos (`postgres`/`postgres`) | **Propios por contenedor** |
| Volumen por base | 1 (`postgres_data`) | 1 (`postgres_data`) | **4 + 1** (`pg_*_data`, `oyl_uploads`) |
| `ms-core` | H2 en memoria | **No existe** | PostgreSQL + Flyway |
| Persistencia de `ms-core` al reiniciar | ❌ se pierde | — | ✅ conservada |
| Healthchecks reales | Parciales | Parciales | ✅ en los 10 servicios |
| Arranque ordenado por salud | ❌ | ❌ | ✅ `depends_on: service_healthy` |
| Compila en `docker compose up --build` | Requiere jar previo | Requiere jar previo | ✅ build multi-stage desde el código |
| App Android con backend | ❌ solo local | ✅ Retrofit (Gson) | ✅ Retrofit (kotlinx) + caché Room |
| Cobertura de los 4 servicios en la app | — | 3 (sin `ms-core`) | **4** (gateway + diagnóstico directo) |
| Manejo de errores de red/HTTP | — | Básico | ✅ clases tipadas + mensaje al usuario |
| Funciona sin conexión | ✅ (todo local) | ❌ (depende del backend) | ✅ (caché Room + cola local) |
| Pruebas de backend | H2 | H2 | **PostgreSQL real embebido** |
| Pruebas de la app Android | Suite local completa | 1 prueba de ejemplo (`2 + 2`) | Suite original + pruebas de red |
| Secretos en el repositorio | Claves demo fijas | `postgres`/`postgres` fijos | Plantilla `.env.example` + `.env` ignorado |

---

## 2. Bases de datos

### A · Proyecto original

`docker-compose.yml` define **un** servicio `postgres`, con un único volumen
`postgres_data` y un usuario compartido. Las bases `oyl_usuarios`, `oyl_productos`,
`oyl_ventas` viven en esa misma instancia.

### B · Repositorio de referencia

Mismo esquema que A: **un** contenedor `postgres`, un volumen `postgres_data`, y un
script `backend/init-databases.sql` que crea cuatro bases dentro de esa instancia:

```
CREATE DATABASE oyl_usuarios WITH OWNER = postgres ...
CREATE DATABASE oyl_productos WITH OWNER = postgres ...
CREATE DATABASE oyl_ventas  WITH OWNER = postgres ...
CREATE DATABASE oyl_core    WITH OWNER = postgres ...
```

Funciona, pero las cuatro bases comparten proceso, credenciales y volumen: una caída
o un borrado del volumen afecta a todo el sistema, y no hay aislamiento entre dominios.

### C · Esta entrega

Cuatro contenedores distintos, cada uno con su base, su usuario, su contraseña y su
volumen:

| Contenedor | Base | Puerto host | Volumen |
|---|---|---|---|
| `postgres-usuarios` | `oyl_usuarios` | 5433 | `pg_usuarios_data` |
| `postgres-productos` | `oyl_productos` | 5434 | `pg_productos_data` |
| `postgres-ventas` | `oyl_ventas` | 5435 | `pg_ventas_data` |
| `postgres-core` | `oyl_core` | 5436 | `pg_core_data` |

Cada microservicio se conecta por DNS interno (`postgres-usuarios:5432`, etc.) y
espera el `healthcheck` de **su** base (`pg_isready -U $POSTGRES_USER -d $POSTGRES_DB`).
Por tanto:

- perder o restaurar la base de ventas no toca usuarios ni productos;
- las credenciales son independientes (una filtración no abre las otras tres);
- se puede respaldar o escalar una base sin las demás.

---

## 3. `ms-core`: de H2 a PostgreSQL

| Aspecto | A · Original | C · Esta entrega |
|---|---|---|
| Motor | H2 en memoria | PostgreSQL 16 |
| Esquema | `ddl-auto: create-drop` (lo crea Hibernate) | Flyway `V1__init_core.sql` + `ddl-auto: validate` |
| Datos al reiniciar | Se pierden | Persisten |
| Consola H2 | Habilitada (`/h2-console`) | Eliminada (no aplica) |
| Sembrado demo | En cada arranque | Una sola vez (idempotente) y desactivable con `OYL_DEMO_SEED=false` |
| Nombres de columnas | Implícitos por Hibernate | Explícitos en `@Column` (`avatar_id`, `weight_grams`, …) |
| Índices | Implícitos | Nombrados (`idx_orders_user_id`, `idx_notification_user_id`) |
| Pruebas | H2 | PostgreSQL real embebido (Zonky 16) |

Hibernate ahora **valida** el esquema contra Flyway: si una entidad y el script se
separan, el arranque falla en vez de corromper datos. La tabla `users` se creó sin
comillas para que coincida con el nombre que espera Hibernate.

---

## 4. Contenedores y arranque

| Aspecto | A · Original | B · Referencia | C · Esta entrega |
|---|---|---|---|
| `docker compose up --build` funciona sin compilar antes | ❌ (`COPY target/*.jar`) | ❌ | ✅ build multi-stage dentro de Docker |
| Caché de dependencias en el build | — | — | ✅ poms primero, `go-offline` |
| Usuario sin privilegios en el contenedor | ❌ (root) | ❌ | ✅ `USER oyl` |
| Healthchecks | Algunos | Algunos | Todos los servicios, con `start_period` |
| Orden de arranque | Inicio en paralelo | Inicio en paralelo | Gateway espera a los 4 servicios |
| `down` conserva los datos | ✅ | ✅ | ✅ (y cada base por separado) |
| `.dockerignore` | ❌ | ❌ | ✅ (no copia `target/`, `build/`, `.git`) |

Se sustituyeron cinco `Dockerfile` casi idénticos por **un** `backend/Dockerfile.maven`
con `ARG MODULE`, que el compose reutiliza cinco veces; `ms-core` conserva su Dockerfile
basado en Gradle.

---

## 5. App Android

| Aspecto | A · Original | B · Referencia | C · Esta entrega |
|---|---|---|---|
| Paquete | `cl.oyl.frutossecos` | `com.OYL.frutossecos` | `cl.oyl.frutossecos` (se conserva el original) |
| Pantallas | 15 | Subconjunto | **15, sin cambios** |
| Persistencia local | Room + SQLite | No | Room + SQLite (como caché) |
| Modo por defecto | Local | Remoto | **Remoto con caché offline** |
| Cliente HTTP | — | Retrofit + Gson | Retrofit + kotlinx.serialization |
| Servicios cubiertos | — | 3 | **4** (incluye `ms-core`: pedidos, inventario, tiendas, avisos) |
| URL configurable | — | Constante en código | `local.properties` / `gradle.properties` |
| Emulador vs teléfono | — | Solo `10.0.2.2` | `10.0.2.2` por defecto, IP LAN configurable |
| HTTP en claro | — | En todos los builds | Solo debug; release exige HTTPS |
| Errores de red | — | Genéricos | Tipados: sin red, timeout, 401, 404, 409, 5xx, JSON inválido |
| Comportamiento sin backend | — | Pantallas vacías | Datos locales + aviso al usuario |
| Diagnóstico de servicios | — | No | Pantalla en Perfil con latencia por servicio |
| Pruebas | Suite completa (navegación, reglas, DAO, UI) | 1 prueba de ejemplo | Suite original + pruebas de la capa de red |

Decisiones de diseño:

1. **Room sigue siendo la fuente que observan las pantallas.** Los repositorios remotos
   escriben en Room como caché; por eso ningún `ViewModel` ni `Composable` tuvo que
   cambiar para conectar el backend. Esto reduce el riesgo de romper funcionalidad.
2. **Las llamadas van por el gateway (8080)** porque es el único punto de entrada
   publicado y ya enruta `/api/v1/**` a `ms-core` y `/api/*` a cada servicio. Las URLs
   directas (8081–8084) se usan solo en el diagnóstico, para localizar qué servicio falla.
3. **El carrito permanece local**: es un borrador por usuario, y el backend no expone un
   carrito persistente (no se inventó uno).
4. **No se copió el código de B**: se adoptó su idea (interfaces por servicio, DTOs),
   pero reescrita en el paquete y la arquitectura de A, con kotlinx.serialization.

---

## 6. Pruebas

Resultados reales de esta entrega (detalle completo en [README.md](README.md#ejecutar-las-pruebas)):

| Conjunto | Motor de base de datos | Resultado |
|---|---|---|
| `ms-core` (`ApiIntegrationTest`, `ConcurrencyTest`, `EmbeddedPostgresSmokeTest`) | PostgreSQL 16 embebido | **9 / 9** |
| `ms-core` `ExternalPostgresTest` (contra un PostgreSQL externo real) | PostgreSQL 14 en `oyl_core` | **1 / 1** |
| `eureka-server`, `api-gateway` | — | **2 / 2** |
| `ms-usuarios` (1 unitaria + contexto) | PostgreSQL real embebido | **5 / 5** |
| `ms-productos` (3 unitarias + contexto) | PostgreSQL real embebido | **4 / 4** |
| `ms-ventas` (2 unitarias + contexto) | PostgreSQL real embebido | **3 / 3** |
| `RemoteServiceFactoryTest` (Retrofit con `MockWebServer`) | — | **6 / 6** |
| Pruebas Android (`:app:testDebugUnitTest`, incluida la suite original) | Room (JVM) | **45 / 45** |
| Compilación Android (`:app:assembleDebug`) | — | APK generado (50 MB) |

En A y B estas pruebas corrían sobre H2, que acepta SQL más permisivo y no reproduce
el comportamiento de PostgreSQL (tipos, restricciones, bloqueos). La prueba de
concurrencia de `ms-core` (dos pedidos por la última unidad) solo es significativa
sobre un motor real.

---

## 7. Qué se conservó y qué se descartó

**Conservado de A**
- Las 15 pantallas, navegación, reglas de negocio, validaciones, carrito, QR, mapa,
  notificaciones locales y suite de pruebas.
- `ms-core` completo (endpoints, reglas de stock, JWT, concurrencia).
- Estructura del reactor Maven del backend.

**Adoptado de B**
- Idea de interfaces Retrofit por servicio y DTOs separados.
- URLs centralizadas en un único lugar.
- Configuración de `10.0.2.2` para el emulador.

**Descartado de B**
- Paquete `com.OYL.frutossecos` (rompería la app y sus pruebas).
- Gson (se mantiene kotlinx.serialization, ya integrado).
- Su app sin pruebas.

**No implementado (deliberadamente)**
- División de `ms-core` en microdominios: implicaría reescribir reglas de negocio
  probadas y no lo pidió el alcance.
- Endpoint de sincronización de pedidos offline: el backend no lo expone.

---

## 8. Limitaciones honestas de esta entrega

1. **`docker compose up` no se ejecutó en el entorno de construcción** (no hay Docker
   Engine disponible). Sí se validó el YAML (servicios, dependencias, healthchecks,
   volúmenes) y se ejecutó cada servicio por separado contra PostgreSQL real.
2. El proyecto está pensado para la máquina del usuario; el workspace remoto se suspende
   por inactividad.
3. La firma de release y FCM quedan sin configurar (requieren keystore y
   `google-services.json` reales).
4. Los pedidos creados sin red no se reenvían automáticamente al servidor.

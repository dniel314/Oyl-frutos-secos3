# OYL Frutos Secos — Repo 1 transformado con arquitectura de referencia

Esta entrega combina la aplicación Android completa del Repo 1 con la infraestructura de microservicios del Repo 2.

## Funcionalidades preservadas

La carpeta `app/` se conserva desde el Repo 1. Android continúa funcionando con Room/SQLite local (`USE_REMOTE_REPOSITORIES=false`): autenticación, catálogo, carrito, pedidos, inventario, QR, tiendas/mapa, perfil y notificaciones locales.

## Backend incorporado

- `backend/eureka-server`: registro y descubrimiento.
- `backend/api-gateway`: punto de entrada HTTP en el puerto 8080.
- `backend/ms-usuarios`: servicio de usuarios de referencia.
- `backend/ms-productos`: servicio de catálogo/categorías de referencia.
- `backend/ms-ventas`: servicio de ventas de referencia.
- PostgreSQL 16 con bases separadas para los microservicios de referencia.
- `backend/ms-core`: backend Kotlin original del Repo 1, preservado como API integral para mantener las capacidades existentes.

El Gateway conserva las rutas del Repo 2 (`/api/usuarios/**`, `/api/productos/**`, `/api/categorias/**`, `/api/ventas/**`, `/api/pedidos/**`) y agrega `/api/v1/**` y `/uploads/**` hacia `ms-core:8084`.

## Alcance y limitaciones transparentes

Esta entrega es una migración híbrida y compatible, no una extracción completa de todos los dominios del backend Kotlin a los tres microservicios Java. El backend original permanece como `ms-core` para no perder endpoints ni reglas de negocio. Los microservicios del Repo 2 se conservan como servicios independientes.

Android permanece en modo local. No se activó el modo remoto porque el Repo 1 no contiene todavía las implementaciones `Remote*` completas y los contratos de ambos backends no son idénticos. Activar ese flag sin implementarlas rompería la app.

## Ejecutar backend

Requisitos: Docker Desktop o Docker Engine + Docker Compose.

1. Copia `.env.example` a `.env` en la raíz del proyecto y cambia los secretos. Cambia los secretos por defecto.
2. Desde la raíz:

   ```bash
   docker compose up --build
   ```

3. Servicios:
   - Gateway: http://localhost:8080
   - Eureka: http://localhost:8761
   - Usuarios: http://localhost:8081
   - Productos: http://localhost:8082
   - Ventas: http://localhost:8083
   - API integral Repo 1: http://localhost:8084 (también vía Gateway bajo `/api/v1`)
   - PostgreSQL: localhost:5432

## Compilar Android

Abrir el proyecto en Android Studio:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

La app sigue funcionando localmente sin requerir backend.

## Próximos pasos para una migración distribuida completa

1. Unificar contratos API y DTO.
2. Extraer progresivamente autenticación, catálogo, ventas, inventario, notificaciones, tiendas y perfil desde `ms-core`.
3. Migrar los datos H2 a PostgreSQL con Flyway, conservando transacciones y reglas de stock.
4. Implementar los repositorios remotos Android y sincronización de movimientos pendientes.
5. Añadir pruebas de contrato y end-to-end.
6. Ejecutar pruebas completas con Docker y Android SDK.

No activar `USE_REMOTE_REPOSITORIES` hasta completar esos pasos.

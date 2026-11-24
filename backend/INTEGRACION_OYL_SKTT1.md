# Integración técnica Repo 1 + Repo 2

## Decisión de compatibilidad

El backend original del Repo 1 contiene más capacidades que los servicios del Repo 2. Para preservar comportamiento, se conserva en `backend/ms-core`. La infraestructura del Repo 2 opera como capa distribuida adicional.

## Rutas del Gateway

| Ruta | Destino |
|---|---|
| `/api/usuarios/**` | `ms-usuarios:8081` |
| `/api/productos/**`, `/api/categorias/**` | `ms-productos:8082` |
| `/api/ventas/**`, `/api/pedidos/**` | `ms-ventas:8083` |
| `/api/v1/**`, `/uploads/**` | `ms-core:8084` |

## Persistencia

- Los tres microservicios de referencia usan PostgreSQL y bases lógicas separadas.
- `ms-core` conserva H2 en memoria, como en el Repo 1.
- No se inventó sincronización de datos entre los distintos almacenes.
- Android sigue usando Room local.

## Seguridad

Cambiar los secretos de JWT, la contraseña del administrador demo y las credenciales de PostgreSQL antes de desplegar. H2 en memoria reinicia sus datos cuando se reinicia `ms-core`. Revisar CORS y configurar orígenes explícitos antes de producción.

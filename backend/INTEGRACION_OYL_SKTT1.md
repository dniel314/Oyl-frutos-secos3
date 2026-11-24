# Integración técnica — estado final

> Documento actualizado. La versión anterior describía `ms-core` sobre H2 y bases
> lógicas compartidas; eso **ya no es así**. Este archivo refleja el estado real
> del código de esta entrega.

## Decisión de compatibilidad

El backend original contiene más capacidades que los tres servicios Java (pedidos,
inventario, tiendas, avisos, QR, concurrencia de stock). Para no perder ninguna regla
de negocio, se conserva íntegro como `backend/ms-core`, ahora sobre PostgreSQL.

## Rutas del Gateway (`api-gateway`, puerto 8080)

| Ruta | Destino |
|---|---|
| `/api/usuarios/**` | `ms-usuarios:8081` |
| `/api/productos/**`, `/api/categorias/**` | `ms-productos:8082` |
| `/api/ventas/**`, `/api/pedidos/**` | `ms-ventas:8083` |
| `/api/v1/**`, `/uploads/**` | `ms-core:8084` |

La app Android consume los cuatro servicios **a través del gateway**; las URLs directas
(8081–8084) se usan solo en la pantalla de diagnóstico del Perfil.

## Persistencia (cambio principal)

| Servicio | Base | Contenedor | Volumen |
|---|---|---|---|
| `ms-usuarios` | `oyl_usuarios` | `postgres-usuarios` | `pg_usuarios_data` |
| `ms-productos` | `oyl_productos` | `postgres-productos` | `pg_productos_data` |
| `ms-ventas` | `oyl_ventas` | `postgres-ventas` | `pg_ventas_data` |
| `ms-core` | `oyl_core` | `postgres-core` | `pg_core_data` |

- **Cuatro instancias PostgreSQL independientes**, cada una con su usuario, contraseña y
  volumen. No hay bases compartidas ni `init-databases.sql` central.
- Esquema administrado por **Flyway** en los cuatro servicios
  (`db/migration/V1__init_*.sql`), con Hibernate en `ddl-auto: validate`.
- `ms-core` ya **no usa H2**: se eliminaron la dependencia, la consola `/h2-console` y el
  perfil de pruebas con H2. Los datos persisten entre reinicios.
- No se inventó sincronización entre las cuatro bases: cada servicio es dueño de sus datos.

## Pruebas

- Backend Maven: PostgreSQL real embebido por prueba (Zonky) en lugar de H2.
- `ms-core`: PostgreSQL embebido compartido con una base nueva por contexto de Spring.
- Android: pruebas JVM de la capa Retrofit contra `MockWebServer`.

Comandos y resultados en el [README](../README.md#ejecutar-las-pruebas).

## Seguridad

- Cambiar `OYL_JWT_SECRET` (32+ caracteres), `OYL_ADMIN_PASSWORD` y **todas** las
  contraseñas `DB_*` antes de desplegar. Partir de `.env.example` y no versionar `.env`.
- La app admite HTTP en claro **solo en builds debug**; release exige HTTPS.
- Los contenedores corren con usuario sin privilegios.
- Revisar CORS del gateway y restringir orígenes antes de producción.

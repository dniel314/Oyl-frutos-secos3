package cl.oyl.backend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * Verificación opcional contra un PostgreSQL EXTERNO (el mismo motor y la misma
 * configuración que usa Docker). Se habilita con:
 *
 *     DB_URL=jdbc:postgresql://localhost:5436/oyl_core \
 *     DB_USERNAME=postgres DB_PASSWORD=... \
 *     ./gradlew test --tests '*ExternalPostgresTest' -Doyl.external.pg=true
 *
 * No usa el PostgreSQL embebido: apunta a la base indicada por DB_URL y comprueba
 * que ms-core usa EXCLUSIVAMENTE su propia base y que Flyway aplicó el esquema.
 * Con el flag apagado, la prueba se omite.
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "oyl.external.pg", matches = "true")
class ExternalPostgresTest(@Autowired private val jdbc: JdbcTemplate) {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") {
                System.getenv("DB_URL")?.takeIf { it.isNotBlank() }
                    ?: "jdbc:postgresql://localhost:5436/oyl_core"
            }
            registry.add("spring.datasource.username") { System.getenv("DB_USERNAME") ?: "postgres" }
            registry.add("spring.datasource.password") { System.getenv("DB_PASSWORD") ?: "postgres" }
        }
    }

    @Test
    fun `ms-core conecta a su propia base y tiene el esquema de flyway`() {
        val db: String = jdbc.queryForObject("select current_database()", String::class.java) ?: ""
        assertEquals("oyl_core", db, "ms-core debe usar exclusivamente la base oyl_core")

        val tables: List<String> = jdbc.queryForList(
            "select table_name from information_schema.tables where table_schema = 'public'",
            String::class.java,
        ).filterNotNull()

        listOf(
            "users", "category", "product", "orders", "order_item",
            "stock_movement", "store", "notification", "device_token",
        ).forEach { tabla -> assertTrue(tabla in tables, "Falta la tabla $tabla") }

        val scripts: List<String> = jdbc.queryForList(
            "select version from flyway_schema_history where success",
            String::class.java,
        ).filterNotNull()
        assertTrue(scripts.contains("1"), "Flyway debe haber aplicado V1__init_core.sql")

        // No debe existir ninguna tabla de los otros microservicios.
        val ajenas = tables.filter {
            it.startsWith("usuario") || it.startsWith("categoria") || it.startsWith("venta")
        }
        assertTrue(ajenas.isEmpty(), "ms-core no debe tener tablas de otros servicios: $ajenas")
    }
}

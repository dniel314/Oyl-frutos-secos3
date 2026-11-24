package cl.oyl.backend

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertTrue

/** Comprueba que el PostgreSQL embebido de las pruebas se inicia y responde SQL. */
class EmbeddedPostgresSmokeTest {
    @Test
    fun `postgres embebido arranca y ejecuta SQL`() {
        val pg = EmbeddedPostgres.builder().start()
        try {
            pg.postgresDatabase.connection.use { c ->
                c.createStatement().use { st ->
                    st.executeQuery("select current_database(), version()").use { rs ->
                        rs.next()
                        val db = rs.getString(1)
                        val version = rs.getString(2)
                        println("Pruebas sobre $db · $version")
                        assertTrue(db.isNotBlank())
                        assertTrue(version.contains("PostgreSQL"))
                    }
                }
            }
        } finally {
            pg.close()
        }
    }

    @Test
    fun `los contadores estaticos funcionan`() {
        val n = AtomicInteger()
        assertTrue(n.incrementAndGet() == 1)
    }
}

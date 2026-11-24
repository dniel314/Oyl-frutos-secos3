package cl.oyl.backend

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.test.context.ContextConfiguration

/**
 * PostgreSQL real y efímero para las pruebas (antes se usaba H2 en memoria).
 *
 * - Una sola instancia de PostgreSQL embebido por JVM (compartida y cerrada al terminar).
 * - Cada contexto de Spring (`@DirtiesContext` crea varios) usa su propia base de datos,
 *   por lo que Flyway crea el esquema desde cero en cada contexto, igual que en producción.
 */
object EmbeddedPostgresSupport {
    private val databases = AtomicInteger()

    val instance: EmbeddedPostgres by lazy {
        val pg = EmbeddedPostgres.builder().start()
        Runtime.getRuntime().addShutdownHook(Thread { runCatching { pg.close() } })
        pg
    }

    private val url: String by lazy {
        "jdbc:postgresql://localhost:${instance.port}/postgres"
    }

    /** Crea una base vacía nueva y devuelve su URL JDBC. */
    fun freshDatabase(): String {
        val name = "oyl_test_${databases.incrementAndGet()}_${instance.port}"
        instance.postgresDatabase.connection.use { c ->
            c.createStatement().use { st -> st.execute("CREATE DATABASE \"$name\"") }
        }
        return "jdbc:postgresql://localhost:${instance.port}/$name"
    }

    fun initialUrl(): String = url
}

@TestConfiguration(proxyBeanMethods = false)
class EmbeddedPostgresConfig {
    @Bean(destroyMethod = "") @Order(Ordered.HIGHEST_PRECEDENCE)
    fun embeddedPostgresMarker(): Any = EmbeddedPostgresSupport.instance
}

/** Apunta spring.datasource.url a una base nueva antes de crear cada contexto. */
class EmbeddedPostgresInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(context: ConfigurableApplicationContext) {
        context.environment.systemProperties["spring.datasource.url"] = EmbeddedPostgresSupport.freshDatabase()
        context.environment.systemProperties["spring.datasource.username"] = "postgres"
        context.environment.systemProperties["spring.datasource.password"] = "postgres"
    }
}

/**
 * Anotación compuesta: contexto Spring con PostgreSQL embebido aislado por prueba.
 * Sustituye al H2 anterior sin cambiar el comportamiento validado.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@SpringBootTest
@ContextConfiguration(initializers = [EmbeddedPostgresInitializer::class])
annotation class PostgresTest

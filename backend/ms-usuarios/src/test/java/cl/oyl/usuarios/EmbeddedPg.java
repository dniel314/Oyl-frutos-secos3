package cl.oyl.usuarios;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PostgreSQL 16 real y efímero para las pruebas (sustituye al H2 anterior).
 * Una instancia por JVM y una base de datos nueva por clase de prueba.
 */
final class EmbeddedPg {

    private static EmbeddedPostgres instance;
    private static final AtomicInteger SEQ = new AtomicInteger();

    private EmbeddedPg() {}

    static synchronized String newDatabase() {
        try {
            if (instance == null) {
                instance = EmbeddedPostgres.builder().start();
                final EmbeddedPostgres pg = instance;
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try { pg.close(); } catch (Exception ignored) { }
                }));
            }
            String name = "oyl_usuarios_test_" + SEQ.incrementAndGet();
            try (Connection c = instance.getPostgresDatabase().getConnection();
                 Statement s = c.createStatement()) {
                s.execute("CREATE DATABASE " + name);
            }
            return "jdbc:postgresql://localhost:" + instance.getPort() + "/" + name;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo iniciar PostgreSQL embebido para pruebas", e);
        }
    }
}

package cl.oyl.productos;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
class MsProductosApplicationTests {

    /** Contexto sobre PostgreSQL real con el esquema creado por Flyway. */
    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        String url = EmbeddedPg.newDatabase();
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @Test
    void contextLoads() {
    }
}

plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.spring") version "2.4.21"
    kotlin("plugin.jpa") version "2.4.21"
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "cl.oyl"
version = "0.1.0"

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

repositories { mavenCentral() }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Persistencia: PostgreSQL real (antes H2 en memoria) + esquema versionado con Flyway.
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    // En pruebas se usa PostgreSQL 16 embebido en procesos (sin Docker, sin H2).
    testImplementation("io.zonky.test:embedded-postgres:2.2.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin { compilerOptions { freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property") } }

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

// bootJar genera además un "-plain.jar" que rompería el COPY del Dockerfile
// (copiaría dos jars a la misma ruta). El artefacto ejecutable basta.
tasks.named<Jar>("jar") {
    enabled = false
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("oyl.test.embedded-postgres", "true")
    // Permite ejecutar ExternalPostgresTest contra un PostgreSQL real:
    //   DB_URL=... DB_USERNAME=... DB_PASSWORD=... ./gradlew test -Doyl.external.pg=true
    // Sin el flag, esa prueba se omite y el resto usa PostgreSQL embebido.
    if (System.getProperty("oyl.external.pg") != null) {
        systemProperty("oyl.external.pg", System.getProperty("oyl.external.pg"))
        environment("DB_URL", System.getenv("DB_URL") ?: "")
        environment("DB_USERNAME", System.getenv("DB_USERNAME") ?: "")
        environment("DB_PASSWORD", System.getenv("DB_PASSWORD") ?: "")
    }
}

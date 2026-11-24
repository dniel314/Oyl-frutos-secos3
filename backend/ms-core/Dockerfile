# =====================================================================
# ms-core: backend integral (Kotlin + Spring Boot 3) sobre PostgreSQL.
# Build multi-stage con Gradle Wrapper: no requiere compilar antes.
# =====================================================================
FROM gradle:9.8.2-jdk21 AS build
WORKDIR /workspace

# 1) Cachear dependencias copiando primero los archivos de build.
COPY gradle ./gradle
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# 2) Código y jar ejecutable.
COPY src ./src
RUN ./gradlew --no-daemon bootJar

# 3) Imagen final mínima, sin privilegios.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar /app/app.jar

RUN addgroup -S oyl && adduser -S oyl -G oyl \
    && mkdir -p /app/uploads && chown -R oyl:oyl /app
USER oyl

ENV SERVER_PORT=8084 \
    OYL_UPLOADS_DIR=/app/uploads \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8084
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

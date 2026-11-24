FROM gradle:9.8.2-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
ENV SERVER_PORT=8084
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

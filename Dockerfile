# Build stage
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
# Warm the dependency cache before copying sources for better layer reuse.
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY src ./src
RUN ./gradlew --no-daemon bootJar

# Runtime stage
FROM eclipse-temurin:21-jre
RUN useradd --create-home --uid 10001 app
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
USER app
EXPOSE 8080

# MaxRAMPercentage because the JVM otherwise takes its default 25% of the
# container limit: on a 512 MB instance that is a 128 MB heap, which Boot +
# Hibernate + springdoc exhaust. The port comes from $PORT via application.yml.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]

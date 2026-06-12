# Build stage: use the repo's own Gradle wrapper for reproducibility
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
# warm the dependency cache in its own layer; tolerate failure (some deps resolve only with sources)
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY src/ src/
# bootJar does not run tests (tests need Docker-in-Docker for Testcontainers)
RUN ./gradlew --no-daemon bootJar

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/build/libs/app.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

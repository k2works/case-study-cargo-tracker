# Build stage
FROM eclipse-temurin:25-jdk AS build

WORKDIR /app

# Copy gradle wrapper and build files
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Download dependencies
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# Copy source code
COPY src/ src/
COPY config/ config/

# Build the application (skip tests in Docker build)
RUN ./gradlew bootJar --no-daemon -x test -x checkstyleMain -x checkstyleTest -x spotbugsMain -x spotbugsTest

# Runtime stage
FROM eclipse-temurin:25-jre

RUN groupadd -r appuser && useradd -r -g appuser appuser

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

RUN chown -R appuser:appuser /app

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

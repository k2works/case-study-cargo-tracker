FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle gradle
COPY src src

RUN sed -i 's/\r$//' gradlew \
    && chmod +x gradlew \
    && ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:25-jre

WORKDIR /app

ENV PORT=8080

RUN useradd --create-home --shell /bin/bash spring

COPY --from=builder /workspace/build/libs/*.jar /app/app.jar

USER spring

EXPOSE 8080

CMD ["sh", "-c", "java ${JAVA_OPTS} -jar /app/app.jar"]

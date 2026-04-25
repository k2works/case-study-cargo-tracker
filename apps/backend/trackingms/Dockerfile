FROM eclipse-temurin:25-jre

WORKDIR /app
ENV PORT=8084
RUN useradd --create-home --shell /bin/bash spring
COPY build/libs/*.jar /app/app.jar
USER spring
EXPOSE 8084
CMD ["sh", "-c", "java ${JAVA_OPTS} -jar /app/app.jar"]

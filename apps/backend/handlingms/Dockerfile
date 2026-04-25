FROM eclipse-temurin:25-jre

WORKDIR /app
ENV PORT=8085
RUN useradd --create-home --shell /bin/bash spring
COPY build/libs/*.jar /app/app.jar
USER spring
EXPOSE 8085
CMD ["sh", "-c", "java ${JAVA_OPTS} -jar /app/app.jar"]

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Heroku Container Runtime は $PORT を注入する。ローカル・kind では既定値を使う。
ENV PORT=8083

RUN addgroup -S spring && adduser -S spring -G spring
COPY build/libs/*.jar /app/app.jar
USER spring

# EXPOSE はローカル・kind 用。Heroku Runtime では使用されない。
EXPOSE 8083

CMD ["sh", "-c", "java ${JAVA_OPTS} -jar /app/app.jar"]

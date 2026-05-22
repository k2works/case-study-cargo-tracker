# routingms コンテナイメージ
#
# マルチステージビルド:
#   1. builder: Gradle で bootJar を生成
#   2. runtime: Eclipse Temurin JRE 21 で軽量実行

# ===== Stage 1: builder =====
FROM gradle:8.14-jdk21 AS builder

WORKDIR /workspace

COPY gradle/ gradle/
COPY settings.gradle build.gradle ./
COPY shared/build.gradle shared/build.gradle
COPY authms/build.gradle authms/build.gradle
COPY routingms/build.gradle routingms/build.gradle
COPY gatewayms/build.gradle gatewayms/build.gradle

RUN gradle :routingms:dependencies --no-daemon || true

COPY shared/src/ shared/src/
COPY routingms/src/ routingms/src/
RUN gradle :routingms:bootJar --no-daemon -x test

# ===== Stage 2: runtime =====
FROM eclipse-temurin:21-jre

RUN groupadd --system --gid 10001 cargo \
    && useradd --system --uid 10001 --gid cargo --create-home cargo

WORKDIR /app

COPY --from=builder /workspace/routingms/build/libs/routingms.jar /app/routingms.jar

USER cargo

EXPOSE 8083

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/routingms.jar --server.port=${PORT:-8083}"]

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD wget --spider -q http://localhost:8083/actuator/health || exit 1

# trackingms コンテナイメージ
#
# マルチステージビルド:
#   1. builder: Gradle で bootJar を生成
#   2. runtime: Eclipse Temurin JRE 21 で軽量実行
#
# 非 root ユーザーで起動し、最小権限の原則に従う。

# ===== Stage 1: builder =====
FROM gradle:8.14-jdk21 AS builder

WORKDIR /workspace

COPY gradle/ gradle/
COPY settings.gradle build.gradle ./
COPY shared/build.gradle shared/build.gradle
COPY authms/build.gradle authms/build.gradle
COPY routingms/build.gradle routingms/build.gradle
COPY gatewayms/build.gradle gatewayms/build.gradle
COPY bookingms/build.gradle bookingms/build.gradle
COPY trackingms/build.gradle trackingms/build.gradle
COPY handlingms/build.gradle handlingms/build.gradle

RUN sed -i 's/java = "25"/java = "21"/' gradle/libs.versions.toml && \
    gradle :trackingms:dependencies --no-daemon || true

COPY shared/src/ shared/src/
COPY trackingms/src/ trackingms/src/
RUN gradle :trackingms:bootJar --no-daemon -x test && \
    find trackingms/build/libs/ -name "trackingms*.jar" ! -name "*plain*" -exec cp {} trackingms/build/libs/trackingms.jar \;

# ===== Stage 2: runtime =====
FROM eclipse-temurin:21-jre

RUN groupadd --system --gid 10001 cargo \
    && useradd --system --uid 10001 --gid cargo --create-home cargo

WORKDIR /app

COPY --from=builder /workspace/trackingms/build/libs/trackingms.jar /app/trackingms.jar

USER cargo

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "/app/trackingms.jar"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
    CMD wget --spider -q http://localhost:${PORT:-8084}/actuator/health || exit 1

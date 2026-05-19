# handlingms コンテナイメージ
#
# マルチステージビルド:
#   1. builder: Gradle で bootJar を生成
#   2. runtime: Eclipse Temurin JRE 25 で軽量実行
#
# 非 root ユーザーで起動し、最小権限の原則に従う。
# 関連: ADR-0003 / ADR-0012

# ===== Stage 1: builder =====
FROM gradle:9.5.0-jdk25 AS builder

WORKDIR /workspace

COPY gradle/ gradle/
COPY config/ config/
COPY settings.gradle.kts build.gradle.kts ./
COPY shared/build.gradle.kts shared/build.gradle.kts
COPY authms/build.gradle.kts authms/build.gradle.kts
COPY bookingms/build.gradle.kts bookingms/build.gradle.kts
COPY routingms/build.gradle.kts routingms/build.gradle.kts
COPY handlingms/build.gradle.kts handlingms/build.gradle.kts
COPY gatewayms/build.gradle.kts gatewayms/build.gradle.kts
COPY trackingms/build.gradle.kts trackingms/build.gradle.kts

# 依存解決のみ先行（ソース変更時のキャッシュ効率化）
RUN gradle :handlingms:dependencies --no-daemon || true

# ソースコードをコピーしてビルド（shared は handlingms の依存モジュール）
COPY shared/src/ shared/src/
COPY handlingms/src/ handlingms/src/
RUN gradle :handlingms:bootJar --no-daemon -x test

# ===== Stage 2: runtime =====
FROM eclipse-temurin:25-jre

# 非 root ユーザー作成
RUN groupadd --system --gid 10001 cargo \
    && useradd --system --uid 10001 --gid cargo --create-home cargo

WORKDIR /app

COPY --from=builder /workspace/handlingms/build/libs/handlingms.jar /app/handlingms.jar

USER cargo

EXPOSE 8085

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/handlingms.jar"]

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
    CMD wget --spider -q http://localhost:8085/actuator/health/liveness || exit 1

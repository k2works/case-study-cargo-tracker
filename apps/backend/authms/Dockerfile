# authms コンテナイメージ
#
# マルチステージビルド:
#   1. builder: Gradle で bootJar を生成
#   2. runtime: Eclipse Temurin JRE 25 で軽量実行
#
# 非 root ユーザーで起動し、最小権限の原則に従う。
# レジストリ: GHCR（ghcr.io/<org>/cargo-tracker-authms）
# 参照: ADR-0003

# ===== Stage 1: builder =====
FROM gradle:9.5.0-jdk25 AS builder

WORKDIR /workspace

# settings.gradle.kts が全サブプロジェクトを include しているため、
# build 対象外のサブプロジェクトも build.gradle.kts ファイル（とディレクトリ）が必要。
# config/ は subprojects { checkstyle / spotbugs } から rootProject.file で参照される。
COPY gradle/ gradle/
COPY config/ config/
COPY settings.gradle.kts build.gradle.kts ./
COPY shared/build.gradle.kts shared/build.gradle.kts
COPY authms/build.gradle.kts authms/build.gradle.kts
COPY bookingms/build.gradle.kts bookingms/build.gradle.kts
COPY routingms/build.gradle.kts routingms/build.gradle.kts
COPY handlingms/build.gradle.kts handlingms/build.gradle.kts
COPY trackingms/build.gradle.kts trackingms/build.gradle.kts
COPY billingms/build.gradle.kts billingms/build.gradle.kts
COPY gatewayms/build.gradle.kts gatewayms/build.gradle.kts

# 依存解決のみ先行（ソース変更時のキャッシュ効率化）
RUN gradle :authms:dependencies --no-daemon || true

# ソースコードをコピーしてビルド
COPY authms/src/ authms/src/
RUN gradle :authms:bootJar --no-daemon -x test

# ===== Stage 2: runtime =====
FROM eclipse-temurin:25-jre

# 非 root ユーザー作成
RUN groupadd --system --gid 10001 cargo \
    && useradd --system --uid 10001 --gid cargo --create-home cargo

WORKDIR /app

COPY --from=builder /workspace/authms/build/libs/authms.jar /app/authms.jar

USER cargo

EXPOSE 8081

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/authms.jar"]

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
    CMD wget --spider -q http://localhost:8081/actuator/health/liveness || exit 1

# bookingms コンテナイメージ
#
# マルチステージビルド:
#   1. builder: Gradle で bootJar を生成
#   2. runtime: Eclipse Temurin JRE 25 で軽量実行
#
# 非 root ユーザーで起動し、最小権限の原則に従う。
# レジストリ: GHCR（ghcr.io/<org>/cargo-tracker-bookingms）
# 参照: ADR-0003

# ===== Stage 1: builder =====
# Gradle 公式イメージ + JDK 25 を使い、Gradle Wrapper（gradlew）に依存せずビルドする。
# ホスト側で gradlew が未生成でもコンテナビルドが完結する。
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
COPY gatewayms/build.gradle.kts gatewayms/build.gradle.kts
COPY trackingms/build.gradle.kts trackingms/build.gradle.kts

# 依存解決のみ先行（ソース変更時のキャッシュ効率化）
RUN gradle :bookingms:dependencies --no-daemon || true

# ソースコードをコピーしてビルド（shared は bookingms の依存モジュール）
COPY shared/src/ shared/src/
COPY bookingms/src/ bookingms/src/
RUN gradle :bookingms:bootJar --no-daemon -x test

# ===== Stage 2: runtime =====
FROM eclipse-temurin:25-jre

# 非 root ユーザー作成
# Eclipse Temurin の Ubuntu ベースイメージは UID/GID 1000 を ubuntu ユーザーに割当済みのため、
# 衝突回避のため 10001 を使用する。
RUN groupadd --system --gid 10001 cargo \
    && useradd --system --uid 10001 --gid cargo --create-home cargo

WORKDIR /app

COPY --from=builder /workspace/bookingms/build/libs/bookingms.jar /app/bookingms.jar

USER cargo

EXPOSE 8082

# JVM オプションは BOOKINGMS_JAVA_OPTS で外部から上書き可能
ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/bookingms.jar"]

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
    CMD wget --spider -q http://localhost:8082/actuator/health/liveness || exit 1

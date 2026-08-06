# 国際貨物輸送管理システム コンテナイメージ
#
# 開発環境（Heroku Container Runtime）向け。
# 詳細は docs/operation/開発環境セットアップ手順書.md を参照。
#
# 制約:
#   - Heroku Container Runtime は x86_64 イメージのみをサポートする。
#     Apple Silicon では --platform linux/amd64 を指定してビルドすること。
#   - web プロセスは Heroku が注入する $PORT で listen しなければならない。
#     EXPOSE はローカル確認用であり、Heroku Runtime では使用されない。
#   - HEALTHCHECK は Heroku Container Runtime では使用されない。

# ---------------------------------------------------------------------
# ビルドステージ
# ---------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /build

# 依存解決の結果をレイヤーキャッシュに載せるため、
# ビルド定義だけを先にコピーする。ソース変更のたびに依存を取り直さない。
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies --configuration runtimeClasspath > /dev/null 2>&1 || true

COPY config ./config
COPY src ./src

# テストは Docker build では実行しない。
# テストは CI（Backend CI）と手元の ./gradlew check で実行する。
# Repository のテストは Testcontainers を必要とし、
# Docker-in-Docker が前提になるためイメージビルドには載せない。

# 開発環境（Heroku）のイメージは bootJar を H2 で起動するため、H2 を実行時依存に含める。
# ADR-003 により H2 は既定で developmentOnly（bootJar に含まれない）としているため、
# 開発環境イメージだけが明示的に opt-in する。
# **既定は false（安全側）。本番向けイメージは既定のままにすること。**
ARG INCLUDE_H2=false
RUN if [ "$INCLUDE_H2" = "true" ]; then \
        ./gradlew --no-daemon bootJar -x test -PincludeH2=true; \
    else \
        ./gradlew --no-daemon bootJar -x test; \
    fi

# ---------------------------------------------------------------------
# 実行ステージ
# ---------------------------------------------------------------------
FROM eclipse-temurin:25-jre

# 非 root ユーザーで実行する
RUN groupadd --system --gid 1001 cargotracker \
    && useradd --system --uid 1001 --gid cargotracker --create-home cargotracker

WORKDIR /app

COPY --from=builder --chown=cargotracker:cargotracker /build/build/libs/*.jar app.jar

USER cargotracker

# ローカル確認用。Heroku Runtime では使用されない。
EXPOSE 8080

# JAVA_OPTS でヒープ上限を dyno のメモリに追従させる。
# シェル形式で起動しているのは $PORT と $JAVA_OPTS を展開するため。
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENV SPRING_PROFILES_ACTIVE=dev

CMD ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]

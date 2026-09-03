# gatewayms のイメージ。ビルドコンテキストは apps/cargo-tracker/backend。
#
# Gradle は入れず Wrapper を使う。イメージの中だけ別の版で組むと、
# ローカルで通ったビルドが本番だけ落ちる。
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /workspace

# 依存の解決までを先に済ませ、ソースだけの変更でここを作り直さないようにする。
COPY gradlew ./
COPY gradle/ gradle/
COPY settings.gradle.kts build.gradle.kts ./
COPY shared/build.gradle.kts shared/build.gradle.kts
COPY gatewayms/build.gradle.kts gatewayms/build.gradle.kts
COPY authms/build.gradle.kts authms/build.gradle.kts
COPY bookingms/build.gradle.kts bookingms/build.gradle.kts
COPY routingms/build.gradle.kts routingms/build.gradle.kts
COPY trackingms/build.gradle.kts trackingms/build.gradle.kts
COPY handlingms/build.gradle.kts handlingms/build.gradle.kts
COPY billingms/build.gradle.kts billingms/build.gradle.kts
COPY contract-tests/build.gradle.kts contract-tests/build.gradle.kts
COPY acceptance-tests/build.gradle.kts acceptance-tests/build.gradle.kts
RUN ./gradlew --no-daemon :gatewayms:dependencies --configuration runtimeClasspath > /dev/null

COPY shared/src shared/src
COPY gatewayms/src gatewayms/src
# テストは CI が回す。イメージのビルドで Testcontainers を起こすと、
# Docker の中で Docker を要求することになる。
RUN ./gradlew --no-daemon :gatewayms:bootJar -x test

FROM eclipse-temurin:25-jre
# ヘルスチェックに curl を使う。JRE イメージには入っていない。
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*
# root で走らせない。
RUN groupadd --system app && useradd --system --gid app --home /app app
# /app は app が書ける必要がある。bookingms は荷主ごとの鍵置き場（ADR-0003）を
# ここに作る。root 所有のままだと AccessDeniedException で起動できない。
RUN mkdir -p /app && chown app:app /app
WORKDIR /app
COPY --from=builder --chown=app:app /workspace/gatewayms/build/libs/gatewayms-*.jar /app/app.jar
USER app
ENV JAVA_OPTS=""
# k8s の probe とは別に、compose でも生死が分かるようにしておく。
# 起動時に Axon Server への接続を検査するので start-period は長めに取る。
HEALTHCHECK --interval=10s --timeout=5s --retries=12 --start-period=120s \
  CMD ["sh", "-c", "curl -fsS http://localhost:${SERVER_PORT:-8080}/actuator/health || exit 1"]
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

# Stage 1: Build（sbt stage で起動スクリプト込みの配布物を生成）
# sbt launcher は project/build.properties の sbt バージョンを自動取得する
FROM sbtscala/scala-sbt:eclipse-temurin-25.0.3_9_1.12.11_3.3.7 AS build
WORKDIR /build

# 依存解決を先に行いレイヤーキャッシュを活用する
COPY build.sbt .
COPY project/ project/
RUN sbt update

COPY app/ app/
COPY conf/ conf/
RUN sbt stage

# Stage 2: Runtime（JRE のみ・非 root ユーザー）
FROM eclipse-temurin:25-jre-alpine
# bash は sbt-native-packager の起動スクリプトに必要
RUN apk add --no-cache bash && addgroup -S cargo && adduser -S cargo -G cargo
WORKDIR /app
COPY --from=build /build/target/universal/stage/ /app/
USER cargo
EXPOSE 9000

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s \
  CMD wget -qO- http://localhost:9000/health || exit 1

# 非 root 実行のため PID ファイルは無効化する（コンテナがプロセスを管理）
ENTRYPOINT ["/app/bin/cargo-tracker"]
CMD ["-Dhttp.port=9000", "-Dpidfile.path=/dev/null"]

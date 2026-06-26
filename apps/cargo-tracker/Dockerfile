# Cargo Tracker (Haskell 版) マルチステージ Dockerfile
#
# Stage 1: stack build で Haskell バイナリ生成
# Stage 2: debian:bookworm-slim にバイナリのみコピー (JVM 不要、軽量)
#
# 詳細は docs/operation/開発環境セットアップ手順書.md を参照。

# =========================================================
# Stage 1: Build (Haskell 9.10 + Stack)
# =========================================================
FROM haskell:9.10-slim AS builder
WORKDIR /build

# 依存解決を先に行いキャッシュレイヤを活用
COPY package.yaml stack.yaml stack.yaml.lock* ./
RUN stack setup
RUN stack build --only-dependencies --no-terminal

# ソースコードをコピーしてビルド
COPY src/ src/
COPY app/ app/
COPY test/ test/

RUN stack install --local-bin-path /out --no-terminal

# =========================================================
# Stage 2: Runtime (Debian Slim + 最小ランタイム依存)
# =========================================================
FROM debian:bookworm-slim AS runtime

# Haskell バイナリの実行に必要な共有ライブラリ
RUN apt-get update && apt-get install -y --no-install-recommends \
      libpq5 \
      libgmp10 \
      ca-certificates \
      wget \
      tini \
    && rm -rf /var/lib/apt/lists/*

# 非 root ユーザー
RUN useradd --create-home --uid 1000 cargo
WORKDIR /app
USER cargo

# ビルダーから実行ファイルと静的アセットをコピー
COPY --from=builder --chown=cargo:cargo /out/cargo-tracker-exe ./cargo-tracker-exe
COPY --chown=cargo:cargo static/ ./static/
COPY --chown=cargo:cargo db/ ./db/

EXPOSE 8080

# ヘルスチェック (アプリ /health エンドポイント)
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -q --spider http://localhost:8080/health || exit 1

# tini で正しいシグナル伝搬
ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["./cargo-tracker-exe"]

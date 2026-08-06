#!/usr/bin/env bash
# E2E 用アプリ起動スクリプト。
# 専用の一時 Postgres を起動 → マイグレーション + デモシード投入 → サーバを exec 起動する。
# Playwright の webServer から呼ばれ、http://localhost:8080/health の応答を待たれる。
set -euo pipefail

APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$APP_DIR"

DB_NAME=cargo-tracker-e2e-db
DB_PORT=55440
export DATABASE_URL="postgres://cargo:cargo@127.0.0.1:${DB_PORT}/cargo_tracker"
export PORT=8080
export STATIC_DIR=static
# http://localhost での動作のため Secure Cookie は無効（既定）。

echo "[e2e] 一時 Postgres を起動（ポート ${DB_PORT}）"
docker rm -f "$DB_NAME" >/dev/null 2>&1 || true
docker run -d --name "$DB_NAME" \
  -e POSTGRES_USER=cargo -e POSTGRES_PASSWORD=cargo -e POSTGRES_DB=cargo_tracker \
  -p ${DB_PORT}:5432 postgres:16-alpine >/dev/null

echo "[e2e] Postgres の起動を待機"
for _ in $(seq 1 30); do
  docker exec "$DB_NAME" pg_isready -U cargo >/dev/null 2>&1 && break
  sleep 1
done

echo "[e2e] ビルド（seed / server）"
cargo build --quiet --bin seed --bin cargo-tracker-server

echo "[e2e] デモシード投入"
./target/debug/seed

echo "[e2e] サーバ起動 http://localhost:${PORT}"
exec ./target/debug/cargo-tracker-server

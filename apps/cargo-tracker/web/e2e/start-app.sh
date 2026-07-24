#!/usr/bin/env bash
# E2E 用アプリ起動スクリプト。
# PostgreSQL（docker compose）を起動→マイグレーション適用→サーバービルド→起動する。
# Playwright の webServer から呼ばれ、http://localhost:8092/healthz が 200 になるまで待機される。
set -euo pipefail

# apps/cargo-tracker へ移動（このスクリプトは apps/cargo-tracker/web/e2e にある）
cd "$(dirname "$0")/../.."

PORT="${PORT:-8092}"
DB_URL="${DB_URL:-postgres://cargo:cargo@localhost:5432/cargo_tracker?sslmode=disable}"
export PORT DB_URL

echo "[e2e] PostgreSQL を起動します..."
docker compose up -d postgres

echo "[e2e] PostgreSQL の準備を待機します..."
for _ in $(seq 1 30); do
  if docker compose exec -T postgres pg_isready -U cargo -d cargo_tracker >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

echo "[e2e] マイグレーションを適用します..."
migrate -path db/migrations -database "$DB_URL" up || true

echo "[e2e] サーバーをビルドします..."
go build -o ./tmp/server ./cmd/server

echo "[e2e] starting server on PORT=$PORT ..."
exec ./tmp/server

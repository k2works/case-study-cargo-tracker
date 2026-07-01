#!/usr/bin/env bash
# T4-14 (IT5 task 3.2): E2E 専用 schema (cargo_tracker_e2e) セットアップ
#
# Playwright E2E テスト実行前に呼び出す。以下を行う:
# 1. cargo_tracker_e2e schema を CREATE SCHEMA IF NOT EXISTS で用意
# 2. dbmate で E2E schema に全 migration を適用
# 3. 主要テーブルを TRUNCATE (test isolation 用の fixture)
#
# 使用例:
#   DATABASE_URL=postgres://cargo:cargo@localhost:5432/cargo_tracker?sslmode=disable \
#   bash apps/cargo-tracker/scripts/e2e-schema-setup.sh
#
# 前提: dbmate v2.x がインストール済み、Postgres 16+ が起動中。

set -euo pipefail

: "${DATABASE_URL:?DATABASE_URL is required}"

# E2E schema 名 (本番用 public とは分離)
E2E_SCHEMA="cargo_tracker_e2e"

echo "[e2e-schema-setup] E2E schema=${E2E_SCHEMA} を準備します"

# 1. schema 作成
psql "${DATABASE_URL}" -v ON_ERROR_STOP=1 -c \
  "CREATE SCHEMA IF NOT EXISTS ${E2E_SCHEMA};"

# 2. dbmate で E2E schema に migration 適用
#    DBMATE_MIGRATIONS_TABLE で schema 分離、search_path で対象 schema 指定
DBMATE_URL="${DATABASE_URL}?options=-c%20search_path=${E2E_SCHEMA}%2Cpublic"
DBMATE_MIGRATIONS_TABLE="${E2E_SCHEMA}.schema_migrations" \
  dbmate --url "${DBMATE_URL}" \
    --migrations-dir apps/cargo-tracker/db/migrations \
    --no-dump-schema \
    up

echo "[e2e-schema-setup] migration 適用完了"

# 3. TRUNCATE (test isolation)
#    dependency 順序を考慮した cascade truncate
psql "${DATABASE_URL}" -v ON_ERROR_STOP=1 -c "
  SET search_path TO ${E2E_SCHEMA};
  TRUNCATE TABLE
    confirmation_code,
    handling_activity,
    tracking_activity,
    session,
    customs_declaration,
    leg,
    itinerary,
    cargo,
    shipper,
    route_candidate,
    estimate,
    carrier_movement,
    voyage,
    location,
    user_roles,
    users,
    notification_log
  RESTART IDENTITY CASCADE;
"

echo "[e2e-schema-setup] TRUNCATE 完了、E2E テスト実行可能"

#!/bin/bash
# PostgreSQL マルチデータベース初期化スクリプト
#
# 目的:
#   Database per Service パターンに従い、各マイクロサービス用の DB を作成する。
#   POSTGRES_MULTIPLE_DATABASES 環境変数（カンマ区切り）で指定された DB をすべて作成する。
#
# 参考: https://github.com/mrts/docker-postgresql-multiple-databases

set -e
set -u

function create_user_and_database() {
  local database=$1
  echo "  Creating database '$database'"
  # psql のデフォルト接続先 DB は user 名と同じ DB だが、本コンテナでは
  # POSTGRES_USER=cargo / POSTGRES_DB=postgres と分離しているため、
  # 接続先 DB を明示する必要がある（明示しないと "cargo" DB を探して FATAL）。
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "${POSTGRES_DB:-postgres}" <<-EOSQL
    CREATE DATABASE $database;
    GRANT ALL PRIVILEGES ON DATABASE $database TO $POSTGRES_USER;
EOSQL
}

if [ -n "${POSTGRES_MULTIPLE_DATABASES:-}" ]; then
  echo "Multiple database creation requested: $POSTGRES_MULTIPLE_DATABASES"
  # カンマと空白文字（スペース・タブ・改行）を区切りとして DB 名を抽出
  for db in $(echo "$POSTGRES_MULTIPLE_DATABASES" | tr ',' ' '); do
    [ -z "$db" ] && continue
    create_user_and_database "$db"
  done
  echo "Multiple databases created"
fi

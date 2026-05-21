#!/bin/bash
# PostgreSQL コンテナ初期化時に複数データベースを作成するスクリプト
#
# docker-entrypoint-initdb.d/ にマウントされて初回起動時にのみ実行される。
# 環境変数 POSTGRES_MULTIPLE_DATABASES にカンマ区切りで DB 名を指定する。
#
# 例: POSTGRES_MULTIPLE_DATABASES=auth_db,booking_read_db,routing_read_db
#
# 各 DB は POSTGRES_USER に対して所有権が付与される。
set -e
set -u

function create_user_and_database() {
    local database
    database=$(echo "$1" | tr -d '[:space:]')
    if [ -z "$database" ]; then
        return
    fi
    echo "  Creating database '$database' owned by '$POSTGRES_USER'"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "postgres" <<-EOSQL
        SELECT 'CREATE DATABASE $database OWNER $POSTGRES_USER'
        WHERE NOT EXISTS (
            SELECT FROM pg_database WHERE datname = '$database'
        )\gexec
EOSQL
}

if [ -n "${POSTGRES_MULTIPLE_DATABASES:-}" ]; then
    echo "Multiple database creation requested: $POSTGRES_MULTIPLE_DATABASES"
    # カンマ区切りを分解して順次作成
    for db in $(echo "$POSTGRES_MULTIPLE_DATABASES" | tr ',' ' '); do
        create_user_and_database "$db"
    done
    echo "Multiple databases created"
fi

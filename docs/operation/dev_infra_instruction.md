# 開発環境セットアップ手順書

## 概要

本ドキュメントは、Case Study Cargo Tracker（Scala 版）を Heroku Container Registry で開発環境にデプロイする手順を説明します。

この開発環境では `apps/cargo-tracker` の Play Framework アプリケーションを Heroku の `container` stack で動かします。本プロジェクトはインメモリ DB（H2 等）を使用しない方針のため、**Heroku Postgres アドオン**を接続して起動確認と画面確認を行います。

> **注意**: Heroku Dev Center の [Container Registry & Runtime](https://devcenter.heroku.com/articles/container-registry-and-runtime) では、`container` stack は高度な用途向けであり、通常は buildpack ベースのデプロイを推奨しています。本プロジェクトでは Docker イメージを明示的に管理したいため、container stack を採用します。

---

## 1. 構成

| 項目 | 内容 |
| :--- | :--- |
| 実行基盤 | Heroku Container Registry / Runtime |
| デプロイ対象 | `apps/cargo-tracker` |
| プロセス種別 | `web` |
| 設定ファイル | `application.conf` + 環境変数オーバーライド |
| データベース | Heroku Postgres（アドオン） |
| マイグレーション | flyway-play（起動時自動適用） |
| HTTP ポート | Heroku が注入する `PORT`（`-Dhttp.port=$PORT`） |

```plantuml
@startuml

title Heroku 開発環境（Play Framework + Heroku Postgres）

node "Developer PC" {
  component "Docker build\nlinux/amd64" as docker
  component "Heroku CLI" as cli
}

node "Heroku Container Registry" {
  artifact "registry.heroku.com/<app>/web" as image
}

node "Heroku Dyno" {
  component "Play Framework\n(sbt stage 出力)" as app
}

database "Heroku Postgres\n(アドオン)" as pg

docker --> image : push
cli --> image : release
app --> pg : JDBC (DATABASE_URL)

@enduml
```

---

## 2. 前提条件

以下を満たしていることを確認してください。

| ツール | バージョン目安 | 確認コマンド |
| :--- | :--- | :--- |
| Heroku CLI | 最新 | `heroku --version` |
| Docker | 最新 | `docker version` |
| Git | 最新 | `git --version` |

Heroku Container Registry の公式ドキュメントでは、コンテナイメージを push する前に `docker ps` と `heroku login` が成功することを前提としています。また、Heroku Container Runtime は `x86_64` イメージのみサポートします。

---

## 3. アプリケーション設定

### 3.1 設定ファイルと環境変数

`application.conf` をベースに、Heroku では環境変数で接続情報を上書きします。

- DB 接続は `DB_URL` / `DB_USER` / `DB_PASSWORD` 環境変数を参照する（`application.conf` で `url = ${?DB_URL}` のように定義）
- flyway-play は有効（起動時に `conf/db/migration/default/` を自動適用）
- ヘルスチェックは `/health`（DB 疎通込み）
- アプリは `PORT` 環境変数を受け取り、未設定時は `9000` で起動

### 3.2 Heroku 向けの注意

- Heroku の web プロセスは `$PORT` で HTTP を listen する必要があります。Dockerfile の `CMD` で `-Dhttp.port=${PORT}` を渡します（`EXPOSE` は Heroku Runtime では使用されません）
- Play は起動時に `play.http.secret.key` が必須です（未設定または `changeme` のままでは本番モードで起動しません）。Config Vars で必ず設定してください
- Heroku Postgres の `DATABASE_URL` は `postgres://user:pass@host:port/db` 形式のため、JDBC 形式（`jdbc:postgresql://...`）への変換が必要です。起動スクリプトで変換するか、`DB_URL` / `DB_USER` / `DB_PASSWORD` を個別に設定します

---

## 4. Dockerfile

Heroku に push する Dockerfile は `apps/cargo-tracker/Dockerfile` を使用します（[インフラストラクチャアーキテクチャ](../design/architecture_infrastructure.md) のマルチステージビルド）。

この Dockerfile は以下の方針です。

- ビルドステージ（`sbtscala/scala-sbt`）で `sbt stage` を実行し、起動スクリプト込みの配布物を生成
- テストは Docker build では実行しない
- 実行イメージは `eclipse-temurin:25-jre-alpine` ベース
- 非 root ユーザーで実行
- `CMD` で `bin/cargo-tracker -Dhttp.port=${PORT:-9000}` を起動

---

## 5. Heroku アプリ作成

### 5.1 ログイン

```bash
heroku login
heroku container:login
```

### 5.2 アプリ作成

```bash
heroku create <app-name> --stack container
```

既存アプリを使う場合は stack を確認し、必要なら container に切り替えます。

```bash
heroku stack:set container -a <app-name>
```

### 5.3 Heroku Postgres アドオンの追加

```bash
heroku addons:create heroku-postgresql:essential-0 -a <app-name>

# 接続情報の確認
heroku pg:credentials:url -a <app-name>
```

Gulp タスクで作成する場合:

```bash
npx gulp deploy:dev:app:create
```

---

## 6. Config Vars 設定

Play の必須設定と DB 接続情報を設定します。

```bash
# Session 署名鍵（必須。漏洩時はローテーション）
heroku config:set PLAY_HTTP_SECRET_KEY="$(openssl rand -base64 48)" -a <app-name>

# DB 接続情報（heroku pg:credentials:url の値から設定）
heroku config:set \
  DB_URL="jdbc:postgresql://<host>:5432/<db>?sslmode=require" \
  DB_USER="<user>" \
  DB_PASSWORD="<password>" \
  -a <app-name>

# JVM オプション（任意）
heroku config:set JAVA_OPTS="-XX:MaxRAMPercentage=75.0" -a <app-name>
```

> **補足**: `application.conf` 側は `play.http.secret.key = ${?PLAY_HTTP_SECRET_KEY}`、`db.default.url = ${?DB_URL}` のように環境変数参照で定義しておきます。

### 6.1 `.env` 設定

Gulp タスクを使う場合は、プロジェクトルートの `.env` に以下を設定します。

```dotenv
DEV_HEROKU_APP_NAME=<app-name>
DEV_DOCKER_PLATFORM=linux/amd64
```

---

## 7. イメージのビルドと push

`apps/cargo-tracker` に移動して実行します。

```bash
cd apps/cargo-tracker
heroku container:push web -a <app-name>
```

Apple Silicon など非 `x86_64` 環境では、Heroku の制約に合わせて `linux/amd64` で build してください。

```bash
docker build --platform linux/amd64 -t registry.heroku.com/<app-name>/web .
docker push registry.heroku.com/<app-name>/web
```

### 7.1 Gulp タスクで実行する場合

```bash
npx gulp deploy:dev:login
npx gulp deploy:dev:build
npx gulp deploy:dev:push
```

`deploy:dev:push` は `deploy:dev:build` で作成した `registry.heroku.com/<app-name>/web` イメージを、そのまま `docker push` します。

---

## 8. リリース

```bash
heroku container:release web -a <app-name>
```

リリース後にアプリを開きます。

```bash
heroku open -a <app-name>
```

ログ確認:

```bash
heroku logs --tail -a <app-name>
```

Gulp タスク:

```bash
npx gulp deploy:dev:release
npx gulp deploy:dev:open
npx gulp deploy:dev:logs
```

---

## 9. 動作確認

### 9.1 起動確認

Heroku 上でアプリが起動したら、以下を確認します。

- ログに `Listening for HTTP on /0.0.0.0:<PORT>` が出る
- flyway-play のマイグレーション適用ログが出る
- `/health` が `{"status":"UP"}` を返す
- ログイン画面が表示される

```bash
curl https://<app-name>.herokuapp.com/health
```

### 9.2 データの扱い

Heroku Postgres はアドオンとして永続化されるため、dyno 再起動でデータは消えません。ただし開発環境用の最小プラン（essential-0）は接続数・容量に制限があります。

> **重要**: Heroku のファイルシステムは ephemeral です。アップロードファイル等の永続化は前提にしないでください。

---

## 10. 更新手順

アプリ更新時は同じ手順で再 build / release します。

```bash
cd apps/cargo-tracker
heroku container:push web -a <app-name>
heroku container:release web -a <app-name>
```

または Gulp で一括実行します。

```bash
npx gulp deploy:dev
```

初回はログイン確認からまとめて実行できます。

```bash
npx gulp deploy:dev:setup
```

---

## 11. トラブルシューティング

### `R10` または起動タイムアウトになる

原因:

- アプリが `PORT` で listen していない
- 起動時の Flyway マイグレーションに時間がかかっている

対処:

- Dockerfile の `CMD` に `-Dhttp.port=${PORT}` が含まれているか確認する
- `heroku logs --tail` で bind エラー・マイグレーションエラーを確認する

### `Configuration error: play.http.secret.key` で起動しない

原因:

- `PLAY_HTTP_SECRET_KEY` が未設定、またはデフォルト値のまま

対処:

```bash
heroku config:set PLAY_HTTP_SECRET_KEY="$(openssl rand -base64 48)" -a <app-name>
```

### DB に接続できない

原因:

- `DB_URL` が `postgres://` 形式のまま（JDBC 形式でない）
- `sslmode=require` が付いていない

対処:

```bash
# JDBC 形式 + sslmode を確認
heroku config:get DB_URL -a <app-name>
# 例: jdbc:postgresql://<host>:5432/<db>?sslmode=require
```

### `unsupported architecture` が出る

原因:

- ARM64 イメージを push している

対処:

```bash
docker build --platform linux/amd64 -t registry.heroku.com/<app-name>/web .
docker push registry.heroku.com/<app-name>/web
```

---

## 12. 制約

- Heroku `container` stack は base image の自動更新を行わないため、セキュリティ修正を取り込むには Docker イメージの再 build / 再 deploy が必要
- Heroku Container Runtime では `HEALTHCHECK` は使用されない（`/health` の確認は外形監視または手動）
- essential プランの Heroku Postgres は接続数上限が小さいため、HikariCP の `maximumPoolSize` を小さく（例: 5）設定する

---

## 13. 関連ドキュメント

- [アプリケーション開発環境セットアップ手順書](./dev_app_instruction.md)
- [バックエンドアーキテクチャ設計](../design/architecture_backend.md)
- [インフラストラクチャアーキテクチャ](../design/architecture_infrastructure.md)
- [技術スタック選定](../design/tech_stack.md)

# ADR-0006 Heroku Container Registry を用いた開発環境デプロイ構成

開発環境のデプロイ先として Heroku を採用し、Docker イメージを Container Registry 経由でデプロイする構成を確立する。

日付: 2026-05-22

## ステータス

承認済み

## コンテキスト

- ケーススタディ用アプリケーション（IT1: authms / routingms / gatewayms / frontend）を外部公開可能な開発環境にデプロイする必要があった
- チームはクラウド環境の運用コストを最小化しつつ、CI/CD の基盤となるデプロイパイプラインを整備したかった
- Spring Boot 3.4.x（コンテナ内 Java 21）と React（Vite）の組み合わせのため、コンテナベースのデプロイが適切と判断した
- Heroku Eco dyno（512MB）というメモリ制約がある

### 直面した技術的課題

IT1 実装中に以下の問題に遭遇した。

| # | 問題 | 解決策 |
|---|------|--------|
| 1 | `heroku container:push --dockerfile` オプション非対応 | `docker build + docker push + heroku container:release` の 3 ステップに変更 |
| 2 | Apple Silicon でビルドした OCI manifest list を Heroku が拒否（`unsupported`） | `--platform linux/amd64 --provenance=false` を追加 |
| 3 | `libs.versions.toml` で `java = "25"` を指定しているが `gradle:8.14-jdk21` イメージには Java 21 しかない | Dockerfile 内で `sed -i 's/java = "25"/java = "21"/'` してツールチェーンを上書き |
| 4 | `bootJar` 生成 JAR のファイル名にバージョンサフィックスが付く（`authms-0.0.1-SNAPSHOT.jar`） | `find ... ! -name "*plain*" -exec cp {} {service}.jar` で固定名にリネーム |
| 5 | `sh -c exec java $JAVA_OPTS -jar ...` でシェル変数展開が乱れ起動直後に status 0 でクラッシュ | `ENTRYPOINT ["java", "-jar", "..."]` の exec 形式に変更 |
| 6 | Heroku Eco dyno が `server.port` を受け取れず R10 タイムアウト | `application-heroku.yml` に `server.port: ${PORT:デフォルト}` を追加 |
| 7 | 既存アプリでは `heroku create --stack container` が適用されない | `heroku stack:set container --app {app}` を初回セットアップ手順に追加 |
| 8 | `JWT_SECRET` が 80 bits（10 バイト）で `WeakKeyException` が発生 | `openssl rand -base64 48`（384 bits）以上の値を使用するよう手順を明記 |
| 9 | Flyway 無効時に H2 DB に初期ユーザーが存在せずログインできない | `data.sql` + `sql.init.mode: always` で初期データを自動投入 |
| 10 | `V2__insert_initial_users.sql` の BCrypt ハッシュが誤り（検証済みハッシュでない） | `bcryptjs` で検証した `$2b$10$...` ハッシュに修正 |
| 11 | nginx の `proxy_buffer_size` デフォルト値（4k）が JWT レスポンスヘッダーに不足 | `proxy_buffer_size 128k` に拡張 |
| 12 | Heroku の SNI ベースルーティングにより TLS 接続が失敗 | `proxy_ssl_server_name on` と `GATEWAY_HOST` 環境変数を追加 |

## 決定

**Heroku Container Registry（`docker build + docker push + heroku container:release`）を使いコンテナデプロイする。**

### デプロイ構成（IT1）

```
frontend (nginx:1.27-alpine)
  └─ /api/* → nginx proxy → gatewayms（Spring Cloud Gateway + JWT 検証）
                              ├─ /api/auth/**, /api/v1/users/** → authms
                              └─ /api/v1/voyages/**, /api/v1/routes/** → routingms
```

### Dockerfile 方針

単一の `Dockerfile` にマルチステージビルドを内包する。

```dockerfile
# Stage 1: gradle:8.14-jdk21 でビルド
FROM gradle:8.14-jdk21 AS builder
RUN sed -i 's/java = "25"/java = "21"/' gradle/libs.versions.toml
RUN gradle :{service}:bootJar --no-daemon -x test && \
    find {service}/build/libs/ -name "{service}*.jar" ! -name "*plain*" \
      -exec cp {} {service}/build/libs/{service}.jar \;

# Stage 2: eclipse-temurin:21-jre で実行
FROM eclipse-temurin:21-jre
COPY --from=builder /workspace/{service}/build/libs/{service}.jar /app/{service}.jar
ENTRYPOINT ["java", "-jar", "/app/{service}.jar"]
```

> **注**: `Dockerfile.heroku`（ローカルビルド済み JAR コピー方式）は採用しない。コンテナ内完結ビルドにより CI/CD との一貫性を保つ。

### docker build コマンド

```bash
docker build \
  --platform linux/amd64 \
  --provenance=false \
  -t registry.heroku.com/{app}/web \
  -f apps/backend/{service}/Dockerfile \
  apps/backend
```

### nginx プロキシ設定（frontend）

```nginx
location /api/ {
    proxy_pass ${GATEWAY_URL};
    proxy_set_header Host ${GATEWAY_HOST};
    proxy_ssl_server_name on;
    proxy_buffer_size   128k;
    proxy_buffers       4 256k;
    proxy_busy_buffers_size 256k;
}
```

### `application-heroku.yml` の必須設定

```yaml
# Heroku の PORT 環境変数を受け取る（必須）
server:
  port: ${PORT:デフォルトポート}

# H2 インメモリ DB（Flyway 無効）
spring:
  datasource:
    url: jdbc:h2:mem:{db名};MODE=PostgreSQL;DB_CLOSE_DELAY=-1
  flyway:
    enabled: false
  sql:
    init:
      mode: always  # data.sql を自動実行
```

### Heroku Config Vars（IT1）

| アプリ | 変数名 | 説明 |
|-------|--------|------|
| authms | `SPRING_PROFILES_ACTIVE` | `heroku` |
| authms | `JAVA_TOOL_OPTIONS` | JVM メモリ制限オプション |
| authms | `JWT_SECRET` | JWT 署名キー（384 bits 以上） |
| routingms | `SPRING_PROFILES_ACTIVE` | `heroku` |
| routingms | `JAVA_TOOL_OPTIONS` | JVM メモリ制限オプション |
| routingms | `KAFKA_BOOTSTRAP_SERVERS` | Aiven Kafka ブローカーアドレス |
| routingms | `KAFKA_SECURITY_PROTOCOL` | `SSL`（Aiven Kafka） |
| routingms | `KAFKA_SSL_CA_CERT` | Aiven CA 証明書 PEM（`\n` 区切り1行） |
| routingms | `KAFKA_SSL_ACCESS_CERT` | Aiven クライアント証明書 PEM（`\n` 区切り1行） |
| routingms | `KAFKA_SSL_ACCESS_KEY` | Aiven クライアント秘密鍵 PEM（`\n` 区切り1行） |
| gatewayms | `SPRING_PROFILES_ACTIVE` | `heroku` |
| gatewayms | `JAVA_TOOL_OPTIONS` | JVM メモリ制限オプション |
| gatewayms | `JWT_SECRET` | JWT 検証キー（authms と同一値） |
| gatewayms | `AUTHMS_URL` | `https://{authms ドメイン}` |
| gatewayms | `ROUTINGMS_URL` | `https://{routingms ドメイン}` |
| frontend | `GATEWAY_URL` | `https://{gatewayms ドメイン}` |
| frontend | `GATEWAY_HOST` | `{gatewayms ドメイン}`（スキームなし） |

### Gulp タスク構成

```
deploy:dev:setup      — セットアップガイド表示
deploy:dev:config     — Config Vars を一括設定（heroku domains で実ドメイン自動取得）
deploy:dev            — 全サービスを順次 push → release
deploy:dev:push:*     — 個別サービスのビルド・プッシュ
deploy:dev:release:*  — 個別サービスのリリース
deploy:dev:logs:*     — 各サービスのログ表示
deploy:dev:open       — frontend をブラウザで開く
deploy:dev:help       — タスク一覧表示
```

### 代替案

| 案 | 却下理由 |
|----|---------|
| Heroku Git デプロイ（Buildpack） | Gradle マルチプロジェクト構成で Buildpack 設定が複雑になるため |
| `heroku container:push` コマンド | `--dockerfile` オプション非対応のため |
| `Dockerfile.heroku`（JAR 事前ビルド方式） | ローカルビルドとコンテナビルドの二重管理を避けるため |
| Railway / Render | チームが Heroku の運用に慣れており、移行コストが高い |

## 影響

### ポジティブ

- Gulp タスク 1 コマンド（`npx gulp deploy:dev`）で全サービスをデプロイできる
- `deploy:dev:config` で `heroku domains` を使い実ドメインを動的取得するため、Heroku のランダムサフィックスドメイン問題に自動対応できる
- コンテナ内完結ビルドにより、ローカルに Java や Gradle をインストールしなくてもデプロイできる
- H2 インメモリ DB で外部依存なしに起動できる

### ネガティブ

- コンテナ内 Gradle ビルドのため初回ビルドに時間がかかる（依存関係キャッシュなし）
- `libs.versions.toml` の Java バージョン（25）と Docker イメージ（21）が乖離しており、`sed` による一時上書きが必要
- H2 インメモリ DB を使用するため dyno の再起動でデータが消える（開発・検証用途に限定）
- `JWT_SECRET` は 384 bits 以上必要。短い値を設定すると起動時に `WeakKeyException` でクラッシュする

## コンプライアンス

- `npx gulp deploy:dev` が全サービスエラーなく完了すること
- `heroku logs -a {app}` で各サービスが `Started ... in N seconds` を出力すること
- `curl -X POST https://{gatewayms-domain}/api/auth/login -d '{"username":"admin","password":"password"}'` が JWT トークンを返すこと
- frontend からブラウザでログインが成功すること

## 備考

- 著者: k2works
- 関連コミット: IT1 Heroku デプロイ実装
- 関連 ADR: ADR-0001（Axon Kafka Aiven 採用）、ADR-0002（MyBatis 採用）
- 初期ユーザー: `admin` / `password`、`routing1` / `password`、`sales1` / `password`（`data.sql` で H2 DB に投入）

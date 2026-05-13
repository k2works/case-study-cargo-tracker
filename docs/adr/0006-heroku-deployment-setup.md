# ADR-0006 Heroku Container Registry を用いた開発環境デプロイ構成

開発環境のデプロイ先として Heroku を採用し、Docker イメージを Container Registry 経由でデプロイする構成を確立する。

日付: 2026-05-13

## ステータス

承認済み

## コンテキスト

- ケーススタディ用アプリケーション（authms / bookingms / gatewayms / frontend）を外部公開可能な開発環境にデプロイする必要があった
- チームはクラウド環境の運用コストを最小化しつつ、CI/CD の基盤となるデプロイパイプラインを整備したかった
- Spring Boot 4.x（Java 25）と React（Vite）の組み合わせのため、コンテナベースのデプロイが適切と判断した
- Heroku Eco dyno（512MB）というメモリ制約がある

### 直面した技術的課題

1. `heroku container:push -f` オプション非対応 → `docker build + docker push` への切り替えが必要
2. Apple Silicon（M シリーズ）でビルドしたイメージが Heroku（linux/amd64）で動作しない → `--platform linux/amd64` 指定が必須
3. Heroku Container Registry が OCI provenance メタデータを拒否 → `--provenance=false` が必須
4. Gradle マルチプロジェクトをコンテナ内でビルドすると他サブプロジェクトのディレクトリ不在エラーが発生 → ローカルビルド済み JAR をコピーする `Dockerfile.heroku` を分離
5. `sh -c exec java $JAVA_OPTS -jar ...` でスペース含む変数が正しく展開されず起動クラッシュ → exec 形式 ENTRYPOINT + `JAVA_TOOL_OPTIONS` 環境変数への変更
6. nginx の `proxy_buffer_size` デフォルト値（4k）が JWT レスポンスヘッダーに対して不足 → `proxy_buffer_size 128k` に拡張
7. Heroku の SNI ベースルーティングにより IP 直接接続で TLS が失敗 → `proxy_ssl_server_name on` と `GATEWAY_HOST` 環境変数の追加
8. 新規 Heroku アプリのドメインが `appname-randomhash.herokuapp.com` 形式になり、Config Vars に設定したドメインが無効 → `heroku domains` コマンドで動的取得する仕組みを Gulp タスクに追加

## 決定

**Heroku Container Registry（`docker build` + `docker push` + `heroku container:release`）を使いコンテナデプロイする。**

### デプロイ構成

```
frontend (nginx:alpine)
  └─ /api/* → nginx proxy → gatewayms
                              ├─ /api/v1/auth/** → authms
                              └─ /api/v1/bookings/**, /api/v1/shippers/**, /api/ping → bookingms
```

### Dockerfile 分離方針

| ファイル | 用途 |
|---------|------|
| `Dockerfile` | ローカル開発・CI 用（Gradle マルチステージビルド） |
| `Dockerfile.heroku` | Heroku デプロイ用（ローカルビルド済み JAR のみコピー） |

### nginx プロキシ設定（frontend）

```nginx
location /api/ {
    proxy_pass ${GATEWAY_URL};
    proxy_set_header Host ${GATEWAY_HOST};
    proxy_ssl_server_name on;
    proxy_ssl_name        ${GATEWAY_HOST};
    proxy_buffer_size          128k;
    proxy_buffers              4 256k;
    proxy_busy_buffers_size    256k;
}
```

### 必要な環境変数

| アプリ | 変数名 | 説明 |
|-------|--------|------|
| authms | `SPRING_PROFILES_ACTIVE=heroku` | H2 インメモリ DB + Flyway 構成を有効化 |
| authms | `JAVA_TOOL_OPTIONS` | JVM メモリ制限オプション |
| authms | `JWT_SECRET` | JWT 署名キー |
| authms | `JWT_ISSUER` | JWT 発行者 |
| bookingms | `SPRING_PROFILES_ACTIVE=heroku` | Axon ローカルバス（AxonServer 無効）構成を有効化 |
| bookingms | `JAVA_TOOL_OPTIONS` | JVM メモリ制限オプション |
| gatewayms | `SPRING_PROFILES_ACTIVE=heroku` | Spring Cloud Gateway 設定を有効化 |
| gatewayms | `JAVA_TOOL_OPTIONS` | JVM メモリ制限オプション |
| gatewayms | `JWT_SECRET` | JWT 検証キー（authms と同一値） |
| gatewayms | `AUTHMS_URL` | authms の実際の Heroku ドメイン |
| gatewayms | `BOOKINGMS_URL` | bookingms の実際の Heroku ドメイン |
| frontend | `GATEWAY_URL` | gatewayms の `https://` URL |
| frontend | `GATEWAY_HOST` | gatewayms のホスト名（URL からスキームを除いた値） |

### Gulp タスク構成

```
deploy:dev:setup    — セットアップガイド表示
deploy:dev:config   — Config Vars を一括設定（domains コマンドで実ドメイン取得）
deploy:dev:login    — heroku container:login
deploy:dev          — login → build → push + release（全サービス並列）
deploy:dev:open     — デプロイ済みアプリをブラウザで開く
deploy:dev:logs:*   — 各サービスのログ表示
```

### 代替案

| 案 | 却下理由 |
|----|---------|
| Heroku Git デプロイ（Buildpack） | Gradle マルチプロジェクト構成で Buildpack 設定が複雑になるため |
| Heroku `container:push` コマンド | `-f`（Dockerfile 指定）オプション非対応のため |
| Railway / Render | チームが Heroku の運用に慣れており、移行コストが高い |
| GitHub Actions からのデプロイのみ | ローカルからの即時デプロイを可能にしたかったため |

## 影響

### ポジティブ

- Gulp タスク 1 コマンド（`npx gulp deploy:dev`）で全サービスの並列デプロイが完結する
- `Dockerfile` と `Dockerfile.heroku` を分離することで、CI ビルドとデプロイビルドの責務が明確になった
- `getAppDomain()` で実際のドメインを動的取得するため、Heroku のランダムサフィックスドメイン問題に自動対応できる

### ネガティブ

- デプロイ前に `./gradlew bootJar` をローカルで実行する必要がある（`deploy:dev:build:backend` タスクが担当）
- Heroku Eco dyno の 512MB 制限により JVM のメモリオプション（`-XX:MaxRAMPercentage=50.0` 等）の調整が必要
- H2 インメモリ DB を使用するため dyno の再起動でデータが消える（開発・検証用途に限定）
- `GATEWAY_HOST` と `GATEWAY_URL` の 2 変数を管理する必要がある

## コンプライアンス

- `npx gulp deploy:dev` が全サービスエラーなく完了すること
- `heroku logs -a <app>` で各サービスが `Started ... in N seconds` ログを出力すること
- `curl -X POST https://<gatewayms-domain>/api/v1/auth/login` が `200 OK` + JWT を返すこと
- frontend からブラウザでログインが成功すること

## 備考

- 著者: k2works
- 関連 ADR: ADR-0003（GHCR 採用。本 ADR は開発環境用 Heroku デプロイを追加する位置づけ）
- 初期ユーザー: `admin` / `password`、`shipper` / `password`（`V005__add_admin_user.sql` で投入）

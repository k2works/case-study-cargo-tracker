# ADR-001: Heroku 環境の API ルーティングと CORS 設定を環境変数駆動に統一

Heroku デプロイで発生した `404` / `403` を防ぐため、Gateway ルーティングと CORS / proxy 設定を環境依存で解決する方針を採用します。

日付: 2026-04-25

## ステータス

承認済み

## コンテキスト

Heroku 上でフロントエンドからログインを実行した際に、`/api/v1/auth/login` で `404` と `403` が発生しました。

- `gatewayms` で Spring Cloud Gateway 5 系の設定キー未対応により route 定義が適用されない
- `gatewayms` が `localhost` 固定で他サービスにルーティングしており、PaaS 上のサービス間接続に不適合
- `frontend` の Nginx proxy で `Host` ヘッダーをフロント側に上書きし、Heroku Router 側で誤解釈される
- `authms` の CORS 許可 Origin が `http://localhost:3000` のみで、Heroku ドメインを拒否する

## 決定

Heroku 向けの API 経路を、コード固定値ではなく環境変数で解決する方式に統一します。

### 変更箇所

- `apps/backend/gatewayms/src/main/resources/application.yml`
  - `spring.cloud.gateway.server.webflux.routes` を使用
  - 各 route の `uri` を `AUTHMS_URL` などの環境変数参照へ変更
- `ops/scripts/deploy_dev.js`
  - `deploy:dev:config` で `heroku apps:info --json` の `web_url` を取得し、`API_GATEWAY_URL` と `*_URL` を自動設定
- `apps/frontend/nginx.conf`
  - `/api/` proxy 時の `Host` ヘッダーを `$proxy_host` へ変更
- `apps/backend/authms/src/main/java/com/example/authms/infrastructure/security/SecurityConfig.java`
  - CORS 許可を `setAllowedOriginPatterns` に変更し、`https://*.herokuapp.com` を許可

### 代替案

- 代替案 1: フロントエンドから各マイクロサービスへ直接アクセスする
  - 却下理由: クライアント側にサービス分離の知識が必要になり、API 境界が崩れる
- 代替案 2: Heroku 固有 URL を設定ファイルに固定記述する
  - 却下理由: アプリ再作成時に URL が変わるため保守性が低い
- 代替案 3: CORS を全許可 (`*`) にする
  - 却下理由: セキュリティ上の許容範囲を超える

## 影響

### ポジティブ

- Heroku 上でログイン API の 404 / 403 を解消できる
- 環境ごとの差分を環境変数に閉じ込め、同一コードで運用できる
- デプロイスクリプトによる設定自動化で手動ミスを減らせる

### ネガティブ

- `deploy:dev:config` 実行前提が強まり、手順逸脱時に不整合が起きる
- `*.herokuapp.com` 許可は開発効率が高い反面、許可範囲が広い

## コンプライアンス

以下を満たすことを確認します。

- `heroku config -a cargo-tracker-3-gatewayms` に `AUTHMS_URL` などの設定が存在する
- `POST /api/v1/auth/login` が
  - Gateway 直アクセスで `200`
  - Frontend 経由アクセスで `200`
- `authms` 応答ヘッダーに `Access-Control-Allow-Origin: https://<frontend>.herokuapp.com` が含まれる

## 備考

- 著者: Codex
- 関連コミット: `ba32a01`
- 関連 ADR: なし

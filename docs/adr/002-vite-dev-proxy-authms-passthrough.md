# ADR-002: 開発環境の Vite dev サーバーで authms を直接プロキシする二段階構成

ローカル開発時のフロントエンド／バックエンド連携経路として、Vite dev サーバーで `/api/v1/auth` を `authms` に直接、`/api` を `gatewayms` に振り分ける二段階プロキシを採用します。

日付: 2026-04-25

## ステータス

承認済み

## コンテキスト

ローカル開発では、フロントエンド (`apps/frontend`) を Vite dev サーバー (`localhost:3000`) で起動し、バックエンドの各マイクロサービスを個別ポートで起動します。

- `gatewayms` は `localhost:8080` で稼働し、`/api/v1/auth/**`、`/api/booking/**` 等を各サービスへルーティングする (`apps/backend/gatewayms/src/main/resources/application.yml:11`)
- `authms` は `localhost:8081` で稼働する (`apps/backend/authms/src/main/resources/application.yml:2`)
- 本番（Heroku）では、フロントエンドの Nginx (`apps/frontend/nginx.conf:12`) が `/api/` 全体を `${API_GATEWAY_URL}`（= Gateway）へプロキシする一段構成（ADR-001）

開発フローで以下の要望と摩擦が生じていました。

- 認証機能（ログイン）の実装・調試では、`authms` 単体を起動するだけで完結させたい（依存サービスの起動コストを下げたい）
- 一方で、`booking` / `routing` / `tracking` 等の API は Gateway のルーティングと JWT フィルタを通した状態で検証したい
- フロントエンドの API クライアント (`apps/frontend/src/lib/api-client.ts:23`) は単一の `BASE_URL` で `fetch` するため、サービス別に向き先を切り替える仕組みをコード側に持たせたくない

この摩擦を `vite.config.ts` のプロキシ設定で解消する方針を選びます。

## 決定

Vite dev サーバーのプロキシで、認証パスと汎用 API パスを別ターゲットに振り分ける二段階構成を採用します。

### 変更箇所

- `apps/frontend/vite.config.ts:10-19`
  - `/api/v1/auth` → `http://localhost:8081`（`authms` 直結）
  - `/api` → `http://localhost:8080`（`gatewayms` 経由）
  - 登録順かつ最長一致で `/api/v1/auth` が優先される前提に依存

```ts
proxy: {
  '/api/v1/auth': {
    target: 'http://localhost:8081',
    changeOrigin: true,
  },
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true,
  },
},
```

### 代替案

- 代替案 1: dev でも `/api/**` を全て Gateway 経由（`localhost:8080`）に統一する
  - 却下理由: 認証単体の検証でも Gateway の起動が必須になり、開発体験が低下する。Gateway の Spring Cloud Gateway 設定変更時に毎回再起動コストが発生する
- 代替案 2: dev で全エンドポイントを各マイクロサービスへ直結する
  - 却下理由: フロントエンドが各サービスのポートを把握する必要があり、Gateway のルーティングと JWT フィルタが dev で全く検証されなくなる
- 代替案 3: フロントエンド API クライアントに環境別の向き先切替ロジックを持たせる
  - 却下理由: 開発/本番の差分がアプリケーションコードに侵入し、ビルド成果物にも影響する。プロキシ層に閉じ込めた方が責務分離として適切
- 代替案 4: Vite プロキシではなく、各バックエンドサービス側で CORS を許可してブラウザから直接呼ぶ
  - 却下理由: dev だけ CORS 構成を緩める必要があり、本番との挙動差が大きくなる。複数オリジンの管理コストも増える

## 影響

### ポジティブ

- `authms` のみ起動した状態でログイン機能を完結検証できる（依存サービスの起動コストを最小化）
- 認証以外の API 呼び出しは dev でも Gateway を経由するため、ルーティングと JWT フィルタの動作確認ができる
- フロントエンドの API クライアント (`apps/frontend/src/lib/api-client.ts`) を環境差分から完全に切り離せる
- 本番（Heroku）の Nginx 一段プロキシ構成（ADR-001）と矛盾せず、エンドポイントパスは共通

### ネガティブ

- dev では Gateway の JWT フィルタ（コミット `14dfe2e` で追加された公開パス検証）がログインリクエストに対して実行されない
- dev/prod でログインのリクエスト経路が異なるため、Gateway 起因の認証バグは Heroku 環境または Gateway 起動状態でしか再現しない
- プロキシのマッチ順序（より具体的な `/api/v1/auth` が `/api` に先んじる）に依存しており、認証以外の `/api/v1/...` パスを将来導入する場合は順序変更や明示分割が必要

## コンプライアンス

以下を満たすことを確認します。

- `apps/frontend/vite.config.ts` に `/api/v1/auth` → `localhost:8081`、`/api` → `localhost:8080` の 2 ルールが順番通りに存在する
- `authms` のみ起動した状態で、`http://localhost:3000/api/v1/auth/login` が `200` を返す
- `gatewayms` と `authms` を両方起動した状態で、`/api/v1/auth/login` 以外の `/api/**` が Gateway 経由で各サービスに到達する
- 本番 Heroku では `apps/frontend/nginx.conf` 経由で `/api/` 全体が Gateway にプロキシされる（ADR-001 のコンプライアンス項目で確認）

## 備考

- 著者: Claude
- 関連コミット: `104b593`（`vite.config.ts` で `/api/v1/auth` を `localhost:8081` に振り分ける設定を導入したコミット）
- 関連 ADR: ADR-001（本番環境の API ルーティング方針）

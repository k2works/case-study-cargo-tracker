# 0010 セッション認証方式 (opaque Cookie + Servant Auth + Postgres KV)

IT5 で Servant Auth によるセッション Cookie 認証を導入する際の選定方針

日付: 2026-07-01

## ステータス

**採用** (2026-07-02、IT5 で発行・IT6 で middleware 完了)

- 2026-07-01 (IT5): `session` テーブル migration + Session VO + Postgres KV +
  POST /login での Session Cookie 発行 (`cargo_session=<token>; HttpOnly; SameSite=Lax; Max-Age=28800`)
  が完了
- 2026-07-02 (IT6 T5-01): `Cargotracker.Shared.Auth.Interfaces.SessionAuth` で
  AuthProtect middleware (`requireCookieAuth` / `cookieProtectedApp`) が完了。
  ADR-0010 の段階移行 (Session 発行 → middleware での検証) はここで一巡した

## コンテキスト

IT1 で **JWT (HS256, HMAC-SHA256) 認証は既に実装済** (`Cargotracker.Shared.Auth.Infrastructure.JwtIssuer`)。API 呼び出し (Bearer token) 想定で実装されているが、Web ブラウザからの操作では以下が問題:

- localStorage / JavaScript から JWT を扱うと XSS で漏洩リスク
- HttpOnly Cookie に載せる必要があるが、その場合 CSRF 対策必須
- E2E (Playwright) の login fixture 実装で必要

IT5 では **既存 JWT (API 用) を維持したまま、Web 用にセッション Cookie を追加** する。API と Web で認証層を分離することで、それぞれに最適な方式を選択できる。

Web 用 Cookie の選択肢:

- (A) **opaque Cookie + Postgres KV**: 短い乱数を Cookie 値とし、サーバ側 `session` テーブルで検証。失効即時反映可能、サーバ側で無効化・強制ログアウト可能
- (B) **JWT を HttpOnly Cookie で搬送**: 既存 JwtIssuer 流用可能。ただし失効即時反映不可 (revocation list が必要)
- (C) **JWT + Refresh Token (Postgres KV)**: 短命 JWT + 長命 Refresh。実装量最大

本プロジェクトの規模 (単一 ECS Fargate タスク、想定同時 50 セッション) では水平スケールの必要性が低く、Web セッションは失効即時反映と実装単純さを優先すべき。

## 決定

**Web セッションに (A) opaque Cookie + Postgres KV を採用する**。既存 IT1 JWT (API 用 Bearer token) は維持する。

- **Web セッション**: opaque Cookie
  - Cookie 値: `crypto-random` 256bit → base64url 44 文字。HttpOnly / Secure / SameSite=Lax
  - サーバ側テーブル: `session (id BIGSERIAL, session_token VARCHAR(64) UK, user_id BIGINT FK users, expires_at TIMESTAMPTZ, last_used_at TIMESTAMPTZ, created_at TIMESTAMPTZ)`
  - Servant 側実装: `AuthProtect "session-cookie"` + カスタム `AuthHandler` (Cookie → session lookup → users JOIN → AuthenticatedUser)
  - 有効期限: 8 時間 (business day)、`last_used_at` 更新で sliding window
  - ログアウト: `DELETE session WHERE session_token = ?` で即時無効化
- **API セッション**: 既存 JWT (HS256) をそのまま維持
  - `Authorization: Bearer <jwt>` ヘッダで受け取り、既存 `JwtIssuer.verifyAndDecode` で検証
  - 用途: 外部システム連携 / モバイル / SPA (将来)
- **CSRF**: Web セッションのみ対象。別 Cookie で Double Submit Cookie パターン (既存 architecture_frontend.md M-04 準拠)。JWT Bearer は Origin 不一致で CSRF リスクなし
- **併用時のルール**: 1 リクエストは Web Cookie / JWT Bearer のいずれか一方のみ。両方存在時は Web Cookie を優先し JWT は無視 (混在バグ防止)

## 影響

- **新規テーブル**: `session` (IT5 migration `20260831100000_create_session.sql` として追加、独立 = tracking_activity 依存なし)
- **依存追加**: `servant-auth-server` (既に tech_stack.md に記載)、`crypto-random` または `entropy`
- **既存への影響**: なし (JWT 認証は API 用として維持、Web は新規追加)
- **将来の水平スケール時**: Postgres が SPOF になるため、その時点で Redis 移行を再検討

## 段階移行計画

- **IT5 (完了)** task 1.2: `session` テーブル migration + POST /login で Session Cookie 発行
  (opaque token 44 文字 + HttpOnly + SameSite=Lax + Max-Age=28800)
- **IT6 T5-01 (完了 2026-07-02)**: AuthProtect middleware 実装
  (`Cargotracker.Shared.Auth.Interfaces.SessionAuth`)
  - `resolveCookieUser :: SessionRepository -> ... -> Maybe AuthenticatedUser` (純粋関数)
  - `requireCookieAuth :: ... -> Handler AuthenticatedUser` (Handler ラッパ、401 発行)
  - `cookieProtectedApp` で任意の Servant ハンドラを Cookie 認証で保護できるようにした
- **IT7+**: 保護対象拡張 (既存 Confirm/Cancel/Link/Unlink/EvaluateRoute への適用と Role-based 権限)
  - Role-based 権限 (Shipper / Sales / RouteDesigner / Handler / Tracker / Accountant / Admin)
  - `requireRoleFromCookie :: Role -> AuthenticatedUser -> Handler ()` の追加

## 関連

- `docs/design/architecture_backend.md` §セキュリティ設計 (Servant Auth ロール定義)
- `docs/design/architecture_frontend.md` §CSRF 対策 M-04 (Double Submit Cookie)
- `docs/development/iteration_plan-5.md` task 1.2 / 1.3
- IT4 完了報告書 (login fixture 追加、navigation-lists test を IT5 セッション実装まで skip)

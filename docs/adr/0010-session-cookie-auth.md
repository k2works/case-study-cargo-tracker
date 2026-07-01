# 0010 セッション認証方式 (opaque Cookie + Servant Auth + Postgres KV)

IT5 で Servant Auth によるセッション Cookie 認証を導入する際の選定方針

日付: 2026-07-01

## ステータス

提案 (2026-07-01、IT5 task 1.3 で実装予定)

## コンテキスト

IT1-4 まで認証は非機能要件として存在しつつも、HTTP ハンドラの Servant 結線 (IT4 繰越 task 1.1) を実装する段階で本格的な認証基盤が必要になった。E2E テスト (IT4 完了時に navigation-lists 1 件が login fixture 実装まで skip 状態) を成立させるためにも IT5 冒頭で認証を確定する必要がある。

Haskell / Servant エコシステムでの選択肢:

- (A) **opaque Cookie + Postgres KV**: 短い乱数 (256bit) を Cookie 値とし、サーバ側 `session` テーブルに `user_id` / `expires_at` を保持。サーバ側で無効化・強制ログアウトが可能
- (B) **JWT (署名付き Cookie)**: HMAC 署名した JSON を Cookie に埋め込む。サーバ側状態不要、水平スケール容易。ただし失効即時反映不可
- (C) **JWT + Refresh Token (Postgres KV)**: 短命 JWT + 長命 Refresh。実装量最大

Java/take-2 / Scala/take-1 は (B) JWT ステートレスを採用。ただし本プロジェクトの規模 (単一 ECS Fargate タスク、想定同時 50 セッション) では水平スケールの必要性が低く、失効即時反映と実装単純さを優先すべき。

## 決定

**(A) opaque Cookie + Postgres KV を採用する**。

- Cookie 値: `crypto-random` 256bit → base64url 44 文字。HttpOnly / Secure / SameSite=Lax
- サーバ側テーブル: `session (id BIGSERIAL, session_token VARCHAR(64) UK, user_id BIGINT FK users, expires_at TIMESTAMPTZ, created_at TIMESTAMPTZ)`
- Servant 側実装: `AuthProtect "session-cookie"` + カスタム `AuthHandler` (Cookie → session lookup → users JOIN → AuthenticatedUser)
- 有効期限: 8 時間 (business day)、更新は都度延長 (sliding window)
- ログアウト: `DELETE session WHERE session_token = ?` で即時無効化
- CSRF: 別 Cookie で Double Submit Cookie パターン (既存 architecture_frontend.md M-04 準拠)

## 影響

- **新規テーブル**: `session` (IT5 migration 015 として追加、confirmation_code の後)
- **依存追加**: `servant-auth-server` (既に tech_stack.md に記載)、`crypto-random` または `entropy`
- **既存への影響**: なし (IT1-4 は認証未実装なため)
- **将来の水平スケール時**: Postgres が SPOF になるため、その時点で JWT (B) or Redis 移行を再検討 (ADR で追記)

## 段階移行計画

- IT5 task 1.2: `session` テーブル migration + `AuthHandler` 実装 + `/auth/login` `/auth/logout` エンドポイント
- IT5 task 1.1: 既存 Servant ハンドラ (Confirm/Cancel/Link/Unlink/EvaluateRoute) に `AuthProtect` を追加
- IT6+: Role-based 権限拡張 (Shipper / Sales / RouteDesigner / Handler / Tracker / Accountant / Admin)

## 関連

- `docs/design/architecture_backend.md` §セキュリティ設計 (Servant Auth ロール定義)
- `docs/design/architecture_frontend.md` §CSRF 対策 M-04 (Double Submit Cookie)
- `docs/development/iteration_plan-5.md` task 1.2 / 1.3
- IT4 完了報告書 (login fixture 追加、navigation-lists test を IT5 セッション実装まで skip)

# ADR 0002: 認証を tower-sessions + 自前 RBAC で実装する（IT1）

## ステータス

承認（IT1 時点）

## コンテキスト

[技術スタック](../design/tech_stack.md) および [バックエンドアーキテクチャ](../design/architecture_backend.md) では、認証・認可を **axum-login 0.17 + tower-sessions** で実装し、`AuthnBackend` / `AuthzBackend` による RBAC を採る方針としていた。

IT1（US-AUTH-01）でウォーキングスケルトンの認証基盤を実装するにあたり、以下を考慮した。

- IT1 に必要な認証要件は「フォームログイン・セッション保持・未認証リダイレクト・ロール別ナビ出し分け・ルート単位のロール認可（403）・ログアウト」であり、比較的単純である
- axum-login 0.17 の `AuthnBackend` trait は `Credentials` / `User` / `Error` 関連型と `authenticate` / `get_user` の実装を要し、セッションとの結合仕様も含めて API 面が大きい
- 認証の中核ロジック（パスワード検証・ユーザー/ロール取得）は既に `infra-persistence` の `SqlxUserRepository`（argon2）+ testcontainers 統合テストで検証済みであり、フレームワークに依存しない

## 決定

IT1 では認証を **tower-sessions（`MemoryStore`）+ 自前の軽量 RBAC** で実装する。

- ログイン成功時に `CurrentUser`（username + roles）をセッションに保存する
- 保護ルートは `require_user` ヘルパーでセッションから `CurrentUser` を取り出し、未認証は `/login` へリダイレクトする
- ロール認可はハンドラ内で `CurrentUser::has_role` を確認し、不許可は `403 Forbidden` を返す
- パスワード検証は `SqlxUserRepository::find_credentials` + argon2 `verify_password` に委譲する

`AuthnBackend` / `AuthzBackend` の trait 実装は導入せず、認証ロジックをアプリケーション側の明示的なコードで保持する。

## 影響

- 実装・テストが単純化され、ログイン/未認証リダイレクト/ロール別ナビ/403 を testcontainers + tower `oneshot` で検証できた
- セッションストアは `MemoryStore` を使用しており、**PostgreSQL セッションストアへの移行はハードニング項目**として残る（プロセス再起動でセッションが失われる／水平スケール不可）
- axum-login への移行余地は残す。ルート単位認可がハンドラ内チェックに散らばるため、ルート数が増える中盤以降で `login_required!` / `permission_required!` 相当のミドルウェア化（axum-login 採用）を再評価する
- 本 ADR は `tech_stack.md` / `architecture_backend.md` の「axum-login」記述に対する IT1 時点の意図的な逸脱を記録するものである。中盤で axum-login を採用する場合は本 ADR を Superseded とし後続 ADR を起票する

# ADR-0005: Cookie 認証 + `users`/`user_roles` による RBAC を採用

認証方式・ユーザーストア・ロールモデルの決定

日付: 2026-07-14

## ステータス

2026-07-14 提案

## コンテキスト

Cargo Tracker は 8 アクター（営業担当者・経路設計者・追跡管理者・荷役作業員・経理担当者・運用管理者・荷主・荷受人）がロールに応じた画面・機能のみを利用します。認証・認可の実装にあたり、以下の設計判断が必要です。

1. **認証基盤**: ASP.NET Core Identity を導入するか、軽量な Cookie 認証を自前で構成するか。
2. **フレームワーク統合**: 本システムは Giraffe（`HttpHandler` DSL）を Primary Adapter とするため、認可は Giraffe の `requiresAuthentication` / `requiresRole` と統合する必要がある。
3. **ロールモデル**: 1 ユーザー 1 ロール（単一カラム）か、多対多（別テーブル）か。data-model.md は `users` + `user_roles` の 2 テーブルを定義している。

## 決定

**ASP.NET Core Cookie 認証 + Donald 軽量ユーザーストア + `users`/`user_roles`（多対多）による RBAC を採用します。**

1. **Cookie 認証**: ASP.NET Core Identity は導入せず、Cookie 認証ミドルウェアを構成する。ログインパス・未認証リダイレクト・公開パス（`/public/tracking/{accessToken}`・`/health`）の除外を設定する。CSRF 保護を有効化する。
2. **軽量ユーザーストア**: `users`（`username`・`email`・`password`・`enabled`）を Donald リポジトリ（ADR-0004）で自前管理する。パスワードは PBKDF2 相当でハッシュ化して保存する。
3. **`users` + `user_roles` の多対多ロール**: ロールは data-model.md（正）に従い `user_roles`（`user_id` + `role`）テーブルで管理する。ロール名は `ROLE_SALES` 等の `ROLE_` プレフィックス表記とし、ui_design.md のロール別ナビゲーションマトリクスを正とする。
4. **Giraffe 統合**: 認可は `requiresAuthentication` / `requiresRole` で行い、各 `HttpHandler` にロール条件を付与する。ナビゲーション・ダッシュボードもロール条件で表示制御する。

根拠は以下のとおりです。

1. **軽量・統合容易**: Identity のフルスタック（EF Core 依存・大量のテーブル）は本システムに過剰。Cookie 認証 + Donald ストアなら ADR-0004 の永続化パターンに一貫して載せられ、Giraffe と直接統合できる。
2. **将来の多ロール要件**: `user_roles` の多対多は、1 ユーザーが複数ロール（例: 追跡管理者かつ経理担当者）を持つ将来要件に構造的に対応でき、data-model.md との整合も取れる。

## 代替案

- **ASP.NET Core Identity**: 実績はあるが EF Core 前提で Donald 方針（ADR-0004）と二重管理になり、Giraffe との統合も冗長。
- **単一 role カラム**: 実装は簡素だが多ロール要件に対応できず、data-model.md（`user_roles` 別テーブル）と矛盾する。

## 影響

- IT1 で `users` / `user_roles` リポジトリ・ログイン/ログアウト画面・ロール別ナビゲーション・シードユーザーを実装する。
- 認可ロジックは `HttpHandler` 合成（`requiresRole`）に集約され、ドメイン層は認証を関知しない。
- ロール表記は全画面・ナビで `ROLE_` プレフィックスに統一する。

## コンプライアンス

- 公開パス（`/public/tracking/{accessToken}`・`/health`）以外は未認証アクセスをログイン画面へリダイレクトすること（統合テストで確認）。
- パスワードは平文保存しないこと（コードレビューで確認）。
- ロール別ナビゲーション表示は ui_design.md のマトリクスと一致すること（ナビ表示の検証テスト）。

## 備考

著者: アーキテクト（Claude Code 支援）。関連: ADR-0004（Donald 永続化）、`docs/design/data-model.md`（Security Context）、`docs/design/architecture_backend.md`（認証・認可）、`docs/design/ui_design.md`（ロール別ナビゲーションマトリクス）、IT1 計画のタスク 2.1-2.6。

# ADR-0004: Cookie 認証と Dapper 軽量ユーザーストア

full な ASP.NET Core Identity（EF Core 前提）を導入せず、Cookie 認証スキーム + Dapper による軽量ユーザーストア + `PasswordHasher` の組み合わせで認証・認可を実装する。

日付: 2026-07-08

## ステータス

2026-07-08 承認されました

## コンテキスト

tech_stack.md および architecture_backend.md では認証・認可に「ASP.NET Core Identity」「AspNetUsers テーブル」を採用する記述がある。一方、本プロジェクトはデータアクセスを Dapper + 手書き SQL に統一し（ADR-0001）、スキーマ管理を DbUp で行う（ADR-0003）方針である。

full な ASP.NET Core Identity は既定で Entity Framework Core を永続化に用い、独自のスキーマ（`AspNetUsers` / `AspNetRoles` / `AspNetUserRoles` 等）とマイグレーション体系を持ち込む。これは以下の点で本プロジェクトの方針と衝突する。

- EF Core の導入は「Dapper に統一」（ADR-0001）と矛盾し、O/R マッパーが二重になる
- Identity のスキーマ生成は DbUp（forward-only・二方言）による一元管理（ADR-0003）の外側に置かれる
- US26 の要件（ユーザー ID/パスワードでのログイン、ロール別アクセス制御、パスワードのハッシュ化）に対して Identity の全機能（メール確認・2FA・ロックアウト・外部プロバイダ等）は過剰である

IT1（US26）の設計整合検証（validating-design 2026-07-08・指摘 B-1）で、実装が Identity ではなく軽量構成を採っていることが設計ドキュメントとの乖離として検出された。

## 決定

**軽量構成を採用する**。full な ASP.NET Core Identity は導入しない。

1. **認証スキームは ASP.NET Core の Cookie 認証**（`AddAuthentication().AddCookie()`）のみを使用する。`LoginPath = /login`・`AccessDeniedPath = /login`・スライディング有効期限。
2. **ユーザーストアは Dapper + `users` テーブル**（DbUp スクリプト 0001）。EF Core は導入しない。永続化ポートは `IUserRepository`（`Shared/Infrastructure/Auth`）。
3. **パスワードのハッシュ化は `Microsoft.AspNetCore.Identity.PasswordHasher<T>`** のみを利用する（PBKDF2・適応的ハッシュ、BCrypt 相当）。full Identity のユーザー管理機能は使わない。
4. **ロールは `users.role` の単一カラム**で保持する（1 ユーザー 1 ロール）。`user_roles` テーブルや多対多は導入しない（YAGNI）。多ロール要件が発生した時点で再検討する。
5. **認可はロール Claim ベース**（`[Authorize(Roles = ...)]` とグローバル認可フィルタ）。ログイン時にユーザーのロールを `ClaimTypes.Role` に載せる。

### ロールの正準集合

ロール名は `ROLE_` プレフィックス付きの以下を正準とする（ユビキタス言語の統一。ui_design のナビゲーション構成に準拠）。

| 定数 | 値 | 対象アクター |
| :--- | :--- | :--- |
| `Roles.Admin` | `ROLE_ADMIN` | システム管理者 |
| `Roles.Sales` | `ROLE_SALES` | 営業担当者 |
| `Roles.RouteDesigner` | `ROLE_ROUTE_DESIGNER` | 経路設計者 |
| `Roles.Tracker` | `ROLE_TRACKER` | 追跡管理者 |
| `Roles.Handler` | `ROLE_HANDLER` | 荷役作業員 |
| `Roles.Billing` | `ROLE_BILLING` | 経理担当者 |

`ROLE_SHIPPER`（荷主）・`ROLE_CONSIGNEE`（荷受人）は外部向けポータルのロールとして **後続イテレーションに繰り延べる**。それまで貨物追跡・予約照会は社内ロール（`ROLE_TRACKER`・`ROLE_SALES`）で代替する。

### 代替案

| 選択肢 | 却下理由 |
| :--- | :--- |
| A. full ASP.NET Core Identity（EF Core） | Dapper 統一（ADR-0001）と DbUp 一元管理（ADR-0003）に反する。要件に対し過剰 |
| B. 独自のパスワードハッシュ実装 | 車輪の再発明。`PasswordHasher` が標準で安全な PBKDF2 を提供する |

## 影響

### ポジティブ

- データアクセスが Dapper に一貫し、スキーマが DbUp に一元化される
- 依存が最小（追加パッケージ不要。`PasswordHasher` は共有フレームワーク同梱）
- US26 の要件を満たす最小構成で、テスト容易性が高い（`UserAuthenticator` の単体テスト・`UserRepository` の Testcontainers 統合テスト）

### ネガティブ

- メール確認・2FA・ロックアウト・パスワードポリシー等は将来必要になった時点で自前実装が要る
- 多ロール・きめ細かい権限が必要になった場合は `user_roles` 導入の再設計が要る（本 ADR を置換する新 ADR で対応）

## コンプライアンス

- `WebApplicationFactory` 統合テストで、未認証リダイレクト・匿名許可パス・ログイン成否・ログアウト・ロール別アクセスを検証する（US26 受入条件）
- `UserRepository` の SQL は ADR-0003 の方言禁止規約に従う（`NOW()`/`RETURNING` 不使用）

## 備考

- 起票: 設計整合検証 validating-design 2026-07-08（指摘 B-1）
- 関連: ADR-0001（集約永続化・Dapper 統一）、ADR-0003（二方言運用・DbUp）
- 関連更新: tech_stack.md（認証行）・architecture_backend.md（セキュリティ設計・ロール表）・iteration_plan-1.md（タスク 2.2・スキーマ）

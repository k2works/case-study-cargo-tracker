# ADR-006: 認証はセッションベースの自作ガードとし Passport を採用しない

認証・認可は express-session によるセッションと自作の `AuthenticatedGuard` / `RolesGuard` + `AuthenticationService` で実装し、Passport（`@nestjs/passport` / `passport-local`）は採用しない。開発時のトランスパイルは SWC（`@swc-node/register`）、本番ビルドは `tsc` とする。

日付: 2026-07-28

## ステータス

承認済み（ADR-001 のセキュリティ実装方針のうち「Passport 戦略」部分を更新する）

## コンテキスト

ADR-001 および `docs/design/architecture_backend.md` は、Spring Security を「NestJS Guard + Passport（LocalStrategy）」へ移植する方針を掲げていた。IT1（US26/US27）で認証を実装するにあたり、以下の要件が明確になった。

- **SSR + htmx** 構成のため、認証失敗・未認証時は JSON ではなく **画面リダイレクト**（`/login` へ）で制御したい
- US26 の受入基準に **ログイン失敗 5 回でのアカウント一時ロック**・**無効化アカウント判定**・**失敗別メッセージ**があり、認証結果を複数の分岐（成功 / 資格情報不一致 / ロック / 無効化）で表現する必要がある
- Passport の `LocalStrategy` は成功/失敗の二値が基本で、上記の多分岐・リダイレクト制御を素直に表現しにくい

また、tsx（esbuild）は `emitDecoratorMetadata` を出力しないため NestJS の DI が壊れる問題があり、開発実行に別のトランスパイラが必要になった。

## 決定

- **認証方式**: `express-session` に`AuthenticatedUser`（ビューモデル）を保持するセッションベース認証とする。ドメインオブジェクトはセッションに載せない
- **認可**: 自作 `AuthenticatedGuard`（未認証→`/login` リダイレクト）と `RolesGuard` + `@Roles` デコレータ（権限不足→403）で実装する
- **認証ユースケース**: `AuthenticationService` が資格情報照合・無効化判定・失敗ロックを担い、結果を判別可能ユニオン（`SUCCESS` / `INVALID_CREDENTIALS` / `LOCKED` / `DISABLED`）で返す。ロック閾値は `account-lock-policy`（純粋関数）に分離する
- **Passport は採用しない**。`@nestjs/passport` / `passport` / `passport-local` の依存は削除する（未使用依存によるサプライチェーン負債を避ける）
- **実行トランスパイラ**: 開発（`dev` / `serve`）・E2E は `@swc-node/register`（`.swcrc` で `decoratorMetadata: true` / React automatic runtime）、本番ビルド（`build`）は `tsc`。テスト（Vitest）は `unplugin-swc` で同等設定

## 影響

- ADR-001・`architecture_backend.md` のセキュリティ設計（LocalStrategy 前提の記述）は本 ADR で更新される。設計図の Passport 表記は将来の設計ドキュメント改訂時に本 ADR に合わせて是正する
- 認証を将来 OIDC / SAML 等の外部 IdP 連携に拡張する場合は、Passport 戦略の再導入を別 ADR で判断する

### 代替案

- **Passport LocalStrategy を採用**: Spring からの移植として自然だが、SSR リダイレクト制御と US26 の多分岐（ロック・無効化）を表現するのにアダプタ層が増え、かえって複雑になる。却下（外部 IdP 連携が必要になった時点で再検討）
- **tsx で実行**: 依存が最小だが `emitDecoratorMetadata` 非対応で NestJS DI が壊れる。却下
- **全実行を tsc に統一**: 一貫するが開発時の起動が遅い。SWC（dev）/ tsc（build）の二本立てを採用

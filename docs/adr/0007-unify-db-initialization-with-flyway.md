# ADR-0007: DB 初期化を Flyway に統一

`local-h2` を含む全プロファイルで DB 初期化を Flyway migration に統一し、`schema.sql` を廃止します。

日付: 2026-05-22

## ステータス

承認済み

## コンテキスト

`authms` と `routingms` では、プロファイルによって `schema.sql` と Flyway migration が混在していました。  
この状態により、次の課題が発生していました。

- `local-h2` では `schema.sql` が使われ、`local-docker` では Flyway が使われるため、スキーマが乖離しやすいです。
- `routingms` で `VARCHAR` 長の不整合が起き、`DataIntegrityViolationException` が発生しました。
- migration に PostgreSQL 方言依存の SQL（`ON CONFLICT`）や制約名依存の記述があり、H2 で再利用できない箇所がありました。
- 環境ごとの初期化方式が異なるため、障害再現性と検証コストが悪化していました。

## 決定

DB 初期化方式を Flyway migration に統一します。

- `authms` / `routingms` の `local-h2` と `heroku` で `spring.flyway.enabled=true` を使用します。
- `spring.sql.init.mode=never` とし、`schema.sql` / `data.sql` による初期化を無効化します。
- `authms` / `routingms` の `schema.sql` は削除します。
- 既存 migration は H2 と PostgreSQL の双方で動作するように修正します。

### 変更箇所

- `apps/backend/authms/src/main/resources/application-local-h2.yml`
- `apps/backend/authms/src/main/resources/application-heroku.yml`
- `apps/backend/authms/src/main/resources/db/migration/V2__insert_initial_users.sql`
- `apps/backend/routingms/src/main/resources/application-local-h2.yml`
- `apps/backend/routingms/src/main/resources/application-heroku.yml`
- `apps/backend/routingms/src/main/resources/db/migration/V3__expand_voyage_number_length.sql`
- `apps/backend/authms/src/main/resources/schema.sql`（削除）
- `apps/backend/routingms/src/main/resources/schema.sql`（削除）

### 代替案

- 代替案 1: `schema.sql` を維持し、migration と毎回同期する  
  却下理由: 同期漏れが発生しやすく、実際に列長不整合が障害化したためです。
- 代替案 2: `local-h2` のみ入力データを短くして回避する  
  却下理由: 根本原因である初期化方式の分岐が残り、再発防止にならないためです。
- 代替案 3: `local-docker` のみを正式経路として H2 を縮退運用する  
  却下理由: 開発の最速ループ（`local-h2`）の価値を失い、TDD 効率を下げるためです。

## 影響

### ポジティブ

- プロファイル間のスキーマ整合性が向上します。
- H2 でも本番相当の migration 経路を通るため、再現性が高まります。
- 初期化ロジックが一元化され、保守性が向上します。

### ネガティブ

- 既存 DB で migration ファイル内容を変更した場合、Flyway checksum 差異対応（`repair`）が必要になることがあります。
- migration を cross-db で維持するため、SQL 記述に制約が増えます。

## コンプライアンス

次を満たすことで、決定の実装完了を確認します。

- `authms` / `routingms` / `gatewayms` の `local-h2` スモークテストが通過すること。
- `authms` / `routingms` の `local-h2` 起動ログで Flyway migration 適用が確認できること。
- `schema.sql` が `authms` / `routingms` に存在しないこと。
- E2E 実行時に列長不整合による `DataIntegrityViolationException` が再発しないこと。

## 備考

- 著者: Codex
- 関連 ADR: ADR-0002

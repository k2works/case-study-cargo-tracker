# ADR 0001: Bounded Context の構成と Packwerk パック配置

設計ドキュメント間で分裂していたコンテキスト境界とパッケージ配置の正本を確定する。

日付: 2026-07-07

## ステータス

2026-07-07 承認されました

## コンテキスト

Java/Spring Boot 版設計を Rails 版に翻案した際、設計ドキュメント間で以下の不整合が生じました。

- Bounded Context 数が architecture_backend.md（6）、data-model.md（7）、domain-model.md（8）で分裂
- Packwerk のパック配置パスが `packs/*`、`app/domains/*`、`app/packages/*` の 3 通りに混在

コンテキスト境界は DDD の中核成果物であり、パック配置は Zeitwerk のオートロードと SimpleCov のレイヤー別カバレッジ計測に直結するため、実装着手前に正本（SSOT）の確定が必要です。

## 決定

1. **Bounded Context は domain-model.md を正典とする 8 コンテキスト**とします: Booking / Shipper / Routing / Tracking / Handling / Billing / Estimation / Shared Domain。
   - Estimation は US01（見積）・US08（経路候補算出）の受け皿として独立を維持します。
   - Shipper は荷主管理の独立性（US02/US03、Booking からは ACL 経由参照）を理由に独立を維持します。将来 Booking との統合が妥当と判明した場合は本 ADR を改訂します。
2. **Packwerk のパック配置は Shopify 標準の `packs/<context>/app/...`** とします（例: `packs/booking/app/domain/booking/aggregates/cargo.rb`）。
   - SimpleCov のグループ定義は `%r{packs/.+/app/domain}` 形式で統一します。
3. **「ドメイン層に Active Record 依存を持ち込まない」ルールは Packwerk では検証できない**ため、RuboCop カスタム cop（domain ディレクトリ内での `ApplicationRecord` / `ActiveRecord` 定数参照の禁止）で担保します。Packwerk はパック間参照と privacy の検証に限定して使用します。

## 影響

- 全設計ドキュメント（architecture_backend / data-model / domain-model / test_strategy / tech_stack / index）のコンテキスト数・パス表記を本 ADR に合わせて統一する。
- 実装時のディレクトリ構成・package.yml・.rubocop.yml のカスタム cop 設定が本 ADR に従う。

## コンプライアンス

- CI で `bin/packwerk check` と RuboCop カスタム cop を実行し、境界違反をビルド失敗として検出します。
- 設計ドキュメントのコンテキスト数・パス表記のレビュー時チェック項目とします。

## 備考

- 著者: 開発チーム（analyzing-review 2026-07-07 の指摘 #1・#2・#22 に基づく）
- 関連: docs/review/設計ドキュメント_review_20260707.md

# Changelog

国際貨物輸送管理システム（Cargo Tracker）Scala 版の変更履歴。

形式: [Keep a Changelog](https://keepachangelog.com/ja/1.1.0/) / バージョニング: [Semantic Versioning](https://semver.org/lang/ja/)。

## [Unreleased]

### Added

- (IT3 以降の追加機能をここに記録)

---

## [0.1.0] - 2026-07-19 — Release 0.1 Internal Alpha

Phase 1 完了（IT1 + IT2）。Booking / Shipper / Estimation / Routing コンテキストの予約・経路設計引き渡し・航海スケジュール管理が動作する内部デモ版。

### Added

#### IT1（2026-06-22 〜 2026-07-05）

- **US26 認証**: ログイン / ログアウト / 30 分スライディングタイムアウト、bcrypt パスワードハッシュ、Play Session ベース、AdminUserSeeder
- **US02 / US03 荷主登録**: 個人 / 法人荷主、自動採番 ShipperId（SH-NNNNNN）、契約番号・割引率
- **US01 輸送見積**: PricingService（モック）、Estimate 集約、ルート候補生成
- **US04 貨物予約**: Cargo 集約、BookingId（BK-NNNNNN）、ShipperExistenceChecker ACL、HazardousDeclaration

#### IT2（2026-07-06 〜 2026-07-19）

- **US05 危険物・冷凍貨物予約**: RefrigerationSpec / TemperatureUnit 値オブジェクト、CargoSpec.create 条件付き必須バリデーション、Flyway V6
- **US06 経路設計者引き渡し**: BookingStatus.canTransitionTo、Cargo.assignToRouting、POST `/bookings/:id/assign-routing`、経路設計者ダッシュボード（RouteProposed 一覧）
- **US24 航海スケジュール登録**: Voyage 集約 + Schedule + CarrierMovement + VoyageNumber（opaque type）、Flyway V7
- **US25 航海スケジュール更新**: PRG で `/voyages/:voyageNumber/edit`、CarrierMovement 全削除 + 再挿入
- **Spike US08**: RouteCandidateSearchSpike（DFS + 深さ制限、純関数）、ADR 0005
- **ロール別ダッシュボード**: Sales / RouteDesigner / Tracker / Settlement / MasterAdmin、ナビバーロール別表示

### Changed

- ヘキサゴナル DDD パッケージ構成へ全面再構成（`domain/model/{aggregates,valueobjects,repositories,acl}` / `application/{commandservices,queryservices,outboundservices/acl}` / `infrastructure/{repositories,services}` / `interfaces/web`）
- 4 Controller のビジネスロジック・永続化を application 層に移譲（AuthCommandService / ShipperCommandService / EstimateCommandService / BookingCommandService / VoyageCommandService）
- 3 Controller に AuthenticatedAction を適用（type-safe な username / roles アクセス）
- HomeController をロール別ダッシュボードに刷新
- AdminUserSeeder の資格情報を application.conf に外出し（IT1 H1）
- Twirl テンプレート `form.scala.html` → `formPage.scala.html`（helper.form 衝突回避）
- scoverage 最低ゲート 75% → 80% に復元（実績 82.34%）
- pre-commit hook から scalafix を CI 専用に移動（30 秒 → 12 秒）

### Added (品質基盤)

- **ArchUnit 5 ルール**: ドメイン純粋性 / application 境界 / コンテキスト分離 / 命名規約 / リポジトリ実装方向
- **DbCleanupSupport trait**: 統合テスト独立性（TRUNCATE RESTART IDENTITY CASCADE）
- **OptimisticLockException**: 楽観ロック準備（cargo / estimate / shipper / voyage に version カラム、IT1 H5）
- **Flyway V5**: shipper / estimate / cargo に version カラム
- **Playwright E2E**: 14 シナリオ（auth / IT1 全フロー / US06 / US24/25 / ナビバー）

### Technical Decisions

- ADR 0001 Play Framework + Scala スタック採用
- ADR 0002 bcrypt パスワードハッシュ + Play Session
- ADR 0003 PricingService 共有（Estimation + Billing）
- ADR 0004 US26 を UC 横断ストーリーとして扱う
- ADR 0005 経路探索アルゴリズム（DFS + 深さ制限、IT3 で再評価）

### Metrics

- ユニット / 統合 / Arch テスト: **158 件全 pass**
- E2E テスト: **14 件全 pass**
- ステートメントカバレッジ: **82.34%**（ブランチ 83.13%）
- ArchUnit: **5 ルール全 pass**

[Unreleased]: https://github.com/k2works/case-study-cargo-tracker/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/k2works/case-study-cargo-tracker/releases/tag/v0.1.0

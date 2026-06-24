# Changelog

国際貨物輸送管理システム（Cargo Tracker）Scala 版の変更履歴。

形式: [Keep a Changelog](https://keepachangelog.com/ja/1.1.0/) / バージョニング: [Semantic Versioning](https://semver.org/lang/ja/)。

## [Unreleased]

### Added

#### IT8 (2026-09-28 〜 2026-10-11) — Release 2.0 GA コード到達

- **US22 法人割引適用**: `BillingCargoSnapshot.corporateDiscountRate` 経由で Shipper.discountRate 自動反映、UI 4 行表示 (適用前 / 割引率 + バッジ / 割引額 / 適用後)、`appendDiscountLineItem` で Discount 明細追加
- **US23 精算処理 (ADR 0019 案 B 採択 = Invoice 集約内 paymentStatus)**:
  - PaymentStatus enum 拡張 (NotIssued / Pending / Overdue / Confirmed / Refunded)
  - Invoice に dueDate / paymentReference + issuePayment / confirmPayment / markOverdue メソッド
  - Cargo.markSettled (Delivered → Settled 遷移)
  - BillingCommandService.detectOverdue (期限超過バッチ API、Cron は IT9 申し送り)
  - 請求書詳細画面に支払欄統合 + 状態別フォーム
  - 受入条件 3 (決済機関連携) は手動 referenceCode 入力に縮小、Stripe/GMO 連携は IT9 申し送り
- **新規 Port / Adapter**:
  - `BookingPublicApi` trait (ADR 0017): Booking Context 公開 API、他 Context は本 trait のみ依存
  - `MailNotificationPort` trait + LoggingMailNotificationAdapter (ADR 0018): IT9 で Pekko Mail/SES 連携予定
  - `HandlingCargoQueryPort` + BookingCargoForHandlingAdapter: routeDeviation 自動判定
- **新規 ADR 5 件**: 0016 (HandlingOrchestrator tx 境界) / 0017 (BookingPublicApi) / 0018 (MailNotificationPort) / 0019 (Payment 集約方針) / 0020 (公開追跡画面例外表示)
- **Flyway 新規 4 件**: V23 (invoice 拡張) / V26 (LossEscalated rename) / V27 (notification_log CHECK 拡張) / V28 (payment テーブル drop)
- **その他改善 (IT7 申し送り 15 件解消)**: `OptimisticLockOps.withOptimisticLock` ヘルパ抽出、`LossEscalated` → `LostEscalated` 命名統一、`TrackingExceptionEventId` opaque type 導入 (PK 直接更新化)、例外対応取消し動線 + 補足コメント追記、Delay 例外モーダル拡張 (新到着予定日 + 対応方針 4 種定型)、TrackingExceptionSpec 同値クラステスト +6 件

### Changed

- Invoice 初期 paymentStatus を Pending → NotIssued に変更 (ADR 0019 反映)
- HandlingOrchestrator の routeDeviation を `HandlingCargoQueryPort.isOnRoute` 経由で自動判定 (従来は false 固定)
- BillingCommandService の `case _ =>` フォールバック削除、sealed Error 網羅性活用
- BillingCommandServiceSpec / TrackingCommandServiceSpec を EitherValues 統一、`@unchecked` 注釈ゼロ達成

### Documentation

- README.md にプロジェクト進捗セクション追加 (Phase 1-4 × Release × IT 一覧)
- CLAUDE.md に TDD コミット規律セクション追加 (Red→Green 分離 + Conventional Commits 例)
- data-model.md / domain-model.md / ui_design.md を ADR 0019 案 B に整合反映、Role 名 6 箇所統一 (Accountant→Settlement / Admin→MasterAdmin)

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

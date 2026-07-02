# Changelog

[Keep a Changelog](https://keepachangelog.com/ja/1.1.0/) 規約に従う。
[Semantic Versioning](https://semver.org/lang/ja/) を採用 (Internal Alpha は 0.x.x-alpha)。

---

## [Unreleased]

### 一巡完成 (v1.0.0-mvp, IT6 実装完了 2026-07-02)

Phase 3 完了 (Release 1.0 MVP) を達成。IT5 高優先技術的負債 5 件 (T5-01〜T5-05) を
完済し、本体 2 ストーリー (US21 輸送料金算出 / US26 荷受人引取通知) を Domain /
Application / Infrastructure (Postgres) / Interfaces / Views / Wire の全レイヤで
一巡完成。Ralph Loop 35 反復で消化した (34 コミット、+139 tests / 502 → 641)。

正式リリースタグ (v1.0.0-mvp) は E2E ハッピーパス追加後の IT7 冒頭で打つ予定。

#### Added (T5-01〜T5-21)

* **Session Cookie 認証 middleware** (T5-01, ADR-0010 段階移行完了):
  `Cargotracker.Shared.Auth.Interfaces.SessionAuth` (resolveCookieUser /
  requireCookieAuth / cookieProtectedApp) + hspec-wai 統合テスト
* **定数時間比較ヘルパ** (T5-02 Phase 1, SEC-04):
  `Cargotracker.Shared.Security.ConstantTime.constantTimeEqText` +
  `Verifier` 型 (Shared)
* **汎用 bcrypt ヘルパ** (T5-02 Phase 2):
  `Cargotracker.Shared.Security.BcryptHash.hashSecret / verifySecret`
* **ConfirmationCode 検証戦略の注入** (T5-02 Phase 3a): `verifyWith :: Verifier -> ...`
* **ConfirmationCode bcrypt 保存移行** (T5-02 Phase 3b): migration +
  Postgres repo hash 切替
* **TxRunner** (T5-03, ADR-0012):
  `Cargotracker.Shared.Application.TxRunner` (RankNTypes newtype) +
  `HandlingPageApi.handlerClaimPost` の runInTx 統合
* **Handling → Tracking 状態反映** (T5-04): Cross-BC helper `markClaimedByBookingId`
* **引取通知印刷用ビュー** (T5-05): `Tracking.Views.ClaimNotificationView` 暫定策
* **確認コード TTL** (T5-11): `ttlSeconds = 86400 (24h)` + `isExpiredAt` +
  `ConfirmationCodeExpired`
* **hspec-wai 日本語 body アサーション統一** (T5-12):
  `test/support/Support/HspecWaiJa` の `bodyContainsText` / `isNotHtmlPage`
* **US21 輸送料金算出**: Pricing BC 新設 (全 7 phases)
  * `Cargotracker.Pricing.Domain.Model.Value.Cost` (Cost VO + Currency VO)
  * `Cargotracker.Pricing.Domain.Model.PricingRule` (PricingRule 集約 +
    CargoCategory + calculate 関数、100 分率割増: General=100/Refrigerated=130/Hazardous=150)
  * `Cargotracker.Pricing.Domain.Model.Value.Discount` (0-100 百分率)
  * `Cargotracker.Pricing.Domain.Model.Value.CurrencyRate` (有効期間付きレート、
    isRateValidAt / convert)
  * `Cargotracker.Pricing.Application.CalculateShippingCostCommand` (フロー:
    findByCurrency → calculate → applyDiscount → convert)
  * `Cargotracker.Pricing.Views.CostCalculationView` (Lucid + Bootstrap 5)
  * `Cargotracker.Pricing.Interfaces.CostCalculationPageApi` (Servant GET + POST)
  * Main.rootApp に `"pricing" : "calculate" : _` 配線
* **US26 荷受人引取通知**: Notification BC 新設 (全 6 phases)
  * `Cargotracker.Notification.Domain.Model.Notification` (Notification 集約 +
    NotificationChannel LogChannel/EmailMockChannel/PrintableHtmlChannel +
    NotificationStatus Pending/Sent/Failed、markSent idempotent / markFailed Sent 保護)
  * `Cargotracker.Notification.Application.SendClaimNotificationCommand` (ADR-0012
    決定 3 準拠、Tx 完了後に deliver + markSent/markFailed で状態反映)
  * `Cargotracker.Notification.Infrastructure.LogDeliveryPort` (現行は Log 出力、
    将来 SmtpDeliveryPort に拡張)
  * `Cargotracker.Notification.Views.NotificationListView` (管理者向けテーブル)
  * `Cargotracker.Notification.Interfaces.NotificationListPageApi` (Servant GET)
  * Handling ↔ Notification Cross-BC 統合 (Rule 4 準拠の Text-only helper
    `sendClaimLogNotificationText` 経由)
  * Main.rootApp に `"notifications" : _` 配線
* **Postgres 実装 (Phase 1-2、IT6 計画外の追加達成)**:
  * Migration 3 本: `20260702130000_create_pricing_rule.sql` /
    `20260702130100_create_currency_rate.sql` / `20260702140000_create_notification.sql`
  * `PostgresPricingRuleRepository` / `PostgresCurrencyRateRepository` /
    `PostgresNotificationRepository` 実装
  * Main.rootApp の InMemory → Postgres 切替 (InMemory 実装はテストフィクスチャ
    用途で残置)

#### Changed

* `TrackingRepository` に `updateTransportStatus` を追加 (T5-04)
* `VerifyClaimAndRegisterCommand.execute` シグネチャに `Verifier` と
  `TrackingRepository m` を追加、さらに Handling → Notification 発火のため wire に
  `NotificationRepository IO` と `NotificationDeliveryPort IO` を追加
* `verify` を `verifyWith constantTimeEqText` の薄いラッパに (API 互換維持)
* `verifyAndConsume` を `verifyAndConsumeWith constantTimeEqText` の薄いラッパに
* `orchestrating-project` skill の IT 開始 checklist に `dbmate status` を追加 (T5-16)
* README.md に「環境変数・Cookie 早見表」節を追加 (T5-19):
  DATABASE_URL / JWT_SECRET / cargo_session (HttpOnly / SameSite=Lax / Max-Age=28800) /
  ConfirmationCode 定数
* `Cargotracker.Shared.Domain.DomainError` に IT6 バリアントを追加:
  `InvalidCurrency / InvalidCost / CurrencyMismatch / InvalidDiscountRate /
  InvalidCurrencyRatePeriod / CurrencyRateExpired / PricingRuleNotFound /
  CurrencyRateNotFound / InvalidNotificationContent`
* domain-model.md / data-model.md / ui_design.md に Pricing BC / Notification BC /
  pricing_rule / currency_rate / notification テーブル / /pricing/calculate /
  /notifications 画面を追記 (T6-04 上流ドキュメント同期)

#### Fixed

* 平文比較 `input /= ccValue cc` を定数時間比較に置き換え (SEC-04 タイミング攻撃対策)
* Handling BC が Notification Domain を直接 import する Rule 4 違反を Text-only
  helper (`sendClaimLogNotificationText`) 経由に修正
* TxRunner を Infrastructure から Application 層に移設 (arch-check T-01 準拠)

#### ADR

* **ADR-0012** トランザクション境界と Cross-BC 参照ポリシー (採用 2026-07-02, IT6)
* **ADR-0010** セッション認証方式 (提案 → **採用 2026-07-02**、AuthProtect middleware
  実装完了に伴う段階移行記述の修正、T5-21)

#### Tests

* 502 → **641 tests** / 0 failures (IT6 期間中に +139 テスト追加)
* Tracking Application Command テスト補強 (T5-08): 8 tests
* POST /login Session Cookie 発行の hspec-wai (T5-10): 4 tests
* Pricing BC: Cost 15 + PricingRule 11 + Discount 11 + CurrencyRate 13 +
  CalculateShippingCostCommand 7 + View 7 + PageApi 7 = **71 tests**
* Notification BC: Notification 10 + SendClaimNotificationCommand 7 +
  LogDeliveryPort 3 + ListView 7 + ListPageApi 5 = **32 tests**
* ConfirmationCode TTL 境界 (T5-11): 8 tests
* BookingPageApi IORef spy (T5-09): 3 tests

#### IT6 完了報告書 / KPT

* [IT6 完了報告書](docs/development/iteration_report-6.md): 実績 30+ SP (計画 18 SP
  対比 167%)、Ralph Loop 30 反復消化の記録
* [IT6 KPT ふりかえり](docs/development/retrospective-6.md): Keep 15 / Problem 10 /
  Try 12 (T6-01〜T6-12) を抽出、平均ベロシティ 24.8 SP を確定

#### 繰越 (IT7 冒頭必達、T6-01〜T6-03)

* T6-01 Playwright E2E ハッピーパス「予約→追跡→引取→料金」1 本追加
* T6-02 developing-review (マルチパースペクティブレビュー) 実施
* T6-03 v1.0.0-mvp git tag 作成 + CHANGELOG [Unreleased] → [1.0.0-mvp] セクション切出し
* T5-18 katip 正式化 (自作 JSON Lines → katip、IT6 未対応)

### 予定 (v0.3.0-mvp-preview, IT5 完了予定 2026-09-13)

Phase 3 前半 (追跡番号発行・荷役・引取・追跡照会) を Domain → Application → 最小 HTTP 結線 → UI の順に完成させ、プレ MVP デモを可能化する。

#### Added (計画)

* **US14** 追跡番号を発行する (TrackingNumber VO + IssueTrackingNumberCommand + BookingConfirmed イベント購読)
* **US15** 荷役作業を記録する (HandlingActivity + Voyage/Location 検証 + POST `/handling/new`)
* **US16** 引取作業を記録する (ConfirmationCode 検証 6 桁数字 + bcrypt cost=10 + 5 回試行上限)
* **US18** 追跡情報を照会する (公開ページ `/public/tracking/:trackingNumber` + Leaflet 地図 + タイムライン + rate-limit)
* **セッション認証** (ADR-0010): opaque Cookie + Postgres KV + Servant Auth (JWT は API 用として維持)
* **予約確定/キャンセル HTTP 結線** (IT4 繰越): POST `/bookings/:id/confirm` / cancel / route (POST + DELETE)
* **Itinerary + Leg 永続化** (ADR-0008 採用昇格): PostgresItineraryRepository + migration 2 本
* **荷役オフライン対応** (ADR-0011 提案予定): Service Worker + IndexedDB queue + BackgroundSync

#### Changed (計画)

* HPC カバレッジ gate 74% → 75% (T4-12)
* arch-check ALLOWLIST 全 5 件に sunset 日付コメント必須化 (T4-16)
* arch-check に H-01 SSoT 検査 (Tracking Context 外の TransportStatus コンストラクタ直接使用を警告)
* domain-model.md §4 Tracking Context に ConfirmationCode VO + ConfirmationCodeGenerator を追加

#### ADR

* **ADR-0008** Itinerary + Leg を Booking 集約配下に配置 (提案 → **採用 2026-07-01**)
* **ADR-0010** セッション認証方式 (opaque Cookie + Servant Auth + Postgres KV) 提案
* **ADR-0011** 荷役オフライン対応方式 (Service Worker + IndexedDB) 提案予定

### 見送り (IT6 繰越)

* EvaluateRoute POST エンドポイント (task 1.1 5/5 未達、制約 Form + Query + Command + View の複数モジュール統合が必要)
* ALLOWLIST 実削減 (sunset 日付追加で解消期限は明示、実削減は IT6/IT7)

### 予定 (v0.2.0、IT4 成果物)

* US08b 経路候補 制約評価 / US09 経路選択・確定 / US11 経路紐付け / US13 予約確定
* arch-check Phase 2 (Rule 6) + Phase 3 (T-01〜T-03)
* Playwright E2E 拡張

---

## [0.1.0-alpha] - 2026-06-29

国際貨物輸送管理システム (Haskell 版) の最初の内部レビュー用バイナリ。

詳細: [docs/release/v0.1.0-alpha.md](docs/release/v0.1.0-alpha.md)

### Added

* **US01** 輸送見積を作成する (`/estimates/new` UI + 候補表示 + 予約遷移)
* **US04 + US05** 貨物予約 + CargoType 動的フォーム (htmx)
* **US06** 予約を経路設計者に引き渡す (Draft → Submitted → RouteProposed)
* **US07** 航海スケジュールを検索する (`/voyages/search` + 検索結果)
* **US08a** 経路候補を算出する 基本 (`/bookings/:id/routes` + DFS)
* **US24 + US25** 航海スケジュール登録・更新 (プリフィル対応)
* **US27** 通関情報を予約に紐付ける (HS 6-10 桁 + 申告ステータス 4 値)
* ロール別ナビメニュー (8 ロール + 未認証)
* ADR-0004 Cross-BC ShipperRef VO
* ADR-0005 BC 固有エラーの分離 (Phase 1 起票)
* HPC ゲート 70% + Domain モジュール監視 (29 件)
* RouteFinder criterion 性能ベンチ (1000 voyage で 12.6ms / 目標 500ms)
* RouteFinder hedgehog プロパティ 3 件
* CHANGELOG.md / docs/release/v0.1.0-alpha.md

### Changed

* arch-check Rule 4 ALLOWLIST: 7 件 → **0 件** (ShipperRef VO 移行完了)
* HPC カバレッジゲート: 60% → 70%
* `submitBooking` のエラー型を `ConcurrentModification` → `InvalidStateTransition` (H-01)
* IdGenerator: partial 関数 `alphaNumTable !!` を total 関数 `intToAlphaNumChar` に置換 (H-02)
* customs_declaration テーブル: IT3 US27 用カラム (hs_code / broker_name / declaration_status) を含む最小スキーマで新規作成

### Fixed

* H-01 submitBooking のエラー型不整合 (ConcurrentModification が状態遷移エラーに化けていた)
* H-02 IdGenerator の partial 関数による潜在的クラッシュリスク + 衝突確率コメントを誕生日パラドックス補正
* H-03 US06 で Submit ボタンが UI に存在しなかった問題 (Draft → Submitted の動線が完成)
* H-09 ベロシティ表記の不一致 (180% / 100% / 22 SP) を 3 ドキュメントで統一注記

### Deprecated

* `Cargotracker.Shared.Domain.DomainError.BookingNotFound` / `InvalidStateTransition`: Phase 3 (IT5+) で `Cargotracker.Booking.Domain.Error` に移管予定 (ADR-0005)

### Security

* APP_ENV=production 環境変数で DATABASE_URL / JWT_SECRET 未設定時 fail-fast 維持

---

## [0.1.0-pre] - 2026-06-27 (IT2)

* IT2 完了時点 (US01 / US04+US05 / US06 / US25 + Try 10 件)
* arch-check Phase 1 + Rule 4 + ALLOWLIST 機構
* Estimation Context

## [0.0.x] - 2026-06-26 (IT1)

* Booking / Shipper / Routing Context の基盤
* Servant + Lucid + htmx + PostgreSQL スタック
* JWT 認証 + Login UI

[Unreleased]: https://github.com/k2works/case-study-cargo-tracker/compare/v0.1.0-alpha...haskell/take-1
[0.1.0-alpha]: https://github.com/k2works/case-study-cargo-tracker/releases/tag/v0.1.0-alpha

# Changelog

国際貨物輸送管理システム（cargo-tracker take-5）の主要な変更履歴。

[Keep a Changelog](https://keepachangelog.com/ja/1.1.0/) のフォーマットに従い、
[Semantic Versioning](https://semver.org/lang/ja/) に準拠する。

---

## [Unreleased]

IT10 で予定（Release 1.1 正式版昇格）:

- 全 Controller メソッド単位 `@PreAuthorize` 付与（US30）
- RestShipperInfoAcl fallback UX 改善（US31、`discountRate=null` + S23 alert-warning）
- Heroku staging app 構築 + JWT 経由 E2E 実機検証（US32）
- Flyway migration × enum 同期 CI 自動検証（US33）
- CHANGELOG v1.1.0 + GitHub Release タグ + 本番デプロイ可能宣言（US34）

---

## [1.1.0] — 2026-06-06（Release 1.1 主要機能完全実装、IT10 で正式版昇格予定）

IT9 完了時点（Phase 2 Buffer 後の Release 1.1）。Stripe webhook 部分入金 + AWS Secrets Manager 自動回転 + 認可基盤 + SendGrid WireMock 統合により、Release 1.1 の主要機能を完全実装。IT8 review 11 件全解消。

### Added（新規機能）

- **Stripe webhook 受信エンドポイント**（ADR-0020 / US26）: `POST /api/v1/billing/webhooks/stripe` で部分入金を受信、HMAC 署名検証 + Stripe Event ID 冪等性キーで重複処理を抑止
- **Invoice 集約に PARTIALLY_PAID 状態**（ADR-0020 / US26）: `BillingStatus` enum に追加、`BalanceTracker` 値オブジェクトで残額追跡
- **`RecordPartialPaymentCommand` + `PartialPaymentRecordedEvent`**（ADR-0020 / US26）: 部分入金時は billingms 内部 event、残額入金時は shared `PaymentRecordedEvent` で cross-service 連携
- **S23 部分入金履歴 UI**（US26）: 残額表示 + 入金履歴テーブル + Stripe ダッシュボード遷移リンク
- **AWS Secrets Manager 統合**（ADR-0021 / US27）: `AwsSecretsManagerTrackingTokenSecretProvider` で AWSCURRENT + AWSPREVIOUS 取得、`@Scheduled` で 5 分ごと refresh
- **Lambda rotation Function + Terraform IaC**（ADR-0021 / US27）: Python 3.12 で AWS 標準 4 ステップ rotation、90 日サイクル
- **gatewayms JWT 検証 GlobalFilter**（US28）: `JwtAuthenticationFilter` で authms 発行 JWT を検証、`X-Forwarded-User` / `X-Forwarded-Role` ヘッダで各 ms に伝搬
- **全 ms `HerokuSecurityConfig`**（US28）: `@Profile("heroku")` で本番のみ `authenticated()` + URL ロール認可、`@Profile("!heroku")` で local 既存テストの permitAll 維持
- **SendGrid WireMock 統合テスト**（US29 / IT8 H1 解消）: `WireMockCompatibleSendGridClient` で SDK Client.buildUri を override、port 指定問題を解決
- **LocalStack 統合テスト（AWS Secrets Manager）**: `testcontainers-localstack` で実 AWS SDK + 認証経路を検証

### Changed（変更）

- **`BillingStatus` enum**: `PARTIALLY_PAID` を追加（7 値化）、状態遷移マトリクスを更新
- **Invoice 集約**: `balance: BalanceTracker` フィールド追加、`handle(RecordPartialPaymentCommand)` 追加
- **`PaymentDetailRecorded` record**: コンストラクタに二重防御の validation 追加（IT8 review M4 統合）
- **ADR-0017**: `lockAtMostFor=PT19H` / `lockAtLeastFor=PT5H` の数値根拠を補強（IT8 review M1 統合）

### Fixed（バグ修正）

- **V5 migration の `chk_invoice_status` CHECK 制約に `PARTIALLY_PAID` 値が未追加**だった不整合を修正（A1.6 統合テストで本番デプロイ前に発見）

### Documentation（ドキュメント）

- ADR-0020 / ADR-0021 を完全実装ステータスに更新
- test_strategy.md に CI コスト測定手順 + E2E poll 実測手順を追加（IT8 review M5 統合）
- コーディングとテストガイドに「外部 SDK 統合時の手順」+ 「Java identifier 禁止パターン」を追加（IT9 retrospective Try T2 + T6）
- user_story.md に US26-29（IT9）+ US30-34（IT10 着手準備）を追加

### IT8 レビュー指摘事項の解消（11 件全解消）

| ID | 指摘 | 解消方法 |
|----|------|----------|
| H1 | SendGrid WireMock 統合テスト未実装 | A4.1 で SDK Client サブクラス化により解消 |
| H2 | IT8 マーカー棚卸し 1.4-1.10 | IT8 内で完全消化済み |
| H3 | @SpringBootTest CI コスト測定 | A4.2 で Gradle forkEvery プロパティ + 手順ドキュメント化 |
| M1 | ShedLock 設定値根拠 | ADR-0017 補強 |
| M2 | Invoice.handle discount 分岐 | 許容（Rule of Three 遵守） |
| M3 | RestShipperInfoAcl fallback UX | IT10 検討（方針確定済み、ui_design.md に先行反映） |
| M4 | PaymentDetailRecorded 二重防御 | A1.4 で record コンストラクタ validation 追加 |
| M5 | E2E poll 実測 | A3.4 で test_strategy.md に手順追加 |
| L1-L3 | 低優先度 | IT11 以降で再評価 |

---

## [1.0.0-candidate] — 2026-06-05（Release 1.0 候補確立、Phase 2 Buffer 完了）

IT8 完了時点（Phase 2 Buffer）。本番デプロイ可能な状態として Release 1.0 候補を確立。

### Added

- **ShedLock + JDBC によるクラスタ排他制御**（ADR-0017）: `OverdueScheduler` に `@SchedulerLock`、`InMemoryLockProvider` 統合テスト
- **SendGrid Dynamic Templates 通知統合**（ADR-0018）: trackingms 6 メソッド + billingms 3 メソッド、`@ConditionalOnProperty` で切替
- **`RestShipperInfoAcl` + Resilience4j Circuit Breaker + Caffeine Cache**（ADR-0015）: bookingms 経由の荷主情報取得、TTL 5min、fallback 実装
- **S23 Circuit Breaker 手動入力 fallback UI**（IT8 T4.2）: Circuit Breaker OPEN 時の手動 discountRate 入力
- **`PaymentDetailRecorded` 補完 event**（ADR-0019）: shared `PaymentRecordedEvent` の補完で payment_method / external_reference を投影
- **全 ms に Spring Security 統一導入**: `SecurityFilterChain` + `SessionCreationPolicy.STATELESS`
- **trackingms 公開トークン四半期ローテーション基盤**（ADR-0013 拡張）: `TrackingTokenSecretProvider` ポート + `StaticTrackingTokenSecretProvider`

### Changed

- 全 ms で `@ProcessingGroup` 命名規約に基づくリネーム（ADR-0014 / 0016）
- handlingms + trackingms outbound publisher を集約発火型に移行（ADR-0012）

### Documentation

- ADR-0017 / 0018 / 0019 / 0020 / 0021 起票

---

## [2.1.0] — 2026-06-05（Phase 2 / IT7 完了）

billingms（Billing Context）を新規立ち上げ、精算機能完成。

### Added

- billingms 新設（Invoice 単一集約、`PENDING → CALCULATED → INVOICED → PAID / OVERDUE / CANCELLED`）
- US21 輸送料金算出（`FareCalculator`）
- US22 法人割引適用（`CorporateDiscountPolicy`）
- US23 精算処理（精算書発行 + 入金確認 + 督促）
- bookingms cross-service で `PaymentRecordedEvent`（shared） を購読し Cargo を SETTLED に遷移
- 全 5 サービスに ArchUnit + Spring scan による構造防止網（15 件）

---

## [2.0.0] — 2026-05-29（Phase 2 / IT6 完了）

追跡・例外処理機能完成。

### Added

- US18 公開追跡照会（時限署名トークン JWT、`PublicTrackingTokenFilter`）
- US19 遅延例外処理
- US20 破損・紛失例外処理 + escalation 通知
- ADR-0012 集約発火型（二段イベント禁止）
- ADR-0013 公開追跡照会の時限署名トークン
- ADR-0014 @ProcessingGroup 命名規約

---

## [2.0.0-rc] — 2026-05-29（Phase 2 / IT5 完了）

追跡・荷役機能完成。

### Added

- US14 追跡番号発行
- US15 荷役作業記録（積込・荷降し・受領）
- US16 引取作業記録（荷受人確認）
- US17 貨物状態手動更新
- trackingms / handlingms 新設

---

## [1.0.0-mvp] — 2026-07-15（Phase 1 / IT4 完了、Release 1.0 MVP）

予約・経路設計 MVP 完成。

### Added

- US01 輸送見積作成
- US02 / US03 荷主登録（個人 / 法人）
- US04 / US05 貨物予約登録（一般 / 危険物・冷凍）
- US06 予約引き渡し
- US07 航海スケジュール検索
- US08 経路候補算出
- US09 経路選択・確定
- US10 経路条件調整
- US11 経路情報紐付
- US12 確定経路通知
- US13 予約確定
- bookingms / routingms / authms / gatewayms 新設

---

## 関連ドキュメント

- [release_plan.md](docs/development/release_plan.md) — リリース計画と進捗
- [release_report-1.0.md](docs/development/release_report-1.0.md) — Release 1.0 候補確立報告書（暫定）
- [iteration_report-9.md](docs/development/iteration_report-9.md) — IT9 完了報告書（Release 1.1 主要機能完全実装）

## バージョニング規則

- **MAJOR.MINOR.PATCH** に従う
- **MAJOR**: 本番互換性を破壊する変更（DB スキーマ削除等）
- **MINOR**: 後方互換性を保つ機能追加（IT 単位での主要機能リリース）
- **PATCH**: 後方互換性を保つバグ修正

例:

- `v1.0.0`: Release 1.0 MVP（IT4 完了）
- `v2.0.0`: Release 2.0（IT6 完了、追跡 + 例外処理）
- `v2.1.0`: Release 2.1（IT7 完了、精算機能）
- `v1.0.0-candidate`: Release 1.0 候補（IT8 完了、本番デプロイ準備）
- `v1.1.0`: Release 1.1（IT9 主要機能完全実装、IT10 正式版昇格予定）

# Changelog

国際貨物輸送管理システム（cargo-tracker take-5）の主要な変更履歴。

[Keep a Changelog](https://keepachangelog.com/ja/1.1.0/) のフォーマットに従い、
[Semantic Versioning](https://semver.org/lang/ja/) に準拠する。

---

## [Unreleased]

IT10 staging 実機検証が完了次第、`v1.1.0` を正式タグ化（staging E2E 経由認可検証 + Stripe Test Mode webhook + Secrets Manager rotation 実機確認後）。

### staging 実機残作業（人間判断・実機環境必要）

- A3.1 Heroku staging app（dev plan）構築 + 各 ms デプロイ
- A3.2 Playwright JWT 経由 E2E（`cross-service.spec.ts`）staging 実行
- A3.3 Stripe Test Mode webhook → billingms staging で PARTIALLY_PAID 検証
- A3.4 AWS Secrets Manager `rotate-secret` 実行 + trackingms refresh ログ確認
- A3.5 SonarQube Quality Gate を staging code で実機計測
- A3.9b Stripe Test Mode から `charge.refunded` / `charge.dispute.created` 送信 → skipped 動作検証
- A3.10b rotation 失敗時の Grafana / PagerDuty 通知実機検証
- A5.2 git tag `v1.1.0` + GitHub Release 公開
- A5.3 README + `docs/index.md` に「本番デプロイ可能」宣言

### v1.1.0 タグ化時点で含まれる IT10 後半の追加成果（中間レビュー解消 + 次イテレーション提案）

`[1.1.0]` セクションに記載した IT10 主要実装に加えて、中間 self-review 実施後に AI Agent 単独完結フェーズで進めた以下のクロージング作業も v1.1.0 に含まれる:

- IT10 中間レビュー L 優先度 4 件全件解消:
  - L1: `PaymentGatewayWebhookController.receive()` の Optional パターンを `orElse(null)` + null チェックに整理（commit `04943b3a`）
  - L2: 3 ms `*CheckConstraintTest` に「複数 ADD CONSTRAINT 順序ロバスト性テスト」を 1 件ずつ追加（commit `f66e8822`）
  - L3: `markFailed` reason を `unsupported_event_type` / `missing_metadata` に分離 + US26 受入基準更新（commit `1c7ef1c0`）
  - L4: README 主要機能見出しを「Release 1.1 候補 / IT10 進行中：実装完了、staging 検証中」に修正（commit `1c4ba54e`）
- ADR-0023 起票（Flyway × enum 同期検証ルール、commit `5d291c9d`）
- IT10 中間マルチパースペクティブ self-review 実施（commit `e307fa69`）+ 解消状況追跡セクション（commit `13475fae`）+ サマリ追記（commit `4ae6936b`）
- `journal-it10.md` 中間サマリ作成・最新化（commits `70fefe38` / `b2dc0dba`）
- 各 index 反映（release_plan / docs/index / docs/development/index / docs/review/index）
- `iteration_plan-11.md` スケルトン作成（次イテレーション提案、commit `f93ee50f`）

---

## [1.1.0] — 2026-06-09（Release 1.1 正式版昇格準備完了、IT10 主要機能完遂）

IT10 完了時点。IT9 までの主要機能完全実装に加えて、IT10 で認可深層強化（全 Controller `@PreAuthorize` + httpBasic 無効化）、Flyway × enum 同期 CI 検証、S23 fallback UX 改善、IT9 レビュー指摘 12 件のうち AI Agent 単独完結可能な 9 件解消（H3 / H4 / H5 / H6 / H7 / H8 / H9 / H10 / M3）を達成。**残る staging 実機検証完了で `v1.1.0` 正式タグを切る運用**。

### Added（新規機能 / IT10）

- **全 Controller クラス単位 `@PreAuthorize`**（US30 / A1.1-A1.3）: billingms / routingms / bookingms / handlingms / trackingms の 11 Controller に `hasAnyRole('XXX', 'ADMIN')` を付与、URL ルール認可と二段重層の深層防御を確立。`PaymentGatewayWebhookController` は HMAC 検証で代替認証のため非対象。
- **`PreAuthFilter`（OncePerRequestFilter）**（US30 / A1.4 / IT9 H3 解消）: 全 5 ms に同型実装、`X-Forwarded-User` / `X-Forwarded-Role` ヘッダから `UsernamePasswordAuthenticationToken` を構築し SecurityContext に設定。`httpBasic.disable()` で BASIC auth bypass リスク解消。
- **S23 部分入金画面 Circuit Breaker OPEN 時の「割引率未確定」alert-warning**（US31 / A2 / IT9 M3 解消）: 既存 `CircuitBreakerHealthController` を InvoiceDetailPage が初回ロード時に呼び、shipperInfo Circuit Breaker が OPEN / FORCED_OPEN なら経理担当者に明示警告。Backend 変更なし。
- **Flyway × enum 同期検証テスト**（US33 / A4.1-A4.2）: 3 ms に Migration SQL パース方式の同期検証テスト追加（IT9 V5 バグ再発防止）。`BillingStatusCheckConstraintTest` / `HandlingTypeCheckConstraintTest` / `TransportStatusCheckConstraintTest`。
- **handlingms `chk_handling_type` CHECK 制約**（A4.2a / V5 migration）: HandlingType (5 値) を DB 値域として強制。
- **trackingms `chk_tracking_summary_current_status` / `chk_tracking_event_transport_status` CHECK 制約**（A4.2b / V5 migration）: TransportStatus (9 値) を DB 値域として強制（event 側は NULL 許容）。
- **HMAC tolerance 境界値テスト 6 件 + Clock 注入**（US32 / A3.7 / IT9 H6 解消）: `PaymentGatewayWebhookController` に Clock を注入し前段 tolerance 検証ロジックを追加、skew 299s / 300s / 301s / 未来側 301s / extractTimestamp ユーティリティを実証。
- **rotation 失敗監視メトリクス**（US32 / A3.10a / IT9 H9 解消）: `AwsSecretsManagerTrackingTokenSecretProvider` に Micrometer Counter（success / failure）+ 連続失敗 Gauge を追加。`operation.md` に「連続失敗 3 回 = Critical」アラート閾値を明文化。

### Changed（変更 / IT10）

- **`PaymentGatewayWebhookIntegrationTest`** （A3.6 / IT9 H5 解消）: 1 巨大メソッド → 4 メソッドに分割（部分入金 / 冪等性 / 残額入金 / 不正署名）、`await timeout` 15s → 5s に短縮、実時間 約半減。
- **`:check` から `localstack-integration` タグをデフォルト除外**（A3.8 / IT9 H7 解消）: `apps/backend/build.gradle` に excludeTags を追加、`-PincludeLocalstackIntegration=true` で明示実行可能。`:check` の実行時間が約 4 分短縮。
- **`AwsSecretsManagerTrackingTokenSecretProvider` コンストラクタ**: `MeterRegistry` 引数を追加（既存 LocalStack IT / 単体テストも追従済み）。
- **`PaymentGatewayWebhookController` コンストラクタ**: `Clock` 引数を追加（`BillingCommonConfig.clock()` Bean が注入される）。

### Documentation（ドキュメント / IT10）

- **US26 受入基準に「対象外イベントの受入動作」**（A3.9a / IT9 H8 解消）: `charge.refunded` / `charge.dispute.created` は skipped 200 + markFailed 仕様であることを明示、将来 US28 / US29 候補を予告。
- **`operation.md` Security 監視に rotation 失敗閾値**: Warning（5 分窓で increment ≥ 5）+ Critical（連続失敗 Gauge ≥ 3）の 2 段階。
- **`operation.md` 2.5 節「ロール棚卸し」**（A1.6 / IT9 H10 解消）: 6 ロール（ACCOUNTANT / ROUTING / SALES / HANDLER / TRACKER / ADMIN）の責務 + 監査手順 + 新規ロール追加チェックリスト。
- **`developing-backend` スキルに認可テストパターン**（A1.5）: `@WebMvcTest` + `@MockitoBean` + `TestMethodSecurityConfig` のひな形をスキル文書化。

### IT9 レビュー指摘事項の解消（12 件中 9 件 / 残 3 件は staging 実機）

| ID | 重要度 | 指摘 | 解消方法 / 担当タスク |
|----|--------|------|------------------|
| H3 | 高 | httpBasic 残置で BASIC auth bypass 可 | A1.4: `httpBasic.disable()` + `PreAuthFilter` 導入 |
| H4 | 高 | URL ルール認可のみで Controller 二段保護なし | A1.1-A1.3: 全 Controller `@PreAuthorize` 付与 |
| H5 | 高 | webhook IT 巨大 1 メソッド + await 15s | A3.6: 4 分割 + await 5s 短縮 |
| H6 | 高 | HMAC tolerance 境界値テスト欠如 | A3.7: Clock 注入 + 境界値 6 件追加 |
| H7 | 高 | `:check` に LocalStack IT 含む（+4 分） | A3.8: デフォルト除外 + 明示実行 property |
| H8 | 高 | charge.refunded / dispute シナリオ未定義 | A3.9a: US26 受入基準明示 + 単体テスト 2 件 |
| H9 | 高 | rotation 失敗時の通知メカニズム欠如 | A3.10a: Counter + Gauge + アラート閾値 |
| H10 | 高 | ロール棚卸し手順未文書化 | A1.6: `operation.md` 2.5 節追加 |
| M3 | 中 | shipperInfo OPEN 時のフロント警告欠如 | A2: alert-warning 常時表示 |
| H1-H2 / M1-M2 / M4-M9 / L1-L7 | — | （IT9 内で解消済みまたは IT11 以降検討） | — |
| H11-H12 相当 | — | staging 実機検証必須項目 | A3.1-A3.5 / A3.9b / A3.10b (Release 1.1 正式タグ前に実施) |

---

## [1.1.0-candidate] — 2026-06-06（IT9 完了時点 / Release 1.1 主要機能完全実装）

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
- `v1.1.0-candidate`: Release 1.1 候補（IT9 主要機能完全実装、staging 検証待ち）
- `v1.1.0`: Release 1.1 正式版（IT10 完了、staging 実機検証 + 認可深層強化 + Flyway × enum 同期検証）

## Release ライン経緯（バージョン順序の説明 / IT9 レビュー M8 解消）

本プロジェクトでは Release 1.0 系（MVP / Phase 2 Buffer）と Release 2.x 系（Phase 2 主要機能）が並行進行したため、CHANGELOG のバージョン順序は時系列ではなく **Release ライン別**になっている。Reader の混乱を避けるため経緯を以下に明示する。

| Release ライン | 目的 | 主要バージョン | 完了 IT |
|---|---|---|---|
| **Release 1.0**（MVP → Buffer → 候補） | 業務基盤（予約 / 経路設計 / 認可） | `v1.0.0-mvp`（IT4）/ `v2.0.0-rc`（IT5）/ `v2.0.0`（IT6）/ `v2.1.0`（IT7）/ `v1.0.0-candidate`（IT8） | IT4-IT8 |
| **Release 1.1**（主要機能完全実装 → 正式版） | 決済 webhook / Secret rotation / 認可基盤 | `v1.1.0-candidate`（IT9）/ `v1.1.0`（IT10） | IT9-IT10 |

**バージョン番号の見かけ上の逆行**（`v2.1.0` → `v1.0.0-candidate` → `v1.1.0`）は、Release 1.0 を「業務基盤として 1.x で確立」する戦略に再整理した経緯による。`v2.x` 系は Phase 2 内の中間バージョンで、Release 1.0 候補確立時に「業務基盤としては 1.x 系」に意味的に統合された（コードは継続維持、タグ名は履歴のため残す）。Release 2.0 / 2.1 タグは IT11 以降の Phase 3 で新規機能群（例: 多通貨 / マルチテナント）に再割当て予定。

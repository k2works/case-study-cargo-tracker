# イテレーション 9 ふりかえり（KPT）

| 項目 | 内容 |
|------|------|
| **イテレーション** | IT9（Release 1.1 / 外部サービス統合 + 認可付与） |
| **期間** | 2026-09-10 〜 2026-09-23（計画 2 週間）/ 2026-06-06（実績 1 日、Ralph Loop 12 iteration） |
| **実績** | 7/8 SP（87.5%）、累計 83/84 SP（99%）、Release 1.1 ほぼ確立 |
| **対象 US** | US26（Stripe webhook 部分入金）/ US27（AWS Secrets Manager 自動回転）/ US28（全 endpoint 認可付与）/ US29（SendGrid WireMock） |
| **コミット数** | 14 件（本体実装 12 + ドキュメント 2） |
| **規模** | バックエンド 5 ms + gatewayms + frontend + IaC で約 45 ファイル / 約 2,600 行追加 |

## サマリー

ADR-0020（Stripe webhook + 部分入金 PARTIALLY_PAID）と ADR-0021（AWS Secrets Manager + Lambda 自動回転）の 2 大設計を完全実装し、Release 1.1 の主要機能（決済自動化 + secret 自動回転 + 本番認可）を達成。IT8 で全 ms 平準化した Spring Security 基盤の上に `@Profile("heroku")` 専用の `HerokuSecurityConfig` を新規追加し、既存テストへの影響をゼロにしながら本番のみ `authenticated()` + ロール認可を有効化する **Profile 分離設計**を実現した。

IT9 計画は当初「スケルトン」だったが、`validating-iteration-plan` で 24 件の不整合を検証し、**US 番号衝突解消（US24/25 → US26-29 リナンバリング）+ 設計ドキュメント先行更新（domain / data / ui）** を実装前に完了。整合性を確保した状態で着手することで、TDD ペース（Red → Green → Refactor）を 12 iteration 通して維持できた。

A1.6 統合テストの実装中に **V5 migration の `chk_invoice_status` CHECK 制約に PARTIALLY_PAID 値が未追加**だったバグを発見、本番デプロイ前に修正できた。これは設計ドキュメントの値リストと実装の Flyway migration の同期不足が原因で、IT10 では Flyway migration と enum の整合性検証を自動化する仕組みを検討する。

**Ralph Loop モード 12 iteration**で IT8 レビュー 11 件中 10 件解消（H1 のみ IT10 持ち越し、SendGrid SDK Client.buildUri 制約）。各 iteration で 2-3 タスク完了の安定ペースを維持。

## Keep（継続すること）

- **Profile 分離設計（HerokuSecurityConfig vs SecurityConfig）**: `@Profile("heroku")` で本番認可を有効化し、`@Profile("!heroku")` で local 既存テストの permitAll を維持。既存 17 件の @SpringBootTest を無改修で済ませた構造的解決
- **整合性検証を実装前に完了**: validating-iteration-plan で 24 件の不整合を検出 → US 番号リナンバリング + 設計ドキュメント先行更新を実装前に完遂。TDD 中の手戻りゼロ
- **BalanceTracker 値オブジェクトでの残額追跡**: `record BalanceTracker(totalDue, paidSoFar)` の不変オブジェクトで `apply` / `remainingBalance` / `isFullyPaid` / `withTotalDue` を提供。Invoice 集約の状態遷移ロジックを Tell-Don't-Ask で BalanceTracker に集約
- **冪等性キーとしての Stripe Event ID**: webhook_processed テーブルに Stripe Event ID を PK として記録し、同一 event の再送を 200 OK + 副作用ゼロで処理。Stripe 公式 retry mechanism と整合する設計
- **shared event と内部 event の分離（A1.4）**: 残額入金時のみ shared `PaymentRecordedEvent` を発火し bookingms cross-service が Cargo を SETTLED に遷移。部分入金時は billingms 内部 `PartialPaymentRecordedEvent` で完結し、cross-service 通知を最小化
- **Ralph Loop モードの安定ペース**: 12 iteration で各 2-3 タスク完了。Phase 0 計画詳細化 → A1.1 → A1.2 → ... の段階的進行で context overflow を回避

## Problem（問題点）

- **P1: V5 migration の CHECK 制約と enum の同期漏れ（A1.6 で発見）**: BillingStatus に PARTIALLY_PAID を追加したが、Flyway V5 migration の `chk_invoice_status` 値リストに反映されていなかった。設計ドキュメント（data-model.md）の値リストと Flyway migration の同期検証が手動で、ヒューマンエラー再発リスクがある
- **P2: SendGrid SDK Client.buildUri 制約による WireMock 統合の困難**: SDK の `URIBuilder.setHost` がホスト名のみ受理（port 不可）のため、WireMock を実 HTTP で受信させる経路が確立できない。IT8 で Mockito 代替したが、SDK 内部 URL 構築ロジックが検証されない盲点が IT9 でも残った（H1 持ち越し）
- **P3: 各 Controller への @PreAuthorize 付与（A3.2）が IT10 持ち越し**: URL ルールベース認可で「深層防御不足」とまでは言えないが、メソッド単位の認可が IT9 内に収まらなかった。IT10 で @WithMockUser + @PreAuthorize テストパターンを確立する
- **P4: staging 環境未構築のため E2E 認可 / Secrets Manager rotation の実機検証ができない**: Definition of Done のデモ項目 4 件中 2 件が「IT10 staging 構築時に確認」状態。本番デプロイ前の安全性確証が部分的
- **P5: LocalStack コンテナ起動コストが trackingms :check で約 4 分追加**: A2.4 で導入した LocalStack IT がフル check に 4 分加算。CI ワークフロー時間が増加するため、CI で分離（マニュアル / nightly）するか forkEvery 設定で並列調整するかの判断が staging 計測後になる
- **P6: テストメソッド名の英数字混在で Java identifier エラー多発**: 日本語 + ASCII 数字 / 大文字英単語の混在（例: `200_OK_応答`、`Event_ID`）で `'(' がありません` エラーが頻発。最初から「日本語のみのテスト名」を運用ルール化すべきだった

## Try（次に試すこと）

- **T1: Flyway migration と enum 同期の自動検証**（IT10）: ArchUnit または独自テストで「BillingStatus enum の値 ⊂ Flyway migration の CHECK 制約値リスト」を検証する仕組みを追加。V5 タイプのバグを CI で検知
- **T2: SendGrid SDK Client サブクラス化による WireMock 統合再挑戦**（IT10、A4.1 持ち越し）: `com.sendgrid.Client` の `buildUri` を override する `WireMockCompatibleClient` を実装。protected メソッドであれば SDK バージョンに依存しない解決策
- **T3: 各 Controller への @PreAuthorize 付与**（IT10、A3.2 持ち越し）: メソッド単位の認可とテストでの @WithMockUser パターン確立。深層防御を完成させる
- **T4: staging 環境構築**（IT10 想定）: Heroku staging app（dev plan）を構築し、JWT 経由 E2E + Stripe Test Mode webhook + AWS Secrets Manager rotation を実機検証。Quality Gate も staging で実機計測
- **T5: RestShipperInfoAcl fallback の UX 改善**（IT10、M3 持ち越し）: 「Circuit Breaker OPEN → 個人扱い」が経理担当者に分かりにくい問題。`discountRate=null`（未確定）を返してフロントエンドで明示警告するパターン
- **T6: テストメソッド名の運用ルール明文化**: 「Java 識別子に英大文字 + 日本語の混在は禁止、英数字は別 word として spacing する」を `コーディングとテストガイド.md` に追記
- **T7: LocalStack IT を CI ワークフローで分離**: GitHub Actions の `localstack-test` ジョブを別 workflow として分離（PR 時は skip、main / nightly でのみ実行）

## 数値指標（KPT 補完）

| メトリクス | 値 | 目標 | 評価 |
|-----------|-----|------|------|
| 計画 SP 達成率 | 87.5%（7/8） | 100% | ⚠️（A3.2 + A4.1 を IT10 持ち越し） |
| バックエンドテスト追加件数 | 35 件（HMAC 7 + IT 2 + BalanceTracker 8 + Aggregate 5 + AWS Mockito 5 + LocalStack 2 + JWT 6） | - | ✅ |
| フロントエンドテスト | 245 件（既存 234 + IT9 新規 11） | - | ✅ |
| billingms カバレッジ | 維持（IT8 89.87% から大きな変化なし、新規 Webhook 系を含めて） | 80%+ | ✅ |
| 全 8 ms `:check` | PASS | PASS | ✅ |
| ArchUnit hard | PASS（4 件継続） | PASS | ✅ |
| IT8 review 解消率 | 10/11（91%、H1 のみ IT10 持ち越し） | 11/11 | ⚠️（高 1 件持ち越し） |
| ADR 新規 / 補強 | ADR-0017 補強（lockAtMostFor 根拠）+ ADR-0020 + ADR-0021 実装完了 | - | ✅ |
| 設計ドキュメント先行更新 | 4 件（user_story / domain / data / ui） | - | ✅ |
| Ralph Loop iteration 数 | 12（Phase 0 + A1.1〜A2.4 + 完了報告書）| - | ✅ |

## イテレーションを終えての考察

IT9 は **Profile 分離設計と整合性検証先行による「変更を楽に安全にできる」を体現した iteration** だった。validating-iteration-plan で発見した 24 件の不整合を実装前に解消することで、TDD 中の手戻りがゼロになった。Ralph Loop モード 12 iteration を通して各 iteration で 2-3 タスク完了の安定ペースを維持できたのは、context overflow を回避する「タスクの細分化 + コミット粒度の徹底」が機能したから。

A1 Stripe webhook 実装では、`BalanceTracker` 値オブジェクト導入で残額追跡を Aggregate から切り出し、`PartialPaymentRecordedEvent` と shared `PaymentRecordedEvent` を分離することで cross-service 契約を最小化した。これは ADR-0012 集約発火型と ADR-0019 内部 event 分離方針の延長線上で、変更の影響範囲を局所化した設計。

A3 認可付与では既存 17 件の @SpringBootTest テストを無改修で維持する `@Profile` 分離設計を採用。本番認可と既存テストの両立という難問を構造的に解決し、Spring Profile の有効活用例として参考になる。

残る課題は SendGrid SDK Client.buildUri 制約による WireMock 統合（H1）と各 Controller @PreAuthorize（A3.2）。これらは IT10 で SDK サブクラス化と @WithMockUser テストパターン確立により完遂し、staging 環境構築と合わせて Release 1.1 を正式版に昇格させる。

---

**作成日**: 2026-06-06
**作成者**: k2works（AI ペアプログラミング、Ralph Loop モード 12 iteration）

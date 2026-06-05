# イテレーション 9 計画（スケルトン）

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | IT9（Release 1.1 / 外部サービス統合 + 認可付与） |
| **期間** | 2 週間（Week 17-18、暫定） |
| **想定ベロシティ** | 8 SP（IT5=10 / IT6=9 / IT7=8 / IT8=8 の平均値、IT8 達成実績の維持） |
| **ゴール** | IT8 起票の ADR-0020（決済機関 webhook）+ ADR-0021（AWS Secrets Manager）を実装し、Release 1.1 を確立する。IT8 で全 ms 平準化した Spring Security 基盤の上に各 endpoint へ authenticated() / @PreAuthorize を順次付与する。 |

---

## ゴール

### イテレーション終了時の達成状態

1. **A1 Stripe webhook 受信**: ADR-0020 実装。決済機関（Stripe）から webhook を受信して `RecordPartialPaymentCommand` を発火し、部分入金 → PARTIALLY_PAID → PAID の状態遷移を Invoice 集約に追加する。Idempotency キー（Stripe Event ID）で重複処理を抑止。
2. **A2 AWS Secrets Manager 統合**: ADR-0021 実装。`AwsSecretsManagerTrackingTokenSecretProvider` を追加し、Heroku Config Vars から AWS Secrets Manager + Lambda 自動回転に移行する。LocalStack で統合テスト。
3. **A3 認可付与**: IT8 で平準化した SecurityFilterChain（permitAll）を各 endpoint で `authenticated()` + `@PreAuthorize("hasRole('ACCOUNTANT')")` 等のロール制約に置換する。E2E テストで全フロー認証付き動作を確認。
4. **A4 IT8 レビュー高優先度の解消**: H1 SendGrid WireMock 統合テスト（Client 注入経路）/ H3 @SpringBootTest CI コスト測定。

### 成功基準

- [ ] Stripe webhook を受信して PARTIALLY_PAID 状態遷移が成立
- [ ] AwsSecretsManagerTrackingTokenSecretProvider が AWSCURRENT / AWSPREVIOUS を取得、`@Scheduled` で 5 分ごとに refresh
- [ ] 全 endpoint に authenticated() + @PreAuthorize が付与され E2E PASS
- [ ] SendGrid WireMock 統合テストが trackingms / billingms 両方で実装
- [ ] テストカバレッジ 80% 以上維持

---

## ユーザーストーリー

### 対象ストーリー（暫定）

| ID | ストーリー | SP | 優先度 |
|----|----------|----|----|
| US24 | 経理担当者として、Stripe で受信した部分入金が自動で請求書に反映されるようにしたい（手作業の externalReference 入力を排除）| 3 | 必須 |
| US25 | 運用担当者として、公開トークンの secret が AWS Secrets Manager で自動回転されるようにしたい（手動 Heroku Config Vars 更新の排除）| 2 | 必須 |
| US26 | システム管理者として、全 endpoint が認証 / 認可されているようにしたい（IT8 まで permitAll だった endpoint の本番化）| 2 | 必須 |
| US27 | 開発チームとして、SendGrid + Resilience4j の WireMock 統合テストで実 HTTP 経路を保証したい（IT8 H1）| 1 | 中 |
| **合計** | | **8** | |

---

## タスク（スケルトン、IT9 着手時に詳細化）

### A1: Stripe webhook 受信（ADR-0020 実装）

| # | タスク | 見積もり |
|---|--------|---------|
| 1.1 | Stripe Java SDK 統合 + PaymentGatewayWebhookController + HMAC 署名検証 | 2h |
| 1.2 | webhook_processed テーブル（Flyway）+ idempotency キー処理 | 1h |
| 1.3 | Invoice 集約に PARTIALLY_PAID 状態 + BalanceTracker 値オブジェクト追加 | 2h |
| 1.4 | RecordPartialPaymentCommand + Saga + 状態遷移 | 2h |
| 1.5 | S23 部分入金履歴 UI（Stripe ダッシュボード遷移リンク含む）| 2h |
| 1.6 | LocalStack + Testcontainers で受信統合テスト | 1h |

### A2: AWS Secrets Manager 統合（ADR-0021 実装）

| # | タスク | 見積もり |
|---|--------|---------|
| 2.1 | software.amazon.awssdk:secretsmanager 依存追加 | 0.5h |
| 2.2 | AwsSecretsManagerTrackingTokenSecretProvider 実装（@ConditionalOnProperty + @Scheduled refresh）| 2h |
| 2.3 | Lambda rotation Function（Python or TypeScript）+ Terraform IaC | 2h |
| 2.4 | LocalStack 統合テスト + AWS Console での手動回転確認 | 1h |

### A3: 認可付与（全 ms endpoint）

| # | タスク | 見積もり |
|---|--------|---------|
| 3.1 | bookingms / routingms / handlingms / billingms / trackingms の SecurityConfig を anyRequest().authenticated() に変更 | 1h |
| 3.2 | 各 Controller に @PreAuthorize("hasRole(ROLE)") 付与（ACCOUNTANT / SHIPPER / OPERATOR）| 2h |
| 3.3 | gatewayms の JWT 検証チェーン強化（authms 発行 JWT を全 ms に伝搬）| 1h |
| 3.4 | E2E / cross-service.spec.ts に JWT 認証ヘッダ統一 | 1h |

### A4: IT8 レビュー高優先度

| # | タスク | 見積もり |
|---|--------|---------|
| 4.1 | H1: SendGrid Client 注入経路で WireMock 統合テスト（trackingms / billingms 各 1 件）| 2h |
| 4.2 | H3: RestShipperInfoAclWireMockIT の @SpringBootTest CI コスト測定 + 必要なら forkEvery 設定 | 1h |

#### タスク合計

| カテゴリ | SP | 理想時間 |
|---------|----|----|
| A1 Stripe webhook | 3 | 10h |
| A2 AWS Secrets Manager | 2 | 5.5h |
| A3 認可付与 | 2 | 5h |
| A4 IT8 レビュー解消 | 1 | 3h |
| **合計** | **8** | **23.5h** |

**進捗率**: 0%（0/8 SP）— IT9 着手前（スケルトン）

---

## スケジュール（暫定）

| 週 | 主担当 |
|----|--------|
| Week 17 Day 1-3 | A1.1-A1.4 Stripe SDK + Invoice 集約拡張 |
| Week 17 Day 4-5 | A1.5-A1.6 S23 部分入金 UI + 統合テスト |
| Week 18 Day 1-2 | A2 AWS Secrets Manager + Lambda |
| Week 18 Day 3-4 | A3 認可付与（全 ms） |
| Week 18 Day 5 | A4 H1/H3 解消 + マルチパースペクティブレビュー + ふりかえり |

---

## 関連ドキュメント

- [iteration_plan-8.md](iteration_plan-8.md) — IT8 完了報告
- [iteration_report-8.md](iteration_report-8.md) — IT8 完了報告書
- [IT8 開発成果物レビュー](../review/IT8_review_20260605.md) — H1 / H3 持ち越し詳細
- [ADR-0020](../adr/0020-payment-gateway-webhook.md) — Stripe webhook 設計
- [ADR-0021](../adr/0021-aws-secrets-manager-rotation.md) — AWS Secrets Manager 設計

---

## 更新履歴

| 日付 | 内容 | 担当 |
|------|------|------|
| 2026-06-05 | IT8 完全達成（H2 持ち越し含む）を受けて IT9 スケルトン計画を作成。IT9 着手時に詳細化（受入条件 / Definition of Done / リスクと対策）| k2works |

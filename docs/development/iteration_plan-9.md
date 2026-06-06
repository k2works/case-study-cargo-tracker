# イテレーション 9 計画

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
| US26 | 経理担当者として、Stripe で受信した部分入金が自動で請求書に反映されるようにしたい（手作業の externalReference 入力を排除）| 3 | 必須 |
| US27 | 運用担当者として、公開トークンの secret が AWS Secrets Manager で自動回転されるようにしたい（手動 Heroku Config Vars 更新の排除）| 2 | 必須 |
| US28 | システム管理者として、全 endpoint が認証 / 認可されているようにしたい（IT8 まで permitAll だった endpoint の本番化）| 2 | 必須 |
| US29 | 開発チームとして、SendGrid + Resilience4j の WireMock 統合テストで実 HTTP 経路を保証したい（IT8 H1）| 1 | 中 |
| **合計** | | **8** | |

---

## タスク

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

**進捗率**: 75%（6/8 SP）— A1 ✅ + A2 ✅ + A3 部分（A3.1 + A3.3）✅ + A4 部分（A4.2）✅、残: A1.6 / A2.4 LocalStack 統合テスト、A3.4 E2E JWT 統一、A4.1 SendGrid WireMock

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

## レビュー指摘事項対応方針（IT8 レビュー由来）

[IT8 開発成果物レビュー（2026-06-05）](../review/IT8_review_20260605.md)で挙げられた指摘事項について、IT9 での対応方針を以下に明示する。

### 高優先度（3 件）

| ID | 指摘 | IT9 での対応 |
|----|------|------------|
| H1 | SendGrid WireMock 統合テスト未実装 | **A4.1 で対応**（タスク見積もり 2h、US29 として正式ストーリー化） |
| H2 | IT8 マーカー棚卸し追加項目 1.4-1.10（14h 分） | **IT8 内で完全消化済み、IT9 対応不要**。[iteration_report-8.md §H2 持ち越し追加消化](iteration_report-8.md) のとおり、T1.4 / T1.5 / T1.6 / T1.7 / T1.8 / T1.9 / T1.10 / T1.11 の 8 件すべてを IT8 内で完了（17h 相当、14h を超過達成）。IT8 レビュー作成時点とその後の完了報告作成時点でステータスが変化した点に留意 |
| H3 | RestShipperInfoAcl の @SpringBootTest CI コスト測定 | **A4.2 で対応**（タスク見積もり 1h、必要に応じて forkEvery 設定） |

### 中優先度（5 件）

| ID | 指摘 | IT9 での対応方針 |
|----|------|---------------|
| M1 | ShedLock の `lockAtMostFor=PT19H` / `lockAtLeastFor=PT5H` 設定値根拠が ADR-0017 で未明示 | **IT9 中対応**（A2 ADR-0021 実装の流れで ADR-0017 にコメント追記、所要時間 0.5h を Buffer 内で吸収） |
| M2 | `Invoice.handle(ApplyDiscountCommand)` の `manualDiscountRate` 分岐がドメインロジック内に混在 | **許容**（現状は 2 行のテルナリで簡潔、Rule of Three 遵守で次回類似分岐出現時に判断。IT9 で A1 部分入金実装時に再評価） |
| M3 | `RestShipperInfoAcl` fallback の UX が「個人扱い」と区別困難 | **IT10 検討**（手動入力 UI で現状カバー済み。デフォルト値を null（discountRate 未確定）にする代替設計は IT9 のスコープ外。US26 の Stripe 統合 UX 設計と合わせて IT10 で再検討） |
| M4 | `PaymentDetailRecorded` の `paymentMethod` / `externalReference` 制約が record コンストラクタで未検証 | **A1 で対応**（部分入金（IT9 / US26）の新規 record `PartialPaymentRecorded` で同種の問題を回避するため、コンストラクタ検証を標準化。既存 `PaymentDetailRecorded` への二重防御追加も A1 内で同時実施） |
| M5 | E2E `cross-service.spec.ts` の poll タイムアウト 30 秒が本番 Kafka 経路と乖離する可能性 | **A3 で対応**（認可付与による E2E 全フロー再実行時に本番 Heroku Kafka 経路での所要時間を実測し、必要なら poll タイムアウトを調整。所要時間 0.5h を A3.4 タスク内に吸収） |

### 低優先度（3 件）

L1 / L2 / L3（IT8 レビューで「次々回以降」と判定された項目）は IT9 スコープ外。IT11 以降のリリース計画で再評価する。

---

## リスクと対策

| # | リスク | 影響度 | 対策 |
|---|-------|-------|------|
| R1 | Stripe SDK の HMAC 署名検証ライブラリのバージョン差異で実機 webhook 検証が失敗 | 高 | TDD で固定 payload + 固定 signature をテスト fixture 化、テスト時は SDK の `Webhook.constructEvent` を呼ぶ実 SDK 経路で確認 |
| R2 | webhook_processed テーブルの冪等性キー（Stripe Event ID）に対する競合（同一 event_id の並列受信） | 中 | UNIQUE 制約 + `INSERT ... ON CONFLICT DO NOTHING` パターン、または UNIQUE 違反を 200 OK にマッピング |
| R3 | Invoice 集約に PARTIALLY_PAID 状態を追加すると既存 Event Store の Replay で不整合（古い Event は PARTIALLY_PAID を知らない） | 高 | EventSourcingHandler で PARTIALLY_PAID 関連 Event は新規追加のみ、既存 PaymentRecordedEvent は不変。新規 PartialPaymentRecordedEvent と既存 PaymentRecordedEvent の共存を集約コードで明示 |
| R4 | AWS Secrets Manager の LocalStack 統合で Lambda rotation の挙動が本番と乖離 | 中 | LocalStack Pro 機能（Lambda）が必要。代替として AWS Console での手動回転確認（A2.4）を必須化 |
| R5 | 全 ms に authenticated() 付与で既存 E2E テストが大量 fail（Authorization ヘッダ不足） | 高 | A3.4 で E2E spec に JWT 発行・付与を一括追加、ローカル authms で発行した JWT を全 ms に伝搬 |
| R6 | SendGrid SDK の Client.buildUri が依然として port を受理しないため、A4.1 の WireMock 統合テストが Builder 注入で迂回できない | 中 | A4.1 の TDD で SendGrid 側の Java SDK バージョン更新（4.10.3 → 4.11+）を検証、または `RestTemplate` ベースの代替 Acl の実装余地を残す |

## 完了条件

### Definition of Done

- [ ] A1-A4 全タスクが「状態」列で [x] に更新されている
- [ ] 全 5 ms（bookingms / routingms / handlingms / billingms / trackingms）で `:check` BUILD SUCCESSFUL
- [ ] フロントエンドで `npm run test:coverage` が 80% 以上を維持
- [ ] ArchUnit hard assertion すべて PASS（ADR-0012 集約発火型ガード継続）
- [ ] SonarQube Quality Gate PASS（new_violations: 0、new_coverage ≥ 80%、new_duplicated_lines_density < 3%）
- [ ] E2E `cross-service.spec.ts` が JWT 認証ヘッダ付きで全フロー PASS
- [ ] マルチパースペクティブレビュー（developing-review）を 1 回以上実施し指摘事項を記録
- [ ] iteration_report-9.md と retrospective-9.md を作成

### デモ項目

- [ ] Stripe Dashboard から Test Mode で webhook を送信 → S23 で部分入金履歴がリアルタイム表示される
- [ ] AWS Secrets Manager Console で手動 rotation 実行 → trackingms の `@Scheduled` refresh で新 secret が反映、既存 JWT も `AWSPREVIOUS` で引き続き検証 OK
- [ ] 認証ヘッダなしで `/api/v1/billing/invoices` に GET → 401 Unauthorized、認証ヘッダ + 不適切ロールで 403 Forbidden、適切ロールで 200 OK
- [ ] SendGrid WireMock スタブで 5xx を返す → failure counter が increment され通知失敗ログが出力される

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
| 2026-06-06 | 整合性検証（validating-iteration-plan）の結果、user_story.md の US24/US25 と番号衝突していたため US24-27 → US26-29 にリナンバリング。user_story.md に US26-29 を新規追加済み | k2works |
| 2026-06-06 | 設計ドキュメント先行更新: domain-model.md に PARTIALLY_PAID / BalanceTracker / RecordPartialPaymentCommand / TrackingTokenSecretProvider 追加、data-model.md に webhook_processed テーブル + paid_so_far カラム追加、ui_design.md に S23 部分入金履歴 + Stripe 遷移リンク + alert-* スタイル追加 | k2works |
| 2026-06-06 | IT8 レビュー指摘事項対応方針を追記（高 H1=A4.1 / H2=IT8 消化済み / H3=A4.2、中 M1=IT9 中対応 / M2=許容 / M3=IT10 / M4=A1 統合 / M5=A3 統合、低 L1-L3=IT11 以降） | k2works |
| 2026-06-06 | IT9 着手 Phase 0 詳細化: タイトルから「（スケルトン）」削除、リスクと対策（R1-R6）/ Definition of Done（8 項目）/ デモ項目（4 項目）を追記 | k2works |
| 2026-06-06 | A1.1〜A1.5b 実装完了（Stripe webhook + BalanceTracker + 部分入金 UI、6h）、A2.1〜A2.3 実装完了（AWS Secrets Manager + Lambda + Terraform、4.5h）、A3.1 全 ms HerokuSecurityConfig 追加 + A3.3 gatewayms JWT 検証 GlobalFilter（2h）、A4.2 + A3.4 CI コスト測定 + M5 poll 実測手順を test_strategy.md に追記（1h） | k2works |

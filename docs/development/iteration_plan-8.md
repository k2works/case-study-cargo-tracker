# イテレーション 8 計画（IT8・本番デプロイ準備 + Phase 2 完了、Phase 2 / 4）

| 項目 | 内容 |
|------|------|
| **イテレーション** | IT8（本番デプロイ準備 + IT7 持ち越し ADR 実装、Phase 2 完了） |
| **期間** | 2026-08-27 〜 2026-09-09（計画 2 週間） |
| **計画 SP** | 8（Buffer + IT7 持ち越し ADR 実装） |
| **想定ベロシティ** | 8.3 SP（IT5=10 / IT6=9 / IT7=8 の平均）。IT8 は 8 SP の計画値と一致 |
| **ステータス** | スケルトン（IT7 完了時点で起票、2026-06-05） |

## ゴール

1. **本番デプロイ可能な状態に仕上げる**: IT7 で起票した ADR-0017 / ADR-0018 / ADR-0019 / ADR-0015 後半を実装し、SendGrid 統合 + ShedLock クラスタ排他 + RestShipperInfoAcl + PaymentDetailRecorded を本番運用可能な状態にする
2. **ADR-0016 完全移行**: 既存 10 グループの旧名 @ProcessingGroup を新規約準拠（cross-/local-/outbound-）に一斉改名し、token 移行手順を本番でも実行可能にする
3. **ArchUnit 1.5+ アップグレード**: JDK 25 のクラスファイル major version 69 完全サポート版に切替え、Spring scan で代替している規約検査を ArchUnit DSL に統一する
4. **Phase 2 完了**: Release 2.1 で精算機能を加えた完全な国際貨物輸送管理システムを本番デプロイ可能な状態にする

## 満足条件

### スコープ（IT7 持ち越し + IT8 仕上げ）

| カテゴリ | 項目 | 規模 | 根拠 |
|---------|------|------|------|
| 外部ライブラリ統合 | ADR-0017 ShedLock 5.x | 3h | OverdueScheduler の multi-instance 対応 |
| 外部ライブラリ統合 | ADR-0018 SendGrid SDK + テンプレート | 4h | trackingms + billingms 通知の実メール送信 |
| 外部ライブラリ統合 | ADR-0015 後半 RestShipperInfoAcl | 4h | Resilience4j + Caffeine + 手動入力 fallback |
| 内部 event 追加 | ADR-0019 PaymentDetailRecorded | 4h | H1 修正の副作用解消、payment テーブルに method/ref 反映 |
| プロセス改善 | TDD Red/Green/Refactor 分離コミット運用 | 0.5h | retrospective-7 P1 / T1 |
| アーキ規約 | ADR-0016 旧名グループ一斉改名 + token 移行 | 6h | 全 10 グループの prefix 規約準拠化 |
| CI 検知 | ArchUnit 1.5+ アップグレード + DSL 統一 | 2h | retrospective-7 T10 |
| ADR | ADR-0020 決済機関 webhook 選定 起票 | 2h | Stripe / GMO の評価、IT9 着手前準備 |

### 追加スコープ（IT7 完了時 IT8 マーカー棚卸し結果、SP 外）

| カテゴリ | 項目 | 規模 | 根拠（IT8 マーカー所在）|
|---------|------|------|------|
| セキュリティ統合 | 全サービス Spring Security 統一（`@PreAuthorize` + SecurityFilterChain）| 3h | InvoiceController L31 / 各 Controller 認可未実装 |
| セキュリティ統合 | trackingms PublicTrackingTokenFilter → SecurityFilterChain 統合 | 1h | PublicTrackingTokenFilter L27 |
| 鍵運用 | trackingms 公開トークン鍵を AWS Secrets Manager + 四半期ローテーション | 2h | trackingms `application.yml` L32 |
| ドメインロジック拡張 | OptimalRouteService の Dijkstra/A* 移行（多段経由・大量航海対応）| 3h | OptimalRouteService L33 |
| 設定駆動拡張 | RateTable の運用設定 DB 移行（経理担当者が料金改定可能）| 2h | BillingCommonConfig L21 / RateTableTest L65 |
| 設定駆動拡張 | BillingProperties paymentDueDays を Map<ShipperType, Integer> に拡張（NET30/60/90）| 1h | BillingProperties L8 / application.yml L24 |
| アーキ整合 | handlingms outbound publisher の集約発火型移行（ArchUnit 除外解消）| 2h | HandlingArchitectureTest L81 |

合計追加: 14h（SP 外、本番デプロイ準備の一部として IT8 内に取り込み）

### スコープ外（IT9 以降）

- 部分入金対応（PartialPaymentRecorded、UI/API 拡張）
- 多言語通知テンプレート（英語版）
- SonarQube Cloud 連携の安定化
- Heroku → AWS 移行検討

## 受け入れ基準

### A1: ShedLock 統合（ADR-0017）

1. `OverdueScheduler.scheduledRun` に `@SchedulerLock(name = "billing-overdue-scheduler")` が付与されている
2. Flyway V3 で billing_read_db に `shedlock` テーブルが作成されている
3. 統合テストで「2 instance 並列発火時に 1 instance のみが処理する」ことを検証
4. Heroku `web=2` 展開時の動作確認（rolling deploy 中の二重発火が発生しないこと）

### A2: SendGrid 統合（ADR-0018）

1. `SendGridNotificationAcl` が `trackingms` / `billingms` の `NotificationAcl` 実装として動作
2. `@ConditionalOnProperty(name = "notification.adapter", havingValue = "sendgrid")` で切替可能
3. テンプレート ID は `application-heroku.yml` で管理（8 種のテンプレート）
4. 送信失敗時は WARN ログ + `notification.sent{status=failure}` counter 発行、業務フローは止めない
5. WireMock + 統合テストで SendGrid client 呼出を検証
6. Heroku Add-on SendGrid Starter プロビジョニング完了

### A3: RestShipperInfoAcl（ADR-0015 後半）

1. `RestShipperInfoAcl` が bookingms `GET /api/v1/shippers/{id}` を呼んで `CorporateContract` を返す
2. `@CircuitBreaker(name = "shipperInfo", fallbackMethod = "fallback")` で Resilience4j 統合
3. `@Cacheable(value = "shipperInfo", ...)` + Caffeine（TTL 5min）でアプリ層キャッシュ
4. circuit OPEN 時の手動入力 fallback UI が S23 に追加（経理担当者が割引率を手動入力可能）
5. WireMock + bookingms タイムアウト時のテストで fallback 経路を検証
6. `StubShipperInfoAcl` は `@ConditionalOnMissingBean` で開発/テスト用に残存

### A4: PaymentDetailRecorded（ADR-0019）

1. `billingms.domain.events.PaymentDetailRecorded` 内部 event を追加
2. `Invoice.handle(RecordPaymentCommand)` 内で shared `PaymentRecordedEvent` + 内部 `PaymentDetailRecorded` を連続 apply
3. `InvoiceProjection.apply(PaymentDetailRecorded)` で `payment.payment_method` / `payment.external_reference` 更新
4. `PaymentMapper.updatePaymentDetail` SQL 追加
5. InvoiceAggregateTest で 2 event の連続発火を `expectEvents` で検証
6. E2E `cross-service.spec.ts` で `payment.payment_method = 'BANK_TRANSFER'` 反映確認

### A5: ADR-0016 旧名グループ一斉改名

1. 全 10 グループ（booking-saga / route-confirmed / cargo-snapshot / handling-cross-service-publish / tracking-local-projection / tracking-issuance-requests / tracking-notifications / handling-activity-events / route-design-requests / local-tracking-exception-projection）の改名マッピング表に従って一斉改名
2. `token_entry` テーブルの token 移行を環境別（local-h2 / local-docker / Heroku 本番）に実行
3. 全サービスの ArchUnit `processingGroupPrefixConvention` を soft warning から hard assertion に変更
4. 移行手順を `architecture_backend.md` に記載

### A6: ArchUnit 1.5+ アップグレード

1. `gradle/libs.versions.toml` の archunit を 1.5.x 以上に更新
2. `BillingArchitectureTest.processingGroupPrefixConvention` を ArchUnit DSL ベースに統一（Spring scan 撤去）
3. 全サービスの ArchUnit テストで `@AnalyzeClasses` が JDK 25 クラスを完全に読めることを検証
4. `tech_stack.md` の archunit バージョンを更新

## タスク

### 1. 基盤改善・移行作業

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 1.1 | ArchUnit 1.5+ アップグレード（libs.versions.toml + 5 サービス ArchUnit テスト DSL 統一） | 2h | - | [ ] |
| 1.2 | ADR-0016 全 10 グループ一斉改名 + token 移行手順実行（local-h2 / local-docker / Heroku 本番）| 6h | - | [ ] |
| 1.3 | TDD Red/Green/Refactor 分離コミット運用ルール文書化（開発ガイド追記）| 0.5h | - | [x] | <!-- IT7 内文書化済（commit 4afd7c05）、pre-commit hook 実装は本タスクで -->
| 1.4 | 全サービス Spring Security 統一（@PreAuthorize + SecurityFilterChain、IT8 マーカー棚卸し）| 3h | - | [ ] |
| 1.5 | trackingms PublicTrackingTokenFilter → SecurityFilterChain 統合 | 1h | - | [ ] |
| 1.6 | trackingms 公開トークン鍵を AWS Secrets Manager + 四半期ローテーション | 2h | - | [ ] |
| 1.7 | OptimalRouteService の Dijkstra/A* 移行（多段経由・大量航海対応）| 3h | - | [ ] |
| 1.8 | RateTable の運用設定 DB 移行（経理担当者が料金改定可能）| 2h | - | [ ] |
| 1.9 | BillingProperties paymentDueDays を Map<ShipperType, Integer> に拡張 | 1h | - | [ ] |
| 1.10 | handlingms outbound publisher の集約発火型移行（ArchUnit 除外解消）| 2h | - | [ ] |

### 2. A1 ShedLock 統合

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 2.1 | ShedLock 5.x 依存追加（spring + jdbc-template）+ Flyway V3 shedlock テーブル | 1h | - | [ ] |
| 2.2 | OverdueScheduler に @SchedulerLock 付与 + 統合テスト（2 instance シミュレーション）| 2h | - | [ ] |

### 3. A2 SendGrid 統合

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 3.1 | SendGrid SDK 依存追加 + SendGridNotificationAcl 実装（trackingms 6 メソッド）| 2h | - | [ ] |
| 3.2 | SendGridNotificationAcl 拡張（billingms 3 メソッド）+ テンプレート ID 設定 | 1h | - | [ ] |
| 3.3 | WireMock 統合テスト + Heroku SendGrid Add-on プロビジョニング | 1h | - | [ ] |

### 4. A3 RestShipperInfoAcl

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 4.1 | Resilience4j + Caffeine 依存追加 + RestShipperInfoAcl 実装 | 2h | - | [ ] |
| 4.2 | Circuit Breaker fallback + 手動入力 UI（S23 改修）| 1.5h | - | [ ] |
| 4.3 | WireMock タイムアウトテスト + Caffeine TTL 検証 | 0.5h | - | [ ] |

### 5. A4 PaymentDetailRecorded

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 5.1 | PaymentDetailRecorded event + Invoice 集約拡張 + InvoiceProjection 拡張 | 2h | - | [ ] |
| 5.2 | PaymentMapper.updatePaymentDetail + cross-service E2E 更新 | 2h | - | [ ] |

### 6. ADR-0020 起票 + 仕上げ

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 6.1 | ADR-0020 決済機関 webhook 選定（Stripe / GMO / Square 評価）起票 | 2h | - | [ ] |
| 6.2 | マルチパースペクティブレビュー実施 → 重要度「高」を IT 内で対応 | 2h | - | [ ] |
| 6.3 | ふりかえり + 完了報告書作成 + release_plan / docs / mkdocs 反映 | 1h | - | [ ] |

### タスク合計

| カテゴリ | SP | 理想時間 | 状態 |
|---------|----|---------|------|
| 基盤改善・移行（1.1-1.3）| - | 8.5h | [-] |
| IT8 マーカー棚卸し追加（1.4-1.10）| - | 14h | [ ] |
| A1 ShedLock | 1 | 3h | [ ] |
| A2 SendGrid | 2 | 4h | [ ] |
| A3 RestShipperInfoAcl | 2 | 4h | [ ] |
| A4 PaymentDetailRecorded | 2 | 4h | [ ] |
| ADR-0020 + 仕上げ | 1 | 5h | [ ] |
| **合計** | **8** | **42.5h** | |

**注**: 1.4-1.10 は IT7 完了時点のコード IT8 マーカー棚卸しで発見した追加項目（14h）。
本番デプロイ準備の一部として SP 外で IT8 内に取り込み。総工数は 28.5h → 42.5h に増加するが、
ストーリーポイント自体は変動なし（IT8 のスコープは「本番デプロイ可能な状態」と定義）。

**進捗率**: 0%（0/8 SP）— IT8 着手前

## スケジュール

### Week 1（Day 1-5）

| 日 | タスク |
|----|--------|
| Day 1 | 1.1 ArchUnit 1.5+ + 1.3 TDD 規律文書化 + 2.1 ShedLock 依存 |
| Day 2 | 1.2 ADR-0016 一斉改名（local-h2 / local-docker 移行）|
| Day 3 | 1.2 ADR-0016 Heroku 本番移行 + 2.2 ShedLock 統合テスト |
| Day 4 | 3.1 SendGrid SDK + trackingms 6 メソッド |
| Day 5 | 3.2 billingms 3 メソッド + 3.3 WireMock + Heroku Add-on |

### Week 2（Day 6-10）

| 日 | タスク |
|----|--------|
| Day 6 | 4.1 RestShipperInfoAcl + Resilience4j |
| Day 7 | 4.2 fallback UI + 4.3 WireMock タイムアウト |
| Day 8 | 5.1 PaymentDetailRecorded event + 集約拡張 |
| Day 9 | 5.2 投影 SQL + cross-service E2E + 6.1 ADR-0020 |
| Day 10 | 6.2 マルチパースペクティブレビュー + 6.3 ふりかえり + 完了報告書 |

## 設計

> **注**: IT8 の設計はすべて IT7 で起票した ADR-0015 後半 / ADR-0017 / ADR-0018 / ADR-0019 に従う。新規 ADR は ADR-0020（決済機関 webhook）のみ起票。

### 主要設計方針

- **ShedLock JdbcTemplateLockProvider**: 既存 `billing_read_db` 内に `shedlock` テーブルを Flyway V3 で作成。複雑な分散ロック実装を避ける。詳細は [ADR-0017](../adr/0017-overdue-scheduler-cluster-lock.md)
- **SendGrid Dynamic Templates**: テンプレート ID を `application-heroku.yml` で管理し、多言語化（IT9）への布石とする。詳細は [ADR-0018](../adr/0018-notification-adapter-selection.md)
- **RestShipperInfoAcl の fallback 階層**: Circuit Breaker OPEN → Caffeine cache → 手動入力 UI の 3 段階。billingms が bookingms 停止中でも業務継続可能に。詳細は [ADR-0015 §後半](../adr/0015-billingms-cross-service-and-shipper-acl.md)
- **PaymentDetailRecorded 内部 event 設計**: shared event は cross-service 最小契約のまま、内部 event で運用情報を補完。詳細は [ADR-0019](../adr/0019-payment-detail-recorded-event.md)
- **ADR-0016 token 移行手順**: 環境別の手順（H2 / Docker / Heroku）を ADR-0016 §3 から引用し、必要なら CLI スクリプト化

## 受け入れ基準（IT7 から引継ぎ）

- [ ] retrospective-7 Try T10（ArchUnit 1.5+ DSL 統一）対応
- [ ] retrospective-7 Try T11（ADR-0016 完全移行）対応
- [ ] retrospective-7 Try T1（TDD Red/Green/Refactor 分離コミット運用化）開発ガイド追記

## 履歴

| 日付 | 内容 | 担当 |
|------|------|------|
| 2026-06-05 | スケルトン作成（IT7 完了時、Ralph Loop モード）。IT7 持ち越し ADR 実装 + アーキ規約完全移行を中心に 8 SP / 28.5h で設計 | k2works |

## 参照

- [リリース計画](release_plan.md)
- [IT7 計画](iteration_plan-7.md)
- [IT7 完了報告書](iteration_report-7.md)
- [IT7 ふりかえり](retrospective-7.md)
- [ADR-0015 billingms cross-service + ShipperInfo ACL](../adr/0015-billingms-cross-service-and-shipper-acl.md)
- [ADR-0016 @ProcessingGroup 一斉改名 + token 移行](../adr/0016-processing-group-renaming.md)
- [ADR-0017 OverdueScheduler クラスタ排他制御](../adr/0017-overdue-scheduler-cluster-lock.md)
- [ADR-0018 通知アダプタ選定](../adr/0018-notification-adapter-selection.md)
- [ADR-0019 PaymentDetailRecorded 補完イベント](../adr/0019-payment-detail-recorded-event.md)

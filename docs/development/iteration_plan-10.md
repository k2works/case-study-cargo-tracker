# イテレーション 10 計画（スケルトン）

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | IT10（Release 1.1 正式版昇格 / 認可強化 + UX 改善 + staging E2E） |
| **期間** | 2 週間（Week 19-20、暫定） |
| **想定ベロシティ** | 8 SP（IT5=10 / IT6=9 / IT7=8 / IT8=8 / IT9=8 の平均値、IT9 100% 達成実績の維持） |
| **ゴール** | IT9 で完成した Release 1.1 主要機能（Stripe webhook / AWS Secrets Manager / 認可基盤 / SendGrid WireMock）の上に、認可深層強化 + UX 改善 + staging E2E + 構造検証自動化を積み上げ、**Release 1.1 を正式版へ昇格**する。GitHub Release タグ + CHANGELOG 確定 + 本番デプロイ可能宣言まで完遂。 |

---

## ゴール

### イテレーション終了時の達成状態

1. **A1 認可深層強化（A3.2 持ち越し）**: 全 ms Controller に `@PreAuthorize("hasRole('XXX')")` を付与し、URL ルール認可と二段重層の深層防御を確立する。`@WithMockUser` テストパターンを `developing-backend` スキルに反映。
2. **A2 RestShipperInfoAcl fallback UX 改善（M3 持ち越し）**: Circuit Breaker OPEN 時の fallback を「個人扱い（discountRate=0）」から「discountRate=null（未確定）」に変更し、S23 で経理担当者に明示警告を表示する。
3. **A3 staging 環境構築 + E2E 認可実機検証**: Heroku staging app（dev plan）構築、JWT 経由 E2E、Stripe Test Mode webhook、AWS Secrets Manager 手動 rotation、SonarQube Quality Gate 実機計測。
4. **A4 Flyway migration × enum 同期自動検証**: ArchUnit または独自テストで「CHECK 制約値リスト ⊃ enum 値」を CI 検証する仕組みを追加（IT9 V5 バグ再発防止）。
5. **A5 Release 1.1 正式版昇格**: CHANGELOG 確定 + GitHub Release タグ + 本番デプロイ可能宣言。

### 成功基準

- [ ] 全 ms Controller に `@PreAuthorize` 付与、@WithMockUser テストで認可違反 403 を検証
- [ ] S23 で Circuit Breaker OPEN 時に「割引率未確定」alert-warning が表示される
- [ ] staging app で E2E（cross-service.spec.ts）が JWT 認証ヘッダ付きで全 PASS
- [ ] staging で Stripe Test Mode webhook が実機到達して PARTIALLY_PAID 遷移する
- [ ] AWS Secrets Manager で rotate-secret 実行 → trackingms refresh で新 secret 反映
- [ ] BillingStatus enum / chk_invoice_status CHECK 制約の同期検証が CI で動く
- [ ] CHANGELOG.md に Release 1.1 セクション + GitHub Release タグ作成

---

## ユーザーストーリー

### 対象ストーリー（暫定）

| ID | ストーリー | SP | 優先度 |
|----|----------|----|----|
| US30 | システム管理者として、全 Controller のメソッド単位で認可違反を 403 で拒否したい（URL ルール認可と深層防御で重層化） | 2 | 必須 |
| US31 | 経理担当者として、Circuit Breaker OPEN 時に「割引率が未確定」と明示警告を受けたい（個人扱い誤認の防止） | 1 | 必須 |
| US32 | 運用担当者として、staging 環境で全 E2E が JWT 認証ヘッダ付きで通ることを確認したい（本番デプロイ前の最終検証） | 3 | 必須 |
| US33 | 開発チームとして、Flyway migration の CHECK 制約と enum 値の不一致を CI で検知したい（IT9 V5 バグ再発防止） | 1 | 必須 |
| US34 | プロダクトオーナーとして、Release 1.1 を GitHub Release タグ + CHANGELOG で正式版として公開したい | 1 | 必須 |
| **合計** | | **8** | |

---

## タスク（スケルトン、IT10 着手時に詳細化）

### A1: 認可深層強化（US30 / 2 SP）

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 1.1 | bookingms / routingms / handlingms / billingms / trackingms の各 Controller メソッドに @PreAuthorize 付与 | 2h | - | [ ] |
| 1.2 | @WithMockUser + 認可違反 403 単体テスト × 5 ms | 2h | - | [ ] |
| 1.3 | developing-backend スキルに @PreAuthorize + @WithMockUser パターンを追記 | 1h | - | [ ] |
| 1.4 | **IT9 レビュー H3**: 各 ms に PreAuthFilter を追加し `X-Forwarded-Role` を Authentication に変換、直接アクセス時の BASIC 認証突破リスクを解消 | 2h | - | [ ] |
| 1.5 | **IT9 レビュー H4**: `@Profile("!heroku")` でも認可ロジックの slice テストを動かす（`@AutoConfigureMockMvc` + `@WithMockUser` で SecurityFilterChain 検証） | 1.5h | - | [ ] |
| 1.6 | **IT9 レビュー H10**: ROLE_ACCOUNTANT / ROLE_ADMIN 等の付与・四半期棚卸し手順を `docs/design/operation.md` に追記 | 1h | - | [ ] |

### A2: fallback UX 改善（US31 / 1 SP）

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 2.1 | RestShipperInfoAcl の fallback を null 返却に変更（既存テスト調整含む） | 1h | - | [ ] |
| 2.2 | InvoiceDetailPage S23 で null discountRate を「割引率未確定」alert-warning として表示 | 1h | - | [ ] |
| 2.3 | フロントエンドテスト追加（alert-warning 表示の単体テスト 2 件）| 1h | - | [ ] |

### A3: staging 環境構築 + E2E（US32 / 3 SP）

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 3.1 | Heroku staging app（dev plan）作成 + 各 ms デプロイ + JWT_SECRET 等 Config Vars 設定 | 3h | - | [ ] |
| 3.2 | staging E2E スクリプト（Playwright JWT ヘッダ自動付与）+ cross-service.spec.ts 実行 | 3h | - | [ ] |
| 3.3 | Stripe Test Mode webhook を staging billingms に向けて手動送信 + PARTIALLY_PAID 検証 | 1h | - | [ ] |
| 3.4 | AWS Secrets Manager で rotate-secret 実行 + trackingms refresh ログ確認 | 1h | - | [ ] |
| 3.5 | SonarQube Quality Gate を staging code で実機計測 | 1h | - | [ ] |
| 3.6 | **IT9 レビュー H5**: `PaymentGatewayWebhookIntegrationTest` を 3 メソッドに分割、`await().atMost(5s)` に短縮 | 1h | - | [ ] |
| 3.7 | **IT9 レビュー H6**: HMAC tolerance 境界値（299s / 300s / 301s）テスト + Clock 注入で時刻固定 | 1.5h | - | [ ] |
| 3.8 | **IT9 レビュー H7**: `:check` から `localstack-integration` タグを除外する設定を `build.gradle` に明示、4 分加算の解消確認 | 0.5h | - | [ ] |
| 3.9 | **IT9 レビュー H8**: `charge.refunded` / `charge.dispute.created` 業務シナリオを US26 受入基準に追加、staging で実機検証 | 2h | - | [ ] |
| 3.10 | **IT9 レビュー H9**: rotation 失敗時の PagerDuty/Slack 通知（Micrometer Counter + アラート閾値）設計と staging 動作確認 | 2h | - | [ ] |

### A4: Flyway × enum 同期自動検証（US33 / 1 SP）

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 4.1 | BillingStatus enum 値 ⊂ Flyway V2/V5 の chk_invoice_status を検証するテスト（@MybatisTest 経由）| 1.5h | - | [ ] |
| 4.2 | 他 ms の同種 enum × CHECK 制約も横展開（handling_type / transport_status 等）| 1.5h | - | [ ] |

### A5: Release 1.1 正式版昇格（US34 / 1 SP）

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 5.1 | CHANGELOG.md に Release 1.1 セクション追加（IT8 + IT9 の機能を集約） | 1h | - | [ ] |
| 5.2 | GitHub Release タグ作成（v1.1.0）+ release notes 公開 | 0.5h | - | [ ] |
| 5.3 | 本番デプロイ可能宣言（README + index.md に明記） | 0.5h | - | [ ] |
| 5.4 | **IT9 レビュー M8**: CHANGELOG のバージョン順序ぶれを「Release ライン経緯」セクションで明示、または再採番 | 0.5h | - | [ ] |
| 5.5 | **IT9 レビュー L5**: README の主要機能表に Stripe webhook / AWS Secrets Manager / 認可基盤を 1 行ずつ追記 | 0.5h | - | [ ] |
| 5.6 | **IT9 レビュー L6**: ADR-0020 / ADR-0021 のステータスを「採用済み（実装完了）」に更新 | 0.5h | - | [ ] |

#### タスク合計

| カテゴリ | SP | 理想時間 | IT9 レビュー指摘の取り込み |
|---------|----|----|----------------------|
| A1 認可深層強化 | 2 | 9.5h | H3 / H4 / H10 |
| A2 fallback UX 改善 | 1 | 3h | （M9 は ADR 起票判断） |
| A3 staging 環境構築 + E2E | 3 | 16h | H5 / H6 / H7 / H8 / H9 |
| A4 Flyway × enum 同期自動検証 | 1 | 3h | — |
| A5 Release 1.1 正式版昇格 | 1 | 3.5h | M8 / L5 / L6 |
| **合計** | **8** | **35h** | 高 8 件 / 中 1 件 / 低 3 件 |

**進捗率**: 0%（0/8 SP）— IT10 着手前（スケルトン、IT9 レビュー指摘 12 件統合済み）

> **見積もり時間が増加した理由**: IT9 マルチパースペクティブレビューの指摘 12 件を統合（22h → 35h）。SP は維持しているが、Week 19-20 の実工数は IT9 比 1.6 倍となる。staging 構築日（Week 19 Day 5）を Week 20 Day 1 まで延長する可能性あり。IT10 着手 1 週目で消化ペースが想定の 70% を下回る場合、A3.9（H8 業務シナリオ）/ A3.10（H9 アラート設計）を IT11 へ持ち越す判断を行う。

---

## スケジュール（暫定）

| 週 | 主担当 |
|----|--------|
| Week 19 Day 1-2 | A1 認可深層強化（5 ms × Controller + @WithMockUser）|
| Week 19 Day 3-4 | A2 fallback UX 改善 + A4 Flyway × enum 同期検証 |
| Week 19 Day 5 | A3.1 Heroku staging app 構築 |
| Week 20 Day 1-3 | A3.2-A3.5 staging E2E + Stripe / AWS / SonarQube 実機検証 |
| Week 20 Day 4 | A5 CHANGELOG + GitHub Release タグ |
| Week 20 Day 5 | マルチパースペクティブレビュー + ふりかえり + 完了報告書 |

---

## IT9 ふりかえり Try の取り込み

[retrospective-9.md](retrospective-9.md) で挙がった Try のうち IT10 で対応:

| ID | Try 内容 | IT10 対応 |
|----|---------|---------|
| T1 | Flyway migration × enum 同期の自動検証 | **A4 で対応** |
| T2 | SDK 制約に直面したら最初にソース展開する習慣を運用ルール化 | A1.3 に `コーディングとテストガイド.md` 追記を含める |
| T3 | 各 Controller への @PreAuthorize 付与 | **A1 で対応** |
| T4 | staging 環境構築 | **A3 で対応** |
| T5 | RestShipperInfoAcl fallback UX 改善 | **A2 で対応** |
| T6 | テストメソッド名の運用ルール明文化 | A1.3 に追記（@PreAuthorize テスト命名と同時に） |
| T7 | LocalStack IT を CI ワークフローで分離 | IT11 に持ち越し（staging 計測結果次第） |

---

## IT9 マルチパースペクティブレビュー指摘の取り込み

[IT9_review_20260606.md](../review/IT9_review_20260606.md) で挙がった 26 件のうち、IT10 で取り込む 12 件と IT11+ に持ち越す 14 件を以下に整理。

### IT10 で取り込む（12 件）

| ID | 観点 | 指摘要約 | IT10 タスク |
|----|------|---------|------------|
| H3 | architect | JWT 信頼境界（直接 ms アクセスで BASIC 突破リスク） | A1.4 PreAuthFilter |
| H4 | architect | `@Profile("heroku")` で認可テストがリグレッション検知不可 | A1.5 slice テスト |
| H5 | tester | `PaymentGatewayWebhookIntegrationTest` の 45s 最悪ケース | A3.6 IT 分割 |
| H6 | tester | HMAC tolerance 境界値 + Clock 注入 | A3.7 |
| H7 | tester | LocalStack IT の `:check` 隔離未確認 | A3.8 |
| H8 | user-rep | 返金 / 過剰入金 / dispute シナリオ欠落 | A3.9 US26 受入基準追加 |
| H9 | user-rep | rotation 失敗時の PagerDuty/Slack 通知未定義 | A3.10 アラート設計 |
| H10 | user-rep | 既存ユーザーの再認可 / 棚卸し運用未定義 | A1.6 operation.md 追記 |
| M8 | tech-writer | CHANGELOG バージョン順序ぶれ | A5.4 経緯セクション |
| L5 | tech-writer | README に IT9 / Release 1.1 反映なし | A5.5 |
| L6 | tech-writer | ADR-0020 / 0021 ステータス未更新 | A5.6 |

### IT11+ に持ち越し（14 件）

| ID | 観点 | 指摘要約 | 持ち越し理由 |
|----|------|---------|---------------|
| H1 | programmer | Invoice ES 決定性（PaymentRecordedEvent に paidSoFar 含める） | shared 契約変更で影響範囲広、ADR 起票必要 |
| H2 | programmer | `PARTIALLY_PAID → PAID` 経路テスト不足 | IT11 で BillingStatusTransitionTest 拡充 |
| M1 | programmer | HerokuSecurityConfig のコピペ（shared-security モジュール抽出） | Rule of Three まで様子見 |
| M2 | programmer | `catch (Exception e)` での例外握り潰し | Stripe SDK 例外階層整理と同時実施 |
| M3 | programmer | WebhookProcessed の不変化（MyBatis @ConstructorArgs） | 既存 IT への影響大、別 PR |
| M4 | architect | SendGrid SDK サブクラス化の脆弱性（ArchUnit 化） | SDK メジャー版アップを待つ |
| M5 | architect | Shared event 境界判定（InvoiceProjection 重複確認） | IT10 staging で実機確認 |
| M6 | tester | `BalanceTracker.withTotalDue` エッジケース | プロパティベース検証導入と同時 |
| M7 | tester | JWT フィルタの時刻 / アルゴリズム境界 | IT10 A1 で部分対応、残りは IT11 |
| M9 | user-rep | 残額しきい値の端数処理ルール | 業務要件確認後、ADR で意思決定 |
| L1 | programmer | `verify` API の意図統一 | テスト改善デー（IT11 リファクタリングデー） |
| L2 | programmer | StripeEventTranslator の paid_amount 単位 | ADR-0020 表記確認のみで足りる |
| L3 | programmer | refresh 失敗の Micrometer メトリクス化 | H9 A3.10 と一緒に IT11 で正式実装 |
| L4 | tester | テストフィクスチャ重複（SIGNING_SECRET） | IT11 で TestFixtures 抽出 |
| L7 | tech-writer | iteration_report / retrospective の数値ぶれ | retrospective-9.md 補完で対応済み |

---

## リスクと対策

| # | リスク | 影響度 | 対策 |
|---|-------|-------|------|
| R1 | Heroku staging app の dev plan 構築コスト（時間 + Add-on 費用）が想定を超える | 高 | dev plan は eco dyno + Kafka shared を選び、月額 $20 以内に抑える。staging を temporary（IT10 期間のみ）として扱い、IT10 完了後は停止 |
| R2 | staging E2E で本番未検出のロール認可漏れが発覚し、A1 @PreAuthorize の修正が必要 | 高 | A1 を Week 19 Day 1-2 で先行完遂、A3 staging E2E は Week 20 で実機検証。差分修正のバッファを Week 20 Day 1-2 に確保 |
| R3 | AWS Secrets Manager の rotate-secret が Lambda 経由で失敗（IAM Role 不足等） | 中 | A2.3 で構築した Terraform IaC を staging に適用し、初回 rotation を手動 trigger して動作確認。失敗時は CloudWatch Logs で詳細確認 |
| R4 | Stripe Test Mode webhook が Heroku のオートスリープで欠落 | 中 | staging を eco dyno で運用、Stripe 側の retry mechanism（72 時間最大 5 回）で復旧。webhook_processed テーブルで欠落検知 |
| R5 | Flyway × enum 同期テスト（A4）の偽陽性で既存テストが失敗 | 低 | Test 設計時に既存 BillingStatus / chk_invoice_status の値リストを比較で確認、想定外の差分があれば追加検出と判断 |
| R6 | CHANGELOG / GitHub Release タグ作成で Release 1.0 候補（IT8）との重複混乱 | 低 | CHANGELOG セクションを「Release 1.0（IT4 MVP）」「Release 1.0 候補（IT8 本番準備）」「Release 1.1（IT9）」「Release 1.1 正式版（IT10）」と明示区分。タグは `v1.0.0` / `v1.1.0` の semver で運用 |

## 完了条件

### Definition of Done

- [ ] A1-A5 全タスクが状態列で [x] に更新されている（25 タスク中 25 完了）
- [ ] 全 5 ms（bookingms / routingms / handlingms / billingms / trackingms）で `:check` BUILD SUCCESSFUL
- [ ] フロントエンド `npm run test:coverage` が 80% 以上を維持（IT9 245 件 + IT10 新規）
- [ ] ArchUnit hard assertion すべて PASS（既存 4 件 + A4 Flyway × enum 同期テスト追加）
- [ ] SonarQube Quality Gate PASS（staging code で実機計測、A3.5）
- [ ] Heroku staging app（authms / 5 ms / gatewayms × 7 + Aiven Kafka + PostgreSQL）が稼働
- [ ] Playwright cross-service.spec.ts が staging に対して JWT 認証ヘッダ付きで全 PASS（A3.2）
- [ ] CHANGELOG.md / GitHub Release v1.1.0 タグ / README + index.md の本番デプロイ可能宣言が反映
- [ ] iteration_report-10.md / retrospective-10.md / release_report-1.1.md 作成

### デモ項目

- [ ] staging app に未認証で `/api/v1/billing/invoices` GET → **401 Unauthorized**
- [ ] staging app に ROLE_SHIPPER 認証で `/api/v1/billing/invoices` GET → **403 Forbidden**（メソッド @PreAuthorize で拒否）
- [ ] staging app に ROLE_ACCOUNTANT 認証で `/api/v1/billing/invoices` GET → **200 OK + Invoice 一覧**
- [ ] S23 で Circuit Breaker OPEN 時 alert-warning「割引率が未確定」が表示される（A2）
- [ ] Stripe Test Mode で webhook を送信 → staging billingms で PARTIALLY_PAID 遷移が S23 に反映
- [ ] AWS Secrets Manager Console で rotate-secret 実行 → CloudWatch Logs で trackingms refresh ログを確認
- [ ] CI で BillingStatus に新規値を追加すると A4 テストが失敗し「Flyway VZ の CHECK 制約に値が反映されていません」エラーを出す（再発防止確認）

---

## 関連ドキュメント

- [iteration_plan-9.md](iteration_plan-9.md) — IT9 計画（100% 達成）
- [iteration_report-9.md](iteration_report-9.md) — IT9 完了報告書
- [retrospective-9.md](retrospective-9.md) — IT9 ふりかえり（KPT）
- [release_plan.md](release_plan.md) — Release 1.1 正式版昇格スケジュール

---

## 更新履歴

| 日付 | 内容 | 担当 |
|------|------|------|
| 2026-06-06 | IT9 100% 達成 + IT8 review 11 件全解消を受けて IT10 スケルトン計画を作成。Release 1.1 正式版昇格を目的とし、認可深層強化（A3.2 持ち越し）/ UX 改善（M3）/ staging E2E / Flyway×enum 自動検証 / CHANGELOG + GitHub Release タグ の 5 ストーリー 8 SP で構成 | k2works |
| 2026-06-06 | IT9 マルチパースペクティブレビュー（5 観点、26 件指摘）を反映。高 8 件（H3 / H4 / H5 / H6 / H7 / H8 / H9 / H10）+ 中 1 件（M8）+ 低 3 件（L5 / L6）の 12 件を A1 / A3 / A5 に統合（タスク 11 件追加、見積時間 22h → 35h）。残り 14 件は「IT11+ に持ち越し」表で明示 | k2works |

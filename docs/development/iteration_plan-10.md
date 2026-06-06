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

#### タスク合計

| カテゴリ | SP | 理想時間 |
|---------|----|----|
| A1 認可深層強化 | 2 | 5h |
| A2 fallback UX 改善 | 1 | 3h |
| A3 staging 環境構築 + E2E | 3 | 9h |
| A4 Flyway × enum 同期自動検証 | 1 | 3h |
| A5 Release 1.1 正式版昇格 | 1 | 2h |
| **合計** | **8** | **22h** |

**進捗率**: 0%（0/8 SP）— IT10 着手前（スケルトン）

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

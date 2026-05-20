---
title: イテレーション 8 計画
description: IT8（精算機能 US21/US22/US23 + IT7 技術的負債回収 TI09）の計画。13 SP（残 15 SP から TI09 2 SP + US21 5 SP + US22 3 SP + US23 5 SP）。Release 1.1 の最終イテレーション。
---

# イテレーション 8 計画

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | 8 / 8 |
| **期間** | Week 15-16（2026-08-20 〜 2026-09-02） |
| **ゴール** | IT7 技術的負債（TrackingController 分離・ExceptionType enum・テスト仕様化）を回収しつつ、精算機能（US21 輸送料金算出・US22 法人割引・US23 精算処理）を実装して Release 1.1 を達成する |
| **目標 SP** | 13 |

---

## ゴール

### イテレーション終了時の達成状態

1. **IT7 技術的負債回収（TI09）**: `TrackingExceptionController` 分離・`ExceptionType enum` 導入・LOSS 通知最小実装・テスト仕様化強化を完了し、TrackingController の SRP 違反を解消する
2. **輸送料金算出（US21）**: 距離・重量・品目カテゴリに基づく料金計算ロジックを billingms に実装し、経理担当者が料金を確認できる
3. **法人割引適用（US22）**: 法人荷主に対する割引率（5〜20%）を適用する料金計算を実装する
4. **精算処理（US23）**: 精算（請求書発行・入金確認）フローを実装し、経理担当者が精算状態を管理できる

### 成功基準

- [ ] `TrackingController` を `TrackingExceptionController` に分離し 330 行 → 各 150 行以下に削減
- [ ] `ExceptionType enum` で String 流通を排除（`DELAY` / `DAMAGE` / `LOSS` を型安全に管理）
- [ ] LOSS 緊急通知の最小実装（管理者への通知ログ明示化 or バッジ表示）
- [ ] 「引取済」状態の予約に対して料金算出・確定が可能（S23 請求詳細・算出）
- [ ] 法人荷主（`CORPORATE`）に対して割引率（0〜30%）が自動適用される
- [ ] `POST /api/v1/billing/invoices/{invoiceId}/settle` で精算が完了できる
- [ ] SonarQube Quality Gate PASS（new_coverage 80% 以上）
- [ ] E2E テスト全通過（既存 13 + 新規追加分）

---

## ユーザーストーリー

### 対象ストーリー

| ID | ユーザーストーリー | SP | 優先度 |
|----|-------------------|----|--------|
| TI09 | IT7 技術的負債回収（TrackingController 分離・ExceptionType enum・テスト仕様化） | 2 | 必須 |
| US21 | 輸送料金を算出する | 5 | 必須 |
| US22 | 法人割引を適用する | 3 | 中 |
| US23 | 精算を処理する | 5 | 必須 |
| **合計** | | **15** | |

> **フィーチャバッファ**: 13 SP コミット（US22 3 SP はバッファとして後回し可能）

### ストーリー詳細

#### TI09: IT7 技術的負債回収（2 SP）

**ストーリー**:
> 開発チームとして、IT7 で蓄積した技術的負債（TrackingController 肥大化・ExceptionType String 流通・LOSS 通知虚偽表示）を解消したい。なぜなら、IT8 の精算実装を安全に行うための基盤を整えるためだ。

**受入条件**:

- [ ] `TrackingExceptionController` を分離し `TrackingController` が単一責任を持つ（各 150 行以下）
- [ ] `ExceptionType enum`（`DELAY` / `DAMAGE` / `LOSS`、`isEscalated()` メソッド付き）を導入し String 流通を排除
- [ ] `TrackingExceptionResponse` DTO を新設し `TrackingExceptionRecord` の REST 直露出を解消
- [ ] `registerException` テストに `ArgumentCaptor` を追加してコマンド内容を検証
- [ ] LOSS 選択時に管理者通知ログ（`WARN` レベル以上）を出力する
- [ ] `AggregateTestFixture` で LOSS→`escalated=true`・`resolveException` 不変条件を検証

#### US21: 輸送料金を算出する（5 SP）

**ストーリー**:
> 経理担当者として、配送完了した予約に対して輸送実績（経路・重量・貨物種別・荷役実績）をもとに輸送料金を算出したい。なぜなら、実際の輸送内容に基づく正確な料金を算出し、精算に進めるからだ。

**受入条件**:

- [ ] 「引取済」状態の予約に対して料金算出を開始できる
- [ ] 輸送実績（経路・距離・重量・貨物種別・荷役作業実績）が表示される
- [ ] 基本料金が自動計算される
- [ ] 算出結果を確認して確定操作ができる
- [ ] 確定後、輸送料金が「確定」状態で登録される
- [ ] 例外（遅延・破損等）が発生している場合、料金調整（減額・補償費用）の入力ができる
- [ ] `GET /api/v1/billing/invoices` で算出済み料金一覧を確認できる（S22 請求一覧）
- [ ] フロント S23 請求詳細・算出画面（`BillingInvoiceDetailPage.tsx`）で料金が表示・確定できる

#### US22: 法人割引を適用する（3 SP）

**ストーリー**:
> 経理担当者として、法人荷主の場合に、契約割引率を基本料金に自動適用して割引後の請求金額を確定したい。なぜなら、法人契約条件に基づく正確な割引を自動化し、手計算ミスを防ぐからだ。

**受入条件**:

- [ ] 荷主種別が「法人」の場合、料金算出時に契約割引率が自動的に取得・表示される
- [ ] 割引率（0〜30%）が基本料金に適用され、割引後の金額が表示される
- [ ] 個人荷主の場合は割引が適用されない（割引率 0%）
- [ ] 割引計算の根拠（割引率・基本料金・割引後料金）が精算書に記載される
- [ ] 割引率は荷主マスターの `discountRate` フィールドから取得される

#### US23: 精算を処理する（5 SP）

**ストーリー**:
> 経理担当者として、確定した輸送料金をもとに精算書を発行し、荷主への通知・入金確認・精算完了処理を行いたい。なぜなら、精算業務を一元管理し、入金状況を追跡して確実に精算を完了できるからだ。

**受入条件**:

- [ ] 「確定」状態の輸送料金をもとに精算書（請求番号・請求金額・支払い期限）を発行できる（S24 精算書発行）
- [ ] 精算書が荷主にメール通知される
- [ ] 決済機関との連携により入金確認ができる
- [ ] 入金確認後、精算状態が「精算済」に更新され予約状態も「精算済」になる
- [ ] 支払い期限超過時、経理担当者に未払い通知が送信される（S25 督促一覧）
- [ ] `POST /api/v1/billing/invoices/{invoiceId}/issue` で精算書を発行できる
- [ ] `PATCH /api/v1/billing/invoices/{invoiceId}/settle` で精算完了できる

---

## タスク

### タスク 0: IT7 技術的負債回収 TI09（2 SP）

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 0.1 | `TrackingExceptionController` を分離（エンドポイント 3 件移行） | 4h | - | [ ] |
| 0.2 | `ExceptionType enum` 導入・`TrackingExceptionRecord` の String フィールドを enum に変更 | 2h | - | [ ] |
| 0.3 | `TrackingExceptionResponse` DTO 新設・MapStruct マッピング実装 | 2h | - | [ ] |
| 0.4 | `registerException` テストに `ArgumentCaptor` 追加・`AggregateTestFixture` ユニットテスト追加 | 4h | - | [ ] |
| 0.5 | LOSS 通知ログ出力（`WARN` レベル）+ SonarQube QG チェック | 2h | - | [ ] |

**小計**: 14h（理想時間）

### タスク 1: US21 輸送料金算出（5 SP）

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 1.1 | `billingms` Gradle モジュール作成・Spring Boot スケルトン + ArchUnit 設定 | 4h | - | [ ] |
| 1.2 | `Invoice` 集約（コマンド・イベント・ハンドラー）+ `ChargeCalculationService` TDD | 4h | - | [ ] |
| 1.3 | `InvoiceMapper`（MyBatis）+ `invoice` テーブル Flyway マイグレーション | 4h | - | [ ] |
| 1.4 | `BillingController` エンドポイント 2 件（GET invoices / POST invoices/{id}/calculate）実装 | 4h | - | [ ] |
| 1.5 | フロント S22 請求一覧（`BillingListPage.tsx`）+ S23 請求詳細・算出（`BillingDetailPage.tsx`）+ Vitest テスト | 4h | - | [ ] |

**小計**: 20h（理想時間）

### タスク 2: US22 法人割引適用（3 SP）

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 2.1 | `DiscountPolicy` ドメインサービス（法人/個人分岐・割引率 0〜30% 適用）TDD | 4h | - | [ ] |
| 2.2 | 荷主マスター `discountRate` フィールド参照 + `ChargeCalculationService` に統合 | 4h | - | [ ] |
| 2.3 | フロント 割引表示（割引前・割引後・割引率・割引根拠）コンポーネント追加 + テスト | 4h | - | [ ] |

**小計**: 12h（理想時間）

### タスク 3: US23 精算処理（5 SP）

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 3.1 | `Invoice` 状態遷移（`PENDING` → `CONFIRMED` → `SETTLED`）集約 TDD | 4h | - | [ ] |
| 3.2 | `PaymentMapper`（MyBatis）+ `payment` テーブル Flyway マイグレーション | 4h | - | [ ] |
| 3.3 | `BillingController` エンドポイント 3 件（POST issue / PATCH settle / GET overdue）実装 | 4h | - | [ ] |
| 3.4 | フロント S24 精算書発行（`BillingIssuePage.tsx`）+ S25 督促一覧（`BillingOverduePage.tsx`）+ テスト | 4h | - | [ ] |
| 3.5 | E2E テスト（精算フロー）+ SonarQube QG PASS 確認 | 4h | - | [ ] |

**小計**: 20h（理想時間）

### タスク合計

| カテゴリ | SP | 理想時間 | 状態 |
|---------|----|---------|------|
| TI09: IT7 技術的負債回収（TrackingController 分離 等） | 2 | 14h | [ ] |
| US21: 輸送料金算出（Invoice 集約・S22/S23） | 5 | 20h | [ ] |
| US22: 法人割引適用（DiscountPolicy・0〜30%） | 3 | 12h | [ ] |
| US23: 精算処理（精算書発行・決済確認・S24/S25） | 5 | 20h | [ ] |
| **合計** | **15** | **66h** | |

**1 SP あたり**: 約 4.4h
**フィーチャバッファ**: US22 3 SP（66h の 18%）
**コミット SP**: 13（US22 バッファ除く）
**進捗率**: 0% (0/13 SP)

---

## スケジュール

### Week 1（2026-08-20〜08-26）

```mermaid
gantt
    title イテレーション 8 - Week 1
    dateFormat  YYYY-MM-DD
    section TI09（負債回収）
    Controller 分離・enum 導入    :ti1, 2026-08-20, 1d
    DTO 新設・テスト強化           :ti2, after ti1, 1d
    LOSS 通知・QG チェック         :ti3, after ti2, 0.5d
    section US21（料金算出）
    billingms スケルトン           :u1, 2026-08-20, 1d
    Charge 集約 TDD               :u2, after u1, 1d
    ChargeMapper + Flyway          :u3, after u2, 1d
    BillingController 実装         :u4, after u3, 1d
    フロント BillingChargePage     :u5, after u4, 1d
```

| 日 | タスク |
|----|--------|
| Day 1（08-20） | TI09: TrackingExceptionController 分離・ExceptionType enum 導入 |
| Day 2（08-21） | TI09: DTO 新設・ArgumentCaptor テスト・AggregateTestFixture 追加 |
| Day 3（08-22） | TI09: LOSS 通知ログ + SonarQube QG 確認、US21: billingms スケルトン作成 |
| Day 4（08-25） | US21: Charge 集約（コマンド・イベント）TDD |
| Day 5（08-26） | US21: ChargeMapper + Flyway マイグレーション + BillingController 実装 |

### Week 2（2026-08-27〜09-02）

```mermaid
gantt
    title イテレーション 8 - Week 2
    dateFormat  YYYY-MM-DD
    section US21（料金算出）
    フロント BillingChargePage     :v1, 2026-08-27, 1d
    section US22（法人割引）
    DiscountPolicy TDD             :v2, 2026-08-27, 1d
    discountRate 統合              :v3, after v2, 1d
    フロント 割引表示               :v4, after v3, 0.5d
    section US23（精算処理）
    Settlement 集約 TDD            :v5, 2026-08-28, 1d
    SettlementMapper + Flyway      :v6, after v5, 1d
    BillingController 3 件         :v7, after v6, 1d
    フロント BillingSettlementPage  :v8, after v7, 1d
    E2E + QG 最終確認              :v9, after v8, 1d
```

| 日 | タスク |
|----|--------|
| Day 6（08-27） | US21: フロント BillingChargePage 実装、US22: DiscountPolicy TDD |
| Day 7（08-28） | US22: discountRate 統合 + フロント割引表示、US23: Settlement 集約 TDD |
| Day 8（08-29） | US23: SettlementMapper + Flyway マイグレーション |
| Day 9（09-01） | US23: BillingController 3 件 + フロント BillingSettlementPage |
| Day 10（09-02） | E2E テスト・SonarQube QG 最終確認・Release 1.1 タグ付け |

---

## 設計

### ドメインモデル（billingms）

domain-model.md の Billing Context に準拠する。

```plantuml
@startuml
package "billingms" {
  class Invoice <<AggregateRoot>> {
    + invoiceId: InvoiceId
    + bookingId: BookingId
    + shipperId: ShipperId
    + baseAmount: Money
    + discountAmount: Money
    + adjustmentAmount: Money
    + totalAmount: Money
    + settlementStatus: SettlementStatus
    + paymentDueDate: LocalDate
    + paidAt: LocalDateTime
    + calculateCharge(command): void
    + issue(command): void
    + settle(command): void
  }

  enum SettlementStatus {
    PENDING
    CONFIRMED
    SETTLED
  }

  class DiscountPolicy <<DomainService>> {
    + apply(shipperType: ShipperType, baseAmount: Money, discountRate: Percentage): Money
  }

  Invoice --> SettlementStatus
  Invoice --> DiscountPolicy
}
@enduml
```

> **注**: data-model.md の `invoice` / `payment` テーブルに合わせる。`Charge`/`Settlement` 独自集約ではなく、`Invoice` 集約 1 本で料金確定〜精算完了まで管理する。

### API 設計

| メソッド | エンドポイント | 説明 |
|---------|---------------|------|
| GET | `/api/v1/billing/invoices` | 請求一覧（S22） |
| GET | `/api/v1/billing/invoices/{invoiceId}` | 請求詳細・算出（S23） |
| POST | `/api/v1/billing/invoices/{invoiceId}/calculate` | 料金算出・確定 |
| POST | `/api/v1/billing/invoices/{invoiceId}/issue` | 精算書発行（S24） |
| PATCH | `/api/v1/billing/invoices/{invoiceId}/settle` | 精算完了 |
| GET | `/api/v1/billing/invoices/overdue` | 督促一覧（S25） |

### データベーススキーマ（billingms）

data-model.md の `billingms（billing_read_db）` 定義（`invoice` / `payment` テーブル）に準拠する。

```sql
-- V001: invoice テーブル（data-model.md 準拠）
CREATE TABLE invoice (
    id               BIGSERIAL PRIMARY KEY,
    invoice_id       VARCHAR(36) NOT NULL UNIQUE,
    booking_id       VARCHAR(36) NOT NULL,
    shipper_id       VARCHAR(36) NOT NULL,
    base_amount      DECIMAL(10,2) NOT NULL,
    discount_amount  DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    adjustment_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    total_amount     DECIMAL(10,2) NOT NULL,
    settlement_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payment_due_date DATE,
    paid_at          TIMESTAMP WITH TIME ZONE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version          BIGINT NOT NULL DEFAULT 0
);

-- V002: payment テーブル（data-model.md 準拠）
CREATE TABLE payment (
    id               BIGSERIAL PRIMARY KEY,
    payment_id       VARCHAR(36) NOT NULL UNIQUE,
    invoice_id       BIGINT NOT NULL REFERENCES invoice(id),
    paid_amount      DECIMAL(10,2) NOT NULL,
    paid_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version          BIGINT NOT NULL DEFAULT 0
);
```

---

## リスクと対策

| リスク | 影響度 | 対策 |
|--------|--------|------|
| billingms の新規マイクロサービス作成が想定より時間がかかる | 高 | IT7 の bookingms/trackingms スケルトンを参考に同一パターンで実装。不明点は早期に spike |
| US22 法人割引が SP 不足でバッファ消費になる | 中 | US22 を最初からバッファとして計画。US21/US23 優先で着手。残時間に応じて実装 |
| SonarQube QG がカバレッジ未達で失敗する | 中 | TI09 で既存テストを強化してから新機能実装。billingms のカバレッジ目標 80% 以上を設定 |
| Release 1.1 E2E が既存シナリオとの組み合わせで失敗する | 低 | 精算フロー E2E を最終日に集中実施。リグレッションは Day 9 に確認 |

---

## 完了条件

### Definition of Done

- [ ] TI09 全タスク完了（TrackingController 分離・enum 導入・LOSS 通知・テスト仕様化）
- [ ] US21 / US23 受入条件を全て満たす
- [ ] US22 受入条件を全て満たす（バッファ実施時）
- [ ] Backend / Frontend 全テストがパス
- [ ] SonarQube Quality Gate PASS（new_coverage 80% 以上、violations 0）
- [ ] E2E テスト全通過（既存 13 シナリオ + 精算フロー新規追加）
- [ ] `git tag Release-1.1` を打ち、GitHub Release を作成

### デモ項目

1. TrackingController 分離後の API 動作確認（例外登録・解決エンドポイント）
2. 輸送料金算出（「引取済」予約 → S23 料金算出・確定）
3. 法人割引自動適用（割引前・割引後・割引率の根拠表示）
4. 精算フロー（PENDING → CONFIRMED → SETTLED、S24 精算書発行・S25 督促一覧）
5. SonarQube QG PASS ダッシュボード確認

---

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-05-20 | 初版作成 | AI Agent |
| 2026-05-20 | 整合性検証による修正: US21/US22/US23 ストーリー文・受入条件を user_story.md に合わせ修正（引取済状態・割引率 0〜30%・メール通知・督促通知）。エンティティ名を Charge/Settlement → Invoice（domain-model.md 準拠）に修正。DB スキーマを invoice/payment テーブル（data-model.md 準拠）に修正。画面 ID を S20/S21 → S22〜S25（ui_design.md 準拠）に修正。 | AI Agent |

---

## 関連ドキュメント

- [イテレーション 7 完了報告書](./iteration_report-7.md)
- [イテレーション 7 ふりかえり](./retrospective-7.md)
- [リリース計画](./release_plan.md)

# イテレーション 8 計画

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | 8 |
| **期間** | Week 15-16（2026-05-12〜2026-05-22） |
| **ゴール** | 破損・紛失例外処理と輸送料金算出の API + 画面を実装する |
| **目標 SP** | 16（US20 8 SP + US21 8 SP） |

---

## ゴール

### イテレーション終了時の達成状態

1. **破損・紛失例外処理（US20）**: 追跡管理者（または荷役作業員）が破損・紛失例外を記録し、貨物状態が「例外発生」に更新され、紛失時には escalation 通知が送信される
2. **輸送料金算出（US21）**: 経理担当者が「引取済」状態の予約に対して輸送実績から料金を算出し、確定できる

### 成功基準

- [ ] US20: 追跡番号と例外種別「破損」または「紛失」・発生状況を記録できる
- [ ] US20: 例外記録後に貨物状態が「例外発生（EXCEPTION）」に更新される
- [ ] US20: 紛失時に緊急フラグが設定され管理職への escalation 通知が送信される
- [ ] US20: 対応内容（補償方針等）を入力して荷主に報告を送信できる
- [ ] US21: 「引取済」状態の予約に対して料金算出を開始できる
- [ ] US21: 輸送実績（経路・距離・重量・貨物種別）が表示され、基本料金が自動計算される
- [ ] US21: 算出した料金を確定（保存）できる
- [ ] 全ユニットテスト（BE + FE）がパス
- [ ] BE テストカバレッジ 80% 以上（JaCoCo）

---

## ユーザーストーリー

### 対象ストーリー

| ID | ユーザーストーリー | BE | FE | SP | 優先度 |
|----|-------------------|----|----|-----|--------|
| US20 | 破損・紛失例外を処理する | 5 | 3 | 8 | 必須 |
| US21 | 輸送料金を算出する | 5 | 3 | 8 | 中 |
| **合計** | | **10** | **6** | **16** | |

### ストーリー詳細

#### US20: 破損・紛失例外を処理する

**ストーリー**:
> 追跡管理者（または荷役作業員）として、輸送中に破損または紛失が発生した場合、例外種別「破損」または「紛失」として記録し、関係者に緊急通知を送りたい。なぜなら、重大な例外は即座に全関係者に共有し、保険手続き・補償対応・代替措置を迅速に開始できるからだ。

**受入条件**:

- [ ] 追跡番号と例外種別「破損」または「紛失」・発生状況を記録できる
- [ ] 例外記録後に貨物状態が「例外発生（EXCEPTION）」に更新される
- [ ] 例外種別「紛失」の場合、緊急フラグが設定されて管理職への escalation 通知が送信される
- [ ] 荷主に破損・紛失発生の通知が送信される
- [ ] 対応内容（補償方針等）を入力して荷主に報告を送信できる

#### US21: 輸送料金を算出する

**ストーリー**:
> 経理担当者として、配送完了した予約に対して輸送実績（経路・重量・貨物種別・荷役実績）をもとに輸送料金を算出したい。なぜなら、実際の輸送内容に基づく正確な料金を算出し、精算に進めるからだ。

**受入条件**:

- [ ] 「引取済」状態の予約に対して料金算出を開始できる
- [ ] 輸送実績（経路・距離・重量・貨物種別・荷役作業実績）が表示される
- [ ] 基本料金が自動計算される
- [ ] 算出結果を確認して確定操作ができる
- [ ] 確定後、輸送料金が「確定」状態で登録される
- [ ] 例外（遅延・破損等）が発生している場合、料金調整（減額・補償費用）の入力ができる

### タスク

#### 1. US20: 破損・紛失例外を処理する（8 SP）

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 1.0a | **[リファクタリング]** BE: `TrackingExceptionController` のレスポンスを `Map<String, Object>` から DTO（record）に変更する（IT7 レビュー M1） | 0.5h | - | [x] |
| 1.0b | **[TDD]** BE: `addException()` に前状態ガード追加（CLAIMED 等の完了状態からは例外追加不可）（IT7 レビュー M2） | 0.5h | - | [x] |
| 1.0c | **[TDD]** BE: `respond()` メソッドに responseContent の null/空文字バリデーション追加（IT7 レビュー M3） | 0.5h | - | [x] |
| 1.1 | **[TDD]** BE: IT7 で構築した `TrackingExceptionEvent` を活用し、DAMAGE・LOST 種別での例外記録ロジックを実装する | 1h | - | [x] |
| 1.2 | **[TDD]** BE: DAMAGE 時に損傷詳細フィールド（damageDescription・photoUrl）を追加・保存する | 1.5h | - | [x] |
| 1.3 | **[TDD]** BE: LOST 時に最終確認場所・最終確認日時フィールドを追加・保存する | 1h | - | [x] |
| 1.4 | BE: DB マイグレーション（tracking_exception_event テーブルに damage_description・photo_url・last_known_location・last_seen_at カラム追加） | 0.5h | - | [x] |
| 1.5 | **[TDD]** FE: 例外記録画面で DAMAGE・LOST 選択時に種別固有フィールドを動的表示する | 2h | - | [x] |
| 1.6 | FE: US20 の FE テストを追加する | 1h | - | [ ] |

**小計**: 8.5h（理想時間）

#### 2. US21: 輸送料金を算出する（8 SP）

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 2.1 | **[TDD]** BE: `billingms` マイクロサービスの雛形を構築する（Spring Boot アプリケーション・DB 接続・MyBatis 設定） | 1.5h | - | [x] |
| 2.2 | **[TDD]** BE: `Invoice` 集約ルート・`InvoiceLineItem`・`Money` 値オブジェクトを実装する | 1.5h | - | [x] |
| 2.3 | **[TDD]** BE: 料金算出ロジック（`calculateFinalAmount()`）を実装する | 1.5h | - | [x] |
| 2.4 | **[TDD]** BE: 料金算出 API（POST /api/billing/v1/invoices/calculate）と料金確定 API（POST /api/billing/v1/invoices/{invoiceId}/confirm）を実装する | 1.5h | - | [x] |
| 2.5 | BE: DB マイグレーション（invoice・invoice_line_item テーブル作成） | 0.5h | - | [x] |
| 2.6 | **[TDD]** FE: 料金算出画面（予約 ID 入力・内訳表示・確定ボタン）を実装する | 2h | - | [x] |
| 2.7 | FE: US21 の FE テストを追加する | 1h | - | [ ] |

**小計**: 9.5h（理想時間）

#### タスク合計

| カテゴリ | SP | 理想時間 | 状態 |
|---------|----|----|------|
| US20: 破損・紛失例外処理（IT7 レビュー対応含む） | 8 | 8.5h | [x] FE テスト（1.6）のみ残 |
| US21: 輸送料金算出 | 8 | 9.5h | [x] FE テスト（2.7）のみ残 |
| **合計** | **16** | **18h** | |

**1 SP あたり**: 約 1.1h
**進捗率**: 93% (14.5/16 SP、FE テスト 1.6・2.7 のみ残)

---

## スケジュール

### Week 1（Day 1-5: 2026-05-12〜2026-05-16）

```mermaid
gantt
    title イテレーション 8 - Week 1
    dateFormat  YYYY-MM-DD
    section US20 破損・紛失例外
    BE: DAMAGE・LOST ロジック・DB     :us20a, 2026-05-12, 2d
    FE: 種別固有フィールド・テスト     :us20b, after us20a, 2d
    section US21 輸送料金
    BE: billingms 雛形・ドメイン       :us21a, 2026-05-16, 1d
```

| 日 | タスク |
|----|--------|
| Day 1 | US20: BE DAMAGE・LOST 記録ロジック（1.1, 1.2） |
| Day 2 | US20: BE LOST フィールド・DB マイグレーション（1.3, 1.4） |
| Day 3 | US20: FE 動的フィールド表示（1.5） |
| Day 4 | US20: FE テスト追加（1.6） |
| Day 5 | US21: BE billingms 雛形・ドメインモデル（2.1, 2.2） |

### Week 2（Day 6-8: 2026-05-19〜2026-05-22）

```mermaid
gantt
    title イテレーション 8 - Week 2
    dateFormat  YYYY-MM-DD
    section US21 輸送料金
    BE: 料金算出ロジック・API・DB       :us21b, 2026-05-19, 2d
    FE: 料金算出画面・テスト            :us21c, 2026-05-21, 2d
```

| 日 | タスク |
|----|--------|
| Day 6 | US21: BE 料金算出ロジック・API（2.3, 2.4） |
| Day 7 | US21: BE DB マイグレーション（2.5）、FE 料金算出画面開始（2.6） |
| Day 8 | US21: FE テスト追加（2.7）、統合確認 |

---

## 設計

### ドメインモデル

#### US20: 破損・紛失例外処理

```plantuml
@startuml
title US20 破損・紛失例外ドメインモデル

package "trackingms" {
  class TrackingActivity {
    + trackingNumber: TrackingNumber
    + transportStatus: TransportStatus
    + exceptions: List<TrackingExceptionEvent>
    + addException(ex: TrackingExceptionEvent): void
  }

  class TrackingExceptionEvent {
    + id: Long
    + exceptionType: ExceptionType
    + occurredAt: LocalDateTime
    + locationUnlocode: String
    + description: String
    + resolutionNotes: String
    + newEstimatedArrival: LocalDate
    + status: ExceptionStatus
    + escalationFlag: Boolean
    + damageDescription: String     <- 新規（破損詳細）
    + photoUrl: String              <- 新規（証拠写真URL）
    + lastKnownLocation: String     <- 新規（紛失時最終確認場所）
    + lastSeenAt: LocalDateTime     <- 新規（紛失時最終確認日時）
  }

  enum ExceptionType {
    DELAY
    DAMAGE
    LOST
    CUSTOMS_HOLD
  }
}

TrackingActivity *-- TrackingExceptionEvent
@enduml
```

> **注**: IT7 で構築済みの `TrackingExceptionEvent` エンティティに DAMAGE・LOST 固有フィールドを追加拡張する。既存の DELAY 処理には影響しない。

#### US21: 輸送料金算出

> **注**: Billing Context のドメインモデル（`docs/design/domain-model.md`）では集約ルートは `Invoice` である。IT8 では Invoice 集約の `calculateFinalAmount()` を活用して料金算出を実装する。US21 スコープでは Invoice の作成・基本料金算出・確定までを対象とし、割引（US22）・精算（US23）は後続イテレーションで対応する。

```plantuml
@startuml
title US21 輸送料金ドメインモデル（Invoice 集約）

package "billingms" {
  class Invoice <<aggregate root>> {
    + invoiceId: InvoiceId
    + cargoBookingId: BillingBookingId
    + shipperId: BillingShipperId
    + baseAmount: Money
    + finalAmount: Money
    + paymentStatus: PaymentStatus
    + issuedAt: Date
    + lineItems: List<InvoiceLineItem>
    + calculateFinalAmount(): Money
  }

  class InvoiceLineItem {
    + description: String
    + amount: Money
    + seqNumber: int
  }

  class Money <<value object>> {
    + amount: BigDecimal
    + currency: CurrencyCode
  }

  enum PaymentStatus {
    PENDING
    CONFIRMED
    OVERDUE
    REFUNDED
  }
}

Invoice *-- InvoiceLineItem
Invoice *-- Money
@enduml
```

### データモデル

#### US20: tracking_exception_event テーブル拡張

```prisma
model tracking_exception_event {
  // ... 既存フィールド（IT7 で作成済み）
  damage_description    String?   // <- 新規追加（破損詳細）
  photo_url             String?   // <- 新規追加（証拠写真URL）
  last_known_location   String?   // <- 新規追加（紛失時最終確認場所）
  last_seen_at          DateTime? // <- 新規追加（紛失時最終確認日時）
}
```

#### US21: invoice / invoice_line_item テーブル（新規 — `docs/design/data-model.md` の billing_db 定義に準拠）

> `data-model.md` で定義済みの `invoice`・`invoice_line_item`・`payment` テーブルを使用する。IT8 では invoice と invoice_line_item の作成までをスコープとし、payment は US23 で対応する。

```prisma
model invoice {
  id                BigInt    @id @default(autoincrement())
  invoice_number    String    @unique       // 精算書番号（業務キー）
  booking_id        String                  // 予約 ID（論理参照）
  shipper_id        String                  // 荷主 ID（論理参照）
  base_amount_value Integer                 // 基本料金（最小通貨単位）
  base_amount_currency String @default("JPY")
  discount_rate     Decimal   @default(0)   // 割引率（IT8 では 0 固定）
  final_amount_value Integer                // 最終金額（最小通貨単位）
  final_amount_currency String @default("JPY")
  tax_rate          Decimal   @default(0.10)
  tax_amount_value  Integer                 // 税額
  payment_status    String    @default("PENDING") // PENDING / CONFIRMED
  issued_at         DateTime
  due_date          DateTime
  created_at        DateTime  @default(now())
  updated_at        DateTime  @updatedAt
}

model invoice_line_item {
  id              BigInt    @id @default(autoincrement())
  invoice_id      BigInt                    // FK → invoice.id
  description     String                    // 明細説明（基本料金・距離料金等）
  amount_value    Integer                   // 金額（最小通貨単位）
  amount_currency String    @default("JPY")
  seq_number      Integer                   // 表示順
  created_at      DateTime  @default(now())
  updated_at      DateTime  @updatedAt
}
```

### ユーザーインターフェース

#### ビュー

**US20: 例外記録画面拡張（既存 TrackingExceptionPage を再利用）**

```plantuml
@startsalt
{+
  { / <b>CargoTracker</b> | 貨物追跡 | 例外管理 | [ログアウト] }
  ----
  {+
    追跡番号     | "TRK-000001   " | [検索]
    ----
    例外種別     | () 遅延 | (X) 破損 | () 紛失
    発生場所     | "SGSIN       "
    発生日時     | "2026-05-15T14:00"
    理由         | "コンテナ落下による損傷"
    -- 破損選択時のみ表示 --
    損傷詳細     | "外装に凹み・内容物一部破損"
    証拠写真URL  | "https://..."
    ----
    ** 対応内容 **
    対応方針     | "保険請求手続き開始"
    [例外を記録する] | [キャンセル]
  }
}
@endsalt
```

```plantuml
@startsalt
{+
  { / <b>CargoTracker</b> | 貨物追跡 | 例外管理 | [ログアウト] }
  ----
  {+
    追跡番号     | "TRK-000002   " | [検索]
    ----
    例外種別     | () 遅延 | () 破損 | (X) 紛失
    発生場所     | "CNSHA       "
    発生日時     | "2026-05-16T09:00"
    理由         | "荷下ろし後に所在不明"
    -- 紛失選択時のみ表示 --
    最終確認場所 | "CNSHA       "
    最終確認日時 | "2026-05-15T18:00"
    ----
    ** 対応内容 **
    対応方針     | "捜索中・荷主に連絡済"
    [例外を記録する] | [キャンセル]
  }
}
@endsalt
```

**US21: 輸送料金算出画面（新規）**

```plantuml
@startsalt
{+
  { / <b>CargoTracker</b> | 精算管理 | [ログアウト] }
  ----
  {+
    予約 ID      | "BK-000001   " | [料金算出]
    ----
    ** 料金内訳 **
    基本料金     | "100,000 円"
    距離料金     | "250,000 円"
    割増（危険物20%） | "70,000 円"
    ----
    <b>合計金額</b> | <b>"420,000 円"</b>
    ----
    [料金を確定する] | [キャンセル]
  }
}
@endsalt
```

#### インタラクション

```plantuml
@startuml
title IT8 画面遷移図

[*] --> 例外管理 : /exceptions

state 例外管理 : /exceptions
例外管理 --> 例外記録 : 新規例外

state 例外記録 : /exceptions/new\nPOST /api/tracking/v1/{tn}/exceptions
例外記録 --> 例外記録 : DAMAGE 固有フィールド表示（動的）
例外記録 --> 例外記録 : LOST 固有フィールド表示（動的）
例外記録 --> 例外記録 : バリデーションエラー
例外記録 --> 例外管理 : 記録成功（PRG）

state 料金算出 : /billing/calculate\nPOST /api/billing/v1/invoices/calculate
料金算出 --> 料金算出 : 内訳表示
料金算出 --> 料金算出 : バリデーションエラー
料金算出 --> 料金確定 : 確定成功

state 料金確定 : POST /api/billing/v1/invoices/{invoiceId}/confirm
@enduml
```

### API 設計

#### US20 拡張 API（既存 API 再利用）

| メソッド | エンドポイント | 説明 |
|---------|---------------|------|
| POST | `/api/tracking/v1/{trackingNumber}/exceptions` | 例外記録（IT7 既存）。DAMAGE・LOST 種別と固有フィールドを追加 |
| PUT | `/api/tracking/v1/{trackingNumber}/exceptions/{id}/response` | 対応内容更新（IT7 既存） |

#### US21 新規 API

| メソッド | エンドポイント | 説明 |
|---------|---------------|------|
| POST | `/api/billing/v1/invoices/calculate` | 予約 ID から輸送料金を算出し Invoice を作成する |
| POST | `/api/billing/v1/invoices/{invoiceId}/confirm` | 算出した料金を確定する |
| GET | `/api/billing/v1/invoices?bookingId={bookingId}` | 予約 ID の精算書情報を取得する |

### ADR

| ADR | タイトル | ステータス |
|-----|---------|--------------|
| ADR-006 | billingms を独立マイクロサービスとして構築する（Invoice 集約を中心とした精算ドメイン） | 提案 |

---

## リスクと対策

| リスク | 影響度 | 対策 |
|--------|--------|------|
| US21: billingms 新規構築で Spring Boot + MyBatis + DB セットアップの工数が予想を超える | 高 | trackingms の雛形をコピーして構築時間を短縮。最悪 bookingms に料金計算ロジックを仮配置し、後続 IT で分離する |
| US20: IT7 の TrackingExceptionEvent 拡張が既存 DELAY 処理に影響する | 中 | 新フィールドはすべて nullable として追加。既存テストが通過することを確認してから実装 |
| US21: bookingms から予約情報（経路距離・貨物種別）を取得する連携が必要 | 中 | 初期実装では REST 同期呼び出し（billingms → bookingms API）で対応。Phase 2 完了後にイベント駆動への移行を検討 |
| ベロシティ 16 SP は IT7（24 SP）より少ないが billingms 新規構築のオーバーヘッドがある | 低 | IT7 実績（23 SP 平均）から見て 16 SP は達成可能。billingms 雛形の工数を 1.5h と控えめに見積もり |

---

## 完了条件

### Definition of Done

- [ ] US20: 破損・紛失例外が記録され、EXCEPTION 状態に遷移する
- [ ] US20: 破損時の損傷詳細・紛失時の最終確認情報が保存される
- [ ] US21: 予約 ID から料金が算出され、内訳が表示される
- [ ] US21: 料金確定後に invoice テーブルに保存される
- [ ] 全ユニットテスト・BE テストカバレッジ 80%+
- [ ] ESLint / SonarQube Quality Gate PASS
- [ ] ドキュメント更新完了

### デモ項目

1. 「破損」を選択すると損傷詳細フィールドが表示され、例外記録後に EXCEPTION へ遷移することを確認する（US20）
2. 「紛失」を選択すると最終確認場所・日時フィールドが表示され、例外記録後に EXCEPTION へ遷移することを確認する（US20）
3. 予約 ID を入力して料金算出すると、基本料金・距離料金・割増料金の内訳が表示され、確定できることを確認する（US21）

---

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-05-11 | 初版作成（IT8 計画） | - |

---

## 関連ドキュメント

- [イテレーション 7 完了報告書](./iteration_report-7.md)
- [リリース計画](./release_plan.md)
- [ユーザーストーリー](../requirements/user_story.md)

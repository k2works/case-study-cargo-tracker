# イテレーション 11 計画

| 項目 | 内容 |
| :--- | :--- |
| イテレーション | IT11 |
| 期間 | 2026-10-05 〜 2026-10-18（2 週間） |
| 対象ストーリー | US21（輸送料金を算出する）・US22（法人割引を適用する） |
| 計画 SP | 9（US21 = 7・US22 = 2） |
| 局面 | **終盤（4 本目）／アウトサイドイン**（[開発戦略](development_strategy.md)） |
| 前提 | [IT10 完了報告書](iteration_report-10.md)・[IT10 ふりかえり](retrospective-10.md) |
| リリース | [Release 2.0](release_plan.md)（IT11〜IT12）——**billingms を業務として立ち上げる最初の IT** |

## 局面とアプローチ

**終盤（4 本目）／アウトサイドイン**（[開発戦略](development_strategy.md)）。

業務の入口（経理担当者の画面）から入り、API・ACL を経てドメインへ降りる。理由は 2 つある。

1. **billingms は 10 イテレーション、ヘルスチェックしか返していない。** 内側から積むと、
   何を計算するかを決めないまま `Money` の演算だけができあがる
2. **料金の規則が業務として決まっていない**（リスク 1）。**画面に「なぜその金額か」を
   出すところから決める**のが最も早く誤りに気づける

## ゴール

**運び終えた貨物に、いくら請求するかが決まるようになる。**

IT1 のウォーキングスケルトンで billingms を起こしてから 10 イテレーション、このサービスは
**ヘルスチェックしか返していない**。予約は取れ、経路は組め、荷役は記録され、追跡でき、
通関を通り、誤配も直せるようになったが、**運賃は 1 円も計算していない**。

IT10 で `Misroute` を「料金調整の根拠」として残したが、**その根拠を読む相手がまだいない**。
IT9 で「キャンセル料は算定していません」と画面に書いたのも、算定する場所が無かったからである。
**本 IT でその相手を作る。**

> **経理担当者（`ROLE_ACCOUNTANT`）が初めて仕事をする IT である。** ロールは IT1 から
> 存在するが、開いている画面が 1 つも無い。ログインしても「準備中」しか見えない状態が
> 10 イテレーション続いていた。

## 前イテレーションからの反映

### ふりかえりの Try（[retrospective-10.md](retrospective-10.md)）

| # | Try | 本 IT での扱い |
| :--- | :--- | :--- |
| 1 | デモ項目の表を「検査名の対応表」として読む | **DoD に入れた。** チェックを入れる前に 1 件ずつ検査を書き出す。IT10 はクローズ直前に 2 件の空白が見つかった |
| 2 | **ADR の決定は、決まった経路を端から端まで 1 度通す** | **成功基準 6 に据えた。** 本 IT は料金計算の規則を ADR で決める——**規則が効く経路（画面 → 集約 → ACL → 相手サービス）を 1 度通す検査**を同じ変更の中で書く。IT10 の「決定を書いて集約に検査も置いたのに、その決定が効く経路を一度も通していなかった」を繰り返さない |
| 3 | 述語を足したら、それを使う場所の一覧を作って読み合わせる。名前で区別する | **進め方に入れた。** 本 IT では `PaymentStatus`・`DiscountPolicyType` を足す。**列挙に値を足したら、その値を使う場所すべてを回る検査**を置く（Try 3 の一般形） |
| 4 | 画面から踏むテストは、遷移先まで含めて 1 本にする | **DoD に入れた。** 精算一覧 → 料金算出 → 確定の 3 画面をまたぐ |
| 5 | ロール別到達性は、ルートガードを通る検査で確かめる | **DoD に入れた。** `ROLE_ACCOUNTANT` に初めて画面を開く IT であり、**開いたつもりで 403** が最も起きやすい |
| 6 | フロントエンドの品質ゲートも CI と同じコマンドで回す | **返済枠 0.1 で `npm run verify` にまとめる。** IT10 は `oxlint` を回さず CI が赤になった |
| 7 | 実環境へ反映したら、Pod の起動時刻とコンテナ内 jar の日時で確かめる | **進め方に入れた。** あわせて `dev:k8s:rollout:image` の `--tag` が効かない件を返済枠 0.2 で直す |

### 引き継ぎ（返済枠・SP 対象外）

IT10 のレビュー低指摘 4 件と、ふりかえりの Try 6・7 を返済枠として持つ。
**IT 序盤（Day 1-3）に独立したコミットで済ませる**——5 IT 連続で全返済できている形を保つ。

| # | 内容 | 見積 | 出典 |
| :--- | :--- | :--- | :--- |
| 0.1 | **フロントエンドの品質ゲートを 1 コマンドにまとめる**（`oxlint` + `tsc -b` + `vitest`）。**壊して赤を確認する** | 3h | IT10 Problem 6 |
| 0.2 | **`dev:k8s:rollout:image` の `--tag` / `DEV_K8S_IMAGE_TAG` が効かない**。効くように直し、**Pod の起動時刻で反映を確かめる手順**を運用手順書に書く | 5h | IT10 Problem 7 |
| 0.3 | **誤配バナーの港を名前で出す**（他画面は港名を優先表示）。`lastHandlingLocationUnLocode` を使う他の画面と揃える | 4h | IT10 レビュー低 15 |
| 0.4 | **`RoutingStatus#visibleToRoutingPlanner` を `switch` 式にする**。否定リストのままだと値を足したとき自動的に開く方向に倒れる。[ADR-026](../adr/026-misroute-detection-and-rerouting.md) 決定 2 が「述語がすべての値を明示的に扱う」と決めているのに、検査になっていない | 3h | IT10 レビュー低 16 |
| 0.5 | **ADR-026 コンプライアンス表の決定 7 の行を、実際の壊し方に直す**（いま決定 5 の壊し方が書いてある） | 1h | IT10 レビュー低 17 |
| 0.6 | **trackingms の配線から完全修飾名を外す**（IT9 の `DetectCustomsHoldUseCase` から伝播） | 2h | IT10 レビュー低 18 |
| 0.7 | **`BookingStatus` の全値がラベルを持つことを検査する**。IT10 はキャプチャを撮って初めて 3 つの欠落に気づいた | 2h | IT10 レビュー スコープ外 |
| 0.8 | **購読側で 2 つ目のユースケースを呼ぶ順序を、壊すと赤くなる形で固定する**。IT9・IT10 で同じ構造が 2 つでき、コメントで順序を説明しているだけ | 4h | IT10 レビュー 懸念 |
| 0.9 | **IT10 時点のコメントが残っていないか確かめる**（実装した IT で消す） | 2h | 各 IT の慣行 |
| **合計** | | **26h** | |

### IT10 から「着手前に決めること」として引き継いだもの

| # | 決めること | 本 IT での扱い |
| :--- | :--- | :--- |
| A | **2 回目の誤配の扱い。** `Misroute` は 1 件しか持たず、組み直した先でまた外れたときに 1 回目が残る | **本 IT で決める（ADR-027）。** 料金調整は回数で変わる——**回数を数える必要があるなら履歴が要り、初回だけでよいなら現状のままでよい**。決めてから US21 の調整計算を書く |
| B | **誤配の記録を経理がどう読むか。** 予約詳細にしか出ず、経理担当者は開けない | **本 IT で決める。** ACL（`BillingSnapshot`）に載せる。**「残っている」と「読める」は別**（IT10 レビュー懸念） |
| C | **料金計算の規則。** US21 は「基本料金が自動計算される」としか言っていない | **本 IT で決める（ADR-027）。** 重量・貨物種別・区間数から算出する。**IT4 の経路候補が返す「概算費用」との関係**も決める——[domain-model.md](../design/domain-model.md) の 862 行が「費用は概算であり、請求される金額ではない（US21 で実料金に差し替える）」と書いている |
| D | **料金算出の起点。** `CargoDeliveredEvent` を待つのか、経理担当者が開始するのか | **経理担当者が開始する。** 受入基準 21-1 が「『引取済』状態の予約に対して料金算出を開始できる」であり、**イベントは要らない**。`CargoDeliveredEvent` は US23（IT12）の精算通知で必要になる——[domain-model.md](../design/domain-model.md) の 1472 行もそう書いている。**リリース計画の「US21 は trackingms の `CargoDeliveredEvent` 発行実装を含む」（レビュー H4）は IT12 へ送る**（注 1） |
| E | **キャンセル料の算定。** IT9 で「算定していません」と画面に書いた | **本 IT の範囲に含める**（[release_plan.md](release_plan.md) の Release 1.1 の注記どおり）。`CancellationFee` は設計済み（[domain-model.md](../design/domain-model.md)） |

## 対象ストーリーと受入基準

### US21: 輸送料金を算出する（7 SP）

| # | 受入基準 | 本 IT での扱い |
| :--- | :--- | :--- |
| 21-1 | 「引取済」状態の予約に対して料金算出を開始できる | **満たす。** 経理担当者が一覧から選ぶ |
| 21-2 | 輸送実績（経路・距離・重量・貨物種別・荷役作業実績）が表示される | **満たす**（距離を除く）。**距離は持っていない**——港のマスタに緯度経度が無く、航海も距離を持たない。**区間数で代替**し、その旨を画面に書く（注 2） |
| 21-3 | 基本料金が自動計算される | **満たす。** 規則は ADR-027 で決める |
| 21-4 | 算出結果を確認して確定操作ができる | **満たす** |
| 21-5 | 確定後、輸送料金が「確定」状態で登録される | **満たす** |
| 21-6 | 例外（遅延・破損等）が発生している場合、料金調整（減額・補償費用）の入力ができる | **満たす。** 誤配（IT10 の `Misroute`）と例外（IT8）の両方を根拠として表示する |

### US22: 法人割引を適用する（2 SP）

| # | 受入基準 | 本 IT での扱い |
| :--- | :--- | :--- |
| 22-1 | 荷主種別が「法人」の場合、契約割引率が自動的に取得・表示される | **満たす。** `shipper.discount_rate` は IT2（US03）で登録済み |
| 22-2 | 割引率（0〜30%）が基本料金に適用され、割引後の金額が表示される | **満たす** |
| 22-3 | 個人荷主の場合は割引が適用されない | **満たす** |
| 22-4 | 割引計算の根拠（割引率・基本料金・割引後料金）が精算書に記載される | **満たす**（明細として持つ） |

## 設計

### ドメインモデル図（本 IT のスコープ）

```plantuml
@startuml
title IT11 スコープ - Billing Context

package "billingms" {
  class Invoice <<aggregate root>> {
    -invoiceId: InvoiceId
    -cargoBookingId: BillingBookingId
    -shipperId: BillingShipperId
    -baseAmount: Money
    -discountRate: DiscountRate
    -finalAmount: Money
    -taxRate: TaxRate
    -taxAmount: Money
    -cancellationFee: CancellationFee
    -lineItems: List<InvoiceLineItem>
    -paymentStatus: PaymentStatus
    +calculateFinalAmount(): Money
    +applyDiscount(policy: DiscountPolicy): void
    +applyCancellationFee(fee: CancellationFee): void
  }
  class Money <<value object>>
  class DiscountRate <<value object>>
  class TaxRate <<value object>>
  class CancellationFee <<value object>>
  class DiscountPolicy <<value object>> {
    -policyType: DiscountPolicyType
    +calculateRate(shipperType: String, amount: Money): DiscountRate
  }
  class InvoiceLineItem <<entity>>
  enum DiscountPolicyType {
    CORPORATE_STANDARD
    NONE
  }
  enum PaymentStatus {
    PENDING
    CONFIRMED
    OVERDUE
    REFUNDED
  }
  class BillingSnapshot <<ACL>> {
    -bookingId: String
    -shipperType: String
    -discountRate: BigDecimal
    -weightKg: BigDecimal
    -cargoType: String
    -legCount: int
    -misroute: MisrouteSnapshot
    +chargeable(): boolean
  }
}

Invoice *-- Money
Invoice *-- DiscountRate
Invoice *-- TaxRate
Invoice *-o CancellationFee
Invoice *-- InvoiceLineItem
Invoice *-- PaymentStatus
Invoice ..> DiscountPolicy : applyDiscount()
DiscountPolicy *-- DiscountPolicyType
Invoice ..> BillingSnapshot : 算出の入力

@enduml
```

> **`PaymentStatus` に `DRAFT` は足さない**（整合性検証の結果 D-2）。
> [domain-model.md](../design/domain-model.md) の `PaymentStatus` は
> `PENDING` / `CONFIRMED` / `OVERDUE` / `REFUNDED` の 4 値で、**支払いの状態**を表す。
> ここに「金額を確定したか」を混ぜると、`CONFIRMED` が「支払い確認済み」と
> 「金額確定済み」の 2 つの意味を持ち、US23（IT12）で支払いを扱う段で必ず揉める。
>
> **本 IT では、算出中の下書きを `Invoice` にしない。** 経理担当者が確定操作をした時点で
> `Invoice` を `PENDING` で発行する（正典の `GenerateInvoiceCommand` がそう書いている）。
> 受入基準 21-5 の「輸送料金が『確定』状態で登録される」は、**`Invoice` が発行済みで
> あること自体**が満たす。`OVERDUE` / `REFUNDED` へ遷移させる相手は US23 まで現れない
> ため、本 IT では**遷移させず、扱う場所すべてを回る検査だけ置く**（Try 3）。
>
> **この判断は ADR-027 の決定として書く**（下書きを永続化するか否かを含む）。
>
> **`DiscountPolicyType` は `CORPORATE_STANDARD` / `NONE` の 2 値だけ実装する。**
> `VOLUME_DISCOUNT` / `SEASONAL` は正典に定義があるが、US22 の受入基準に無く、
> **決める相手（契約条件）がいない**。列挙は正典どおり 4 値を宣言し、**本 IT で
> 算定に使うのは 2 値**という形にはしない——**扱わない値を宣言すると、
> `switch` が網羅していても業務としては空である**（IT10 Problem 3 の形）。
> 2 値だけ宣言し、残りは US23 以降で足す旨を注に書く（注 10）。

### 状態遷移図

```plantuml
@startuml
title 精算書の状態（IT11 のスコープ）

state "算出中（永続化しない）" as calc
[*] --> calc : 引取済の予約を選ぶ（21-1）
calc --> calc : 割引・調整を入れ直す（21-6・22-2）
calc --> PENDING : 確定する（21-4・21-5）\n**ここで Invoice が生まれる**
PENDING --> CONFIRMED : 入金を確認する（**US23・IT12**）
PENDING --> OVERDUE : 支払期限を超える（**US23・IT12**）
CONFIRMED --> REFUNDED : 返金する（**US23・IT12**）

note right of PENDING
  **発行したら金額は動かない。**
  請求書は荷主へ出す約束であり、
  出したあとに黙って変わると
  請求の根拠が消える。
  訂正は US23（IT12）で
  「取り消して出し直す」形にする
end note

note bottom
  **本 IT で起こす遷移は「算出中 → PENDING」の 1 本だけ。**
  残る 3 本は US23（IT12）。列挙は 4 値すべて宣言し、
  **扱う場所すべてを回る検査**を置く（Try 3）が、
  遷移そのものは本 IT では起こさない
end note

@enduml
```

### ER 図（本 IT のスコープ）

```plantuml
@startuml
title IT11 スコープ - billing_db（既存定義に本 IT の追加分を重ねたもの）

entity "invoice（精算書）" as invoice {
  * id : BIGSERIAL <<PK>>
  --
  * invoice_number : VARCHAR(30) <<UK>>
  * booking_id : VARCHAR(20) <<UK>>
  * total_amount_value : NUMERIC(15,2) <<変更（注 3）>>
  * total_amount_currency : VARCHAR(3)
  * tax_rate : NUMERIC(5,4) <<DEFAULT 0.1000>>
  * tax_amount : NUMERIC(15,2)
  * payment_status : VARCHAR(30)
  issued_at : TIMESTAMP WITH TIME ZONE
  due_date : DATE
  discount_amount_value : NUMERIC(15,2) <<変更（注 3）>>
  discount_amount_currency : VARCHAR(3)
  cancellation_fee_value : NUMERIC(15,2) <<変更（注 3）>>
  cancellation_fee_currency : VARCHAR(3)
  cancellation_fee_rate : NUMERIC(5,4)
  booking_status_at_cancel : VARCHAR(30)
  --
  ' 本 IT で追加する列（注 11）
  base_amount_value : NUMERIC(15,2) <<追加>>
  base_amount_currency : VARCHAR(3) <<追加>>
  discount_rate : NUMERIC(5,4) <<追加>>
  shipper_id : VARCHAR(20) <<追加>>
}

entity "invoice_line_item（精算明細）" as line {
  * id : BIGSERIAL <<PK>>
  --
  * invoice_id : BIGINT <<FK>>
  * description : VARCHAR(200)
  * amount_value : NUMERIC(15,2) <<変更（注 3）>>
  * amount_currency : VARCHAR(3)
  * seq_number : INTEGER
}

invoice ||--o{ line

@enduml
```

> **金額は `NUMERIC(15,2)` にする。** [data-model.md](../design/data-model.md) の定義は
> `INTEGER`（最小通貨単位）と書いているが、**割引後の金額に端数が出る**（基本料金 × 0.85 など）。
> 端数の丸め方は ADR-027 で決め、**丸めた結果を保存する**（注 3）。
> `tax_*` は `NOT NULL` であり**書かずには行を作れない**——本 IT では消費税を業務として
> 扱わないが、**既定値（10%）で埋めて保存し、画面にも内訳として出す**
> （[ui_design.md](../design/ui_design.md) の請求書詳細が消費税行を持っている。注 12）。
>
> **`discount_rate` を列として足す**（注 11）。正典は `discount_amount_*`（割引額）だけを
> 持つが、受入基準 22-4 は**割引率も精算書に載せる**ことを求めている。額だけでは率を
> 復元できない（基本料金と割引額から割り戻すと丸めの分だけずれる）。
> **`payment` テーブルは本 IT では触らない**（US23・IT12）。

### 画面遷移図

```plantuml
@startuml
title IT11 スコープ - 経理担当者の画面

state "ダッシュボード" as dash
state "精算管理 /billing" as list
state "料金算出 /billing/new/:bookingId" as calc
state "請求書詳細 /billing/:invoiceId" as detail

[*] --> dash : ログイン（ROLE_ACCOUNTANT）
dash --> list : [精算管理]
dash --> list : 料金未算出が N 件あります
list --> calc : 引取済の予約を選ぶ
calc --> calc : 入力に誤りがある（調整額・割引率）
dash --> calc : （直接は開けない。一覧を経由する）
calc --> detail : 確定する（POST → 303 → GET）
list --> detail : 精算書を開く
detail --> [*]

@enduml
```

> **件数から対象一覧へ辿れる**（[横断規約](release_plan.md)）。ダッシュボードに
> 「料金を算出していない引取済の予約が N 件あります」を出す——**経理担当者は他に
> 気づく手段を持たない**（メールの仕組みは無い）。

## タスク

### Phase 0: 返済枠（Day 1-3・26h）

上表の 0.1〜0.9。**独立したコミットで済ませる。**

### Phase 1: 受け入れテストと ADR（Day 3-4）

| # | タスク | 見積 | 備考 |
| :--- | :--- | :--- | :--- |
| 1.1 | **ADR-027 を書く**——料金計算の規則・2 回目の誤配の扱い・端数の丸め・確定後の不変性・`BillingSnapshot` の範囲 | 6h | **決定ごとに検査を用意し、決定が効く経路を 1 度通す**（Try 2） |
| 1.2 | デモ項目を受け入れテストに翻訳（Red） | 4h | 画面から踏む。**遷移先まで 1 本にする**（Try 4） |

### Phase 2: 画面と導線（Day 4-6）

| # | タスク | 見積 | 備考 |
| :--- | :--- | :--- | :--- |
| 2.1 | 精算管理の一覧（`/billing`）——精算書の一覧と、**料金未算出の引取済予約** | 6h | 2 つの待ち行列を 1 画面に置く |
| 2.2 | 料金算出の画面（`/billing/new/:bookingId`）——実績の表示・基本料金・割引・調整の入力 | 8h | 21-2・21-3・21-6・22-1〜22-3 |
| 2.3 | 請求書詳細（`/billing/:invoiceId`）——確定した内容と明細・金額内訳 | 4h | 22-4。**正典の名称は「請求書詳細」**。金額内訳は [ui_design.md](../design/ui_design.md) の既存定義（基本運賃・法人割引・キャンセル料・例外調整・消費税・合計）に合わせる |
| 2.4 | ダッシュボードに件数と導線 | 3h | 横断規約。**ロール別到達性をルートガードを通る検査で**（Try 5） |

### Phase 3: API と ACL（Day 6-8）

| # | タスク | 見積 | 備考 |
| :--- | :--- | :--- | :--- |
| 3.1 | `BillingSnapshot` の提供口を bookingms に置く | 5h | 既存の `CargoSnapshot`（handlingms 向け）と同じ形。**誤配の記録を載せる**（決めること B） |
| 3.2 | billingms 側の ACL（`BillingSnapshotFinder`）と契約テスト | 5h | `CargoSnapshotContract` と同じ形で共有 |
| 3.3 | 料金算出・確定の API | 6h | **応答の操作一覧に載せる**（IT9・IT10 で 2 度踏んだ形） |
| 3.4 | 例外の実績を trackingms から引く（21-6 の根拠） | 4h | 誤配は bookingms、例外は trackingms |

### Phase 4: ドメイン（Day 8-10）

| # | タスク | 見積 | 備考 |
| :--- | :--- | :--- | :--- |
| 4.1 | `Money`・`DiscountRate`・`InvoiceId` | 5h | **端数の丸めは 1 か所に置く** |
| 4.2 | `Invoice` 集約——算出・割引・調整・確定 | 8h | 確定後は動かない |
| 4.3 | 採番（`InvoiceId` ← `invoice_number` 列）と永続化 | 5h | **DB のシーケンス**（[ADR-011](../adr/011-booking-id-numbering.md) と同じ形） |
| 4.4 | キャンセル料の算定（`CancellationFee`） | 5h | IT9 の「算定していません」を消す |

### Phase 5: 実環境（Day 11-12）

| # | タスク | 見積 | 備考 |
| :--- | :--- | :--- | :--- |
| 5.1 | kind で引取 → 料金算出 → 確定を 1 往復させる | 5h | **反映は Pod の起動時刻で確かめる**（Try 7） |
| 5.2 | 誤配した貨物の料金調整を実環境で通す | 3h | IT10 の `Misroute` が初めて読まれる |

### Phase 6: マニュアルと計画への差し戻し（Day 12-13）

| # | タスク | 見積 | 備考 |
| :--- | :--- | :--- | :--- |
| 6.1 | **ユーザーマニュアル 12 章（精算管理）を新設**・キャプチャ生成 | 6h | 経理担当者が初めて使う画面 |
| 6.2 | 09 章・11 章の「キャンセル料は算定していません」を直す | 2h | 算定するようになる |
| 6.3 | 設計文書への反映（注 1〜16） | 5h | 整合性検証で 8 件増えた（注 10〜16 と注 3 の拡張） |

### 見積もり合計

| 区分 | 見積 |
| :--- | :--- |
| Phase 0（返済枠・SP 対象外） | 26h |
| Phase 1 受け入れテストと ADR | 10h |
| Phase 2 画面と導線 | 21h |
| Phase 3 API と ACL | 20h |
| Phase 4 ドメイン | 23h |
| Phase 5 実環境 | 8h |
| Phase 6 マニュアルと設計反映 | 13h |
| **合計（SP 対象 = Phase 1-6）** | **95h** |
| **総計（返済枠を含む）** | **121h** |

> 9 SP に対して 95h（1 SP ≒ 10.6h）。IT10（7 SP / 87h ≒ 12.4h）より密度が高い。
> **注 10〜16 の設計反映が増えた分を Phase 6.3 に 2h 足している。**

## スケジュール

| Day | 内容 |
| :--- | :--- |
| 1-3 | Phase 0（返済枠 9 件） |
| 3-4 | Phase 1（ADR-027・受け入れテスト） |
| 4-6 | Phase 2（画面と導線） |
| 6-8 | Phase 3（API と ACL） |
| 8-10 | Phase 4（ドメイン） |
| 11-12 | Phase 5（実環境） |
| 12-13 | Phase 6（マニュアル・設計反映） |
| 14 | クローズ（`closing-iteration`） |

## 成功基準

1. **経理担当者が、引取済の予約から料金を算出して確定できる**（21-1〜21-5）
2. **法人には割引が、個人には割引が適用されない**（22-1〜22-3）
3. **誤配・例外を根拠として調整を入れられる**（21-6）——IT10 の `Misroute` が初めて読まれる
4. **キャンセル料が算定される**——IT9 の「算定していません」が消える
5. **ダッシュボードの件数から、料金未算出の予約へ辿れる**（横断規約）
6. **ADR-027 の決定ごとに検査があり、その決定が効く経路を 1 度通した**（Try 2）
7. **列挙に足した値を使う場所すべてを回る検査がある**（Try 3・IT10 で 3 回踏んだ形）
8. **`ROLE_ACCOUNTANT` の到達性を、ルートガードを通る検査で確かめた**（Try 5）
9. **デモ項目 1 件ごとに、それを守る検査を書き出した**（Try 1）

## リスク

| # | リスク | 影響 | 対策 |
| :--- | :--- | :--- | :--- |
| 1 | **料金計算の規則が業務として決まっていない** | 実装したあとで「その計算式ではない」となる | ADR-027 で決め、**根拠（重量・種別・区間数）を画面に出す**。金額そのものより「なぜその金額か」が読めることを優先する |
| 2 | **距離を持っていない**（21-2） | 受入基準を字面どおりには満たせない | 区間数で代替し、画面に明記する。**満たせないことを完了報告書に記録する**（注 2） |
| 3 | **billingms が初めて他サービスを読む** | ACL の形が定まらず、BC 独立性を崩す | `CargoSnapshot`（handlingms 向け）と**同じ形**にする。契約テストを共有カーネルに置く |
| 4 | **経理の画面が 1 つも無い状態から 3 画面を作る** | ロール別到達性の欠落（IT7・IT9 で踏んだ形） | Try 5 をそのまま適用。**ルートガードを通る検査**で確かめる |
| 5 | **金額の端数** | 丸め方が 2 か所に分かれ、画面と保存値が食い違う | 丸めを 1 か所に置き、**画面はサーバが返した値を出すだけ**にする |
| 6 | **確定後の不変性** | 請求の根拠が黙って変わる | 集約で守り、**壊して赤を確認する** |

## 設計への反映が必要な箇所（注）

> **すべて反映済み**（Phase 6.3）。下表は何を直したかの記録である。

| # | 箇所 | 内容 |
| :--- | :--- | :--- |
| 1 | [release_plan.md](release_plan.md) の Release 2.0 | 「US21 は trackingms の `CargoDeliveredEvent` 発行実装を含む」（レビュー H4）を **US23（IT12）へ送る**。US21 の起点は経理担当者の操作であり、イベントは要らない |
| 2 | [user_story.md](../requirements/user_story.md) の US21-2 | **距離は持っていない**（港マスタに緯度経度が無い）。区間数で代替する旨を追記する |
| 3 | [data-model.md](../design/data-model.md) の `invoice` | 金額列を `INTEGER` から `NUMERIC(15,2)` へ。**割引後に端数が出る** |
| 4 | [domain-model.md](../design/domain-model.md) の Billing Context | `PaymentStatus` を IT11 では `DRAFT` / `CONFIRMED` の 2 つだけ置くことを明記（残りは US23） |
| 5 | [ui_design.md](../design/ui_design.md) | 料金算出（`/billing/new/:bookingId`）を画面一覧とロール別到達性の表に追加 |
| 6 | [domain-model.md](../design/domain-model.md) の Billing Context | **`BillingSnapshot`（ACL）を要素表に追加**。handlingms 向けの `CargoSnapshot` と同じ形で、billingms が bookingms から料金算出の入力を引く。**誤配の記録を載せる**（IT10 レビューの懸念「残っていると読めるは別」への対応） |
| 7 | [domain-model.md](../design/domain-model.md) の Billing Context | **`InvoiceId` は採番された請求番号を持つ**（`invoice_number` 列に対応。予約の `BookingId` と同じ形）。DB の `id` ではない旨を明記する |
| 8 | [architecture_backend.md](../design/architecture_backend.md) の `CargoCancelledEvent` の行 | 「キャンセル料の算定は **US23・IT11**」と書いてあるが、**US23 は IT12** である。[release_plan.md](release_plan.md) の Release 1.1 注記が「キャンセル料の算定（billingms 側）は Release 2.0 の **US21** に含める」と書いており、そちらが正。**US21・IT11** に直す |
| 9 | [architecture_backend.md](../design/architecture_backend.md) の `CargoCancelledEvent` の行 | **billingms へは本 IT でも発行しない**。料金算出の起点は経理担当者の操作であり（決めること D）、キャンセルされた予約も一覧から選ぶ。**読む側の無い配線を先に敷かない**（[ADR-025](../adr/025-customs-declaration-and-cancellation-approval.md) 決定 3）という判断はそのまま生きる——その旨に書き換える |
| 10 | [domain-model.md](../design/domain-model.md) の `DiscountPolicyType` | 本 IT で実装するのは `CORPORATE_STANDARD` / `NONE` の 2 値。`VOLUME_DISCOUNT` / `SEASONAL` は**決める相手（契約条件）がいない**ため宣言しない。US23 以降で足す旨を明記する |
| 11 | [data-model.md](../design/data-model.md) の `invoice` | **`base_amount_*`・`discount_rate`・`shipper_id` の 3 種を追加する。** 正典は割引を額（`discount_amount_*`）でしか持たないが、受入基準 22-4 は**率も精算書に載せる**ことを求めており、額から割り戻すと丸めの分だけずれる。`shipper_id` は正典の `Invoice` が `BillingShipperId` を持つのに列が無い |
| 12 | [ui_design.md](../design/ui_design.md) の請求書詳細 | 金額内訳の**消費税行は本 IT でも出す**。`tax_rate`・`tax_amount` は `NOT NULL` であり書かずには行を作れない。**業務として税率を扱うのは US23** であり、本 IT は既定値（10%）で埋める旨を明記する |
| 13 | [domain-model.md](../design/domain-model.md) の Billing Context ビジネスルール 1 | 「Invoice は `CargoDeliveredEvent` 受信後にのみ発行できる」を、**「引取済（`CLAIMED`）の予約に対して経理担当者が発行できる」**に直す。起点は経理担当者の操作であり（決めること D）、イベントは要らない。**イベント駆動と書いたまま実装すると、読む側の無い配線を敷くことになる** |
| 14 | [domain-model.md](../design/domain-model.md) の料金計算ロジック | 「基本料金 = **距離係数** × 重量 × 貨物種別係数」の**距離係数を区間数係数に替える**。港のマスタに緯度経度が無く、航海も距離を持たない（注 2 と対）。**user_story だけ直して domain-model を残すと、式が 2 つ残る** |
| 15 | [domain-model.md](../design/domain-model.md) の `PaymentStatus` | 4 値のまま変えない。**本 IT で起こす遷移は「発行（`PENDING`）」の 1 本だけ**で、残る 3 本は US23 である旨を明記する（列挙に値を足さない判断そのものを残す） |
| 16-a | [data-model.md](../design/data-model.md) の `booking_status_at_cancel` | bookingms 側は IT9 で **`booking_status_at_request`**（申請時の予約状態）として持っている。billing 側の列名は `booking_status_at_cancel`。**同じ値の名前が 2 つある**——`BillingSnapshot` で運ぶ際にどちらの意味かを決め、ACL の変換で明示する |
| 16 | [ui_design.md](../design/ui_design.md) の画面一覧 | 精算管理（`/billing`）の対象ストーリー欄が `US21-US23` になっている。本 IT で満たすのは US21・US22 であり、**US23 の支払い確認は IT12** である旨を分けて書く |

## デモ項目

**各項目に、それを守る検査を書き出した**（Try 1）。IT10 はクローズ直前に 2 件の空白が
見つかった——実装はどちらも入っていたので実演では緑になり、そのままチェックを入れていたら
「見せられたから通った」で終わっていた。

| # | 項目 | アクター | 受入基準 | それを守る検査 |
| :--- | :--- | :--- | :--- | :--- |
| 1 | ダッシュボードの件数から、料金未算出の予約へ辿れることを示す | 経理担当者 | 横断規約 | `billing.spec.ts#デモ 1` |
| 2 | 引取済の予約を選ぶと、輸送実績が出ることを示す | 経理担当者 | 21-2 | `billing.spec.ts#デモ 2`（区間数で代替する旨まで見る） |
| 3 | 基本料金が自動で計算され、**その根拠**が出ることを示す | 経理担当者 | 21-3 | `billing.spec.ts#デモ 3`（4 つの係数すべて）・`MoneyTest`・`ChargeCalculationTest` |
| 4 | 法人荷主では割引率が自動で入り、割引後の金額が出ることを示す | 経理担当者 | 22-1・22-2 | `billing.spec.ts#デモ 4`・`DiscountPolicyTest` |
| 5 | 個人荷主では割引が適用されないことを示す | 経理担当者 | 22-3 | `billing.spec.ts#デモ 5`（割引率の欄が**無い**ことまで見る）・`DiscountPolicyTest` |
| 6 | 誤配した貨物では、その記録が根拠として出ることを示す | 経理担当者 | 21-6 | `billing.spec.ts#デモ 6`・`BillingSnapshotTest`（ACL が誤配を運ぶ） |
| 7 | 調整（減額・補償）を入れると、合計が変わることを示す | 経理担当者 | 21-6 | `billing.spec.ts#デモ 7`・`InvoiceTest`（明細を積むと合計が変わる） |
| 8 | 確定すると精算書が「確定」になり、**金額が動かなくなる**ことを示す | 経理担当者 | 21-4・21-5 | `billing.spec.ts#デモ 8`（発行後に金額を動かす操作が**残っていない**ことまで見る）・`InvoiceTest`（発行後の変更を断る） |
| 9 | 請求書詳細に割引の根拠（割引率・基本料金・割引後料金）が記載されていることを示す | 経理担当者 | 22-4 | `billing.spec.ts#デモ 9`（割引**率**が出ることまで見る——額だけでは率を復元できない） |
| 10 | キャンセルされた予約にキャンセル料が算定されることを示す | 経理担当者 | US30-9（IT9 からの持ち越し） | `billing.spec.ts#デモ 10`（料率の根拠まで見る）・`CancellationFeeTest` |

## DoD（完了の定義）

- [x] 対象ストーリーの受入基準を満たす。**21-2 の距離は果たせない**（区間数で代替。完了報告書に記録する）
- [x] 成功基準 1〜9 を満たす
- [x] **受入基準 21-1〜21-6・22-1〜22-4 それぞれに、達成を確かめる検査がある**
- [x] **デモ項目 10 件それぞれに、それを守る検査を書き出した**（Try 1）。デモ項目の表に検査名を書いた
- [x] 全テストが緑。**品質ゲートは CI と同じコマンドで回した**——`./gradlew build`（全サービス）と `npm run verify`（返済枠 0.1・Try 6）
- [x] **CI が緑**（run [32933658224](https://github.com/k2works/case-study-cargo-tracker/actions/runs/32933658224)・全 6 ジョブ成功）。
  ただし E2E の 2 件（`tracking.spec.ts` の IT8 デモ項目）が**初回失敗してリトライで通っている**
  ——**過去 3 回の CI でも同じ 2 件が同じ形で落ちており**、本 IT の変更とは無関係な脆さである。
  **IT12 の返済枠に送る**（[ふりかえり](retrospective-11.md) の Problem に記録）
- [x] **`TZ=UTC` でも緑**（フロント 486・バックエンド全サービス）
- [x] **ドメイン層のカバレッジが 90% 以上**（billingms の domain/model は 98%）
- [x] SonarQube Quality Gate が **両プロジェクトで PASS**（新規カバレッジ 90.7 / 89.6・違反 0）
- [x] **ADR-027 の決定ごとに検査があり、その決定が効く経路を 1 度通した**（Try 2）。8 決定すべてに検査名と壊し方を記入。決定 5 は kind で実際に 409 を踏み、決定 1 は係数の掛け忘れ 3 通りで赤を確認
- [x] **列挙に足した値を使う場所すべてを回る検査がある**（Try 3）。`CargoType`（`@EnumSource` で係数）・`CancelledAtStatus`（`@EnumSource` で料率）・`PaymentStatus`（4 値の表示名）・`DiscountPolicyType`（`@EnumSource` で割引額）。いずれも `values()` から回す
- [x] **画面から踏むテストが、遷移先まで含めて 1 本になっている**（Try 4。デモ 8・9 が確定 → 詳細まで踏む）
- [x] **`ROLE_ACCOUNTANT` の到達性を、ルートガードを通る検査で確かめた**（Try 5。実環境 E2E でも踏んだ）
- [x] **この IT で書いた「〜しない」「〜まで確かめる」の数だけ検査があり、壊して赤を確認した**（返済枠 0.1・0.2・0.4・0.5・0.8、決定 1 の係数 3 通り、決定 4 の 2 通り、SQL の絞り 2 通り）
- [x] **新しく足した値が層をまたいで生き延びる**。区間数・割引率・誤配の記録・キャンセルの申請時状態を、ACL → ユースケース → API → 画面まで通した（実 DB の統合テストと実環境 E2E の両方で）
- [x] **件数から対象一覧へ辿れる**（[横断規約](release_plan.md)）。デモ 1 が実環境でも踏んだ
- [x] **サービス間の呼び出しを実際に 1 往復させた**（kind で予約 → 経路 → 荷役 → 通関 → 引取 → 料金算出 → 確定）
- [x] **実環境へ反映したら、Pod の起動時刻とコンテナ内 jar の日時で確かめた**（Try 7。it11-rev1 / rev2 の 2 回）
- [x] **デモ項目 10 件をこの順で通した**（`billing.spec.ts` 12 件が緑）
- [x] **IT10 時点のコメントが残っていない**（返済枠 0.9）
- [x] **JIG / jig-erd の出力を再生成した**
- [x] ユーザーマニュアル **12 章を新設**し、11 章の「算定していません」を直し、**キャプチャ 3 枚を再生成して目視した**（2 件の欠陥が見つかった）
- [x] **注 1〜16 を設計文書に反映した**（`user_story.md` / `domain-model.md` / `data-model.md` / `ui_design.md` / `architecture_backend.md` / `release_plan.md`）
- [x] `docs/index.md` / `development/index.md` / `mkdocs.yml` を同期した

## 進捗

| 区分 | 状態 |
| :--- | :--- |
| **実績** | **9 SP / 9 SP（100%）**・29 コミット・139 ファイル・10,468 行追加 |
| 返済枠（0.1〜0.9） | **完了**（9 件・独立コミット 9 本。`./gradlew build` 緑・`npm run verify` 緑） |
| Phase 1 受け入れテストと ADR | **完了**（[ADR-027](../adr/027-transport-charge-calculation.md) 決定 8 件・`billing.spec.ts` 12 件を Red で確認） |
| Phase 2 画面と導線 | **完了**（3 画面 + ダッシュボード導線。E2E 12 件・コンポーネント 18 件・ユニット 8 件が緑） |
| Phase 3 API と ACL | **完了**（`BillingSnapshot` 契約・両側の契約テスト・REST API・認可。bookingms 側の提供口を含む） |
| Phase 4 ドメイン | **完了**（`Money`・`TransportCharge`・`DiscountPolicy`・`CancellationFee`・`Invoice`・永続化・採番） |
| Phase 5 実環境 | **完了**（kind で引取 → 料金算出 → 確定を 1 往復。実環境 E2E 23 件が緑） |
| Phase 6 マニュアルと設計反映 | **完了**（12 章新設・キャプチャ 3 枚・11 章の修正・注 1〜16 の反映） |

## 整合性検証の結果

着手前に [validating-iteration-plan](../../.claude/skills/validating-iteration-plan/SKILL.md) と
[validating-design](../../.claude/skills/validating-design/SKILL.md) の観点で突合した。

| 軸 | 対象 | 結果 | 修正した点 |
| :--- | :--- | :--- | :--- |
| テンプレート | [イテレーション計画.md](../template/イテレーション計画.md) | **要修正 → 修正** | 「局面とアプローチ」「見積もり合計」「整合性検証の結果」「更新履歴」「関連ドキュメント」の 5 節が抜けていた。IT10 の計画にはあり、**本計画だけが落としていた** |
| ユーザーストーリー | [user_story.md](../requirements/user_story.md) | OK | 受入基準 21-1〜21-6・22-1〜22-4 が全文一致。22-1 に正典の「**料金算出時に**」を補った |
| ドメインモデル | [domain-model.md](../design/domain-model.md) | **要修正 → 修正（5 件）** | ① `bookingId` → **`cargoBookingId`** ② **`PaymentStatus` に `DRAFT` を足さない**——正典の 4 値は支払いの状態であり、そこに金額確定を混ぜると `CONFIRMED` が 2 つの意味を持つ。**算出中は永続化せず、確定操作で `PENDING` として発行する** ③ `finalAmount`・`taxRate`・`taxAmount`・`cancellationFee` を集約に戻した ④ `applyDiscount(rate)` → **`applyDiscount(policy: DiscountPolicy)`**・`calculate()` → `calculateFinalAmount()` ⑤ Try 3 欄が「`DiscountPolicyType` を足す」と書きながら図にも要素表にも無かった（**内部の食い違い**） |
| データモデル | [data-model.md](../design/data-model.md) | **要修正 → 修正（4 件）** | ① `tax_rate`・`tax_amount` は `NOT NULL`。**書かずには行を作れない**のに ER 図から落ちていた ② `due_date`・`discount_amount_*`・`cancellation_fee_*` の既存 4 種が落ちていた ③ `base_amount_*`・`discount_rate`・`shipper_id` は**新規追加**であることを注 11 に明記 ④ `confirmed_at` / `confirmed_by` は不要になった（発行＝確定のため `issued_at` で足りる） |
| UI 設計（ビュー） | [ui_design.md](../design/ui_design.md) | **要修正 → 修正（2 件）** | ① `/billing/:invoiceId` の正典名称は「**請求書詳細**」であり「精算書詳細」ではない ② 金額内訳に**消費税行がある**（注 12）。`/billing/new/:bookingId` が画面一覧に無い点は注 5 で既出 |
| UI 設計（インタラクション） | [ui_design.md](../design/ui_design.md) | **要修正 → 修正** | 画面遷移図に**入力誤りの自己ループ**が無かった（調整額・割引率の入力がある）。確定は **POST → 303 → GET** と明記した。取得は htmx ではなく **React Query**（本プロジェクトは React SPA）——既存パターンを踏襲する |
| 開発戦略 | [development_strategy.md](development_strategy.md) | OK | IT11 は終盤（IT8〜IT12）・アウトサイドイン、US21・US22 で 9 SP。戦略の割当表と一致。**「終盤で新しい結合方式を発明しない」**に従い、ACL は `CargoSnapshot` と同じ形にする |
| 過去計画の連続性 | IT6・IT9・IT10 の計画 | **要注意 → 注記** | IT6 →IT9 →IT11 と繰り越された **US30-9（キャンセル料）** を本 IT で果たす。ただし bookingms は `booking_status_at_request`、billing は `booking_status_at_cancel` と**同じ値に 2 つの名前がある**（注 16-a）。IT10 返済枠 0.6 の「受け皿の要否判断」の結論も本 IT で消化する |
| 前 IT のレビュー指摘 | [IT10 レビュー](../review/イテレーション10_review_20260826.md) | OK | IT11 送りの低 4 件（15〜18）を返済枠 0.3〜0.6 に、懸念 2 件を 0.7・0.8 に計上済み |
| ゴールの内部整合 | 本計画全体 | **要修正 → 修正** | DoD が「注 1〜5」、Phase 6.3 が「注 1〜3」と書いていたが、**注は 9 件あった**（検証後 17 件）。書き写した数は正典が増えても追随しない（[DoD は条件を書き写さず引用する]の形） |

> **検証で 15 件の不整合が出た。うち 11 件は正典を読まずに書いた形である。**
> 最も重かったのは **`PaymentStatus` に `DRAFT` を足そうとしていたこと**。正典の 4 値は
> 支払いの状態を表しており、そこへ「金額を確定したか」を混ぜると、`CONFIRMED` が
> **「支払い確認済み」と「金額確定済み」の 2 つの意味**を持つ。US23（IT12）で支払いを
> 扱う段になって初めて破綻し、そのときには請求書がすでに発行されている。
> **着手前に潰せた**。

## 更新履歴

| 日付 | 内容 | 担当 |
| :--- | :--- | :--- |
| 2026-08-26 | 初版作成（US21・US22／9 SP／返済枠 9 件・26h） | 開発 |
| 2026-08-26 | **返済枠 9 件を完了**（Phase 0）。0.1 は oxlint が警告を exit 0 で返しており、`verify` に入れても品質ゲートとして働かないことが分かった（`--deny-warnings` を足した）。0.2 の原因は `--tag` の解釈ではなく `kubectl set image` の性質だった——既定タグが base に直書きされているため spec が変わらず、Pod が作り直されない。0.4 は ADR-026 決定 2 が「述語がすべての値を明示的に扱う」と決めていながら、実装が否定リストで**足した値が自動的に開く方向へ倒れて**いた。0.5 は決定 7 の欄に決定 5 の壊し方が書いてあった。0.7 は `Record<string, string>` を型で網羅させる形に変え、0.8 は順序をコメントから `InOrder` の検査に移した | 開発 |
| 2026-08-26 | **整合性検証を実施し 15 件の不整合を修正。** `PaymentStatus` の `DRAFT` を取りやめ（算出中は永続化せず、確定操作で `PENDING` 発行）、集約に `finalAmount`・`taxRate`・`taxAmount`・`cancellationFee` を戻し、ER 図に既存 7 列を戻した。画面名を「請求書詳細」に統一。テンプレート必須 5 節を追加。注を 9 件 → 17 件に増補し、DoD の参照を「注 1〜16」に直した | 開発 |

## 関連ドキュメント

- [リリース計画](release_plan.md)
- [開発戦略](development_strategy.md)
- [IT10 完了報告書](iteration_report-10.md)・[IT10 ふりかえり](retrospective-10.md)
- [IT10 レビュー](../review/イテレーション10_review_20260826.md)
- [ユーザーストーリー](../requirements/user_story.md)（US21・US22）
- [ドメインモデル](../design/domain-model.md)（6. Billing Context）
- [データモデル](../design/data-model.md)（billing_db）
- [UI 設計](../design/ui_design.md)（精算管理・請求書詳細）
- [ADR-011 予約 ID の採番](../adr/011-booking-id-numbering.md)
- [ADR-012 値オブジェクトの粒度](../adr/012-value-object-granularity.md)
- [ADR-025 通関申告とキャンセル承認](../adr/025-customs-declaration-and-cancellation-approval.md)
- [ADR-026 誤配の検知と経路の再設計](../adr/026-misroute-detection-and-rerouting.md)
- ADR-027 輸送料金の算定規則（本 IT で作成）

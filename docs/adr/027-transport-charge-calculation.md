# ADR-027: 輸送料金の算定規則

引取済の貨物に対して輸送料金を算出し（US21）、法人には契約割引を適用する（US22）ときに、
**何を根拠に計算し、どこまでを精算書として残し、いつ動かなくなるか**を決める。

日付: 2026-08-26

## ステータス

承認済み（決定 1〜8）

## コンテキスト

IT1 のウォーキングスケルトンで billingms を起こしてから 10 イテレーション、
**このサービスはヘルスチェックしか返していない。** 予約は取れ、経路は組め、荷役は記録され、
追跡でき、通関を通り、誤配も直せるようになったが、**運賃は 1 円も計算していない。**

そのため、いくつかの決定が「あとで billingms が引き受ける」ことを前提に据え置かれてきた。

| 据え置かれたもの | 出どころ | 状態 |
| :--- | :--- | :--- |
| 誤配による料金調整 | [ADR-026](026-misroute-detection-and-rerouting.md) 決定 3（US28-8） | `Misroute` に事実だけ残した。**読む相手がいない** |
| キャンセル料の算定 | [ADR-025](025-customs-declaration-and-cancellation-approval.md)（US30-9） | IT9 は画面に「算定していません」と書いた |
| 経路候補の概算費用 | [ADR-017](017-route-candidates-api.md) | 「US21 で実料金に差し替える」と書いてある |

**本 IT でその相手を作る。** 着手前に決めておくことが 8 つある。

### 前提: 正典の料金式は距離を必要とするが、距離は持っていない

[domain-model.md](../design/domain-model.md) の Billing Context に料金計算ロジックがある。

```text
基本料金 = 距離係数 × 重量（kg） × 貨物種別係数
```

**距離は算出できない。** 港のマスタ（`location`）は UN/LOCODE と名称と業務タイムゾーンしか
持たず、緯度経度が無い。航海（`voyage` / `carrier_movement`）も距離を持たない。

受入基準 21-2 も「輸送実績（経路・**距離**・重量・貨物種別・荷役作業実績）が表示される」と
書いている。**字面どおりには満たせない。**

## 決定

### 決定 1: 基本料金は「区間数 × 重量 × 貨物種別」で算定する。距離の代わりに区間数を使う

```text
基本料金 = 基準運賃 × 区間係数 × 重量係数 × 貨物種別係数

  基準運賃   = 50,000 円（1 区間・1,000kg・一般貨物のとき）
  区間係数   = 旅程の区間数（直行なら 1、積み替え 1 回なら 2）
  重量係数   = 重量（kg）÷ 1,000（下限 0.1）
  貨物種別係数 = GENERAL 1.0 / HAZARDOUS 1.8 / REFRIGERATED 1.5
```

貨物種別係数は正典の値をそのまま使う。**区間数を距離の代わりに使うのは、区間数が
「どれだけ運んだか」に比例する唯一の実測値だからである**——旅程は経路設計者が確定し、
荷役の実績と突き合わせ済みで、後から変わらない。

> **区間数は距離の代用として粗い。** 東京 → 横浜と東京 → ロサンゼルスが同じ 1 区間になる。
> それでも採るのは、**代用が粗いことは画面に書けるが、根拠が無いことは書けない**からである。
> 港に緯度経度を持たせて距離を出す案は、マスタの整備（全港の座標）が要り、US21 の範囲を超える。
>
> **画面には内訳（区間数・重量・種別・それぞれの係数）を出す。** 金額そのものより
> 「なぜその金額か」が読めることを優先する——経理担当者は請求の根拠を荷主に説明する。

**重量係数に下限（0.1）を置く。** 置かないと、軽量の貨物が 0 円に近づく。運ぶ手間は
重量に比例しない。

### 決定 2: 端数は 1 円単位で四捨五入する。丸めは `Money` の中 1 か所だけで行う

割引後の金額（基本料金 × 0.85 など）には端数が出る。

- **保存するのは丸めたあとの値である。** 丸める前の値を保存して表示のたびに丸めると、
  明細の合計と総額が 1 円ずれる場面が出る
- **丸めは `Money` の演算の中でだけ行う。** 呼び出し側が丸めると、丸める場所が増えるたび
  結果が変わりうる（[IT11 計画](../development/iteration_plan-11.md) リスク 5）
- 画面は**サーバが返した値をそのまま出す**。フロントで金額の計算をしない

DB の金額列は `NUMERIC(15,2)` にする。[data-model.md](../design/data-model.md) は `INTEGER`
（最小通貨単位）と書いているが、**丸めの単位（円）と保存の単位を一致させる**ほうが、
読んだときに何が起きたか分かる。

### 決定 3: 算出中は永続化しない。確定操作で `Invoice` を `PENDING` として発行する

`PaymentStatus` に `DRAFT` を足さない。

正典の `PaymentStatus` は `PENDING` / `CONFIRMED` / `OVERDUE` / `REFUNDED` の 4 値で、
**支払いの状態**を表す。ここに「金額を確定したか」を混ぜると、`CONFIRMED` が
**「支払い確認済み」と「金額確定済み」の 2 つの意味**を持つ。US23（IT12）で支払いを
扱う段になって初めて破綻し、そのときには請求書がすでに発行されている。

したがって:

- 経理担当者が料金算出の画面を開いている間、`Invoice` はまだ存在しない
- 確定操作をした時点で `Invoice` を `PENDING` で発行する（正典の `GenerateInvoiceCommand`）
- 受入基準 21-5 の「輸送料金が『確定』状態で登録される」は、**`Invoice` が発行済みで
  あること自体**が満たす

> **下書きを保存しないと、入力中の調整が失われるのではないか。** 失われる。ただし
> 料金算出は 1 回の操作で終わる作業であり（実績を見て、調整を入れて、確定する）、
> 中断して翌日続ける類のものではない。**下書きを持つと、下書きのまま忘れられた精算書が
> 溜まる**——それを見つける手段をまた作ることになる。

**本 IT で起こす遷移は「算出中 → `PENDING`」の 1 本だけである。** 残る 3 本
（`CONFIRMED` / `OVERDUE` / `REFUNDED`）は US23。列挙は 4 値すべて宣言し、
**扱う場所すべてを回る検査**を置くが、遷移そのものは起こさない。

### 決定 4: 発行した精算書の金額は動かない

請求書は荷主へ出す約束である。出したあとに黙って変わると、請求の根拠が消える。

集約が守る——`Invoice` に金額を変える手段を置かない。訂正は US23（IT12）で
「取り消して出し直す」形にする。

> **`booking_id` の UNIQUE 制約と対になる**（正典のビジネスルール 5）。同じ貨物に
> 二重に請求書を出せない。**制約と集約の両方で守る**——制約だけだと画面に 500 が出て、
> 集約だけだと同時に 2 回押されたときに通る。

### 決定 5: 料金算出の起点は経理担当者の操作である。イベントは待たない

正典のビジネスルール 1 は「Invoice は貨物配送完了（`CargoDeliveredEvent` 受信）または
キャンセル確定（`CargoCancelledEvent` 受信）後にのみ発行できる」と書いている。
**この「イベント受信で発行できるようになる」を、本 IT では採らない。**

受入基準 21-1 は「『引取済』状態の予約に対して料金算出を**開始できる**」であり、
開始するのは経理担当者である。イベントを起点にすると:

- `CargoDeliveredEvent` を trackingms に実装する必要がある（未実装）
- **読む側の無い配線を先に敷くことになる**（[ADR-025](025-customs-declaration-and-cancellation-approval.md) 決定 3 と同じ判断）
- キャンセルされた貨物は配送完了しないため、別の入口がどのみち要る

したがって **billingms は引取済（`CLAIMED`）の予約を bookingms から引く**。
`CargoDeliveredEvent` は US23（IT12）の精算通知で必要になる——そのときに実装する。

> 正典のビジネスルール 1 は「引取済の予約に対して経理担当者が発行できる」に直す
> （[IT11 計画](../development/iteration_plan-11.md) 注 13）。**イベント駆動と書いたまま
> 実装すると、読む側の無い配線を敷くことになる。**

### 決定 6: 誤配・例外・キャンセル料は「明細（`InvoiceLineItem`）」として積む

料金調整（減額・補償費用）を基本料金に混ぜない。**根拠が読めなくなる。**

| 明細 | 出どころ | 符号 |
| :--- | :--- | :--- |
| 法人割引 | `shipper.discount_rate`（US22） | 減額 |
| 誤配による調整 | `Misroute`（US28-8・[ADR-026](026-misroute-detection-and-rerouting.md) 決定 3） | 減額（経理担当者が入力） |
| 例外による調整 | 遅延・破損・紛失（US19・US20） | 減額または補償費用 |
| キャンセル料 | `CancellationFee`（US30-9） | 加算 |

**誤配と例外の調整額は自動で決めない。** どれだけ減額するかは荷主との関係で決まる話であり、
規則にできない。**画面は根拠（いつ・どこで外れたか／どの例外が起きたか）を出し、
金額は経理担当者が入れる。**

> **「残っている」と「読める」は別である**（IT10 レビューの懸念）。`Misroute` は予約詳細に
> しか出ておらず、経理担当者はその画面を開けない。**`BillingSnapshot` に載せる**
> （決定 7）。

### 決定 7: `BillingSnapshot`（ACL）が bookingms から料金算出の入力を引く

handlingms 向けの `CargoSnapshot`（[ADR-023](023-handling-activity-validation.md) 決定 2）と
**同じ形にする。** 終盤で新しい結合方式を発明しない（[開発戦略](../development/development_strategy.md)）。

`BillingSnapshot` が運ぶもの:

| 項目 | 用途 |
| :--- | :--- |
| `bookingId` / `bookingStatus` | 引取済かどうかの判定（決定 5） |
| `shipperId` / `shipperType` / `discountRate` | 法人割引（US22） |
| `weightKg` / `cargoType` | 基本料金（決定 1） |
| `legCount` / 旅程の区間 | 区間係数と、実績の表示（21-2） |
| `misroute`（いつ・どこで） | 料金調整の根拠（決定 6） |
| `cancellation`（申請時の状態・陸揚げ地） | キャンセル料の算定根拠 |

**billingms は bookingms の型を持ち込まない。** 受けるのは billingms 側の DTO であり、
そこからこちらの言葉（`Money`・`DiscountRate`）へ変換する。契約は共有カーネルの
testFixtures に 1 つ置き、両側が読む。

> **同じ値に 2 つの名前がある。** キャンセル時の予約状態は bookingms で
> `booking_status_at_request`（申請時）、billing 側の列は `booking_status_at_cancel`。
> **ACL の変換で、どちらの意味かを明示する**——申請した時点の状態であり、
> 承認された時点の状態ではない。

### 決定 8: 消費税は既定 10% で計算して保存する。税率を業務として扱うのは US23

`invoice.tax_rate` / `tax_amount` は `NOT NULL`（[data-model.md](../design/data-model.md)）であり、
**書かずには行を作れない。**

本 IT は税率を業務として扱わない（US21・US22 の受入基準に無い）。既定値 10% で計算し、
[ui_design.md](../design/ui_design.md) の請求書詳細が持つ金額内訳のとおり画面にも出す。

**税率を変える手段は置かない。** 置くと、それが正しく使われているかを確かめる相手
（税区分・軽減税率・輸出免税）が要る。US23 で精算を扱うときに決める。

## 影響

### 良い影響

- **経理担当者が初めて仕事をする。** ロールは IT1 から存在するが、開いている画面が 1 つも無い
  状態が 10 イテレーション続いていた
- **据え置かれた 3 つの決定が決着する**（誤配の料金調整・キャンセル料・概算費用との関係）
- **`Money` を持つ集約ができる。** [architecture_backend.md](../design/architecture_backend.md) が
  「金額を扱う」を特殊要件として挙げていながら、扱う場所が無かった

### 悪い影響・受け入れるリスク

- **区間数は距離の代用として粗い**（決定 1）。東京 → 横浜と東京 → ロサンゼルスが同じ 1 区間になる。
  **受入基準 21-2 を字面どおりには満たせない**ことを完了報告書に記録する
- **料金の規則が業務として決まっていない。** 基準運賃 50,000 円は置いた数字であり、
  実際の運賃表ではない。**根拠を画面に出す**ことで、違っていたときに気づけるようにする
- **下書きを保存しない**（決定 3）ため、入力中に画面を閉じると調整の入力は失われる

## コンプライアンス

**決定ごとに検査を置き、1 件ずつ実装を壊して赤になることを確かめる。** 表に検査名を書いた
時点では完了としない——IT8 は 11 決定のうち 3 件が空振りだった。

**決定が効く経路を、端から端まで 1 度通す**（IT10 Try 2）。集約に検査を置いて終わりにしない
——IT10 は決定を書いて集約に検査も置いたのに、その決定が効く経路を一度も通していなかった。

| 決定 | 検査 | 壊して赤を確認 |
| :--- | :--- | :--- |
| 1 区間数 × 重量 × 貨物種別 | `TransportChargeTest`（4 係数・区間数の効き・重量の下限と境目）・`CargoTypeTest#everyTypeHasAPositiveFactor` / `#chargesMoreForSpecialCargo`・`billing-new-page.test.tsx#基本料金の根拠に、4 つの係数がすべて出る` | **済**（**係数を 1 つずつ掛け忘れる 3 通りすべてで赤**。区間 0 本・重量 0 以下・種別なしも断る） |
| 2 端数は 1 円で四捨五入・丸めは `Money` の中 | `MoneyTest`（四捨五入・負の対称性・丸めた値を保持・丸め後で等価）・`money.test.ts`（画面は整形するだけ） | **済**（丸めを外すと `keepsTheRoundedValue` が赤。負の丸めを `HALF_DOWN` にすると `roundsNegativeAmountsSymmetrically` が赤） |
| 3 算出中は永続化しない・確定で `PENDING` 発行 | `InvoiceTest#isPendingWhenIssued` / `PaymentStatus` の 4 値と表示名・`billing-new-page.test.tsx`（算出画面は保存しない）・`invoice-detail-page.test.tsx#発行直後の状態は未入金である` | **済**（`PaymentStatus` に `DRAFT` を足すと `hasTheAgreedValues` が赤。表示名を落とすと `everyStatusHasALabel` が赤） |
| 4 発行した精算書は動かない | `InvoiceTest.Immutability`（明細を足せない・渡した一覧の書き換えが効かない）・`invoice-detail-page.test.tsx#金額を動かす操作が残っていない` | **済**（**2 通りで別々に赤**——変更可能な一覧を返すと前者、写さず参照を持つと後者。画面に「調整を追加」を戻すと 3 つ目も赤） |
| 5 起点は経理担当者・イベントは待たない | `CalculateChargeUseCaseTest#rejectsCargoThatCannotBeBilled`（算出・確定の両方）・`BillableCargoQueryIntegrationTest#selectsOnlyDeliveredAndCancelledCargoes`・`BillingControllerTest`（409）・`billing-new-page.test.tsx#引取が終わっていない予約では、料金を出さない` | **済**（**SQL の絞りを外すと 2 件が赤**。`CargoDeliveredEvent` の購読は 1 か所も無い） |
| 6 調整は明細として積む・金額は自動で決めない | `InvoiceTest#appliesAdjustments` / `#rejectsAdjustmentsWithoutDescription`・`CalculateChargeUseCaseTest#carriesTheMisrouteWithoutDecidingTheAdjustment`・`billing-new-page.test.tsx`（内容・金額の無い調整を断る） | **済**（内容を空にすると集約が断る。**誤配があっても基本料金が変わらないことまで見る**——自動で減額する実装にすると赤） |
| 7 `BillingSnapshot` は `CargoSnapshot` と同じ形 | `BillingSnapshotContractTest`（項目の名簿・本番の変換器・知らない項目・個人の判定）・`RestBillingSnapshotFinderTest`（経路・名乗り・404 は空・500 は隠さない）・`BillingLookupControllerTest`（プロバイダ側） | **済**（名乗りを契約と食い違わせると赤。**承認済みの絞りを外すと予約が重複して赤**） |
| 8 消費税は既定 10%・変える手段は置かない | `BillingIdentifiersTest.Taxes`・`InvoiceTest#sumsUpTheTotal`・`invoice-detail-page.test.tsx#金額内訳に割引の根拠が載る`（消費税行） | **済**（税率を変える API も画面の入力欄も無い。`TaxRate.of` は US23 の足場として残す） |

## 関連

- [ADR-011](011-booking-id-numbering.md)（予約 ID の採番。請求番号も同じ形で採る）
- [ADR-017](017-route-candidates-api.md)（経路候補 API。**概算費用を実料金に差し替えるのは本 ADR**）
- [ADR-023](023-handling-activity-validation.md)（荷役の検証。決定 2 の `CargoSnapshot` を踏襲する）
- [ADR-025](025-customs-declaration-and-cancellation-approval.md)（通関申告とキャンセル承認。**決定 3 の「読む側の無い配線を先に敷かない」を継承**）
- [ADR-026](026-misroute-detection-and-rerouting.md)（誤配の検知と経路の再設計。決定 3 が残した `Misroute` を本 ADR が読む）

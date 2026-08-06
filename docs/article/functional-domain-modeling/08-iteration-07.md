# 第 8 章：IT7 料金算出と精算

## このイテレーションのゴール

> 割引ポリシー管理・輸送料金算出・法人割引適用・精算処理を実装し、配送完了（引取済）から料金算出→精算書発行→入金確認→予約 Settled 同期までを一気通貫させる。Billing コンテキストを立ち上げ、Release 1.1 を出荷する。

金額を扱う最後のコンテキストです。丸め誤差・通貨の整合・支払状態という、業務システムで最も間違えやすい 3 点が同時に来ます。

| 項目 | 内容 |
| :--- | :--- |
| 目標 SP | 16（US-ADM-01 / US21 / US22 / US23） |
| ユニットテスト | 211 件緑（+23） |
| 統合テスト | 140 件緑（+12） |
| カバレッジ（全体 / ドメイン層） | 90.6% / 89.7% |
| ADR | 0013 料金算出と Billing↔Booking 連携は合成層と状態射影で行う |

## 扱うユーザーストーリー

| ID | ストーリー |
| :--- | :--- |
| US-ADM-01 | 割引ポリシーを管理する |
| US21 | 輸送料金を算出する |
| US22 | 法人割引を適用する |
| US23 | 精算を処理する |

## モデリング：金額を型で守る

Billing のモジュール冒頭に、設計方針が明記されています。

```fsharp
// src/CargoTracker.Billing/Domain.fs
// 金額は Money（最小通貨単位の int64 + 通貨コード・銀行家丸め）で表現し、丸め誤差を排除する。
// 支払い状態は PaymentState DU で各ケースに時刻を埋め込み「Confirmed なのに paidAt が null」を型排除する。
```

通貨コードは DU です。

```fsharp
/// 通貨コード（国内は日本円。USD は多通貨対応の契約を明示し異通貨演算の不変条件を検証可能にする）。
type CurrencyCode =
    | JPY
    | USD
```

コメントの「USD は多通貨対応の契約を明示し異通貨演算の不変条件を検証可能にする」が要点です。**単一通貨なら通貨フィールドは不要**ですが、それでは「異なる通貨の金額を足してはいけない」というルールをテストできません。2 通貨あることで、不変条件が意味を持ちます。

姉妹シリーズで見た通り、10 実装のすべてが単一通貨前提でした。そのうち通貨を型として持ったのは F# を含むごく一部です。Java 実装は `int`（円）だけを持ち、通貨の概念がありません。

金額そのものは、最小通貨単位の整数と通貨の組です。

```fsharp
/// 金額：最小通貨単位の整数（円は 1 円単位）と通貨コード。
type Money =
    { Amount: int64
      Currency: CurrencyCode }

module Money =
    let zero (currency: CurrencyCode) : Money = { Amount = 0L; Currency = currency }

    let create (amount: int64) (currency: CurrencyCode) : Result<Money, DomainError> =
        if amount < 0L then
            Error(ValidationError("Money", "金額は 0 以上でなければなりません。"))
        else
            Ok { Amount = amount; Currency = currency }
```

`decimal` でも `float` でもなく `int64` です。最小通貨単位（円なら 1 円、ドルならセント）の整数で持つことで、**加算・減算に丸め誤差が入りません**。

加算は通貨の一致を要求します。

```fsharp
    /// 同一通貨のみ加算できる。
    let add (a: Money) (b: Money) : Result<Money, DomainError> =
        if a.Currency <> b.Currency then
            Error(BusinessRuleViolation("CurrencyMismatch", "通貨が異なる金額は加算できません。"))
        else
            Ok { a with Amount = a.Amount + b.Amount }
```

`add` が `Result` を返すため、呼び出し側は通貨不一致の可能性を無視できません。

### 型で守りきらなかった選択

ここは第 1 章で予告した妥協点です。通貨を型パラメータにすれば（`Money<JPY>` のように）、通貨不一致は**コンパイルエラー**にできます。F# には単位付き測定（`[<Measure>]`）という機能があり、これを使えば実現可能です。

しかし実装は実行時の `Result` を選びました。理由は DB からの復元です。`money_currency` カラムの文字列から `Money<'Currency>` を構築するには、文字列から型を決める仕組みが要ります。型と値の橋渡しが煩雑になり、リポジトリ層のコードが膨らみます。

**型で表せることと、表す価値があることは別**という判断が、ここでも働いています。

### 丸めを明示する

乗算だけは丸めが発生します。

```fsharp
    /// 係数の乗算は最小通貨単位へ銀行家丸め（MidpointRounding.ToEven）で丸める。
    let multiply (factor: decimal) (m: Money) : Money =
        let raw = decimal m.Amount * factor
        let rounded = Math.Round(raw, MidpointRounding.ToEven)
        { m with Amount = int64 rounded }
```

`MidpointRounding.ToEven`（銀行家丸め）を明示しています。0.5 を常に切り上げると累積的に金額が増えるため、会計処理では偶数側に丸める方式が使われます。

姉妹シリーズの Java 実装は `RoundingMode.DOWN`（切り捨て）でした。どちらも「デフォルトに委ねない」という点では正しく、**丸めは業務判断である**という認識が両実装にあります。ただし方式が違うため、同じ入力で 1 円ずれます。丸め方式の選択理由を ADR に残すべき類の判断です。

戻り値が `Result` ではなく `Money` である点にも意味があります。乗算は失敗しません——通貨は変わらず、係数は常に適用できるためです。**失敗しない演算は `Result` で包まない**という規律が保たれています。

## モデリング：支払状態が時刻を持つ

支払状態は 4 つあり、それぞれが異なる時刻を持ちます。

```fsharp
type PaymentState =
    | Pending of dueDate: DateTimeOffset
    | Confirmed of paidAt: DateTimeOffset
    | Overdue of dueDate: DateTimeOffset
    | Refunded of refundedAt: DateTimeOffset
```

第 5 章の `BookingState` と同じ技法ですが、こちらのほうが分かりやすい例です。

- `Pending` は**支払期限**を持つ
- `Confirmed` は**入金日時**を持つ
- `Overdue` は**超過した期限**を持つ
- `Refunded` は**返金日時**を持つ

素朴に書けば `DueDate`・`PaidAt`・`RefundedAt` の 3 フィールドをすべて nullable で持つことになります。その形では「入金済みなのに入金日時が null」も「未払いなのに返金日時がある」も作れてしまいます。

DU なら、**各状態がその状態で意味を持つ時刻だけを持ちます**。

姉妹シリーズの TypeScript 実装では、この領域で実際のバグが出ています。

> 割引率逆算による請求書復元クラッシュの解消（High・重大バグ）
> paidAt 復元・入金確認の順序

`paidAt` の復元順序の問題は、状態と時刻が別フィールドであるがゆえに起きます。片方だけ復元された中間状態が存在するためです。DU なら、状態を構築する時点で時刻が揃っていなければ構築できません。

## モデリング：支払状態の遷移

```fsharp
    /// 支払い状態遷移（ビジネスルール 3・4）。不正遷移は InvalidStateTransition で拒否する。
    let execute (invoice: Invoice) (command: InvoiceCommand) : Result<Invoice * BillingEvent list, DomainError> =
        match invoice.Payment, command with
        | Pending _, ConfirmPayment paidAt
        | Overdue _, ConfirmPayment paidAt ->
            Ok(
                { invoice with
                    Payment = Confirmed paidAt },
                [ PaymentConfirmed(invoice.InvoiceId, paidAt) ]
            )
        | Pending dueDate, MarkOverdue now when now > dueDate ->
            Ok(
                { invoice with
                    Payment = Overdue dueDate },
                [ PaymentOverdue invoice.InvoiceId ]
            )
        | Confirmed _, IssueRefund refundedAt ->
            Ok(
                { invoice with
                    Payment = Refunded refundedAt },
                [ PaymentRefunded(invoice.InvoiceId, refundedAt) ]
            )
        | state, cmd -> Error(InvalidStateTransition(PaymentState.name state, InvoiceCommand.name cmd))
```

2 つの技法が使われています。

**or パターン** — `| Pending _, ConfirmPayment paidAt | Overdue _, ConfirmPayment paidAt ->` で、「未払いからでも期限超過からでも入金確認できる」を 1 つの腕にまとめています。

**`when` ガード** — `| Pending dueDate, MarkOverdue now when now > dueDate ->` で、期限を過ぎている場合だけ超過に遷移します。期限前に `MarkOverdue` を呼んでも、この腕にはマッチせず catch-all で `InvalidStateTransition` になります。

`Pending dueDate` で期限を取り出し、それと `now` を比較しているのが要点です。**期限は状態が持っている**ので、別途フィールドを読む必要がありません。

姉妹シリーズの Go 実装では、まさにこの比較でバグが出ています。

> 支払期限 DATE/TIMESTAMP 境界バグ（`MarkOverdue`・`IsActiveOn`）→ 日付単位比較へ正規化・当日時刻付き境界テスト追加

支払期限が DATE 型（00:00）、比較対象が TIMESTAMP だったため、期限当日に到着した貨物を期限超過と誤判定していました。F# 実装は両方 `DateTimeOffset` で揃えているため同じ問題は起きませんが、**型が揃っていることと業務的に正しいことは別**です。「期限当日の 23:59 は超過か」という問いには、型は答えません。

## モデリング：請求金額の組み立て

請求書の発行は、割引と消費税を順に適用します。

```fsharp
        let finalAmount =
            baseAmount |> Money.multiply (1.0m - DiscountRate.value discountRate)

        // 割引後小計に消費税を課す（IT8・US22）。
        let taxRate = ConsumptionTax.StandardRate
        let taxAmount = finalAmount |> ConsumptionTax.calculate taxRate

        let dueDate = issuedAt.AddDays 30.0

        let invoice =
            { InvoiceId = invoiceId
              CargoBookingId = bookingId
              ShipperId = shipperId
              BaseAmount = baseAmount
              DiscountRate = discountRate
              FinalAmount = finalAmount
              TaxRate = taxRate
              TaxAmount = taxAmount
              IssuedAt = issuedAt
              Payment = Pending dueDate }
```

計算過程の値がすべて保存されています。基本金額・割引率・割引後小計・税率・税額。

これは冗長に見えますが、**発行時点の計算根拠を凍結する**ための設計です。後から税率が変わっても、発行済みの請求書は当時の税率を保持します。

姉妹シリーズの TypeScript 実装が「割引率逆算による請求書復元クラッシュ」を起こしたのは、この逆をやったためです。割引後金額と割引率から元金額を逆算する設計では、丸めが入った時点で復元できません。

請求総額は導出です。

```fsharp
    /// 請求総額 = 割引後小計（FinalAmount） + 消費税（TaxAmount）（IT8・US22）。
    let totalAmount (invoice: Invoice) : Money =
        match Money.add invoice.FinalAmount invoice.TaxAmount with
        | Ok m -> m
        | Error _ -> invoice.FinalAmount // 通貨不一致は発生しない（同一通貨で構築）
```

`Money.add` が `Result` を返すため、通貨不一致の分岐を書く必要があります。ここでは「同一通貨で構築しているので起きない」という理由でフォールバックしています。

**型が要求する分岐に、実務的に意味のない腕が生じる**例です。コメントで理由を書くことで、次の読み手が「なぜエラーを握り潰すのか」と悩まずに済みます。理想的には `Money` の通貨を型で固定すればこの腕自体が消えますが、前述の通り永続化とのトレードオフで採っていません。

## ワークフロー：コンテキストをまたぐ精算

入金が確認されたら、予約側も `Settled` にする必要があります。しかし Billing Context は Booking Context のドメインモデルに触れません。

ADR-0013 に「料金算出と Billing↔Booking 連携は合成層と状態射影で行う」と記録されています。**合成層**（`CargoTracker.Web`）が両コンテキストのアプリケーション関数を呼び分けます。

第 5 章で見た `RouteAssignment.settle` が Booking 側の入口です。

```fsharp
    /// 精算完了を反映する（US23・Delivered → Settled）。
    let settle (repo: CargoRepository) (dispatcher: BookingEventDispatcher) (bookingId: BookingId) =
        applyCommand repo dispatcher bookingId Settle
```

Billing 側は入金確認、Booking 側は `Settle` コマンド。両者を繋ぐのは合成層であり、**どちらのコンテキストも相手を知りません**。

## このイテレーションの学び

### 金額の型が防いだもの・防がなかったもの

| 型で防げた | 型では防げなかった |
| :--- | :--- |
| 丸め誤差の蓄積（`int64` 最小単位） | 丸め方式の業務的な妥当性 |
| 「入金済みなのに入金日時なし」 | 「期限当日は超過か」の業務判断 |
| 通貨の異なる金額の加算（実行時 `Result`） | 通貨をまたぐ演算のコンパイル時排除 |
| 割引後金額の逆算による情報損失 | 消費税率の変更時の遡及範囲 |

右列はいずれも**業務の言葉で決めるべきこと**であり、型の守備範囲外です。

### 受入残を正直に記録する

このイテレーションのゴールは「Release 1.1 を出荷する」でしたが、実際には受入基準を満たしきれませんでした。IT8 のゴール文にその残りが列挙されています。

> IT7 で先送りした US21-23 の受入残（割引ポリシーマスタ接続・消費税/付加料金・支払期限・輸送実績表示・期限超過通知）を充足し…

5 項目が積み残っています。目標 SP 16 に対してストーリーが 4 つ（うち 1 つは管理機能）という詰め込みが、そのまま残として出ました。

姉妹シリーズで見た通り、未達を未達として記録すること自体は正しい実践です。重要なのはその後で、F# 実装は **IT8 を「受入残を消化する回」として明示的に計画しました**。「次のイテレーションで対応」という曖昧な扱いにせず、ゴール文に列挙して回収しています。

---

- 前の章：[第 7 章：IT6 輸送例外の登録と解決](07-iteration-06.md)
- 次の章：[第 9 章：IT8 実務品質への引き上げ](09-iteration-08.md)

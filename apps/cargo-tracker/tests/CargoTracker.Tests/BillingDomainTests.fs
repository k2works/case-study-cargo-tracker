module CargoTracker.Tests.BillingDomainTests

open System
open Xunit
open FsUnit.Xunit
open FsCheck
open FsCheck.Xunit
open CargoTracker.Shared.Domain
open CargoTracker.Billing.Domain

// US-ADM-01/US21/US22/US23: Billing ドメイン（Money 銀行家丸め・DiscountRate 0〜30%・
// DiscountPolicy・PaymentState 遷移・Invoice.generate/execute）。

let private jpy amount =
    match Money.create amount JPY with
    | Ok m -> m
    | Error e -> failwithf "%A" e

let private dto (y, m, d) =
    DateTimeOffset(y, m, d, 0, 0, 0, TimeSpan.Zero)

let private corporate () =
    match BillingShipperId.create "SHP-CORP" true with
    | Ok s -> s
    | Error e -> failwithf "%A" e

let private individual () =
    match BillingShipperId.create "SHP-IND" false with
    | Ok s -> s
    | Error e -> failwithf "%A" e

// ---- Money ----

[<Fact>]
let ``Money は負値で Error`` () =
    match Money.create -1L JPY with
    | Error(ValidationError("Money", _)) -> ()
    | other -> failwithf "ValidationError を期待したが: %A" other

[<Fact>]
let ``異通貨の加算は拒否される`` () =
    // 現状 JPY のみだが将来の多通貨に備えた契約。同通貨は加算成功。
    match Money.add (jpy 100L) (jpy 200L) with
    | Ok m -> m.Amount |> should equal 300L
    | Error e -> failwithf "%A" e

[<Fact>]
let ``Money.multiply は銀行家丸め（0.5 は偶数側へ）を行う`` () =
    // 5 * 0.5 = 2.5 → 偶数側 2、3 * 0.5 = 1.5 → 偶数側 2
    (Money.multiply 0.5m (jpy 5L)).Amount |> should equal 2L
    (Money.multiply 0.5m (jpy 3L)).Amount |> should equal 2L
    // 10% 割引: 12345 * 0.9 = 11110.5 → 偶数側 11110
    (Money.multiply 0.9m (jpy 12345L)).Amount |> should equal 11110L

[<Property>]
let ``Money.multiply 1.0 は金額を変えない`` (amount: int64) =
    // 非負に正規化（Money は 0 以上）
    let a = abs (amount % 1_000_000_000L)
    (Money.multiply 1.0m (jpy a)).Amount = a

// ---- Charge（料金算出・US21）----

[<Fact>]
let ``基本料金は 距離係数×重量×貨物種別係数 で算出される（US21）`` () =
    // 距離係数 100 × 重量 500kg × 一般 1.0 = 50000
    (Charge.calculateBase 100m 500m General JPY).Amount |> should equal 50_000L
    // 危険物 1.8 → 90000
    (Charge.calculateBase 100m 500m Hazardous JPY).Amount |> should equal 90_000L
    // 冷凍 1.5 → 75000
    (Charge.calculateBase 100m 500m Refrigerated JPY).Amount |> should equal 75_000L

[<Fact>]
let ``距離は確定経路の区間数から自動導出される（US21・1 区間 500km）`` () =
    Charge.deriveDistance 0 |> should equal 0m
    Charge.deriveDistance 1 |> should equal 500m
    Charge.deriveDistance 2 |> should equal 1000m
    // 負の区間数は 0km に丸める
    Charge.deriveDistance -3 |> should equal 0m

[<Fact>]
let ``距離係数は 導出距離×単価 で算出される（US21）`` () =
    // 2 区間（1000km）× 単価 0.1 = 距離係数 100
    Charge.distanceFactorOf 2 0.1m |> should equal 100m
    // 距離係数 100 × 重量 500 × 一般 1.0 = 50000
    (Charge.calculateBase (Charge.distanceFactorOf 2 0.1m) 500m General JPY).Amount
    |> should equal 50_000L

[<Fact>]
let ``例外時の料金調整は基本料金から減額される（US21 受入6・IT8）`` () =
    // 基本料金 50000 から 8000 減額 → 42000
    (Charge.applyAdjustment 8_000L { Amount = 50_000L; Currency = JPY }).Amount
    |> should equal 42_000L
    // 減額しすぎても 0 円を下回らない
    (Charge.applyAdjustment 60_000L { Amount = 50_000L; Currency = JPY }).Amount
    |> should equal 0L
    // 負の調整額は無視（減額 0）
    (Charge.applyAdjustment -100L { Amount = 50_000L; Currency = JPY }).Amount
    |> should equal 50_000L

[<Fact>]
let ``消費税は割引後小計に標準税率10%で課税される（US22・IT8）`` () =
    // 小計 45000 × 10% = 4500
    (ConsumptionTax.calculate ConsumptionTax.StandardRate { Amount = 45_000L; Currency = JPY }).Amount
    |> should equal 4_500L

[<Fact>]
let ``精算書は税抜小計・消費税・税込総額を保持する（US22・IT8）`` () =
    let rate =
        match DiscountRate.create 0.10m with
        | Ok r -> r
        | Error e -> failwithf "%A" e

    let bid = BillingBookingId.ofString "BKG-TAX01"

    let sid =
        { ShipperId = "SHP"
          IsCorporate = true }

    let issuedAt = DateTimeOffset(2026, 10, 6, 0, 0, 0, TimeSpan.Zero)

    let invoice, _ =
        Invoice.generateWithRate
            (InvoiceId.ofString "INV-TAX01")
            bid
            sid
            { Amount = 50_000L; Currency = JPY }
            rate
            issuedAt

    // 割引後小計 45000・消費税 4500・税込総額 49500
    invoice.FinalAmount.Amount |> should equal 45_000L
    invoice.TaxRate |> should equal 0.10m
    invoice.TaxAmount.Amount |> should equal 4_500L
    (Invoice.totalAmount invoice).Amount |> should equal 49_500L

// ---- DiscountRate ----

[<Fact>]
let ``割引率は 0〜30% の範囲外で Error`` () =
    match DiscountRate.create 0.31m with
    | Error(ValidationError("DiscountRate", _)) -> ()
    | other -> failwithf "ValidationError を期待したが: %A" other

    match DiscountRate.create -0.01m with
    | Error(ValidationError("DiscountRate", _)) -> ()
    | other -> failwithf "ValidationError を期待したが: %A" other

[<Fact>]
let ``割引率は 30% ちょうどを許容する（境界）`` () =
    match DiscountRate.create 0.30m with
    | Ok r -> DiscountRate.value r |> should equal 0.30m
    | Error e -> failwithf "%A" e

// ---- DiscountPolicy.calculateRate ----

[<Fact>]
let ``法人荷主の CorporateStandard は 10% 割引（US22）`` () =
    match DiscountPolicy.calculateRate (corporate ()) (jpy 500_000L) CorporateStandard with
    | Ok r -> DiscountRate.value r |> should equal 0.10m
    | Error e -> failwithf "%A" e

[<Fact>]
let ``個人荷主の CorporateStandard は割引なし（US22）`` () =
    match DiscountPolicy.calculateRate (individual ()) (jpy 500_000L) CorporateStandard with
    | Ok r -> DiscountRate.value r |> should equal 0.0m
    | Error e -> failwithf "%A" e

[<Fact>]
let ``VolumeDiscount は 100 万円以上で 15%、未満で 5%`` () =
    match DiscountPolicy.calculateRate (individual ()) (jpy 1_000_000L) VolumeDiscount with
    | Ok r -> DiscountRate.value r |> should equal 0.15m
    | Error e -> failwithf "%A" e

    match DiscountPolicy.calculateRate (individual ()) (jpy 999_999L) VolumeDiscount with
    | Ok r -> DiscountRate.value r |> should equal 0.05m
    | Error e -> failwithf "%A" e

[<Fact>]
let ``割引方針の toString/ofString は往復する`` () =
    for p in [ CorporateStandard; VolumeDiscount; Seasonal; NoDiscount ] do
        match DiscountPolicy.ofString (DiscountPolicy.toString p) with
        | Ok back -> back |> should equal p
        | Error e -> failwithf "%A" e

// ---- DiscountPolicyMaster.resolveApplicableRate（US22・マスタ権威・IT8）----

let private rateOf v =
    match DiscountRate.create v with
    | Ok r -> r
    | Error e -> failwithf "%A" e

let private masterOf policy rateVal =
    DiscountPolicyMaster.create policy (rateOf rateVal) "" (DateOnly(2026, 10, 1)) None

[<Fact>]
let ``法人荷主は有効な CorporateStandard マスタの率を適用する（マスタ権威）`` () =
    // マスタの率 12% を採用（ハードコード 10% ではない）
    let masters = [ masterOf CorporateStandard 0.12m ]
    let r = DiscountPolicyMaster.resolveApplicableRate masters true (jpy 400_000L)
    DiscountRate.value r |> should equal 0.12m

[<Fact>]
let ``個人荷主は CorporateStandard マスタがあっても割引 0`` () =
    let masters = [ masterOf CorporateStandard 0.12m ]
    let r = DiscountPolicyMaster.resolveApplicableRate masters false (jpy 400_000L)
    DiscountRate.value r |> should equal 0.0m

[<Fact>]
let ``100 万円以上はボリューム割引マスタが適用される`` () =
    let masters = [ masterOf VolumeDiscount 0.15m ]
    let r = DiscountPolicyMaster.resolveApplicableRate masters false (jpy 1_000_000L)
    DiscountRate.value r |> should equal 0.15m

[<Fact>]
let ``複数該当時は割引率が最大のマスタを採用する`` () =
    let masters = [ masterOf CorporateStandard 0.10m; masterOf Seasonal 0.20m ]
    let r = DiscountPolicyMaster.resolveApplicableRate masters true (jpy 400_000L)
    DiscountRate.value r |> should equal 0.20m

[<Fact>]
let ``該当マスタが無ければ割引 0`` () =
    let r = DiscountPolicyMaster.resolveApplicableRate [] true (jpy 400_000L)
    DiscountRate.value r |> should equal 0.0m

// ---- Invoice.generate ----

let private bookingId () =
    match BillingBookingId.create "BKG-0001" with
    | Ok b -> b
    | Error e -> failwithf "%A" e

let private genInvoice shipper policy amount =
    match
        Invoice.generate (InvoiceId.ofString "INV-0001") (bookingId ()) shipper (jpy amount) policy (dto (2026, 10, 6))
    with
    | Ok(inv, evts) -> inv, evts
    | Error e -> failwithf "%A" e

[<Fact>]
let ``精算書発行は法人割引を適用し最終金額・支払期限を設定する（US21/US22/US23）`` () =
    let inv, evts = genInvoice (corporate ()) CorporateStandard 400_000L

    inv.BaseAmount.Amount |> should equal 400_000L
    DiscountRate.value inv.DiscountRate |> should equal 0.10m
    inv.FinalAmount.Amount |> should equal 360_000L // 400000 * 0.9

    match inv.Payment with
    | Pending due -> due |> should equal (dto (2026, 11, 5)) // +30 日
    | other -> failwithf "Pending を期待したが: %A" other

    match evts with
    | [ InvoiceCreated(_, _, amount) ] -> amount.Amount |> should equal 360_000L
    | other -> failwithf "InvoiceCreated を期待したが: %A" other

[<Fact>]
let ``個人荷主は割引なしで基本料金＝最終金額`` () =
    let inv, _ = genInvoice (individual ()) CorporateStandard 400_000L
    inv.FinalAmount.Amount |> should equal 400_000L

// ---- Invoice.execute（PaymentState 遷移）----

[<Fact>]
let ``入金確認で Pending→Confirmed へ遷移する（US23）`` () =
    let inv, _ = genInvoice (corporate ()) CorporateStandard 400_000L

    match Invoice.execute inv (ConfirmPayment(dto (2026, 10, 20))) with
    | Ok(updated, [ PaymentConfirmed(_, paidAt) ]) ->
        PaymentState.name updated.Payment |> should equal "Confirmed"
        paidAt |> should equal (dto (2026, 10, 20))
    | other -> failwithf "PaymentConfirmed を期待したが: %A" other

[<Fact>]
let ``支払期限超過で Pending→Overdue へ遷移する（US23）`` () =
    let inv, _ = genInvoice (corporate ()) CorporateStandard 400_000L

    // 期限（11/5）より後
    match Invoice.execute inv (MarkOverdue(dto (2026, 11, 10))) with
    | Ok(updated, [ PaymentOverdue _ ]) -> PaymentState.name updated.Payment |> should equal "Overdue"
    | other -> failwithf "PaymentOverdue を期待したが: %A" other

[<Fact>]
let ``期限内の MarkOverdue は不正遷移として拒否される`` () =
    let inv, _ = genInvoice (corporate ()) CorporateStandard 400_000L

    match Invoice.execute inv (MarkOverdue(dto (2026, 10, 20))) with
    | Error(InvalidStateTransition _) -> ()
    | other -> failwithf "InvalidStateTransition を期待したが: %A" other

[<Fact>]
let ``Overdue からの入金確認は許容される`` () =
    let inv, _ = genInvoice (corporate ()) CorporateStandard 400_000L

    let overdue =
        match Invoice.execute inv (MarkOverdue(dto (2026, 11, 10))) with
        | Ok(a, _) -> a
        | Error e -> failwithf "%A" e

    match Invoice.execute overdue (ConfirmPayment(dto (2026, 11, 12))) with
    | Ok(updated, _) -> PaymentState.name updated.Payment |> should equal "Confirmed"
    | Error e -> failwithf "%A" e

[<Fact>]
let ``確定後の返金は Confirmed→Refunded へ遷移する`` () =
    let inv, _ = genInvoice (corporate ()) CorporateStandard 400_000L

    let confirmed =
        match Invoice.execute inv (ConfirmPayment(dto (2026, 10, 20))) with
        | Ok(a, _) -> a
        | Error e -> failwithf "%A" e

    match Invoice.execute confirmed (IssueRefund(dto (2026, 10, 25))) with
    | Ok(updated, [ PaymentRefunded _ ]) -> PaymentState.name updated.Payment |> should equal "Refunded"
    | other -> failwithf "PaymentRefunded を期待したが: %A" other

[<Fact>]
let ``Pending からの返金は不正遷移として拒否される`` () =
    let inv, _ = genInvoice (corporate ()) CorporateStandard 400_000L

    match Invoice.execute inv (IssueRefund(dto (2026, 10, 25))) with
    | Error(InvalidStateTransition _) -> ()
    | other -> failwithf "InvalidStateTransition を期待したが: %A" other

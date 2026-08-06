namespace CargoTracker.Billing.Domain

open System
open CargoTracker.Shared.Domain

// Billing コンテキストのドメイン層（US-ADM-01: 割引ポリシー / US21: 料金算出 / US22: 法人割引 / US23: 精算）。
// 金額は Money（最小通貨単位の int64 + 通貨コード・銀行家丸め）で表現し、丸め誤差を排除する。
// 支払い状態は PaymentState DU で各ケースに時刻を埋め込み「Confirmed なのに paidAt が null」を型排除する。
// イベントは BC ローカル DU（BillingEvent・ADR-0002）。

/// 通貨コード（国内は日本円。USD は多通貨対応の契約を明示し異通貨演算の不変条件を検証可能にする）。
type CurrencyCode =
    | JPY
    | USD

module CurrencyCode =
    let toString (c: CurrencyCode) : string =
        match c with
        | JPY -> "JPY"
        | USD -> "USD"

    let ofString (value: string) : Result<CurrencyCode, DomainError> =
        match value with
        | "JPY" -> Ok JPY
        | "USD" -> Ok USD
        | other -> Error(ValidationError("CurrencyCode", sprintf "未対応の通貨コードです: %s" other))

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

    /// 同一通貨のみ加算できる。
    let add (a: Money) (b: Money) : Result<Money, DomainError> =
        if a.Currency <> b.Currency then
            Error(BusinessRuleViolation("CurrencyMismatch", "通貨が異なる金額は加算できません。"))
        else
            Ok { a with Amount = a.Amount + b.Amount }

    /// 係数の乗算は最小通貨単位へ銀行家丸め（MidpointRounding.ToEven）で丸める。
    let multiply (factor: decimal) (m: Money) : Money =
        let raw = decimal m.Amount * factor
        let rounded = Math.Round(raw, MidpointRounding.ToEven)
        { m with Amount = int64 rounded }

/// 割引率：0〜30% の不変条件をスマートコンストラクタで保証（ビジネスルール 2）。
type DiscountRate = private DiscountRate of decimal

module DiscountRate =
    let create (value: decimal) : Result<DiscountRate, DomainError> =
        if value < 0.0m || value > 0.30m then
            Error(ValidationError("DiscountRate", "割引率は 0〜30% の範囲でなければなりません。"))
        else
            Ok(DiscountRate value)

    let zero = DiscountRate 0.0m
    let value (DiscountRate v) = v

/// 精算書 ID（単一ケース DU）。
type InvoiceId = private InvoiceId of string

module InvoiceId =
    let create (value: string) : Result<InvoiceId, DomainError> =
        if String.IsNullOrWhiteSpace value then
            Error(ValidationError("InvoiceId", "精算書 ID は空にできません。"))
        else
            Ok(InvoiceId value)

    /// 一意採番（IdGenerator ポート・ADR-0006）。INV- プレフィックス + Guid 短縮。
    let generate (newId: IdGenerator) : InvoiceId =
        let suffix = (newId ()).ToString("N").Substring(0, 12).ToUpperInvariant()
        InvoiceId(sprintf "INV-%s" suffix)

    let ofString (value: string) : InvoiceId = InvoiceId value
    let value (InvoiceId v) = v

/// Booking Context の Cargo との関連識別子（単一ケース DU・BC 分離）。
type BillingBookingId = private BillingBookingId of string

module BillingBookingId =
    let create (value: string) : Result<BillingBookingId, DomainError> =
        if String.IsNullOrWhiteSpace value then
            Error(ValidationError("BillingBookingId", "予約 ID は空にできません。"))
        else
            Ok(BillingBookingId value)

    let ofString (value: string) : BillingBookingId = BillingBookingId value
    let value (BillingBookingId v) = v

/// 荷主参照 ID（法人判定を内包・BC 分離のため Shipper を直接参照しない）。
type BillingShipperId =
    { ShipperId: string; IsCorporate: bool }

module BillingShipperId =
    let create (shipperId: string) (isCorporate: bool) : Result<BillingShipperId, DomainError> =
        if String.IsNullOrWhiteSpace shipperId then
            Error(ValidationError("BillingShipperId", "荷主 ID は空にできません。"))
        else
            Ok
                { ShipperId = shipperId
                  IsCorporate = isCorporate }

    let isCorporate (s: BillingShipperId) : bool = s.IsCorporate

/// 割引方針（法人・ボリューム・シーズン・なし）。
type DiscountPolicy =
    | CorporateStandard
    | VolumeDiscount
    | Seasonal
    | NoDiscount

module DiscountPolicy =
    let toString (p: DiscountPolicy) : string =
        match p with
        | CorporateStandard -> "CORPORATE_STANDARD"
        | VolumeDiscount -> "VOLUME_DISCOUNT"
        | Seasonal -> "SEASONAL"
        | NoDiscount -> "NO_DISCOUNT"

    let ofString (value: string) : Result<DiscountPolicy, DomainError> =
        match value with
        | "CORPORATE_STANDARD" -> Ok CorporateStandard
        | "VOLUME_DISCOUNT" -> Ok VolumeDiscount
        | "SEASONAL" -> Ok Seasonal
        | "NO_DISCOUNT" -> Ok NoDiscount
        | other -> Error(ValidationError("DiscountPolicy", sprintf "未知の割引方針です: %s" other))

    /// 割引率計算（純粋関数）。法人以外の CorporateStandard は割引なし。
    let calculateRate
        (shipper: BillingShipperId)
        (amount: Money)
        (policy: DiscountPolicy)
        : Result<DiscountRate, DomainError> =
        match policy with
        | NoDiscount -> DiscountRate.create 0.0m
        | CorporateStandard when BillingShipperId.isCorporate shipper -> DiscountRate.create 0.10m
        | CorporateStandard -> DiscountRate.create 0.0m
        | VolumeDiscount when amount.Amount >= 1_000_000L -> DiscountRate.create 0.15m
        | VolumeDiscount -> DiscountRate.create 0.05m
        | Seasonal -> DiscountRate.create 0.08m

/// 貨物種別（料金係数・BC 分離のため Booking の CargoType を直接参照しない）。
type CargoCategory =
    | General
    | Hazardous
    | Refrigerated

module CargoCategory =
    /// 貨物種別係数（domain-model 料金計算ロジック）。
    let factor (c: CargoCategory) : decimal =
        match c with
        | General -> 1.0m
        | Hazardous -> 1.8m
        | Refrigerated -> 1.5m

    let ofString (value: string) : Result<CargoCategory, DomainError> =
        match value with
        | "GENERAL" -> Ok General
        | "HAZARDOUS" -> Ok Hazardous
        | "REFRIGERATED" -> Ok Refrigerated
        | other -> Error(ValidationError("CargoCategory", sprintf "未知の貨物種別です: %s" other))

module Charge =
    /// 区間 1 本あたりの標準輸送距離（km）。確定経路の区間数から距離を導出する係数。
    /// 地理座標を持たないため、確定経路の leg 数を距離の代理指標とする（US21 距離自動導出）。
    let StandardLegDistanceKm = 500m

    /// 確定経路の区間数から輸送距離（km）を導出する（US21）。区間が無い（未確定）場合は 0km。
    let deriveDistance (legCount: int) : decimal =
        decimal (max 0 legCount) * StandardLegDistanceKm

    /// 距離係数（= 導出距離 × 1km あたり単価）。calculateBase の distanceFactor に渡す。
    let distanceFactorOf (legCount: int) (unitPricePerKm: decimal) : decimal =
        deriveDistance legCount * unitPricePerKm

    /// 基本料金 = 距離係数 × 重量（kg）× 貨物種別係数（domain-model 料金計算ロジック）。
    /// 最小通貨単位（円）へ銀行家丸めする。距離係数は 1km あたりの単価（円）。
    let calculateBase
        (distanceFactor: decimal)
        (weightKg: decimal)
        (category: CargoCategory)
        (currency: CurrencyCode)
        : Money =
        let raw = distanceFactor * weightKg * CargoCategory.factor category
        let rounded = Math.Round(raw, MidpointRounding.ToEven)

        { Amount = int64 rounded
          Currency = currency }

    /// 例外時の料金調整（減額・補償費用）を基本料金へ適用する（US21 受入6・IT8）。
    /// 調整額は基本料金から減額する。減額しすぎても 0 円を下回らない。
    let applyAdjustment (adjustment: int64) (baseAmount: Money) : Money =
        { baseAmount with
            Amount = max 0L (baseAmount.Amount - max 0L adjustment) }

/// 消費税（US22 消費税・付加料金・IT8）。割引後小計に対して課税する。
module ConsumptionTax =
    /// 日本の標準消費税率（10%）。
    let StandardRate = 0.10m

    /// 消費税額 = 割引後小計 × 税率（最小通貨単位へ銀行家丸め）。
    let calculate (rate: decimal) (subtotal: Money) : Money = subtotal |> Money.multiply rate

/// 割引ポリシーマスタ（US-ADM-01）。運用管理者が登録・変更・無効化する。
/// 割引方針（DiscountPolicy）に割引率・適用条件・有効期限・有効フラグを付与したマスタレコード。
type DiscountPolicyMaster =
    { Id: int64 option // 永続化前は None
      Policy: DiscountPolicy
      Rate: DiscountRate
      ApplicableCondition: string
      EffectiveFrom: DateOnly
      EffectiveTo: DateOnly option // 無期限は None
      Active: bool }

module DiscountPolicyMaster =

    /// 新規ポリシーを作成する（割引率は 0〜30% を DiscountRate で保証）。
    let create
        (policy: DiscountPolicy)
        (rate: DiscountRate)
        (condition: string)
        (effectiveFrom: DateOnly)
        (effectiveTo: DateOnly option)
        : DiscountPolicyMaster =
        { Id = None
          Policy = policy
          Rate = rate
          ApplicableCondition = condition
          EffectiveFrom = effectiveFrom
          EffectiveTo = effectiveTo
          Active = true }

    /// 指定日に有効か（active かつ有効期間内）。US22 の割引計算は有効なポリシーのみ使用する。
    let isEffectiveOn (date: DateOnly) (m: DiscountPolicyMaster) : bool =
        m.Active
        && date >= m.EffectiveFrom
        && (match m.EffectiveTo with
            | Some until -> date <= until
            | None -> true)

    /// 無効化する（US-ADM-01 受入 5）。無効化されたポリシーは割引計算に使われない。
    let deactivate (m: DiscountPolicyMaster) : DiscountPolicyMaster = { m with Active = false }

    /// 荷主・金額に適用する割引率をマスタから解決する（US22・IT8）。マスタの `discount_rate` を権威とする。
    /// 適用優先: 法人荷主は CorporateStandard、次に金額条件を満たす VolumeDiscount（100 万円以上）、
    /// 次に Seasonal。複数該当時は割引率が最大のポリシーを採用する。該当なしは 0%。
    /// masters は「有効な（isEffectiveOn を満たす）」ポリシーのみを渡す前提。
    let resolveApplicableRate (masters: DiscountPolicyMaster list) (isCorporate: bool) (amount: Money) : DiscountRate =
        let applicable =
            masters
            |> List.filter (fun m ->
                match m.Policy with
                | CorporateStandard -> isCorporate
                | VolumeDiscount -> amount.Amount >= 1_000_000L
                | Seasonal -> true
                | NoDiscount -> false)

        match applicable with
        | [] -> DiscountRate.zero
        | _ ->
            applicable
            |> List.maxBy (fun m -> DiscountRate.value m.Rate)
            |> fun m -> m.Rate

/// 支払い状態：各ケースに必要な時刻データを埋め込む。
type PaymentState =
    | Pending of dueDate: DateTimeOffset
    | Confirmed of paidAt: DateTimeOffset
    | Overdue of dueDate: DateTimeOffset
    | Refunded of refundedAt: DateTimeOffset

module PaymentState =
    let name (s: PaymentState) : string =
        match s with
        | Pending _ -> "Pending"
        | Confirmed _ -> "Confirmed"
        | Overdue _ -> "Overdue"
        | Refunded _ -> "Refunded"

/// 精算書（集約ルート）。基本料金・割引率・最終金額を保持する。
type Invoice =
    { InvoiceId: InvoiceId
      CargoBookingId: BillingBookingId
      ShipperId: BillingShipperId
      BaseAmount: Money
      DiscountRate: DiscountRate
      FinalAmount: Money // 割引後小計（税抜）
      TaxRate: decimal // 消費税率（IT8・US22）
      TaxAmount: Money // 消費税額（IT8・US22）
      IssuedAt: DateTimeOffset
      Payment: PaymentState }

/// Billing コンテキストのドメインイベント（BC ローカル DU・ADR-0002）。
type BillingEvent =
    | InvoiceCreated of InvoiceId * BillingBookingId * Money
    | PaymentConfirmed of InvoiceId * DateTimeOffset
    | PaymentOverdue of InvoiceId
    | PaymentRefunded of InvoiceId * DateTimeOffset

/// 精算書への操作コマンド。
type InvoiceCommand =
    | ConfirmPayment of paidAt: DateTimeOffset
    | MarkOverdue of now: DateTimeOffset
    | IssueRefund of refundedAt: DateTimeOffset

module InvoiceCommand =
    let name (c: InvoiceCommand) : string =
        match c with
        | ConfirmPayment _ -> "ConfirmPayment"
        | MarkOverdue _ -> "MarkOverdue"
        | IssueRefund _ -> "IssueRefund"

module Invoice =

    /// 請求総額 = 割引後小計（FinalAmount） + 消費税（TaxAmount）（IT8・US22）。
    let totalAmount (invoice: Invoice) : Money =
        match Money.add invoice.FinalAmount invoice.TaxAmount with
        | Ok m -> m
        | Error _ -> invoice.FinalAmount // 通貨不一致は発生しない（同一通貨で構築）

    /// 発行（割引率を直接指定）：割引適用と最終金額計算を合成。支払期限は発行日 + 30 日（ビジネスルール 3）。
    /// 割引ポリシーマスタ（US-ADM-01）の率を権威とする場合はこの関数に解決済み率を渡す（IT8・US22）。
    let generateWithRate
        (invoiceId: InvoiceId)
        (bookingId: BillingBookingId)
        (shipperId: BillingShipperId)
        (baseAmount: Money)
        (discountRate: DiscountRate)
        (issuedAt: DateTimeOffset)
        : Invoice * BillingEvent list =
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

        invoice, [ InvoiceCreated(invoiceId, bookingId, finalAmount) ]

    /// 発行（割引方針から率を導出）：`DiscountPolicy` DU のハードコード率で発行する（IT7 互換）。
    let generate
        (invoiceId: InvoiceId)
        (bookingId: BillingBookingId)
        (shipperId: BillingShipperId)
        (baseAmount: Money)
        (policy: DiscountPolicy)
        (issuedAt: DateTimeOffset)
        : Result<Invoice * BillingEvent list, DomainError> =
        match DiscountPolicy.calculateRate shipperId baseAmount policy with
        | Error e -> Error e
        | Ok discountRate -> Ok(generateWithRate invoiceId bookingId shipperId baseAmount discountRate issuedAt)

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

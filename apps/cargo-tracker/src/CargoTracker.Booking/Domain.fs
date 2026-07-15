namespace CargoTracker.Booking.Domain

open System
open CargoTracker.Shared.Domain

// Booking コンテキストのドメイン層（US04: 貨物予約 / US05: 危険物・冷凍 / US06: 経路設計依頼）。
// 不正状態を型で表現不能にする（Make Illegal States Unrepresentable）ことが設計の中心。
//
// IT2 スコープ: BookingState は Preliminary / RoutingRequested / Cancelled のみを実装する。
// RouteProposed 以降（Confirmed・TrackingIssued・InTransit・Delivered・Settled）は後続 IT で追加する。
// BookingAmount（Money）は Billing Context 実装時（IT4+）に Cargo へ追加する（段階導入）。

/// 予約の業務識別コード（BKG- プレフィックス + Guid 先頭 8 文字）。domain-model の `BookingId of string` に対応。
type BookingId = private BookingId of string

module BookingId =

    /// IdGenerator ポート（unit -> Guid）から決定的にコードを生成する（ADR-0006）。
    let generate (newId: IdGenerator) : BookingId =
        let head = (newId ()).ToString("N").Substring(0, 8).ToUpperInvariant()
        BookingId(sprintf "BKG-%s" head)

    /// 既存の文字列から復元する（永続化層用）。
    let ofString (value: string) : BookingId = BookingId value

    let value (BookingId v) = v

/// 貨物重量（kg）。正の値かつ 30,000 kg（コンテナ最大積載相当）以下のみを許容する。
/// US04 の重量入力要件に対応（設計レビュー #34: ドメイン未対応カラム cargo.weight の解消）。
type Weight = private Weight of decimal

module Weight =

    /// コンテナ最大積載相当の重量上限（kg）。
    let maxWeightKg = 30_000m

    let create (value: decimal) : Result<Weight, DomainError> =
        if value <= 0m then
            Error(ValidationError("Weight", "重量は正の値でなければなりません。"))
        elif value > maxWeightKg then
            Error(ValidationError("Weight", "重量は 30,000 kg（コンテナ最大積載相当）以下でなければなりません。"))
        else
            Ok(Weight value)

    let value (Weight v) = v

/// 危険物申告情報（US05）。危険物クラス・UN 番号・正式輸送品名を必須で持つ。
type HazardousDeclaration =
    private
        { HazardClass: string
          UnNumber: string
          ProperShippingName: string }

module HazardousDeclaration =

    let create
        (hazardClass: string)
        (unNumber: string)
        (properShippingName: string)
        : Result<HazardousDeclaration, DomainError> =
        if String.IsNullOrWhiteSpace hazardClass then
            Error(ValidationError("HazardClass", "危険物クラスは空にできません。"))
        elif String.IsNullOrWhiteSpace unNumber then
            Error(ValidationError("UnNumber", "UN 番号は空にできません。"))
        elif String.IsNullOrWhiteSpace properShippingName then
            Error(ValidationError("ProperShippingName", "正式輸送品名は空にできません。"))
        else
            Ok
                { HazardClass = hazardClass
                  UnNumber = unNumber
                  ProperShippingName = properShippingName }

    let hazardClass (d: HazardousDeclaration) = d.HazardClass
    let unNumber (d: HazardousDeclaration) = d.UnNumber
    let properShippingName (d: HazardousDeclaration) = d.ProperShippingName

/// 温度単位（US05）。
type TemperatureUnit =
    | Celsius
    | Fahrenheit

module TemperatureUnit =

    /// 永続化・DTO 用の文字列表現。DU ↔ 文字列変換はこの一箇所に集約する（DRY）。
    let toString (unit: TemperatureUnit) : string =
        match unit with
        | Celsius -> "CELSIUS"
        | Fahrenheit -> "FAHRENHEIT"

    /// 文字列から温度単位を復元する。`C`/`F` の短縮表記も許容する。
    let ofString (value: string) : Result<TemperatureUnit, DomainError> =
        match value.Trim().ToUpperInvariant() with
        | "CELSIUS"
        | "C" -> Ok Celsius
        | "FAHRENHEIT"
        | "F" -> Ok Fahrenheit
        | other -> Error(ValidationError("TemperatureUnit", sprintf "未知の温度単位です: %s" other))

/// 温度管理条件（US05）。最低温度 ≤ 最高温度の不変条件をスマートコンストラクタで保証する。
type TemperatureRequirement =
    private
        { MinTemperature: decimal
          MaxTemperature: decimal
          Unit: TemperatureUnit }

module TemperatureRequirement =

    let create
        (minTemperature: decimal)
        (maxTemperature: decimal)
        (unit: TemperatureUnit)
        : Result<TemperatureRequirement, DomainError> =
        if minTemperature > maxTemperature then
            Error(ValidationError("TemperatureRequirement", "最低温度は最高温度以下でなければなりません。"))
        else
            Ok
                { MinTemperature = minTemperature
                  MaxTemperature = maxTemperature
                  Unit = unit }

    let minTemperature (t: TemperatureRequirement) = t.MinTemperature
    let maxTemperature (t: TemperatureRequirement) = t.MaxTemperature
    let unit (t: TemperatureRequirement) = t.Unit

/// 貨物種別（US05）。危険物・冷凍は必要な追加情報を DU ケースに埋め込み、必須制約を型に昇格する。
type CargoType =
    | General
    | Hazardous of HazardousDeclaration
    | Refrigerated of TemperatureRequirement

/// ルート仕様（US04）。出発地・目的地・到着期限の要件。出発地 ≠ 目的地を保証する。
type RouteSpecification =
    private
        { Origin: Location
          Destination: Location
          ArrivalDeadline: DateOnly }

module RouteSpecification =

    let create
        (origin: Location)
        (destination: Location)
        (arrivalDeadline: DateOnly)
        : Result<RouteSpecification, DomainError> =
        if Location.sameAs origin destination then
            Error(BusinessRuleViolation("RouteSpecification", "出発地と目的地は異なる必要があります。"))
        else
            Ok
                { Origin = origin
                  Destination = destination
                  ArrivalDeadline = arrivalDeadline }

    let origin (r: RouteSpecification) = r.Origin
    let destination (r: RouteSpecification) = r.Destination
    let arrivalDeadline (r: RouteSpecification) = r.ArrivalDeadline

/// 荷受人（任意）。予約時点では未確定を許容する（consignee_* は将来カラム・IT4+）。
type Consignee =
    private
        { Name: string
          Address: string
          ContactEmail: string }

module Consignee =

    let create (name: string) (address: string) (contactEmail: string) : Result<Consignee, DomainError> =
        if String.IsNullOrWhiteSpace name then
            Error(ValidationError("ConsigneeName", "荷受人名は空にできません。"))
        else
            Ok
                { Name = name
                  Address = address
                  ContactEmail = contactEmail }

    let name (c: Consignee) = c.Name
    let address (c: Consignee) = c.Address
    let contactEmail (c: Consignee) = c.ContactEmail

/// 貨物個数（任意、1 以上）。
type Quantity = private Quantity of int

module Quantity =

    let create (value: int) : Result<Quantity, DomainError> =
        if value < 1 then
            Error(ValidationError("Quantity", "個数は 1 以上でなければなりません。"))
        else
            Ok(Quantity value)

    let value (Quantity v) = v

/// 品名（任意、最大 500 文字）。
type Description = private Description of string

module Description =

    let create (value: string) : Result<Description, DomainError> =
        if isNull value then
            Error(ValidationError("Description", "品名が null です。"))
        elif value.Length > 500 then
            Error(ValidationError("Description", "品名は 500 文字以内でなければなりません。"))
        else
            Ok(Description value)

    let value (Description v) = v

/// 予約状態（IT2 スコープ）。RouteProposed 以降は後続 IT で追加する。
type BookingState =
    | Preliminary
    | RoutingRequested
    | Cancelled of reason: string

module BookingState =

    /// 永続化のための文字列表現（cargo.booking_status）。
    let toString (state: BookingState) : string =
        match state with
        | Preliminary -> "PRELIMINARY"
        | RoutingRequested -> "ROUTING_REQUESTED"
        | Cancelled _ -> "CANCELLED"

    /// 永続化された文字列から状態を復元する（cargo.booking_status）。
    /// 【往復非対称の注意】`Cancelled reason` の reason は booking_status カラムに保持しないため、
    /// `toString`→`ofString` のラウンドトリップで理由は失われ空文字で復元される。
    /// キャンセル理由の永続化が必要になった時点で cancellation_reason カラムを追加する（IT4+）。
    let ofString (value: string) : Result<BookingState, DomainError> =
        match value with
        | "PRELIMINARY" -> Ok Preliminary
        | "ROUTING_REQUESTED" -> Ok RoutingRequested
        | "CANCELLED" -> Ok(Cancelled "")
        | other -> Error(ValidationError("BookingState", sprintf "未知の予約状態です: %s" other))

/// 集約への操作コマンド（IT2 スコープ）。
type BookingCommand =
    | SubmitForRouting
    | Cancel of reason: string

/// Booking コンテキストのドメインイベント。BC 固有イベントはローカル record とする（ADR-0002 の Shared 循環回避方針）。
type BookingEvent =
    | CargoBooked of BookingId * ShipperId
    | RoutingRequestedEvent of BookingId
    | BookingCancelled of BookingId * reason: string

/// 集約ルート。予約の中心。状態遷移・貨物仕様を統括する。
type Cargo =
    { BookingId: BookingId
      ShipperId: ShipperId
      Consignee: Consignee option
      RouteSpecification: RouteSpecification
      CargoType: CargoType
      Weight: Weight
      State: BookingState
      // 以下 3 つは domain-model の Cargo に定義された任意属性。IT2 では入力フォーム未対応のため
      // 常に None で生成する（書き込み経路は貨物明細の詳細化を行う後続 IT で追加する）。
      Dimensions: (decimal * decimal * decimal) option
      Quantity: Quantity option
      Description: Description option }

module Cargo =

    /// 新規予約（US04/US05）。BookCargo はコマンドではなくファクトリ関数として表現する。
    /// 検証済みの値オブジェクトを受け取り、集約と発行イベントを返す。
    let book
        (newId: IdGenerator)
        (shipperId: ShipperId)
        (consignee: Consignee option)
        (routeSpec: RouteSpecification)
        (cargoType: CargoType)
        (weight: Weight)
        : Result<Cargo * BookingEvent list, DomainError> =
        let bookingId = BookingId.generate newId

        let cargo =
            { BookingId = bookingId
              ShipperId = shipperId
              Consignee = consignee
              RouteSpecification = routeSpec
              CargoType = cargoType
              Weight = weight
              State = Preliminary
              Dimensions = None
              Quantity = None
              Description = None }

        Ok(cargo, [ CargoBooked(bookingId, shipperId) ])

    /// 状態遷移（US06）。網羅的パターンマッチにより許可されない遷移は InvalidStateTransition を返す。
    let execute (cargo: Cargo) (command: BookingCommand) : Result<Cargo * BookingEvent list, DomainError> =
        match cargo.State, command with
        | Preliminary, SubmitForRouting ->
            Ok({ cargo with State = RoutingRequested }, [ RoutingRequestedEvent cargo.BookingId ])

        // Cancelled からの Cancel は不正遷移。Preliminary / RoutingRequested からは Cancel 可能。
        | Cancelled _, Cancel _ -> Error(InvalidStateTransition(BookingState.toString cargo.State, "Cancel"))

        | _, Cancel reason -> Ok({ cargo with State = Cancelled reason }, [ BookingCancelled(cargo.BookingId, reason) ])

        | state, cmd -> Error(InvalidStateTransition(BookingState.toString state, sprintf "%A" cmd))

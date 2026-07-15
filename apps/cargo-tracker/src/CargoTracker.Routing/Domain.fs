namespace CargoTracker.Routing.Domain

open System
open CargoTracker.Shared.Domain

// Routing コンテキストのドメイン層（US24: 航海登録 / US25: 更新 / US07: 検索 / US08: 経路候補算出）。
// 中盤インサイドアウト: 値オブジェクト・不変条件をスマートコンストラクタと FsCheck で先に固める。
//
// VoyageNumber は Booking Context 固有型（Leg.Voyage 用）とは別の Routing Context 固有型（domain-model 型帰属方針）。

/// 航海番号（Routing Context 固有・単一ケース DU）。
type VoyageNumber = private VoyageNumber of string

module VoyageNumber =

    let create (value: string) : Result<VoyageNumber, DomainError> =
        if String.IsNullOrWhiteSpace value then
            Error(ValidationError("VoyageNumber", "航海番号は空にできません。"))
        elif value.Length > 20 then
            Error(ValidationError("VoyageNumber", "航海番号は 20 文字以内でなければなりません。"))
        else
            Ok(VoyageNumber value)

    let ofString (value: string) : VoyageNumber = VoyageNumber value
    let value (VoyageNumber v) = v

/// 船名（1〜100 文字）。
type VesselName = private VesselName of string

module VesselName =

    let create (value: string) : Result<VesselName, DomainError> =
        if String.IsNullOrWhiteSpace value then
            Error(ValidationError("VesselName", "船名は空にできません。"))
        elif value.Length > 100 then
            Error(ValidationError("VesselName", "船名は 100 文字以内でなければなりません。"))
        else
            Ok(VesselName value)

    let value (VesselName v) = v

/// 運送会社名（1〜100 文字）。
type CarrierName = private CarrierName of string

module CarrierName =

    let create (value: string) : Result<CarrierName, DomainError> =
        if String.IsNullOrWhiteSpace value then
            Error(ValidationError("CarrierName", "運送会社名は空にできません。"))
        elif value.Length > 100 then
            Error(ValidationError("CarrierName", "運送会社名は 100 文字以内でなければなりません。"))
        else
            Ok(CarrierName value)

    let value (CarrierName v) = v

/// 対応貨物種別タグ（Routing Context・航海が受け入れ可能な貨物種別）。
type CargoTypeTag =
    | General
    | Hazardous
    | Refrigerated

module CargoTypeTag =

    let toString (tag: CargoTypeTag) : string =
        match tag with
        | General -> "GENERAL"
        | Hazardous -> "HAZARDOUS"
        | Refrigerated -> "REFRIGERATED"

    let ofString (value: string) : Result<CargoTypeTag, DomainError> =
        match value.Trim().ToUpperInvariant() with
        | "GENERAL" -> Ok General
        | "HAZARDOUS" -> Ok Hazardous
        | "REFRIGERATED" -> Ok Refrigerated
        | other -> Error(ValidationError("CargoTypeTag", sprintf "未知の貨物種別です: %s" other))

/// 運送区間（出発港・到着港・出発日時・到着日時・順序）。
/// 出発港 ≠ 到着港・出発日時 < 到着日時をスマートコンストラクタで保証する。
type CarrierMovement =
    private
        { DepartureLocation: Location
          ArrivalLocation: Location
          DepartureDate: DateTimeOffset
          ArrivalDate: DateTimeOffset
          SeqNumber: int }

module CarrierMovement =

    let create
        (departureLocation: Location)
        (arrivalLocation: Location)
        (departureDate: DateTimeOffset)
        (arrivalDate: DateTimeOffset)
        (seqNumber: int)
        : Result<CarrierMovement, DomainError> =
        if Location.sameAs departureLocation arrivalLocation then
            Error(BusinessRuleViolation("CarrierMovement", "出発港と到着港は異なる必要があります。"))
        elif departureDate >= arrivalDate then
            Error(BusinessRuleViolation("CarrierMovement", "出発日時は到着日時より前でなければなりません。"))
        elif seqNumber < 1 then
            Error(ValidationError("SeqNumber", "区間順序は 1 以上でなければなりません。"))
        else
            Ok
                { DepartureLocation = departureLocation
                  ArrivalLocation = arrivalLocation
                  DepartureDate = departureDate
                  ArrivalDate = arrivalDate
                  SeqNumber = seqNumber }

    let departureLocation (m: CarrierMovement) = m.DepartureLocation
    let arrivalLocation (m: CarrierMovement) = m.ArrivalLocation
    let departureDate (m: CarrierMovement) = m.DepartureDate
    let arrivalDate (m: CarrierMovement) = m.ArrivalDate
    let seqNumber (m: CarrierMovement) = m.SeqNumber

/// 航海スケジュール（順序付き運送区間の非空列）。
/// 連結制約 `movement[n].到着港 = movement[n+1].出発港` と時系列（前区間到着 ≤ 次区間出発）を保証する。
type Schedule = private Schedule of CarrierMovement list

module Schedule =

    /// 運送区間列からスケジュールを構成する。非空・連結・時系列を検証する。
    let create (movements: CarrierMovement list) : Result<Schedule, DomainError> =
        match movements with
        | [] -> Error(ValidationError("Schedule", "航海スケジュールは 1 つ以上の運送区間が必要です。"))
        | _ ->
            let connectivityBroken =
                movements
                |> List.pairwise
                |> List.tryFind (fun (prev, next) -> not (Location.sameAs prev.ArrivalLocation next.DepartureLocation))

            let timelineBroken =
                movements
                |> List.pairwise
                |> List.tryFind (fun (prev, next) -> prev.ArrivalDate > next.DepartureDate)

            match connectivityBroken, timelineBroken with
            | Some _, _ ->
                Error(BusinessRuleViolation("ScheduleConnectivity", "運送区間が連結していません（前区間の到着港と次区間の出発港が一致しません）。"))
            | _, Some _ -> Error(BusinessRuleViolation("ScheduleTimeline", "運送区間の時系列が不正です（前区間の到着後に次区間が出発する必要があります）。"))
            | None, None -> Ok(Schedule movements)

    let movements (Schedule ms) = ms

    /// スケジュール全体の出発港（最初の区間の出発港）。
    let origin (Schedule ms) = (List.head ms).DepartureLocation

    /// スケジュール全体の到着港（最後の区間の到着港）。
    let destination (Schedule ms) = (List.last ms).ArrivalLocation

    /// スケジュール全体の出発日時（最初の区間の出発）。
    let departureDate (Schedule ms) = (List.head ms).DepartureDate

    /// スケジュール全体の到着日時（最後の区間の到着）。
    let arrivalDate (Schedule ms) = (List.last ms).ArrivalDate

/// 航海登録・更新イベント（US24/US25）。BC 固有イベントはローカル DU とする（ADR-0002）。
type VoyageEvent =
    | VoyageRegistered of VoyageNumber
    | VoyageScheduleUpdated of VoyageNumber

/// 集約ルート。特定の船舶が実施する一連の運送区間（スケジュール）と対応貨物種別を管理する。
type Voyage =
    { VoyageNumber: VoyageNumber
      Vessel: VesselName
      Carrier: CarrierName
      Schedule: Schedule
      SupportedCargoTypes: Set<CargoTypeTag> }

module Voyage =

    /// 航海を新規登録する（US24）。検証済みの値オブジェクトを受け取り、集約と発行イベントを返す。
    let register
        (voyageNumber: VoyageNumber)
        (vessel: VesselName)
        (carrier: CarrierName)
        (schedule: Schedule)
        (supportedCargoTypes: Set<CargoTypeTag>)
        : Result<Voyage * VoyageEvent list, DomainError> =
        if Set.isEmpty supportedCargoTypes then
            Error(ValidationError("SupportedCargoTypes", "対応貨物種別を 1 つ以上指定してください。"))
        else
            let voyage =
                { VoyageNumber = voyageNumber
                  Vessel = vessel
                  Carrier = carrier
                  Schedule = schedule
                  SupportedCargoTypes = supportedCargoTypes }

            Ok(voyage, [ VoyageRegistered voyageNumber ])

    /// スケジュール・付随情報を更新する（US25）。航海番号は不変。
    let update
        (vessel: VesselName)
        (carrier: CarrierName)
        (schedule: Schedule)
        (supportedCargoTypes: Set<CargoTypeTag>)
        (voyage: Voyage)
        : Result<Voyage * VoyageEvent list, DomainError> =
        if Set.isEmpty supportedCargoTypes then
            Error(ValidationError("SupportedCargoTypes", "対応貨物種別を 1 つ以上指定してください。"))
        else
            let updated =
                { voyage with
                    Vessel = vessel
                    Carrier = carrier
                    Schedule = schedule
                    SupportedCargoTypes = supportedCargoTypes }

            Ok(updated, [ VoyageScheduleUpdated voyage.VoyageNumber ])

    /// 指定の貨物種別に対応しているか。
    let supports (tag: CargoTypeTag) (voyage: Voyage) : bool =
        Set.contains tag voyage.SupportedCargoTypes

// ---- US08: 経路候補算出ドメインサービス（ADR-0009・自コンテキストの Voyage から構成）----

/// 経路探索の条件（出発地・目的地・貨物種別・到着期限）。
type RouteQuery =
    { Origin: Location
      Destination: Location
      CargoType: CargoTypeTag
      Deadline: DateTimeOffset }

/// 経路候補の 1 区間（どの航海で、どこからどこへ、いつ）。
type RouteLeg =
    { VoyageNumber: VoyageNumber
      From: Location
      To: Location
      Departure: DateTimeOffset
      Arrival: DateTimeOffset }

/// 経路候補（Routing 固有型・Estimation の RouteCandidate とは別概念・ADR-0009）。
type RouteCandidate =
    { Legs: RouteLeg list
      TransitPorts: Location list
      TransitDays: int
      EstimatedCost: decimal
      IsDirect: bool }

/// 経路候補算出ドメインサービス（US08）。純粋関数。
/// 登録済み Voyage 群を航海単位のエッジ（出発港→到着港）とみなし、出発地→目的地の接続経路を探索する。
module RouteComputation =

    /// 探索の最大乗継段数（発散防止・直行 + 最大 2 回乗継まで）。
    let private maxLegs = 3

    /// 費用の暫定ヒューリスティック（区間所要日数ベース）。正式な料金計算は Billing（IT7）で置換する。
    let private dailyRate = 12_000m

    /// Voyage を航海単位のエッジに変換する。
    let private toEdge (v: Voyage) : RouteLeg =
        { VoyageNumber = v.VoyageNumber
          From = Schedule.origin v.Schedule
          To = Schedule.destination v.Schedule
          Departure = Schedule.departureDate v.Schedule
          Arrival = Schedule.arrivalDate v.Schedule }

    /// 経路候補を推奨順に算出する。貨物種別対応・接続・期限を制約とし、直行優先→所要日数昇順で並べる。
    let computeCandidates (voyages: Voyage list) (query: RouteQuery) : RouteCandidate list =
        // 貨物種別に対応する航海のみをエッジ化する。
        let edges =
            voyages |> List.filter (Voyage.supports query.CargoType) |> List.map toEdge

        // 現在地から目的地までの経路を深さ優先で探索する（時刻連結・期限・訪問済み航海の重複回避）。
        let rec search
            (current: Location)
            (earliest: DateTimeOffset)
            (usedVoyages: Set<string>)
            (acc: RouteLeg list)
            : RouteLeg list list =
            if List.length acc >= maxLegs then
                []
            else
                edges
                |> List.filter (fun e ->
                    Location.sameAs e.From current
                    && e.Departure >= earliest
                    && not (Set.contains (VoyageNumber.value e.VoyageNumber) usedVoyages))
                |> List.collect (fun e ->
                    let legs = acc @ [ e ]

                    if Location.sameAs e.To query.Destination then
                        [ legs ]
                    else
                        search e.To e.Arrival (Set.add (VoyageNumber.value e.VoyageNumber) usedVoyages) legs)

        let toCandidate (legs: RouteLeg list) : RouteCandidate =
            let arrival = (List.last legs).Arrival
            let departure = (List.head legs).Departure
            let transitDays = int (arrival - departure).TotalDays
            // 経由港 = 最初の出発港と最後の到着港を除いた中間の到着港。
            let transitPorts =
                legs |> List.map (fun l -> l.To) |> List.rev |> List.tail |> List.rev

            { Legs = legs
              TransitPorts = transitPorts
              TransitDays = transitDays
              EstimatedCost = decimal transitDays * dailyRate
              IsDirect = List.length legs = 1 }

        search query.Origin DateTimeOffset.MinValue Set.empty []
        |> List.map toCandidate
        // 期限内に到達できる候補のみ。
        |> List.filter (fun c -> (List.last c.Legs).Arrival <= query.Deadline)
        // 直行を最優先、次に所要日数の短い順。
        |> List.sortBy (fun c -> (not c.IsDirect), c.TransitDays)

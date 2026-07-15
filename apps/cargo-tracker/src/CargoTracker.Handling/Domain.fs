namespace CargoTracker.Handling.Domain

open System
open CargoTracker.Shared.Domain

// Handling コンテキストのドメイン層（US15: 荷役記録 / US16: 引取記録）。
// 荷役妥当性検証（validateFor）をデシジョンテーブルの純粋関数として実装する（domain-model 準拠）。
// 通関（Customs）ゲートは次 IT。IT5 は Receive/Load/Unload/Claim を対象とする。

/// Booking Context との関連識別子（Handling 固有の単一ケース DU・BC 分離）。
type CargoBookingId = private CargoBookingId of string

module CargoBookingId =

    let create (value: string) : Result<CargoBookingId, DomainError> =
        if String.IsNullOrWhiteSpace value then
            Error(ValidationError("CargoBookingId", "予約 ID は空にできません。"))
        else
            Ok(CargoBookingId value)

    let ofString (value: string) : CargoBookingId = CargoBookingId value
    let value (CargoBookingId v) = v

/// Handling Context 固有の航海番号型（BC 分離）。
type VoyageNumber = private VoyageNumber of string

module VoyageNumber =

    let create (value: string) : Result<VoyageNumber, DomainError> =
        if String.IsNullOrWhiteSpace value then
            Error(ValidationError("VoyageNumber", "航海番号は空にできません。"))
        else
            Ok(VoyageNumber value)

    let ofString (value: string) : VoyageNumber = VoyageNumber value
    let value (VoyageNumber v) = v

/// 荷役種別。VoyageNumber 必須の種別にのみ航海番号を型で埋め込む（不正状態の排除）。
type HandlingType =
    | Receive
    | Load of VoyageNumber
    | Unload of VoyageNumber
    | Customs
    | Claim

module HandlingType =

    let toString (handlingType: HandlingType) : string =
        match handlingType with
        | Receive -> "RECEIVE"
        | Load _ -> "LOAD"
        | Unload _ -> "UNLOAD"
        | Customs -> "CUSTOMS"
        | Claim -> "CLAIM"

/// 荷役妥当性検証の結果（デシジョンテーブル）。
type ValidationOutcome =
    | Valid
    | Warning of message: string
    | Misrouted

/// ACL 経由で取得した貨物情報のスナップショット（妥当性検証に使用）。
type LegSnapshot =
    { LoadLocation: string
      UnloadLocation: string
      VoyageNumber: string }

type CargoSnapshot =
    { BookingId: string
      Origin: string
      Destination: string
      ItineraryLegs: LegSnapshot list }

/// 荷役作業（集約ルート）。荷役の登録と妥当性検証を担う。
type HandlingActivity =
    { CargoBookingId: CargoBookingId
      Type: HandlingType
      Location: Location
      CompletionTime: DateTimeOffset
      // US16: 引取（Claim）時のみ荷受人確認（署名または確認コード）を持つ。
      ConsigneeConfirmation: string option }

/// Handling コンテキストのドメインイベント（BC ローカル DU）。
type HandlingEvent =
    | HandlingActivityRegistered of CargoBookingId * handlingType: string
    | CargoMisrouted of CargoBookingId

module HandlingActivity =

    /// 荷役妥当性検証（デシジョンテーブルの純粋関数化）。
    let validateFor (snapshot: CargoSnapshot) (handlingType: HandlingType) (location: Location) : ValidationOutcome =
        let code = Location.value location

        match handlingType with
        | Receive ->
            if code = snapshot.Origin then
                Valid
            else
                Warning "受領場所が出発港と一致しません。"
        | Load voyage ->
            let matches =
                snapshot.ItineraryLegs
                |> List.exists (fun leg -> leg.LoadLocation = code && leg.VoyageNumber = VoyageNumber.value voyage)

            if matches then Valid else Misrouted
        | Unload voyage ->
            let matches =
                snapshot.ItineraryLegs
                |> List.exists (fun leg -> leg.UnloadLocation = code && leg.VoyageNumber = VoyageNumber.value voyage)

            if matches then Valid else Misrouted
        | Customs -> Valid
        | Claim ->
            if code = snapshot.Destination then
                Valid
            else
                Warning "引取場所が目的港と一致しません。"

    /// 荷役作業を登録する（US15/US16）。
    /// 引取（Claim）は荷受人確認が必須（US16 受入2）。Misrouted 時は CargoMisrouted も発行する。
    /// 戻り値に妥当性検証結果（Warning/Misrouted）を含め、UI で警告表示できるようにする（US15 受入7）。
    let register
        (snapshot: CargoSnapshot)
        (handlingType: HandlingType)
        (location: Location)
        (completionTime: DateTimeOffset)
        (consigneeConfirmation: string option)
        : Result<HandlingActivity * ValidationOutcome * HandlingEvent list, DomainError> =
        match handlingType, consigneeConfirmation with
        | Claim, None
        | Claim, Some "" -> Error(BusinessRuleViolation("ConsigneeConfirmationRequired", "引取には荷受人確認（署名または確認コード）が必要です。"))
        | _ ->
            let bookingId = CargoBookingId.ofString snapshot.BookingId
            let outcome = validateFor snapshot handlingType location

            let activity =
                { CargoBookingId = bookingId
                  Type = handlingType
                  Location = location
                  CompletionTime = completionTime
                  ConsigneeConfirmation = consigneeConfirmation }

            let baseEvents =
                [ HandlingActivityRegistered(bookingId, HandlingType.toString handlingType) ]

            let events =
                match outcome with
                | Misrouted -> baseEvents @ [ CargoMisrouted bookingId ]
                | _ -> baseEvents

            Ok(activity, outcome, events)

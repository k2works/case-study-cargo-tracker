namespace CargoTracker.Tracking.Domain

open System
open CargoTracker.Shared.Domain

// Tracking コンテキストのドメイン層（US14: 追跡番号発行 / US15-17: 状態遷移 / US18: 照会）。
// 状態は保持フィールドにせず、イベント履歴から currentStatus 関数で導出する（domain-model 準拠）。
// 例外（TrackingException）は IT6 で追加するため、IT5 は Exceptions を持たず非例外遷移のみ扱う。

/// 追跡番号（Tracking Context 固有の単一ケース DU・一意採番）。
type TrackingNumber = private TrackingNumber of string

module TrackingNumber =

    let create (value: string) : Result<TrackingNumber, DomainError> =
        if String.IsNullOrWhiteSpace value then
            Error(ValidationError("TrackingNumber", "追跡番号は空にできません。"))
        elif value.Length > 20 then
            Error(ValidationError("TrackingNumber", "追跡番号は 20 文字以内でなければなりません。"))
        else
            Ok(TrackingNumber value)

    /// 一意採番（IdGenerator ポートから生成・ADR-0006）。TRK- プレフィックス + Guid 短縮。
    let generate (newId: IdGenerator) : TrackingNumber =
        let suffix = (newId ()).ToString("N").Substring(0, 12).ToUpperInvariant()
        TrackingNumber(sprintf "TRK-%s" suffix)

    let ofString (value: string) : TrackingNumber = TrackingNumber value
    let value (TrackingNumber v) = v

/// Booking Context との関連を保持する Tracking 固有の予約参照 ID（単一ケース DU・BC 分離）。
type TrackingBookingId = private TrackingBookingId of string

module TrackingBookingId =

    let create (value: string) : Result<TrackingBookingId, DomainError> =
        if String.IsNullOrWhiteSpace value then
            Error(ValidationError("TrackingBookingId", "予約 ID は空にできません。"))
        else
            Ok(TrackingBookingId value)

    let ofString (value: string) : TrackingBookingId = TrackingBookingId value
    let value (TrackingBookingId v) = v

/// 追跡状態（9 ケース・イベント履歴からの導出値。Shared の TransportStatus とは別型）。
type TrackingStatus =
    | NotReceived
    | Received
    | Loaded
    | OnboardCarrier
    | Unloaded
    | AwaitingClaim
    | Claimed
    | InException
    | Unknown

/// 追跡イベントの種別。非例外の到達可能な状態に 1:1 で対応する（例外は IT6）。
/// domain-model の `TrackingEventType.toStatus` に対応。
type TrackingEventType =
    | ReceivedEvent
    | LoadedEvent
    | DepartedEvent // US17: 出港 → OnboardCarrier
    | UnloadedEvent
    | ArrivedEvent // US17: 入港 → AwaitingClaim
    | ClaimedEvent

module TrackingEventType =

    /// イベント種別から到達する追跡状態を導出する。
    let toStatus (eventType: TrackingEventType) : TrackingStatus =
        match eventType with
        | ReceivedEvent -> Received
        | LoadedEvent -> Loaded
        | DepartedEvent -> OnboardCarrier
        | UnloadedEvent -> Unloaded
        | ArrivedEvent -> AwaitingClaim
        | ClaimedEvent -> Claimed

    let toString (eventType: TrackingEventType) : string =
        match eventType with
        | ReceivedEvent -> "RECEIVED"
        | LoadedEvent -> "LOADED"
        | DepartedEvent -> "DEPARTED"
        | UnloadedEvent -> "UNLOADED"
        | ArrivedEvent -> "ARRIVED"
        | ClaimedEvent -> "CLAIMED"

    let ofString (value: string) : Result<TrackingEventType, DomainError> =
        match value with
        | "RECEIVED" -> Ok ReceivedEvent
        | "LOADED" -> Ok LoadedEvent
        | "DEPARTED" -> Ok DepartedEvent
        | "UNLOADED" -> Ok UnloadedEvent
        | "ARRIVED" -> Ok ArrivedEvent
        | "CLAIMED" -> Ok ClaimedEvent
        | other -> Error(ValidationError("TrackingEventType", sprintf "未知の追跡イベント種別です: %s" other))

/// 追跡イベント（時系列で記録される出来事。位置・時刻が必須）。
type TrackingActivityEvent =
    { EventType: TrackingEventType
      Location: Location
      CompletionTime: DateTimeOffset }

/// Tracking コンテキストのドメインイベント（BC ローカル DU・ADR-0002 の Shared 循環回避方針）。
type TrackingEvent =
    | TrackingNumberIssued of TrackingNumber * TrackingBookingId
    | TrackingEventRecorded of TrackingNumber * TrackingEventType

/// 集約ルート。追跡活動全体を管理する。状態は Events から導出（保持しない）。
type TrackingActivity =
    { TrackingNumber: TrackingNumber
      BookingId: TrackingBookingId
      Events: TrackingActivityEvent list } // 時系列（新しい順）

/// 集約への操作コマンド。
type TrackingCommand = RecordEvent of TrackingActivityEvent

module TrackingActivity =

    /// 追跡番号を発行し新規追跡活動を生成する（US14）。イベント空＝NotReceived を導出。
    let issue (newId: IdGenerator) (bookingId: TrackingBookingId) : TrackingActivity * TrackingEvent list =
        let trackingNumber = TrackingNumber.generate newId

        let activity =
            { TrackingNumber = trackingNumber
              BookingId = bookingId
              Events = [] }

        activity, [ TrackingNumberIssued(trackingNumber, bookingId) ]

    /// 現在の追跡状態：最新イベントから導出する純粋関数（例外は IT6・IT5 は非例外のみ）。
    let currentStatus (activity: TrackingActivity) : TrackingStatus =
        match activity.Events with
        | [] -> NotReceived
        | latest :: _ -> TrackingEventType.toStatus latest.EventType

    /// 追跡イベントを時系列（新しい順）に追加する。状態は導出のため自動更新される。
    let execute
        (activity: TrackingActivity)
        (command: TrackingCommand)
        : Result<TrackingActivity * TrackingEvent list, DomainError> =
        match command with
        | RecordEvent event ->
            let updated =
                { activity with
                    Events = event :: activity.Events }

            Ok(updated, [ TrackingEventRecorded(activity.TrackingNumber, event.EventType) ])

    /// TrackingStatus を Shared の TransportStatus へ写像する（アプリ層で Booking.Delivery へ同期）。
    /// ケースの追加・変更はコンパイルエラーとしてここに伝播する（BC 分離・網羅変換）。
    let toTransportStatus (status: TrackingStatus) : TransportStatus =
        match status with
        | NotReceived -> TransportStatus.NotReceived
        | Received -> TransportStatus.Received
        | Loaded -> TransportStatus.Loaded
        | OnboardCarrier -> TransportStatus.OnboardCarrier
        | Unloaded -> TransportStatus.Unloaded
        | AwaitingClaim -> TransportStatus.AwaitingClaim
        | Claimed -> TransportStatus.Claimed
        | InException -> TransportStatus.InException
        | Unknown -> TransportStatus.Unknown

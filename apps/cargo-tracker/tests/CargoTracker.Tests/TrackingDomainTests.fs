module CargoTracker.Tests.TrackingDomainTests

open System
open Xunit
open FsUnit.Xunit
open FsCheck
open FsCheck.Xunit
open CargoTracker.Shared.Domain
open CargoTracker.Tracking.Domain

// US14: 追跡番号発行 / US15-17: 状態遷移（導出値）/ 状態は Events から currentStatus で導出

let private fixedId (guid: Guid) : IdGenerator = fun () -> guid

let private loc code =
    match Location.create code with
    | Ok l -> l
    | Error e -> failwithf "テスト前提の Location 生成に失敗: %s" e

let private dto (y, m, d) =
    DateTimeOffset(y, m, d, 0, 0, 0, TimeSpan.Zero)

let private bookingId () =
    match TrackingBookingId.create "BKG-0001" with
    | Ok b -> b
    | Error e -> failwithf "%A" e

let private event etype code day =
    { EventType = etype
      Location = loc code
      CompletionTime = dto (2026, 9, day) }

// ---- TrackingNumber ----

[<Fact>]
let ``追跡番号は空なら Error を返す`` () =
    match TrackingNumber.create "" with
    | Error(ValidationError(field, _)) -> field |> should equal "TrackingNumber"
    | other -> failwithf "ValidationError を期待したが: %A" other

[<Fact>]
let ``追跡番号は TRK- プレフィックスで採番される`` () =
    let tn = TrackingNumber.generate (fixedId (Guid.NewGuid()))
    (TrackingNumber.value tn).StartsWith("TRK-") |> should equal true

[<Fact>]
let ``異なる Guid からは異なる追跡番号が採番される`` () =
    let a = TrackingNumber.generate (fixedId (Guid.NewGuid()))
    let b = TrackingNumber.generate (fixedId (Guid.NewGuid()))
    TrackingNumber.value a |> should not' (equal (TrackingNumber.value b))

// ---- 追跡番号発行（US14）----

[<Fact>]
let ``追跡番号発行で NotReceived の追跡活動が生成され TrackingNumberIssued を発行する`` () =
    let activity, events =
        TrackingActivity.issue (fixedId (Guid.NewGuid())) (bookingId ())

    TrackingActivity.currentStatus activity |> should equal NotReceived
    activity.Events |> should be Empty

    match events with
    | [ TrackingNumberIssued(_, bid) ] -> TrackingBookingId.value bid |> should equal "BKG-0001"
    | other -> failwithf "TrackingNumberIssued を期待したが: %A" other

// ---- 状態導出（US15-17）----

let private issued () =
    let activity, _ = TrackingActivity.issue (fixedId (Guid.NewGuid())) (bookingId ())
    activity

[<Fact>]
let ``イベント記録で状態が最新イベントから導出される`` () =
    let a0 = issued ()

    let a1 =
        match TrackingActivity.execute a0 (RecordEvent(event ReceivedEvent "JPTYO" 1)) with
        | Ok(a, _) -> a
        | Error e -> failwithf "%A" e

    TrackingActivity.currentStatus a1 |> should equal Received

    let a2 =
        match TrackingActivity.execute a1 (RecordEvent(event LoadedEvent "JPTYO" 2)) with
        | Ok(a, _) -> a
        | Error e -> failwithf "%A" e

    TrackingActivity.currentStatus a2 |> should equal Loaded

[<Fact>]
let ``引取イベントで Claimed が導出される（配送完了）`` () =
    let a0 = issued ()

    let claimed =
        [ event ReceivedEvent "JPTYO" 1
          event LoadedEvent "JPTYO" 2
          event UnloadedEvent "USLAX" 3
          event ClaimedEvent "USLAX" 4 ]
        |> List.fold
            (fun acc e ->
                match TrackingActivity.execute acc (RecordEvent e) with
                | Ok(a, _) -> a
                | Error err -> failwithf "%A" err)
            a0

    TrackingActivity.currentStatus claimed |> should equal Claimed

[<Fact>]
let ``イベント記録は TrackingEventRecorded を発行する`` () =
    let a0 = issued ()

    match TrackingActivity.execute a0 (RecordEvent(event ReceivedEvent "JPTYO" 1)) with
    | Ok(_, [ TrackingEventRecorded(_, et) ]) -> et |> should equal ReceivedEvent
    | other -> failwithf "TrackingEventRecorded を期待したが: %A" other

[<Property>]
let ``最新イベントの種別が現在状態を決定する（導出の一貫性）`` (etype: TrackingEventType) =
    let a0 = issued ()

    match TrackingActivity.execute a0 (RecordEvent(event etype "JPTYO" 1)) with
    | Ok(a, _) -> TrackingActivity.currentStatus a = TrackingEventType.toStatus etype
    | Error _ -> false

// ---- TransportStatus 変換（BC 分離）----

[<Fact>]
let ``TrackingStatus は Shared TransportStatus へ網羅変換される`` () =
    TrackingActivity.toTransportStatus Claimed
    |> should equal TransportStatus.Claimed

    TrackingActivity.toTransportStatus NotReceived
    |> should equal TransportStatus.NotReceived

[<Property>]
let ``TransportStatus は toString→ofString でラウンドトリップする`` (status: TransportStatus) =
    match TransportStatus.ofString (TransportStatus.toString status) with
    | Ok restored -> restored = status
    | Error _ -> false

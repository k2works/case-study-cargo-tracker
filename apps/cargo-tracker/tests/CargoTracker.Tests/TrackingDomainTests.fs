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

// ---- 追加: エラー/境界分岐（カバレッジ補強）----

[<Fact>]
let ``追跡番号は 20 文字超で Error`` () =
    match TrackingNumber.create (String.replicate 21 "A") with
    | Error(ValidationError("TrackingNumber", _)) -> ()
    | other -> failwithf "ValidationError を期待したが: %A" other

[<Fact>]
let ``予約参照 ID は空で Error`` () =
    match TrackingBookingId.create "" with
    | Error(ValidationError("TrackingBookingId", _)) -> ()
    | other -> failwithf "ValidationError を期待したが: %A" other

[<Fact>]
let ``TransportStatus は未知の値で Error`` () =
    match TransportStatus.ofString "NOPE" with
    | Error(ValidationError("TransportStatus", _)) -> ()
    | other -> failwithf "ValidationError を期待したが: %A" other

[<Fact>]
let ``TrackingEventType は未知の値で Error`` () =
    match TrackingEventType.ofString "NOPE" with
    | Error(ValidationError("TrackingEventType", _)) -> ()
    | other -> failwithf "ValidationError を期待したが: %A" other

[<Fact>]
let ``TrackingEventType の toString と ofString が往復する`` () =
    for et in
        [ ReceivedEvent
          LoadedEvent
          DepartedEvent
          UnloadedEvent
          ArrivedEvent
          ClaimedEvent ] do
        match TrackingEventType.ofString (TrackingEventType.toString et) with
        | Ok restored -> restored |> should equal et
        | Error e -> failwithf "%A" e

[<Fact>]
let ``出港・入港イベントは輸送中・引取待ちを導出する`` () =
    let a0 = issued ()

    let departed =
        match TrackingActivity.execute a0 (RecordEvent(event DepartedEvent "JPTYO" 1)) with
        | Ok(a, _) -> a
        | Error e -> failwithf "%A" e

    TrackingActivity.currentStatus departed |> should equal OnboardCarrier

    let arrived =
        match TrackingActivity.execute departed (RecordEvent(event ArrivedEvent "USLAX" 2)) with
        | Ok(a, _) -> a
        | Error e -> failwithf "%A" e

    TrackingActivity.currentStatus arrived |> should equal AwaitingClaim

[<Fact>]
let ``例外・不明状態も TransportStatus へ変換される`` () =
    TrackingActivity.toTransportStatus InException
    |> should equal TransportStatus.InException

    TrackingActivity.toTransportStatus Unknown
    |> should equal TransportStatus.Unknown

    TrackingActivity.toTransportStatus OnboardCarrier
    |> should equal TransportStatus.OnboardCarrier

    TrackingActivity.toTransportStatus Unloaded
    |> should equal TransportStatus.Unloaded

    TrackingActivity.toTransportStatus AwaitingClaim
    |> should equal TransportStatus.AwaitingClaim

// ---- 例外の登録・解決（US19/US20・IT6）----

let private registerEx exType code day desc activity =
    match TrackingActivity.execute activity (RegisterException(exType, loc code, dto (2026, 9, day), desc)) with
    | Ok(a, evts) -> a, evts
    | Error e -> failwithf "例外登録に失敗: %A" e

[<Fact>]
let ``例外を登録すると状態が InException を導出する（US19）`` () =
    let a0 = issued ()
    let a1, evts = registerEx Delay "USLAX" 1 "荒天による寄港遅延" a0

    TrackingActivity.currentStatus a1 |> should equal InException
    a1.Exceptions |> List.length |> should equal 1

    match evts with
    | [ TrackingExceptionDetected(_, Delay) ] -> ()
    | other -> failwithf "TrackingExceptionDetected(Delay) を期待したが: %A" other

[<Fact>]
let ``紛失例外は必ずエスカレーションされ ExceptionEscalated を発行する（US20・ルール3）`` () =
    let a0 = issued ()
    let a1, evts = registerEx Lost "USLAX" 1 "海上事故により紛失" a0

    match a1.Exceptions with
    | [ { Resolution = Unresolved escalated } ] -> escalated |> should equal true
    | other -> failwithf "Unresolved(true) を期待したが: %A" other

    evts
    |> List.exists (function
        | ExceptionEscalated(_, Lost) -> true
        | _ -> false)
    |> should equal true

[<Fact>]
let ``破損例外はエスカレーションしない（US20）`` () =
    let a0 = issued ()
    let a1, evts = registerEx Damage "USLAX" 1 "荷崩れによる破損" a0

    match a1.Exceptions with
    | [ { Resolution = Unresolved escalated } ] -> escalated |> should equal false
    | other -> failwithf "Unresolved(false) を期待したが: %A" other

    evts
    |> List.exists (function
        | ExceptionEscalated _ -> true
        | _ -> false)
    |> should equal false

[<Fact>]
let ``例外を解決すると状態が例外発生前へ導出復帰する（ルール5）`` () =
    let a0 = issued ()

    // 積込済みまで進めてから例外発生
    let loaded =
        [ ReceivedEvent, "JPTYO", 1; LoadedEvent, "JPTYO", 2 ]
        |> List.fold
            (fun acc (e, c, d) ->
                match TrackingActivity.execute acc (RecordEvent(event e c d)) with
                | Ok(a, _) -> a
                | Error err -> failwithf "%A" err)
            a0

    let inEx, _ = registerEx Delay "USLAX" 3 "遅延" loaded
    TrackingActivity.currentStatus inEx |> should equal InException

    let resolved =
        match TrackingActivity.execute inEx (ResolveException(0, dto (2026, 9, 4), "対応完了")) with
        | Ok(a, evts) ->
            evts
            |> List.exists (function
                | TrackingExceptionResolved(_, Delay) -> true
                | _ -> false)
            |> should equal true

            a
        | Error e -> failwithf "%A" e

    // 例外発生前の状態（Loaded）へ復帰する
    TrackingActivity.currentStatus resolved |> should equal Loaded

[<Fact>]
let ``解決済み例外の再解決は AlreadyResolved で拒否する`` () =
    let a0 = issued ()
    let inEx, _ = registerEx Delay "USLAX" 1 "遅延" a0

    let resolved =
        match TrackingActivity.execute inEx (ResolveException(0, dto (2026, 9, 2), "一次対応")) with
        | Ok(a, _) -> a
        | Error e -> failwithf "%A" e

    match TrackingActivity.execute resolved (ResolveException(0, dto (2026, 9, 3), "再対応")) with
    | Error(BusinessRuleViolation(rule, _)) -> rule |> should equal "AlreadyResolved"
    | other -> failwithf "AlreadyResolved を期待したが: %A" other

[<Fact>]
let ``存在しない例外の解決は NotFound を返す`` () =
    let a0 = issued ()

    match TrackingActivity.execute a0 (ResolveException(5, dto (2026, 9, 1), "無効")) with
    | Error(NotFound(entity, _)) -> entity |> should equal "TrackingException"
    | other -> failwithf "NotFound を期待したが: %A" other

[<Property>]
let ``例外種別の toString/ofString は往復する`` (exType: ExceptionType) =
    match ExceptionType.ofString (ExceptionType.toString exType) with
    | Ok back -> back = exType
    | Error _ -> false

[<Fact>]
let ``未解決例外が残る限り InException を優先導出する（複数例外）`` () =
    let a0 = issued ()
    let a1, _ = registerEx Delay "USLAX" 1 "遅延1" a0
    let a2, _ = registerEx Damage "USLAX" 2 "破損1" a1

    // 1 件だけ解決してもまだ未解決が残るため InException
    let partial =
        match TrackingActivity.execute a2 (ResolveException(0, dto (2026, 9, 3), "再対応")) with
        | Ok(a, _) -> a
        | Error e -> failwithf "%A" e

    TrackingActivity.currentStatus partial |> should equal InException

[<Fact>]
let ``通関保留例外は InException を導出しエスカレーションしない（CustomsHold）`` () =
    let a0 = issued ()
    let a1, evts = registerEx CustomsHold "USLAX" 1 "税関検査による保留" a0

    TrackingActivity.currentStatus a1 |> should equal InException

    match a1.Exceptions with
    | [ { ExceptionType = CustomsHold
          Resolution = Unresolved escalated } ] -> escalated |> should equal false
    | other -> failwithf "Unresolved(false) の CustomsHold を期待したが: %A" other

    // CustomsHold は TrackingExceptionDetected を発行しエスカレーションは発行しない
    evts
    |> List.exists (function
        | TrackingExceptionDetected(_, CustomsHold) -> true
        | _ -> false)
    |> should equal true

    evts
    |> List.exists (function
        | ExceptionEscalated _ -> true
        | _ -> false)
    |> should equal false

[<Fact>]
let ``例外解決で対応内容（ResolutionNote）が記録される（US19 対応報告）`` () =
    let a0 = issued ()
    let a1, _ = registerEx Delay "USLAX" 1 "遅延" a0

    match TrackingActivity.execute a1 (ResolveException(0, dto (2026, 9, 2), "新到着予定日 9/10 を提示")) with
    | Ok(a, _) ->
        match a.Exceptions with
        | [ { ResolutionNote = Some note } ] -> note |> should equal "新到着予定日 9/10 を提示"
        | other -> failwithf "ResolutionNote=Some を期待したが: %A" other
    | Error e -> failwithf "%A" e

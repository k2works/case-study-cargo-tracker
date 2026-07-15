module CargoTracker.Tests.BookingApplicationTests

open System
open Xunit
open FsUnit.Xunit
open CargoTracker.Shared.Domain
open CargoTracker.Booking.Domain
open CargoTracker.Booking.Application

// US04: 貨物予約を登録する / US05: 危険物・冷凍 / US06: 経路設計依頼（アプリケーション層）

let fixedId (guid: Guid) : IdGenerator = fun () -> guid

/// 保存された Cargo を記録するインメモリリポジトリスタブ。
let private repoStub () =
    let store = System.Collections.Generic.Dictionary<string, Cargo>()

    let repo =
        { Save =
            fun cargo ->
                async {
                    store[BookingId.value cargo.BookingId] <- cargo
                    return Ok()
                }
          Update =
            fun cargo ->
                async {
                    store[BookingId.value cargo.BookingId] <- cargo
                    return Ok()
                }
          FindById =
            fun bookingId ->
                async {
                    match store.TryGetValue(BookingId.value bookingId) with
                    | true, c -> return Ok(Some c)
                    | false, _ -> return Ok None
                } }

    repo, store

let private existingShipper = { Exists = fun _ -> async { return Ok true } }

let private missingShipper = { Exists = fun _ -> async { return Ok false } }

let private notifierStub (calls: System.Collections.Generic.List<BookingId>) : RoutingRequestNotifier =
    { Notify =
        fun bid ->
            async {
                calls.Add bid
                return Ok()
            } }

let private baseCommand () : BookCargoCommand =
    { ShipperId = (Guid.NewGuid()).ToString()
      OriginUnlocode = "JPTYO"
      DestinationUnlocode = "USLAX"
      ArrivalDeadline = DateOnly(2026, 9, 1)
      CargoType = GeneralInput
      WeightKg = 500m
      Consignee = None }

[<Fact>]
let ``荷主が存在すれば予約を登録し保存する`` () =
    let repo, store = repoStub ()

    let result =
        BookCargo.book repo existingShipper (fixedId (Guid.NewGuid())) (baseCommand ())
        |> Async.RunSynchronously

    match result with
    | Ok(cargo, events) ->
        cargo.State |> should equal Preliminary
        store.ContainsKey(BookingId.value cargo.BookingId) |> should equal true

        match events with
        | [ CargoBooked _ ] -> ()
        | other -> failwithf "CargoBooked を期待したが: %A" other
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``荷主が存在しなければ NotFound で保存しない`` () =
    let repo, store = repoStub ()

    let result =
        BookCargo.book repo missingShipper (fixedId (Guid.NewGuid())) (baseCommand ())
        |> Async.RunSynchronously

    match result with
    | Error(NotFound(entity, _)) ->
        entity |> should equal "Shipper"
        store.Count |> should equal 0
    | other -> failwithf "NotFound を期待したが: %A" other

[<Fact>]
let ``出発地と目的地が同一なら予約できない`` () =
    let repo, _ = repoStub ()

    let cmd =
        { baseCommand () with
            DestinationUnlocode = "JPTYO" }

    let result =
        BookCargo.book repo existingShipper (fixedId (Guid.NewGuid())) cmd
        |> Async.RunSynchronously

    match result with
    | Error(BusinessRuleViolation(rule, _)) -> rule |> should equal "RouteSpecification"
    | other -> failwithf "BusinessRuleViolation を期待したが: %A" other

[<Fact>]
let ``危険物の必須情報が欠けると予約できない`` () =
    let repo, _ = repoStub ()

    let cmd =
        { baseCommand () with
            CargoType = HazardousInput("", "UN1203", "Gasoline") }

    let result =
        BookCargo.book repo existingShipper (fixedId (Guid.NewGuid())) cmd
        |> Async.RunSynchronously

    match result with
    | Error(ValidationError(field, _)) -> field |> should equal "HazardClass"
    | other -> failwithf "ValidationError を期待したが: %A" other

[<Fact>]
let ``冷凍貨物は温度条件込みで予約できる`` () =
    let repo, _ = repoStub ()

    let cmd =
        { baseCommand () with
            CargoType = RefrigeratedInput(-20m, 5m, "CELSIUS") }

    let result =
        BookCargo.book repo existingShipper (fixedId (Guid.NewGuid())) cmd
        |> Async.RunSynchronously

    match result with
    | Ok(cargo, _) ->
        match cargo.CargoType with
        | Refrigerated _ -> ()
        | other -> failwithf "Refrigerated を期待したが: %A" other
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``経路設計依頼で RoutingRequested に遷移し保存・通知する`` () =
    let repo, store = repoStub ()
    let calls = System.Collections.Generic.List<BookingId>()

    let bookResult =
        BookCargo.book repo existingShipper (fixedId (Guid.NewGuid())) (baseCommand ())
        |> Async.RunSynchronously

    match bookResult with
    | Ok(cargo, _) ->
        let result =
            BookCargo.submitForRouting repo (notifierStub calls) cargo.BookingId
            |> Async.RunSynchronously

        match result with
        | Ok(updated, events) ->
            updated.State |> should equal RoutingRequested
            store[BookingId.value cargo.BookingId].State |> should equal RoutingRequested
            calls.Count |> should equal 1

            match events with
            | [ RoutingRequestedEvent _ ] -> ()
            | other -> failwithf "RoutingRequestedEvent を期待したが: %A" other
        | Error e -> failwithf "Ok を期待したが Error: %A" e
    | Error e -> failwithf "book で Error: %A" e

[<Fact>]
let ``存在しない予約の経路設計依頼は NotFound`` () =
    let repo, _ = repoStub ()
    let calls = System.Collections.Generic.List<BookingId>()

    let result =
        BookCargo.submitForRouting repo (notifierStub calls) (BookingId.ofString "BKG-NOPE")
        |> Async.RunSynchronously

    match result with
    | Error(NotFound(entity, _)) -> entity |> should equal "Cargo"
    | other -> failwithf "NotFound を期待したが: %A" other

// US09/US11/US13: 経路確定〜予約確定ワークフロー（RouteAssignment）。

let private loc code =
    match Location.create code with
    | Ok l -> l
    | Error e -> failwithf "%A" e

/// 発火されたイベントを記録するディスパッチャスタブ。
let private dispatcherStub (recorded: System.Collections.Generic.List<BookingEvent>) =
    { Dispatch =
        fun e ->
            async {
                recorded.Add e
                return ()
            } }

let private nullDispatcher = NullBookingEventDispatcher.create ()

/// JPTYO→USLAX・期限 2026-09-01 を満たす直行旅程。
let private satisfyingItinerary () =
    let leg =
        match
            Leg.create
                (loc "JPTYO")
                (loc "USLAX")
                (DateTimeOffset(2026, 8, 1, 0, 0, 0, TimeSpan.Zero))
                (DateTimeOffset(2026, 8, 14, 0, 0, 0, TimeSpan.Zero))
                (VoyageNumber.ofString "V001")
        with
        | Ok l -> l
        | Error e -> failwithf "%A" e

    match CargoItinerary.create [ leg ] with
    | Ok i -> i
    | Error e -> failwithf "%A" e

/// 経路設計中（RoutingRequested）の予約を repo に用意して BookingId を返す。
let private seedRoutingRequested repo =
    let cargo, _ =
        match
            BookCargo.book repo existingShipper (fixedId (Guid.NewGuid())) (baseCommand ())
            |> Async.RunSynchronously
        with
        | Ok r -> r
        | Error e -> failwithf "%A" e

    let calls = System.Collections.Generic.List<BookingId>()

    BookCargo.submitForRouting repo (notifierStub calls) cargo.BookingId
    |> Async.RunSynchronously
    |> ignore

    cargo.BookingId

[<Fact>]
let ``proposeRoute で経路設計中の予約が RouteProposed になる`` () =
    let repo, _ = repoStub ()
    let bid = seedRoutingRequested repo

    match
        RouteAssignment.proposeRoute repo nullDispatcher bid (satisfyingItinerary ())
        |> Async.RunSynchronously
    with
    | Ok(cargo, events) ->
        match cargo.State with
        | RouteProposed _ -> ()
        | other -> failwithf "RouteProposed を期待したが: %A" other

        events |> should contain (CargoRouted bid)
    | Error e -> failwithf "成功を期待したが: %A" e

[<Fact>]
let ``confirmBooking で RouteProposed の予約が Confirmed になる`` () =
    let repo, _ = repoStub ()
    let bid = seedRoutingRequested repo

    RouteAssignment.proposeRoute repo nullDispatcher bid (satisfyingItinerary ())
    |> Async.RunSynchronously
    |> ignore

    match RouteAssignment.confirmBooking repo nullDispatcher bid |> Async.RunSynchronously with
    | Ok(cargo, events) ->
        match cargo.State with
        | Confirmed _ -> ()
        | other -> failwithf "Confirmed を期待したが: %A" other

        events |> should contain (BookingConfirmed bid)
    | Error e -> failwithf "成功を期待したが: %A" e

[<Fact>]
let ``restoreToRouting で Confirmed の予約が RoutingRequested に差し戻される`` () =
    let repo, _ = repoStub ()
    let bid = seedRoutingRequested repo

    RouteAssignment.proposeRoute repo nullDispatcher bid (satisfyingItinerary ())
    |> Async.RunSynchronously
    |> ignore

    RouteAssignment.confirmBooking repo nullDispatcher bid
    |> Async.RunSynchronously
    |> ignore

    match
        RouteAssignment.restoreToRouting repo nullDispatcher bid
        |> Async.RunSynchronously
    with
    | Ok(cargo, events) ->
        cargo.State |> should equal RoutingRequested
        events |> should contain (BookingRestoredToRouting bid)
    | Error e -> failwithf "成功を期待したが: %A" e

[<Fact>]
let ``存在しない予約への proposeRoute は NotFound を返す`` () =
    let repo, _ = repoStub ()

    match
        RouteAssignment.proposeRoute repo nullDispatcher (BookingId.ofString "BKG-NOPE") (satisfyingItinerary ())
        |> Async.RunSynchronously
    with
    | Error(NotFound(entity, _)) -> entity |> should equal "Cargo"
    | other -> failwithf "NotFound を期待したが: %A" other

[<Fact>]
let ``proposeRoute 成功時はイベントが post-commit で発火される`` () =
    let repo, _ = repoStub ()
    let bid = seedRoutingRequested repo
    let recorded = System.Collections.Generic.List<BookingEvent>()

    RouteAssignment.proposeRoute repo (dispatcherStub recorded) bid (satisfyingItinerary ())
    |> Async.RunSynchronously
    |> ignore

    recorded |> List.ofSeq |> should contain (CargoRouted bid)

[<Fact>]
let ``失敗時（NotFound）はイベントを発火しない`` () =
    let repo, _ = repoStub ()
    let recorded = System.Collections.Generic.List<BookingEvent>()

    RouteAssignment.proposeRoute repo (dispatcherStub recorded) (BookingId.ofString "BKG-NOPE") (satisfyingItinerary ())
    |> Async.RunSynchronously
    |> ignore

    recorded.Count |> should equal 0

// US12: 荷主通知ワークフロー。

let private shipperNotifierStub (recorded: System.Collections.Generic.List<string>) : ShipperNotifier =
    { Notify =
        fun _ recipient message ->
            async {
                recorded.Add(sprintf "%s|%s" recipient message)
                return Ok()
            } }

[<Fact>]
let ``notifyRouteToShipper は確定経路がある予約を通知する`` () =
    let repo, _ = repoStub ()
    let bid = seedRoutingRequested repo

    RouteAssignment.proposeRoute repo nullDispatcher bid (satisfyingItinerary ())
    |> Async.RunSynchronously
    |> ignore

    let recorded = System.Collections.Generic.List<string>()

    match
        RouteAssignment.notifyRouteToShipper repo (shipperNotifierStub recorded) bid
        |> Async.RunSynchronously
    with
    | Ok() -> recorded.Count |> should equal 1
    | Error e -> failwithf "成功を期待したが: %A" e

[<Fact>]
let ``確定経路が無い予約への通知は業務ルール違反`` () =
    let repo, _ = repoStub ()
    let bid = seedRoutingRequested repo // RoutingRequested（旅程なし）
    let recorded = System.Collections.Generic.List<string>()

    match
        RouteAssignment.notifyRouteToShipper repo (shipperNotifierStub recorded) bid
        |> Async.RunSynchronously
    with
    | Error(BusinessRuleViolation(rule, _)) -> rule |> should equal "ShipperNotification"
    | other -> failwithf "BusinessRuleViolation を期待したが: %A" other

[<Fact>]
let ``ドメイン検証エラー時（不正遷移）はイベントを発火しない`` () =
    // Preliminary（RoutingRequested 未満）へ proposeRoute → 不正遷移でイベント未発火。
    let repo, store = repoStub ()

    let cargo, _ =
        match
            BookCargo.book repo existingShipper (fixedId (Guid.NewGuid())) (baseCommand ())
            |> Async.RunSynchronously
        with
        | Ok r -> r
        | Error e -> failwithf "%A" e

    let recorded = System.Collections.Generic.List<BookingEvent>()

    match
        RouteAssignment.proposeRoute repo (dispatcherStub recorded) cargo.BookingId (satisfyingItinerary ())
        |> Async.RunSynchronously
    with
    | Error(InvalidStateTransition _) -> recorded.Count |> should equal 0
    | other -> failwithf "InvalidStateTransition を期待したが: %A" other

    ignore store

[<Fact>]
let ``永続化失敗時はイベントを発火しない`` () =
    // Update が必ず失敗する repo。RoutingRequested の予約に対して proposeRoute。
    let store = System.Collections.Generic.Dictionary<string, Cargo>()

    let baseRepo, _ = repoStub ()

    let cargo, _ =
        match
            BookCargo.book baseRepo existingShipper (fixedId (Guid.NewGuid())) (baseCommand ())
            |> Async.RunSynchronously
        with
        | Ok r -> r
        | Error e -> failwithf "%A" e

    let routing =
        match Cargo.execute cargo SubmitForRouting with
        | Ok(c, _) -> c
        | Error e -> failwithf "%A" e

    store[BookingId.value routing.BookingId] <- routing

    let failingRepo =
        { Save = fun _ -> async { return Ok() }
          Update = fun _ -> async { return Error(BusinessRuleViolation("CargoRepository", "書き込み失敗")) }
          FindById =
            fun bid ->
                async {
                    match store.TryGetValue(BookingId.value bid) with
                    | true, c -> return Ok(Some c)
                    | false, _ -> return Ok None
                } }

    let recorded = System.Collections.Generic.List<BookingEvent>()

    match
        RouteAssignment.proposeRoute failingRepo (dispatcherStub recorded) routing.BookingId (satisfyingItinerary ())
        |> Async.RunSynchronously
    with
    | Error(BusinessRuleViolation("CargoRepository", _)) -> recorded.Count |> should equal 0
    | other -> failwithf "永続化失敗を期待したが: %A" other

[<Fact>]
let ``cancelAndNotify はキャンセル後に荷主へ通知する（US13 受入基準6）`` () =
    let repo, _ = repoStub ()
    let bid = seedRoutingRequested repo
    let recorded = System.Collections.Generic.List<string>()
    let events = System.Collections.Generic.List<BookingEvent>()

    match
        RouteAssignment.cancelAndNotify repo (dispatcherStub events) (shipperNotifierStub recorded) bid "荷主都合"
        |> Async.RunSynchronously
    with
    | Ok(cargo, _) ->
        cargo.State |> should equal (Cancelled "荷主都合")
        recorded.Count |> should equal 1
        (recorded.[0]) |> should haveSubstring "キャンセル"
    | Error e -> failwithf "成功を期待したが: %A" e

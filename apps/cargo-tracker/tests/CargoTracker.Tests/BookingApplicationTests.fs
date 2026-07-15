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

let private notifierStub (calls: System.Collections.Generic.List<BookingId>) =
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

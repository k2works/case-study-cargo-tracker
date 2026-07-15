module CargoTracker.Tests.BookingDomainTests

open System
open Xunit
open FsUnit.Xunit
open FsCheck
open FsCheck.Xunit
open CargoTracker.Shared.Domain
open CargoTracker.Booking.Domain

// US04: 貨物予約を登録する / US05: 危険物・冷凍 / US06: 経路設計依頼

/// テスト用の固定 IdGenerator（ADR-0006 の注入ポートを決定的にする）。
let fixedId (guid: Guid) : IdGenerator = fun () -> guid

let private loc code =
    match Location.create code with
    | Ok l -> l
    | Error e -> failwithf "テスト前提の Location 生成に失敗: %s" e

let private routeSpec () =
    match RouteSpecification.create (loc "JPTYO") (loc "USLAX") (DateOnly(2026, 9, 1)) with
    | Ok r -> r
    | Error e -> failwithf "テスト前提の RouteSpecification 生成に失敗: %A" e

let private weight kg =
    match Weight.create kg with
    | Ok w -> w
    | Error e -> failwithf "テスト前提の Weight 生成に失敗: %A" e

let private shipperId () = ShipperId.ofGuid (Guid.NewGuid())

// ---- Weight（US04 重量要件・レビュー #34 対応）----

[<Fact>]
let ``重量は正の値なら Ok を返す`` () =
    match Weight.create 1200.5m with
    | Ok w -> Weight.value w |> should equal 1200.5m
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``重量が 0 以下なら Error を返す`` () =
    match Weight.create 0m with
    | Error(ValidationError(field, _)) -> field |> should equal "Weight"
    | other -> failwithf "ValidationError を期待したが: %A" other

[<Fact>]
let ``重量が上限を超えると Error を返す`` () =
    match Weight.create 30_001m with
    | Error(ValidationError _) -> ()
    | other -> failwithf "ValidationError を期待したが: %A" other

[<Property>]
let ``0 より大きく上限以下の重量は常に Ok になる`` (NormalFloat f) =
    let kg = decimal (abs f % 30_000.0) + 0.001m

    match Weight.create kg with
    | Ok _ -> true
    | Error _ -> false

// ---- RouteSpecification（US04 出発地 ≠ 目的地）----

[<Fact>]
let ``出発地と目的地が異なれば Ok を返す`` () =
    match RouteSpecification.create (loc "JPTYO") (loc "USLAX") (DateOnly(2026, 9, 1)) with
    | Ok _ -> ()
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``出発地と目的地が同一なら Error を返す`` () =
    match RouteSpecification.create (loc "JPTYO") (loc "JPTYO") (DateOnly(2026, 9, 1)) with
    | Error(BusinessRuleViolation(rule, _)) -> rule |> should equal "RouteSpecification"
    | other -> failwithf "BusinessRuleViolation を期待したが: %A" other

// ---- CargoType（US05 危険物・冷凍）----

[<Fact>]
let ``危険物申告は全項目が揃えば Ok を返す`` () =
    match HazardousDeclaration.create "3" "UN1203" "Gasoline" with
    | Ok _ -> ()
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``危険物クラスが空なら Error を返す`` () =
    match HazardousDeclaration.create "" "UN1203" "Gasoline" with
    | Error(ValidationError(field, _)) -> field |> should equal "HazardClass"
    | other -> failwithf "ValidationError を期待したが: %A" other

[<Fact>]
let ``温度管理条件は最低温度が最高温度以下なら Ok を返す`` () =
    match TemperatureRequirement.create -20m 5m Celsius with
    | Ok _ -> ()
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``温度管理条件は最低温度が最高温度を超えると Error を返す`` () =
    match TemperatureRequirement.create 10m 5m Celsius with
    | Error(ValidationError(field, _)) -> field |> should equal "TemperatureRequirement"
    | other -> failwithf "ValidationError を期待したが: %A" other

// ---- Cargo.book（US04/US05）----

[<Fact>]
let ``貨物予約を登録すると Preliminary 状態で CargoBooked イベントを発行する`` () =
    let guid = Guid.NewGuid()
    let sid = shipperId ()

    match Cargo.book (fixedId guid) sid None (routeSpec ()) General (weight 500m) with
    | Ok(cargo, events) ->
        cargo.State |> should equal Preliminary
        cargo.Consignee |> should equal None
        events |> should equal [ CargoBooked(cargo.BookingId, sid) ]
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``予約 ID は BKG- プレフィックスで発番される`` () =
    let guid = Guid.NewGuid()

    match Cargo.book (fixedId guid) (shipperId ()) None (routeSpec ()) General (weight 500m) with
    | Ok(cargo, _) -> (BookingId.value cargo.BookingId).StartsWith("BKG-") |> should equal true
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``危険物貨物として予約できる`` () =
    let haz =
        match HazardousDeclaration.create "3" "UN1203" "Gasoline" with
        | Ok d -> d
        | Error e -> failwithf "%A" e

    match Cargo.book (fixedId (Guid.NewGuid())) (shipperId ()) None (routeSpec ()) (Hazardous haz) (weight 500m) with
    | Ok(cargo, _) ->
        match cargo.CargoType with
        | Hazardous _ -> ()
        | other -> failwithf "Hazardous を期待したが: %A" other
    | Error e -> failwithf "Ok を期待したが Error: %A" e

// ---- Cargo.execute（US06 経路設計依頼・キャンセル）----

let private preliminaryCargo () =
    match Cargo.book (fixedId (Guid.NewGuid())) (shipperId ()) None (routeSpec ()) General (weight 500m) with
    | Ok(cargo, _) -> cargo
    | Error e -> failwithf "%A" e

[<Fact>]
let ``Preliminary から経路設計依頼で RoutingRequested に遷移し RoutingRequestedEvent を発行する`` () =
    let cargo = preliminaryCargo ()

    match Cargo.execute cargo SubmitForRouting with
    | Ok(updated, events) ->
        updated.State |> should equal RoutingRequested
        events |> should equal [ RoutingRequestedEvent cargo.BookingId ]
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``Preliminary から Cancel で Cancelled に遷移する`` () =
    let cargo = preliminaryCargo ()

    match Cargo.execute cargo (Cancel "顧客都合") with
    | Ok(updated, events) ->
        updated.State |> should equal (Cancelled "顧客都合")
        events |> should equal [ BookingCancelled(cargo.BookingId, "顧客都合") ]
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``RoutingRequested から再度の経路設計依頼は不正遷移になる`` () =
    let cargo = preliminaryCargo ()

    match Cargo.execute cargo SubmitForRouting with
    | Ok(routing, _) ->
        match Cargo.execute routing SubmitForRouting with
        | Error(InvalidStateTransition(current, _)) -> current |> should equal "ROUTING_REQUESTED"
        | other -> failwithf "InvalidStateTransition を期待したが: %A" other
    | Error e -> failwithf "%A" e

[<Fact>]
let ``Cancelled からの Cancel は不正遷移になる`` () =
    let cargo = preliminaryCargo ()

    match Cargo.execute cargo (Cancel "初回") with
    | Ok(cancelled, _) ->
        match Cargo.execute cancelled (Cancel "再度") with
        | Error(InvalidStateTransition _) -> ()
        | other -> failwithf "InvalidStateTransition を期待したが: %A" other
    | Error e -> failwithf "%A" e

[<Fact>]
let ``booking_status の文字列表現が状態と一致する`` () =
    BookingState.toString Preliminary |> should equal "PRELIMINARY"
    BookingState.toString RoutingRequested |> should equal "ROUTING_REQUESTED"
    BookingState.toString (Cancelled "x") |> should equal "CANCELLED"

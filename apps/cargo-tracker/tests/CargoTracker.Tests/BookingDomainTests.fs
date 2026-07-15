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

[<Fact>]
let ``重量は上限ちょうど 30,000kg なら Ok を返す（境界値）`` () =
    match Weight.create 30_000m with
    | Ok w -> Weight.value w |> should equal 30_000m
    | Error e -> failwithf "境界ちょうどは Ok を期待したが Error: %A" e

[<Fact>]
let ``重量は有効側最小 0.001kg なら Ok を返す（境界値）`` () =
    match Weight.create 0.001m with
    | Ok w -> Weight.value w |> should equal 0.001m
    | Error e -> failwithf "有効側最小は Ok を期待したが Error: %A" e

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
let ``UN 番号が空なら Error を返す`` () =
    match HazardousDeclaration.create "3" "" "Gasoline" with
    | Error(ValidationError(field, _)) -> field |> should equal "UnNumber"
    | other -> failwithf "ValidationError を期待したが: %A" other

[<Fact>]
let ``正式輸送品名が空なら Error を返す`` () =
    match HazardousDeclaration.create "3" "UN1203" "" with
    | Error(ValidationError(field, _)) -> field |> should equal "ProperShippingName"
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

[<Fact>]
let ``温度管理条件は最低温度と最高温度が等しければ Ok を返す（境界値）`` () =
    match TemperatureRequirement.create 5m 5m Celsius with
    | Ok t ->
        TemperatureRequirement.minTemperature t |> should equal 5m
        TemperatureRequirement.maxTemperature t |> should equal 5m
    | Error e -> failwithf "等値境界は Ok を期待したが Error: %A" e

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

// ---- BookingState.ofString（永続化文字列からの復元）----

[<Fact>]
let ``booking_status 文字列から状態を復元できる`` () =
    let ok expected actual =
        match actual with
        | Ok v -> v |> should equal expected
        | Error e -> failwithf "Ok を期待したが Error: %A" e

    ok Preliminary (BookingState.ofString "PRELIMINARY")
    ok RoutingRequested (BookingState.ofString "ROUTING_REQUESTED")
    ok (Cancelled "") (BookingState.ofString "CANCELLED")

[<Fact>]
let ``未知の booking_status は Error を返す`` () =
    match BookingState.ofString "UNKNOWN" with
    | Error(ValidationError(field, _)) -> field |> should equal "BookingState"
    | other -> failwithf "ValidationError を期待したが: %A" other

// ---- Consignee（荷受人・任意）----

[<Fact>]
let ``荷受人は名前があれば Ok を返しアクセサで取り出せる`` () =
    match Consignee.create "山田太郎" "東京都港区" "yamada@example.com" with
    | Ok c ->
        Consignee.name c |> should equal "山田太郎"
        Consignee.address c |> should equal "東京都港区"
        Consignee.contactEmail c |> should equal "yamada@example.com"
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``荷受人名が空なら Error を返す`` () =
    match Consignee.create "" "住所" "a@example.com" with
    | Error(ValidationError(field, _)) -> field |> should equal "ConsigneeName"
    | other -> failwithf "ValidationError を期待したが: %A" other

// ---- Quantity（個数・任意）----

[<Fact>]
let ``個数は 1 以上なら Ok を返す`` () =
    match Quantity.create 3 with
    | Ok q -> Quantity.value q |> should equal 3
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``個数が 0 以下なら Error を返す`` () =
    match Quantity.create 0 with
    | Error(ValidationError(field, _)) -> field |> should equal "Quantity"
    | other -> failwithf "ValidationError を期待したが: %A" other

// ---- Description（品名・任意）----

[<Fact>]
let ``品名は 500 文字以内なら Ok を返す`` () =
    match Description.create "電子部品" with
    | Ok d -> Description.value d |> should equal "電子部品"
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``品名が 500 文字を超えると Error を返す`` () =
    match Description.create (String.replicate 501 "あ") with
    | Error(ValidationError(field, _)) -> field |> should equal "Description"
    | other -> failwithf "ValidationError を期待したが: %A" other

// ---- HazardousDeclaration アクセサ ----

[<Fact>]
let ``危険物申告のアクセサで各項目を取り出せる`` () =
    match HazardousDeclaration.create "3" "UN1203" "Gasoline" with
    | Ok d ->
        HazardousDeclaration.hazardClass d |> should equal "3"
        HazardousDeclaration.unNumber d |> should equal "UN1203"
        HazardousDeclaration.properShippingName d |> should equal "Gasoline"
    | Error e -> failwithf "%A" e

// ---- TemperatureRequirement アクセサ ----

[<Fact>]
let ``温度管理条件のアクセサで各項目を取り出せる`` () =
    match TemperatureRequirement.create -20m 5m Fahrenheit with
    | Ok t ->
        TemperatureRequirement.minTemperature t |> should equal -20m
        TemperatureRequirement.maxTemperature t |> should equal 5m
        TemperatureRequirement.unit t |> should equal Fahrenheit
    | Error e -> failwithf "%A" e

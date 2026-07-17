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
let ``有効な重量は create して value で元の値に戻る（ラウンドトリップ不変条件）`` (NormalFloat f) =
    // 0 < kg <= 上限 の有効域に写像し、create→value のラウンドトリップで値が保存されることを性質化する。
    let kg = decimal (abs f % 29_999.0) + 0.001m

    match Weight.create kg with
    | Ok w -> Weight.value w = kg
    | Error _ -> false

[<Property>]
let ``0 以下の重量は必ず Error になる`` (PositiveInt n) =
    // 無効域（≤ 0）を境界（0）を含めて生成し、必ず Error になることを性質化する。
    let kg = -decimal n

    match Weight.create kg with
    | Error(ValidationError("Weight", _)) -> true
    | _ -> false

[<Property>]
let ``上限を超える重量は必ず Error になる`` (PositiveInt n) =
    let kg = Weight.maxWeightKg + decimal n

    match Weight.create kg with
    | Error(ValidationError("Weight", _)) -> true
    | _ -> false

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
let ``RoutingRequested から Cancel で Cancelled に遷移する`` () =
    let cargo = preliminaryCargo ()

    match Cargo.execute cargo SubmitForRouting with
    | Ok(routing, _) ->
        match Cargo.execute routing (Cancel "在庫調整") with
        | Ok(updated, events) ->
            updated.State |> should equal (Cancelled "在庫調整")

            match events with
            | [ BookingCancelled _ ] -> ()
            | other -> failwithf "BookingCancelled を期待したが: %A" other
        | Error e -> failwithf "Ok を期待したが Error: %A" e
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

    ok Preliminary (BookingState.ofString None "PRELIMINARY")
    ok RoutingRequested (BookingState.ofString None "ROUTING_REQUESTED")
    ok (Cancelled "") (BookingState.ofString None "CANCELLED")

[<Fact>]
let ``未知の booking_status は Error を返す`` () =
    match BookingState.ofString None "UNKNOWN" with
    | Error(ValidationError(field, _)) -> field |> should equal "BookingState"
    | other -> failwithf "ValidationError を期待したが: %A" other

[<Fact>]
let ``ROUTE_PROPOSED は旅程が無いと復元できない`` () =
    match BookingState.ofString None "ROUTE_PROPOSED" with
    | Error(ValidationError("BookingState", _)) -> ()
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

// ---- IT4: Leg / CargoItinerary（US11）----

let private dto (y, m, d) =
    DateTimeOffset(y, m, d, 0, 0, 0, TimeSpan.Zero)

let private voyageNo n = VoyageNumber.ofString n

let private leg load unload loadT unloadT vn =
    match Leg.create (loc load) (loc unload) loadT unloadT (voyageNo vn) with
    | Ok l -> l
    | Error e -> failwithf "テスト前提の Leg 生成に失敗: %A" e

[<Fact>]
let ``輸送区間は積込港≠荷降港かつ積込<荷降なら Ok`` () =
    match Leg.create (loc "JPTYO") (loc "USLAX") (dto (2026, 8, 1)) (dto (2026, 8, 20)) (voyageNo "V001") with
    | Ok _ -> ()
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``輸送区間は積込港と荷降港が同一なら Error`` () =
    match Leg.create (loc "JPTYO") (loc "JPTYO") (dto (2026, 8, 1)) (dto (2026, 8, 20)) (voyageNo "V001") with
    | Error(BusinessRuleViolation("Leg", _)) -> ()
    | other -> failwithf "BusinessRuleViolation を期待したが: %A" other

[<Fact>]
let ``旅程は空なら Error`` () =
    match CargoItinerary.create [] with
    | Error(ValidationError("Legs", _)) -> ()
    | other -> failwithf "ValidationError を期待したが: %A" other

[<Fact>]
let ``連結した区間列は旅程を構成でき端点が取れる`` () =
    let l1 = leg "JPTYO" "SGSIN" (dto (2026, 8, 1)) (dto (2026, 8, 8)) "V-A"
    let l2 = leg "SGSIN" "USLAX" (dto (2026, 8, 9)) (dto (2026, 8, 25)) "V-B"

    match CargoItinerary.create [ l1; l2 ] with
    | Ok itin ->
        Location.value (CargoItinerary.firstLoadLocation itin) |> should equal "JPTYO"
        Location.value (CargoItinerary.lastUnloadLocation itin) |> should equal "USLAX"
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``連結が途切れる区間列は LegConnectivity エラー`` () =
    let l1 = leg "JPTYO" "SGSIN" (dto (2026, 8, 1)) (dto (2026, 8, 8)) "V-A"
    let l2 = leg "HKHKG" "USLAX" (dto (2026, 8, 9)) (dto (2026, 8, 25)) "V-B"

    match CargoItinerary.create [ l1; l2 ] with
    | Error(BusinessRuleViolation("LegConnectivity", _)) -> ()
    | other -> failwithf "LegConnectivity を期待したが: %A" other

/// 港コード列（3 港以上）を連番の連結した区間列に変換する。
let private connectedLegsFrom (ports: string list) =
    ports
    |> List.pairwise
    |> List.mapi (fun i (a, b) -> leg a b (dto (2026, 8, 1 + i)) (dto (2026, 8, 2 + i)) (sprintf "V-%d" i))

[<Property>]
let ``連結した任意長の区間列は常に旅程を構成できる`` (PositiveInt n) =
    // 相異なる港コード列から 2〜6 港を選び、連結した区間列を構成する。
    let ports = [ "JPTYO"; "SGSIN"; "USLAX"; "HKHKG"; "CNSHA"; "KRPUS" ]
    let count = 2 + (n % (ports.Length - 1))
    let selected = ports |> List.truncate count
    let legs = connectedLegsFrom selected

    match CargoItinerary.create legs with
    | Ok itin ->
        Location.value (CargoItinerary.firstLoadLocation itin) = List.head selected
        && Location.value (CargoItinerary.lastUnloadLocation itin) = List.last selected
    | Error _ -> false

// ---- IT4: 経路提案〜予約確定の状態遷移（US11/US13）----

/// routeSpec（JPTYO→USLAX・期限 2026-09-01）を満たす旅程。
let private satisfyingItinerary () =
    match CargoItinerary.create [ leg "JPTYO" "USLAX" (dto (2026, 8, 1)) (dto (2026, 8, 20)) "V001" ] with
    | Ok i -> i
    | Error e -> failwithf "%A" e

let private routingRequestedCargo () =
    let cargo = preliminaryCargo ()

    match Cargo.execute cargo SubmitForRouting with
    | Ok(c, _) -> c
    | Error e -> failwithf "%A" e

[<Fact>]
let ``経路提案で RoutingRequested から RouteProposed に遷移し CargoRouted を発行する`` () =
    let cargo = routingRequestedCargo ()

    match Cargo.execute cargo (ProposeRoute(satisfyingItinerary ())) with
    | Ok(updated, events) ->
        (match updated.State with
         | RouteProposed _ -> ()
         | other -> failwithf "RouteProposed を期待したが: %A" other)

        events |> should equal [ CargoRouted cargo.BookingId ]
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``ルート仕様を満たさない旅程は経路提案できない`` () =
    let cargo = routingRequestedCargo ()
    // 目的地が USLAX でない旅程
    let badItinerary =
        match CargoItinerary.create [ leg "JPTYO" "SGSIN" (dto (2026, 8, 1)) (dto (2026, 8, 20)) "V001" ] with
        | Ok i -> i
        | Error e -> failwithf "%A" e

    match Cargo.execute cargo (ProposeRoute badItinerary) with
    | Error(BusinessRuleViolation("RouteSpecification", _)) -> ()
    | other -> failwithf "BusinessRuleViolation を期待したが: %A" other

[<Fact>]
let ``期限を超過する旅程は経路提案できない`` () =
    let cargo = routingRequestedCargo ()
    // 到着 9/20 > 期限 9/1
    let lateItinerary =
        match CargoItinerary.create [ leg "JPTYO" "USLAX" (dto (2026, 9, 1)) (dto (2026, 9, 20)) "V001" ] with
        | Ok i -> i
        | Error e -> failwithf "%A" e

    match Cargo.execute cargo (ProposeRoute lateItinerary) with
    | Error(BusinessRuleViolation("RouteSpecification", _)) -> ()
    | other -> failwithf "BusinessRuleViolation を期待したが: %A" other

let private routeProposedCargo () =
    let cargo = routingRequestedCargo ()

    match Cargo.execute cargo (ProposeRoute(satisfyingItinerary ())) with
    | Ok(c, _) -> c
    | Error e -> failwithf "%A" e

[<Fact>]
let ``予約確定で RouteProposed から Confirmed に遷移し BookingConfirmed を発行する`` () =
    let cargo = routeProposedCargo ()

    match Cargo.execute cargo ConfirmBooking with
    | Ok(updated, events) ->
        (match updated.State with
         | Confirmed _ -> ()
         | other -> failwithf "Confirmed を期待したが: %A" other)

        events |> should equal [ BookingConfirmed cargo.BookingId ]
    | Error e -> failwithf "Ok を期待したが Error: %A" e

let private confirmedCargo () =
    match Cargo.execute (routeProposedCargo ()) ConfirmBooking with
    | Ok(c, _) -> c
    | Error e -> failwithf "%A" e

[<Fact>]
let ``配送完了で Confirmed から Delivered に遷移し CargoDelivered を発行する（US21・IT7）`` () =
    let cargo = confirmedCargo ()

    match Cargo.execute cargo MarkDelivered with
    | Ok(updated, events) ->
        (match updated.State with
         | Delivered _ -> ()
         | other -> failwithf "Delivered を期待したが: %A" other)

        events |> should equal [ CargoDelivered cargo.BookingId ]
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``精算完了で Delivered から Settled に遷移し BookingSettled を発行する（US23・IT7）`` () =
    let delivered =
        match Cargo.execute (confirmedCargo ()) MarkDelivered with
        | Ok(c, _) -> c
        | Error e -> failwithf "%A" e

    match Cargo.execute delivered Settle with
    | Ok(updated, events) ->
        (match updated.State with
         | Settled _ -> ()
         | other -> failwithf "Settled を期待したが: %A" other)

        events |> should equal [ BookingSettled delivered.BookingId ]
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``Confirmed からの精算完了は不正遷移（配送完了前）`` () =
    match Cargo.execute (confirmedCargo ()) Settle with
    | Error(InvalidStateTransition _) -> ()
    | other -> failwithf "InvalidStateTransition を期待したが: %A" other

[<Fact>]
let ``予約確定から差し戻すと RoutingRequested に戻る（US13 受入条件4）`` () =
    let confirmed =
        match Cargo.execute (routeProposedCargo ()) ConfirmBooking with
        | Ok(c, _) -> c
        | Error e -> failwithf "%A" e

    match Cargo.execute confirmed RestoreToRouting with
    | Ok(updated, events) ->
        updated.State |> should equal RoutingRequested
        events |> should equal [ BookingRestoredToRouting confirmed.BookingId ]
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``RouteProposed からもキャンセルできる（US13 受入条件5）`` () =
    let cargo = routeProposedCargo ()

    match Cargo.execute cargo (Cancel "荷主都合") with
    | Ok(updated, _) -> updated.State |> should equal (Cancelled "荷主都合")
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``Preliminary からの経路提案は不正遷移`` () =
    let cargo = preliminaryCargo ()

    match Cargo.execute cargo (ProposeRoute(satisfyingItinerary ())) with
    | Error(InvalidStateTransition _) -> ()
    | other -> failwithf "InvalidStateTransition を期待したが: %A" other

[<Fact>]
let ``Preliminary からの予約確定は不正遷移`` () =
    match Cargo.execute (preliminaryCargo ()) ConfirmBooking with
    | Error(InvalidStateTransition(current, _)) -> current |> should equal "PRELIMINARY"
    | other -> failwithf "InvalidStateTransition を期待したが: %A" other

[<Fact>]
let ``RoutingRequested からの予約確定は不正遷移（経路提案前）`` () =
    match Cargo.execute (routingRequestedCargo ()) ConfirmBooking with
    | Error(InvalidStateTransition(current, _)) -> current |> should equal "ROUTING_REQUESTED"
    | other -> failwithf "InvalidStateTransition を期待したが: %A" other

[<Fact>]
let ``RouteProposed からの再経路提案は不正遷移`` () =
    match Cargo.execute (routeProposedCargo ()) (ProposeRoute(satisfyingItinerary ())) with
    | Error(InvalidStateTransition(current, _)) -> current |> should equal "ROUTE_PROPOSED"
    | other -> failwithf "InvalidStateTransition を期待したが: %A" other

[<Fact>]
let ``RoutingRequested からの差し戻しは不正遷移（確定前）`` () =
    match Cargo.execute (routingRequestedCargo ()) RestoreToRouting with
    | Error(InvalidStateTransition(current, _)) -> current |> should equal "ROUTING_REQUESTED"
    | other -> failwithf "InvalidStateTransition を期待したが: %A" other

[<Fact>]
let ``輸送区間は積込時刻と荷降時刻が等しいと Error（境界値）`` () =
    let t = dto (2026, 8, 1)

    match Leg.create (loc "JPTYO") (loc "USLAX") t t (voyageNo "V001") with
    | Error(BusinessRuleViolation("Leg", _)) -> ()
    | other -> failwithf "BusinessRuleViolation を期待したが: %A" other

[<Fact>]
let ``booking_status に ROUTE_PROPOSED と CONFIRMED が対応する`` () =
    BookingState.toString (RouteProposed(satisfyingItinerary ()))
    |> should equal "ROUTE_PROPOSED"

    BookingState.toString (Confirmed(satisfyingItinerary ()))
    |> should equal "CONFIRMED"

// ---- IT4 M3 / IT5 task6: 不正遷移マトリクスの Theory 網羅（Cancelled からの前進系を含む）----

/// 各状態の Cargo を構築する（テスト用）。
let private cargoInState (stateName: string) : Cargo =
    let confirmed () =
        match Cargo.execute (routeProposedCargo ()) ConfirmBooking with
        | Ok(c, _) -> c
        | Error e -> failwithf "%A" e

    match stateName with
    | "Preliminary" -> preliminaryCargo ()
    | "RoutingRequested" -> routingRequestedCargo ()
    | "RouteProposed" -> routeProposedCargo ()
    | "Confirmed" -> confirmed ()
    | "Cancelled" ->
        match Cargo.execute (preliminaryCargo ()) (Cancel "テスト") with
        | Ok(c, _) -> c
        | Error e -> failwithf "%A" e
    | other -> failwithf "未知の状態: %s" other

/// コマンド名から BookingCommand を構築する。
let private commandByName (name: string) : BookingCommand =
    match name with
    | "SubmitForRouting" -> SubmitForRouting
    | "ProposeRoute" -> ProposeRoute(satisfyingItinerary ())
    | "ConfirmBooking" -> ConfirmBooking
    | "RestoreToRouting" -> RestoreToRouting
    | other -> failwithf "未知のコマンド: %s" other

[<Theory>]
// Preliminary から前進できるのは SubmitForRouting のみ
[<InlineData("Preliminary", "ProposeRoute")>]
[<InlineData("Preliminary", "ConfirmBooking")>]
[<InlineData("Preliminary", "RestoreToRouting")>]
// RoutingRequested から前進できるのは ProposeRoute のみ
[<InlineData("RoutingRequested", "SubmitForRouting")>]
[<InlineData("RoutingRequested", "ConfirmBooking")>]
[<InlineData("RoutingRequested", "RestoreToRouting")>]
// RouteProposed から前進できるのは ConfirmBooking のみ
[<InlineData("RouteProposed", "SubmitForRouting")>]
[<InlineData("RouteProposed", "ProposeRoute")>]
[<InlineData("RouteProposed", "RestoreToRouting")>]
// Confirmed から前進できるのは RestoreToRouting のみ
[<InlineData("Confirmed", "SubmitForRouting")>]
[<InlineData("Confirmed", "ProposeRoute")>]
[<InlineData("Confirmed", "ConfirmBooking")>]
// Cancelled からはいずれの前進遷移も不可
[<InlineData("Cancelled", "SubmitForRouting")>]
[<InlineData("Cancelled", "ProposeRoute")>]
[<InlineData("Cancelled", "ConfirmBooking")>]
[<InlineData("Cancelled", "RestoreToRouting")>]
let ``非許可の状態遷移は InvalidStateTransition を返す`` (stateName: string) (commandName: string) =
    let cargo = cargoInState stateName
    let command = commandByName commandName

    match Cargo.execute cargo command with
    | Error(InvalidStateTransition _) -> ()
    | other -> failwithf "InvalidStateTransition を期待したが (%s, %s): %A" stateName commandName other

[<Fact>]
let ``Cancelled からの再キャンセルは不正遷移`` () =
    match Cargo.execute (cargoInState "Cancelled") (Cancel "再") with
    | Error(InvalidStateTransition _) -> ()
    | other -> failwithf "InvalidStateTransition を期待したが: %A" other

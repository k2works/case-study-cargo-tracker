module CargoTracker.Tests.RoutingDomainTests

open System
open Xunit
open FsUnit.Xunit
open CargoTracker.Shared.Domain
open CargoTracker.Routing.Domain

// US24/US25/US07/US08: Routing ドメイン層（Voyage 集約・Schedule 連結制約）。

let private loc code =
    match Location.create code with
    | Ok l -> l
    | Error e -> failwithf "テスト前提の Location 生成に失敗: %s" e

let private dt (y, m, d) =
    DateTimeOffset(y, m, d, 0, 0, 0, TimeSpan.Zero)

/// 運送区間を生成する（テスト前提のため失敗時は例外）。
let private movement origin dest depart arrive seq =
    match CarrierMovement.create (loc origin) (loc dest) depart arrive seq with
    | Ok m -> m
    | Error e -> failwithf "%A" e

// ---- CarrierMovement（区間の不変条件）----

[<Fact>]
let ``運送区間は出発港≠到着港かつ出発<到着なら Ok`` () =
    match CarrierMovement.create (loc "JPTYO") (loc "SGSIN") (dt (2026, 9, 1)) (dt (2026, 9, 10)) 1 with
    | Ok _ -> ()
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``運送区間は出発港と到着港が同一なら Error`` () =
    match CarrierMovement.create (loc "JPTYO") (loc "JPTYO") (dt (2026, 9, 1)) (dt (2026, 9, 10)) 1 with
    | Error(BusinessRuleViolation("CarrierMovement", _)) -> ()
    | other -> failwithf "BusinessRuleViolation を期待したが: %A" other

[<Fact>]
let ``運送区間は出発日時が到着日時以降なら Error`` () =
    match CarrierMovement.create (loc "JPTYO") (loc "SGSIN") (dt (2026, 9, 10)) (dt (2026, 9, 1)) 1 with
    | Error(BusinessRuleViolation("CarrierMovement", _)) -> ()
    | other -> failwithf "BusinessRuleViolation を期待したが: %A" other

[<Fact>]
let ``運送区間は順序が 1 未満なら Error`` () =
    match CarrierMovement.create (loc "JPTYO") (loc "SGSIN") (dt (2026, 9, 1)) (dt (2026, 9, 10)) 0 with
    | Error(ValidationError("SeqNumber", _)) -> ()
    | other -> failwithf "ValidationError を期待したが: %A" other

// ---- Schedule（連結・時系列制約）----

[<Fact>]
let ``スケジュールは空なら Error`` () =
    match Schedule.create [] with
    | Error(ValidationError("Schedule", _)) -> ()
    | other -> failwithf "ValidationError を期待したが: %A" other

[<Fact>]
let ``連結した区間列はスケジュールを構成できる`` () =
    let m1 = movement "JPTYO" "SGSIN" (dt (2026, 9, 1)) (dt (2026, 9, 10)) 1
    let m2 = movement "SGSIN" "USLAX" (dt (2026, 9, 11)) (dt (2026, 9, 25)) 2

    match Schedule.create [ m1; m2 ] with
    | Ok s ->
        Location.value (Schedule.origin s) |> should equal "JPTYO"
        Location.value (Schedule.destination s) |> should equal "USLAX"
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``直行便（単一区間）はスケジュールを構成できる`` () =
    let m1 = movement "JPTYO" "USLAX" (dt (2026, 9, 1)) (dt (2026, 9, 20)) 1

    match Schedule.create [ m1 ] with
    | Ok s -> Location.value (Schedule.destination s) |> should equal "USLAX"
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``連結が途切れる区間列は ScheduleConnectivity エラー`` () =
    // m1 到着 SGSIN ≠ m2 出発 HKHKG で連結が途切れる。
    let m1 = movement "JPTYO" "SGSIN" (dt (2026, 9, 1)) (dt (2026, 9, 10)) 1
    let m2 = movement "HKHKG" "USLAX" (dt (2026, 9, 11)) (dt (2026, 9, 25)) 2

    match Schedule.create [ m1; m2 ] with
    | Error(BusinessRuleViolation("ScheduleConnectivity", _)) -> ()
    | other -> failwithf "ScheduleConnectivity を期待したが: %A" other

[<Fact>]
let ``時系列が逆転する区間列は ScheduleTimeline エラー`` () =
    // m1 到着 9/10 より m2 出発 9/5 が前で時系列が逆転。
    let m1 = movement "JPTYO" "SGSIN" (dt (2026, 9, 1)) (dt (2026, 9, 10)) 1
    let m2 = movement "SGSIN" "USLAX" (dt (2026, 9, 5)) (dt (2026, 9, 25)) 2

    match Schedule.create [ m1; m2 ] with
    | Error(BusinessRuleViolation("ScheduleTimeline", _)) -> ()
    | other -> failwithf "ScheduleTimeline を期待したが: %A" other

// ---- CargoTypeTag ----

[<Fact>]
let ``貨物種別タグは文字列と往復できる`` () =
    for tag in [ General; Hazardous; Refrigerated ] do
        match CargoTypeTag.ofString (CargoTypeTag.toString tag) with
        | Ok restored -> restored |> should equal tag
        | Error e -> failwithf "%A" e

[<Fact>]
let ``未知の貨物種別タグは Error`` () =
    match CargoTypeTag.ofString "UNKNOWN" with
    | Error(ValidationError("CargoTypeTag", _)) -> ()
    | other -> failwithf "ValidationError を期待したが: %A" other

// ---- Voyage 集約（US24/US25）----

let private validSchedule () =
    match Schedule.create [ movement "JPTYO" "USLAX" (dt (2026, 9, 1)) (dt (2026, 9, 20)) 1 ] with
    | Ok s -> s
    | Error e -> failwithf "%A" e

let private vessel name =
    match VesselName.create name with
    | Ok v -> v
    | Error e -> failwithf "%A" e

let private carrier name =
    match CarrierName.create name with
    | Ok c -> c
    | Error e -> failwithf "%A" e

let private voyageNo n =
    match VoyageNumber.create n with
    | Ok v -> v
    | Error e -> failwithf "%A" e

[<Fact>]
let ``航海を登録すると VoyageRegistered イベントを発行する`` () =
    let vn = voyageNo "V001"

    match
        Voyage.register vn (vessel "Ever Given") (carrier "Evergreen") (validSchedule ()) (Set.ofList [ General ])
    with
    | Ok(voyage, events) ->
        voyage.VoyageNumber |> should equal vn
        events |> should equal [ VoyageRegistered vn ]
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``対応貨物種別が空だと航海を登録できない`` () =
    match Voyage.register (voyageNo "V001") (vessel "V") (carrier "C") (validSchedule ()) Set.empty with
    | Error(ValidationError("SupportedCargoTypes", _)) -> ()
    | other -> failwithf "ValidationError を期待したが: %A" other

[<Fact>]
let ``航海は指定貨物種別への対応可否を判定できる`` () =
    match
        Voyage.register (voyageNo "V001") (vessel "V") (carrier "C") (validSchedule ()) (Set.ofList [ Refrigerated ])
    with
    | Ok(voyage, _) ->
        Voyage.supports Refrigerated voyage |> should equal true
        Voyage.supports Hazardous voyage |> should equal false
    | Error e -> failwithf "%A" e

// ---- RouteComputation（US08 経路候補算出）----

/// 指定条件で航海を 1 件生成するヘルパー。
let private makeVoyage vn origin dest depart arrive tags =
    let sched =
        match Schedule.create [ movement origin dest depart arrive 1 ] with
        | Ok s -> s
        | Error e -> failwithf "%A" e

    match Voyage.register (voyageNo vn) (vessel "V") (carrier "C") sched (Set.ofList tags) with
    | Ok(v, _) -> v
    | Error e -> failwithf "%A" e

let private query origin dest cargoType deadline : RouteQuery =
    { Origin = loc origin
      Destination = loc dest
      CargoType = cargoType
      Deadline = deadline }

[<Fact>]
let ``直行便があれば経路候補として算出される`` () =
    let voyages =
        [ makeVoyage "V001" "JPTYO" "USLAX" (dt (2026, 9, 1)) (dt (2026, 9, 20)) [ General ] ]

    let candidates =
        RouteComputation.computeCandidates voyages (query "JPTYO" "USLAX" General (dt (2026, 10, 1)))

    candidates |> List.length |> should equal 1
    (List.head candidates).IsDirect |> should equal true

[<Fact>]
let ``直行便が乗継便より優先される`` () =
    let voyages =
        [ makeVoyage "V-DIRECT" "JPTYO" "USLAX" (dt (2026, 9, 1)) (dt (2026, 9, 20)) [ General ]
          makeVoyage "V-A" "JPTYO" "SGSIN" (dt (2026, 9, 1)) (dt (2026, 9, 8)) [ General ]
          makeVoyage "V-B" "SGSIN" "USLAX" (dt (2026, 9, 9)) (dt (2026, 9, 25)) [ General ] ]

    let candidates =
        RouteComputation.computeCandidates voyages (query "JPTYO" "USLAX" General (dt (2026, 10, 1)))

    candidates |> List.length |> should equal 2
    // 先頭は直行便
    (List.head candidates).IsDirect |> should equal true

    VoyageNumber.value (List.head candidates).Legs.Head.VoyageNumber
    |> should equal "V-DIRECT"

[<Fact>]
let ``乗継で接続する経路が算出され経由港が示される`` () =
    let voyages =
        [ makeVoyage "V-A" "JPTYO" "SGSIN" (dt (2026, 9, 1)) (dt (2026, 9, 8)) [ General ]
          makeVoyage "V-B" "SGSIN" "USLAX" (dt (2026, 9, 9)) (dt (2026, 9, 25)) [ General ] ]

    let candidates =
        RouteComputation.computeCandidates voyages (query "JPTYO" "USLAX" General (dt (2026, 10, 1)))

    candidates |> List.length |> should equal 1
    let c = List.head candidates
    c.IsDirect |> should equal false
    c.Legs |> List.length |> should equal 2
    c.TransitPorts |> List.map Location.value |> should equal [ "SGSIN" ]

[<Fact>]
let ``貨物種別に対応しない航海は候補から除外される`` () =
    let voyages =
        [ makeVoyage "V001" "JPTYO" "USLAX" (dt (2026, 9, 1)) (dt (2026, 9, 20)) [ General ] ]
    // 冷凍貨物を要求するが航海は General のみ対応
    let candidates =
        RouteComputation.computeCandidates voyages (query "JPTYO" "USLAX" Refrigerated (dt (2026, 10, 1)))

    candidates |> should be Empty

[<Fact>]
let ``期限内に到達できない経路は候補から除外される`` () =
    let voyages =
        [ makeVoyage "V001" "JPTYO" "USLAX" (dt (2026, 9, 1)) (dt (2026, 9, 20)) [ General ] ]
    // 到着 9/20 だが期限は 9/10
    let candidates =
        RouteComputation.computeCandidates voyages (query "JPTYO" "USLAX" General (dt (2026, 9, 10)))

    candidates |> should be Empty

[<Fact>]
let ``時刻が連結しない乗継は経路にならない`` () =
    // V-B が V-A 到着（9/8）より前に出発（9/2）するため接続不可
    let voyages =
        [ makeVoyage "V-A" "JPTYO" "SGSIN" (dt (2026, 9, 1)) (dt (2026, 9, 8)) [ General ]
          makeVoyage "V-B" "SGSIN" "USLAX" (dt (2026, 9, 2)) (dt (2026, 9, 20)) [ General ] ]

    let candidates =
        RouteComputation.computeCandidates voyages (query "JPTYO" "USLAX" General (dt (2026, 10, 1)))

    candidates |> should be Empty

[<Fact>]
let ``航海を更新すると航海番号は不変で VoyageScheduleUpdated を発行する`` () =
    let vn = voyageNo "V001"

    let voyage =
        match Voyage.register vn (vessel "Old") (carrier "C") (validSchedule ()) (Set.ofList [ General ]) with
        | Ok(v, _) -> v
        | Error e -> failwithf "%A" e

    match
        Voyage.update
            (vessel "New Vessel")
            (carrier "C")
            (validSchedule ())
            (Set.ofList [ General; Refrigerated ])
            voyage
    with
    | Ok(updated, events) ->
        updated.VoyageNumber |> should equal vn
        VesselName.value updated.Vessel |> should equal "New Vessel"
        events |> should equal [ VoyageScheduleUpdated vn ]
    | Error e -> failwithf "Ok を期待したが Error: %A" e

module CargoTracker.Tests.RoutingApplicationTests

open System
open Xunit
open FsUnit.Xunit
open CargoTracker.Shared.Domain
open CargoTracker.Routing.Domain
open CargoTracker.Routing.Application

// US24/US25/US07/US08: Routing アプリケーション層（ワークフロー）。

let private dt (y, m, d) =
    DateTimeOffset(y, m, d, 0, 0, 0, TimeSpan.Zero)

/// インメモリ VoyageRepository スタブ。
let private repoStub () =
    let store = System.Collections.Generic.Dictionary<string, Voyage>()

    let repo =
        { Save =
            fun v ->
                async {
                    store[VoyageNumber.value v.VoyageNumber] <- v
                    return Ok()
                }
          Update =
            fun v ->
                async {
                    store[VoyageNumber.value v.VoyageNumber] <- v
                    return Ok()
                }
          FindByNumber =
            fun vn ->
                async {
                    match store.TryGetValue(VoyageNumber.value vn) with
                    | true, v -> return Ok(Some v)
                    | false, _ -> return Ok None
                }
          FindAll = fun () -> async { return Ok(store.Values |> List.ofSeq) } }

    repo, store

let private movementInput dep arr departDate arriveDate : MovementInput =
    { DepartureUnlocode = dep
      ArrivalUnlocode = arr
      DepartureDate = departDate
      ArrivalDate = arriveDate }

let private command vn movements tags : VoyageCommand =
    { VoyageNumber = vn
      VesselName = "Ever Given"
      CarrierName = "Evergreen"
      Movements = movements
      SupportedCargoTypes = tags }

let private directCmd vn =
    command vn [ movementInput "JPTYO" "USLAX" (dt (2026, 9, 1)) (dt (2026, 9, 20)) ] [ "GENERAL" ]

[<Fact>]
let ``航海を登録すると保存される`` () =
    let repo, store = repoStub ()

    let result =
        VoyageWorkflow.register repo (directCmd "V001") |> Async.RunSynchronously

    match result with
    | Ok voyage ->
        VoyageNumber.value voyage.VoyageNumber |> should equal "V001"
        store.ContainsKey "V001" |> should equal true
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``同一航海番号の登録は重複エラー`` () =
    let repo, _ = repoStub ()

    VoyageWorkflow.register repo (directCmd "V001")
    |> Async.RunSynchronously
    |> ignore

    let result =
        VoyageWorkflow.register repo (directCmd "V001") |> Async.RunSynchronously

    match result with
    | Error(BusinessRuleViolation("VoyageNumber", _)) -> ()
    | other -> failwithf "重複エラーを期待したが: %A" other

[<Fact>]
let ``連結しない区間列の登録は失敗する`` () =
    let repo, _ = repoStub ()

    let cmd =
        command
            "V002"
            [ movementInput "JPTYO" "SGSIN" (dt (2026, 9, 1)) (dt (2026, 9, 8))
              movementInput "HKHKG" "USLAX" (dt (2026, 9, 9)) (dt (2026, 9, 25)) ]
            [ "GENERAL" ]

    match VoyageWorkflow.register repo cmd |> Async.RunSynchronously with
    | Error(BusinessRuleViolation("ScheduleConnectivity", _)) -> ()
    | other -> failwithf "ScheduleConnectivity を期待したが: %A" other

[<Fact>]
let ``存在しない航海の更新は NotFound`` () =
    let repo, _ = repoStub ()

    match VoyageWorkflow.update repo (directCmd "NOPE") |> Async.RunSynchronously with
    | Error(NotFound("Voyage", _)) -> ()
    | other -> failwithf "NotFound を期待したが: %A" other

[<Fact>]
let ``登録済み航海を更新できる`` () =
    let repo, store = repoStub ()

    VoyageWorkflow.register repo (directCmd "V001")
    |> Async.RunSynchronously
    |> ignore

    let updated =
        { directCmd "V001" with
            VesselName = "New Vessel"
            SupportedCargoTypes = [ "GENERAL"; "REFRIGERATED" ] }

    match VoyageWorkflow.update repo updated |> Async.RunSynchronously with
    | Ok v ->
        VesselName.value v.Vessel |> should equal "New Vessel"
        Voyage.supports Refrigerated store["V001"] |> should equal true
    | Error e -> failwithf "Ok を期待したが Error: %A" e

let private loc code =
    match Location.create code with
    | Ok l -> l
    | Error e -> failwithf "%s" e

[<Fact>]
let ``検索は出発地・目的地・貨物種別で絞り込む`` () =
    let repo, _ = repoStub ()

    VoyageWorkflow.register repo (directCmd "V001")
    |> Async.RunSynchronously
    |> ignore
    // 別ルートの航海
    VoyageWorkflow.register
        repo
        (command "V002" [ movementInput "JPTYO" "SGSIN" (dt (2026, 9, 1)) (dt (2026, 9, 8)) ] [ "GENERAL" ])
    |> Async.RunSynchronously
    |> ignore

    let result =
        VoyageWorkflow.search repo (loc "JPTYO") (loc "USLAX") General
        |> Async.RunSynchronously

    match result with
    | Ok [ v ] -> VoyageNumber.value v.VoyageNumber |> should equal "V001"
    | other -> failwithf "1 件を期待したが: %A" other

[<Fact>]
let ``経路候補算出はワークフロー経由で候補を返す`` () =
    let repo, _ = repoStub ()

    VoyageWorkflow.register repo (directCmd "V001")
    |> Async.RunSynchronously
    |> ignore

    let query: RouteQuery =
        { Origin = loc "JPTYO"
          Destination = loc "USLAX"
          CargoType = General
          Deadline = dt (2026, 10, 1) }

    match VoyageWorkflow.computeRoutes repo query |> Async.RunSynchronously with
    | Ok candidates ->
        candidates |> List.length |> should equal 1
        (List.head candidates).IsDirect |> should equal true
    | Error e -> failwithf "Ok を期待したが Error: %A" e

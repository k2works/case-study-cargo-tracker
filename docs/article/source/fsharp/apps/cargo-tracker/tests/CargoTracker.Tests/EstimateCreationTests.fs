module CargoTracker.Tests.EstimateCreationTests

open System
open Xunit
open FsUnit.Xunit
open CargoTracker.Shared.Domain
open CargoTracker.Estimation.Domain
open CargoTracker.Estimation.Application

// US01: 輸送見積を作成する（アプリ層。外部経路サービスはスタブ）

let fixedId (guid: Guid) : IdGenerator = fun () -> guid

let private run a = Async.RunSynchronously a

let private sampleCandidate =
    match RouteCandidate.create "V001" "SGSIN" 21 120000m with
    | Ok c -> c
    | Error e -> failwithf "%A" e

/// 常に 1 候補を返すスタブ経路サービス。
let stubRouting (candidates: RouteCandidate list) : ExternalRoutingServicePort =
    { FetchCandidateRoutes = fun _ -> async { return Ok candidates } }

let captureRepo (saved: Estimate list ref) : EstimateRepository =
    { Save =
        fun e ->
            async {
                saved.Value <- e :: saved.Value
                return Ok()
            } }

let validCommand =
    { OriginUnlocode = "JPTYO"
      DestinationUnlocode = "USLAX"
      ArrivalDeadline = DateOnly(2026, 9, 1)
      CargoType = General
      WeightKg = 500m }

[<Fact>]
let ``見積を作成するとスタブ候補が反映され保存される`` () =
    let saved = ref []
    let repo = captureRepo saved
    let routing = stubRouting [ sampleCandidate ]

    match run (EstimateCreation.create repo routing (fixedId (Guid.NewGuid())) validCommand) with
    | Ok estimate ->
        estimate.Candidates |> List.length |> should equal 1
        estimate.Status |> should equal Created
        saved.Value |> List.length |> should equal 1
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``不正な UN/LOCODE は検証エラーで保存されない`` () =
    let saved = ref []
    let repo = captureRepo saved
    let routing = stubRouting [ sampleCandidate ]

    let cmd =
        { validCommand with
            OriginUnlocode = "JP" }

    match run (EstimateCreation.create repo routing (fixedId (Guid.NewGuid())) cmd) with
    | Error(ValidationError(field, _)) ->
        field |> should equal "Origin"
        saved.Value |> should be Empty
    | other -> failwithf "ValidationError を期待したが: %A" other

[<Fact>]
let ``出発地と目的地が同一なら見積を作成できない`` () =
    let saved = ref []
    let repo = captureRepo saved
    let routing = stubRouting [ sampleCandidate ]

    let cmd =
        { validCommand with
            DestinationUnlocode = "JPTYO" }

    match run (EstimateCreation.create repo routing (fixedId (Guid.NewGuid())) cmd) with
    | Error(BusinessRuleViolation(rule, _)) ->
        rule |> should equal "SameOriginDestination"
        saved.Value |> should be Empty
    | other -> failwithf "BusinessRuleViolation を期待したが: %A" other

[<Fact>]
let ``外部経路サービスの失敗は伝播し保存されない`` () =
    let saved = ref []
    let repo = captureRepo saved

    let routing =
        { FetchCandidateRoutes = fun _ -> async { return Error(NotFound("Route", "JPTYO-USLAX")) } }

    match run (EstimateCreation.create repo routing (fixedId (Guid.NewGuid())) validCommand) with
    | Error(NotFound _) -> saved.Value |> should be Empty
    | other -> failwithf "NotFound を期待したが: %A" other

module CargoTracker.Tests.EstimationDomainTests

open System
open Xunit
open FsUnit.Xunit
open FsCheck
open CargoTracker.Shared.Domain
open CargoTracker.Estimation.Domain

// US01: 輸送見積を作成する

/// テスト用の固定 IdGenerator（ADR-0006 の注入ポートを決定的にする）。
let fixedId (guid: Guid) : IdGenerator = fun () -> guid

let private loc code =
    match Location.create code with
    | Ok l -> l
    | Error e -> failwithf "テスト前提の Location 生成に失敗: %s" e

[<Fact>]
let ``重量は正の値なら Ok を返す`` () =
    match WeightKg.create 1200.5m with
    | Ok w -> WeightKg.value w |> should equal 1200.5m
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``重量が 0 以下なら Error を返す`` () =
    match WeightKg.create 0m with
    | Error(ValidationError(field, _)) -> field |> should equal "WeightKg"
    | other -> failwithf "ValidationError を期待したが: %A" other

[<Fact>]
let ``重量が上限を超えると Error を返す`` () =
    match WeightKg.create 30_001m with
    | Error(ValidationError _) -> ()
    | other -> failwithf "ValidationError を期待したが: %A" other

[<Fact>]
let ``見積を作成すると Created 状態で EstimateCreated イベントを発行する`` () =
    let guid = Guid.NewGuid()

    let weight =
        match WeightKg.create 500m with
        | Ok w -> w
        | Error e -> failwithf "%A" e

    let result =
        Estimate.create (fixedId guid) (loc "JPTYO") (loc "USLAX") (DateOnly(2026, 9, 1)) General weight

    match result with
    | Ok(estimate, event) ->
        estimate.Status |> should equal Created
        estimate.Candidates |> should be Empty
        EstimateId.value estimate.EstimateId |> should equal guid
        event.EstimateId |> should equal estimate.EstimateId
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``出発地と目的地が同一なら見積を作成できない`` () =
    let weight =
        match WeightKg.create 500m with
        | Ok w -> w
        | Error e -> failwithf "%A" e

    let result =
        Estimate.create (fixedId (Guid.NewGuid())) (loc "JPTYO") (loc "JPTYO") (DateOnly(2026, 9, 1)) General weight

    match result with
    | Error(BusinessRuleViolation(rule, _)) -> rule |> should equal "SameOriginDestination"
    | other -> failwithf "BusinessRuleViolation を期待したが: %A" other

[<Fact>]
let ``ルート候補を一括入替できる`` () =
    let weight =
        match WeightKg.create 500m with
        | Ok w -> w
        | Error e -> failwithf "%A" e

    let estimate =
        match
            Estimate.create (fixedId (Guid.NewGuid())) (loc "JPTYO") (loc "USLAX") (DateOnly(2026, 9, 1)) General weight
        with
        | Ok(e, _) -> e
        | Error e -> failwithf "%A" e

    let candidate =
        match RouteCandidate.create "V001" "SGSIN" 21 120000m with
        | Ok c -> c
        | Error e -> failwithf "%A" e

    match Estimate.replaceCandidates [ candidate ] estimate with
    | Ok updated -> updated.Candidates |> List.length |> should equal 1
    | Error e -> failwithf "Ok を期待したが Error: %A" e

/// FsCheck プロパティ: 上限以下の正の重量は常に Ok を返す。
[<Fact>]
let ``上限以下の正の重量は常に Ok を返す`` () =
    let property (raw: int) =
        let normalized = decimal ((abs raw % 30000) + 1)

        match WeightKg.create normalized with
        | Ok w -> WeightKg.value w = normalized
        | Error _ -> false

    Check.QuickThrowOnFailure property

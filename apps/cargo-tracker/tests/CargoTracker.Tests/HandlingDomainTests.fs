module CargoTracker.Tests.HandlingDomainTests

open System
open Xunit
open FsUnit.Xunit
open CargoTracker.Shared.Domain
open CargoTracker.Handling.Domain

// US15: 荷役作業を記録する / US16: 引取作業を記録する（荷役妥当性検証・引取の荷受人確認）

let private loc code =
    match Location.create code with
    | Ok l -> l
    | Error e -> failwithf "テスト前提の Location 生成に失敗: %s" e

let private voyage n =
    match VoyageNumber.create n with
    | Ok v -> v
    | Error e -> failwithf "%A" e

let private now = DateTimeOffset(2026, 9, 10, 0, 0, 0, TimeSpan.Zero)

/// JPTYO→USLAX 直行（V001）の貨物スナップショット。
let private snapshot () =
    { BookingId = "BKG-0001"
      Origin = "JPTYO"
      Destination = "USLAX"
      ItineraryLegs =
        [ { LoadLocation = "JPTYO"
            UnloadLocation = "USLAX"
            VoyageNumber = "V001" } ] }

// ---- validateFor（デシジョンテーブル）----

[<Fact>]
let ``受領は出発港と一致すれば Valid`` () =
    HandlingActivity.validateFor (snapshot ()) Receive (loc "JPTYO")
    |> should equal Valid

[<Fact>]
let ``受領が出発港と不一致なら Warning`` () =
    match HandlingActivity.validateFor (snapshot ()) Receive (loc "CNSHA") with
    | Warning _ -> ()
    | other -> failwithf "Warning を期待したが: %A" other

[<Fact>]
let ``積込が旅程の積込港・航海と一致すれば Valid`` () =
    HandlingActivity.validateFor (snapshot ()) (Load(voyage "V001")) (loc "JPTYO")
    |> should equal Valid

[<Fact>]
let ``積込が旅程と不一致なら Misrouted`` () =
    HandlingActivity.validateFor (snapshot ()) (Load(voyage "V999")) (loc "JPTYO")
    |> should equal Misrouted

[<Fact>]
let ``引取が目的港と一致すれば Valid`` () =
    HandlingActivity.validateFor (snapshot ()) Claim (loc "USLAX")
    |> should equal Valid

// ---- register（US15/US16）----

[<Fact>]
let ``受領を登録すると HandlingActivityRegistered を発行する`` () =
    match HandlingActivity.register (snapshot ()) Receive (loc "JPTYO") now None with
    | Ok(activity, outcome, events) ->
        outcome |> should equal Valid
        activity.Type |> should equal Receive

        match events with
        | [ HandlingActivityRegistered(_, t) ] -> t |> should equal "RECEIVE"
        | other -> failwithf "HandlingActivityRegistered を期待したが: %A" other
    | Error e -> failwithf "Ok を期待したが: %A" e

[<Fact>]
let ``引取は荷受人確認が無いと登録できない（US16 受入2）`` () =
    match HandlingActivity.register (snapshot ()) Claim (loc "USLAX") now None with
    | Error(BusinessRuleViolation(rule, _)) -> rule |> should equal "ConsigneeConfirmationRequired"
    | other -> failwithf "BusinessRuleViolation を期待したが: %A" other

[<Fact>]
let ``引取は荷受人確認があれば登録できる`` () =
    match HandlingActivity.register (snapshot ()) Claim (loc "USLAX") now (Some "署名:山田") with
    | Ok(activity, _, _) -> activity.ConsigneeConfirmation |> should equal (Some "署名:山田")
    | Error e -> failwithf "Ok を期待したが: %A" e

[<Fact>]
let ``ルート外の積込登録は Misrouted かつ CargoMisrouted を発行する`` () =
    match HandlingActivity.register (snapshot ()) (Load(voyage "V999")) (loc "JPTYO") now None with
    | Ok(_, outcome, events) ->
        outcome |> should equal Misrouted

        events
        |> List.exists (fun e ->
            match e with
            | CargoMisrouted _ -> true
            | _ -> false)
        |> should equal true
    | Error e -> failwithf "Ok を期待したが: %A" e

[<Fact>]
let ``荷役種別は toString で対応する文字列になる`` () =
    HandlingType.toString Receive |> should equal "RECEIVE"
    HandlingType.toString (Load(voyage "V001")) |> should equal "LOAD"
    HandlingType.toString (Unload(voyage "V001")) |> should equal "UNLOAD"
    HandlingType.toString Customs |> should equal "CUSTOMS"
    HandlingType.toString Claim |> should equal "CLAIM"

// ---- 追加: エラー/境界分岐（カバレッジ補強）----

[<Fact>]
let ``航海番号は空で Error`` () =
    match VoyageNumber.create "" with
    | Error(ValidationError("VoyageNumber", _)) -> ()
    | other -> failwithf "ValidationError を期待したが: %A" other

[<Fact>]
let ``予約識別子は空で Error`` () =
    match CargoBookingId.create "" with
    | Error(ValidationError("CargoBookingId", _)) -> ()
    | other -> failwithf "ValidationError を期待したが: %A" other

[<Fact>]
let ``荷降しは旅程と一致すれば Valid・不一致なら Misrouted`` () =
    HandlingActivity.validateFor (snapshot ()) (Unload(voyage "V001")) (loc "USLAX")
    |> should equal Valid

    HandlingActivity.validateFor (snapshot ()) (Unload(voyage "V001")) (loc "CNSHA")
    |> should equal Misrouted

[<Fact>]
let ``通関は常に Valid（IT5 はゲートなし）`` () =
    HandlingActivity.validateFor (snapshot ()) Customs (loc "JPTYO")
    |> should equal Valid

[<Fact>]
let ``引取が目的港と不一致なら Warning`` () =
    match HandlingActivity.validateFor (snapshot ()) Claim (loc "CNSHA") with
    | Warning _ -> ()
    | other -> failwithf "Warning を期待したが: %A" other

[<Fact>]
let ``荷降しを登録すると航海番号込みで記録される`` () =
    match HandlingActivity.register (snapshot ()) (Unload(voyage "V001")) (loc "USLAX") now None with
    | Ok(activity, outcome, _) ->
        outcome |> should equal Valid

        match activity.Type with
        | Unload _ -> ()
        | other -> failwithf "Unload を期待したが: %A" other
    | Error e -> failwithf "%A" e

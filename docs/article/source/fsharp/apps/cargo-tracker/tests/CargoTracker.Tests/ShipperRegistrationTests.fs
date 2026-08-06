module CargoTracker.Tests.ShipperRegistrationTests

open System
open Xunit
open FsUnit.Xunit
open CargoTracker.Shared.Domain
open CargoTracker.Shipper.Domain
open CargoTracker.Shipper.Application

// US02/US03: 荷主登録ワークフロー（Port はスタブ関数で差し込む）

/// 保存された荷主を捕捉するスタブリポジトリ。exists で重複を制御する。
let stubRepo (exists: bool) (saved: Shipper list ref) : ShipperRepository =
    { ExistsByEmail = fun _ -> async { return Ok exists }
      Save =
        fun s ->
            async {
                saved.Value <- s :: saved.Value
                return Ok()
            } }

let fixedId (guid: Guid) : IdGenerator = fun () -> guid

let private run a = Async.RunSynchronously a

let individualCommand =
    { Name = "山田太郎"
      Email = "yamada@example.com"
      Phone = None
      Address = None
      Corporate = None }

[<Fact>]
let ``個人荷主を登録すると保存されイベントを返す`` () =
    let saved = ref []
    let repo = stubRepo false saved

    match run (ShipperRegistration.register repo (fixedId (Guid.NewGuid())) individualCommand) with
    | Ok event ->
        (ShipperCode.value event.ShipperCode).StartsWith("SHP-") |> should equal true
        saved.Value |> List.length |> should equal 1
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``メールが重複していると登録できず保存されない`` () =
    let saved = ref []
    let repo = stubRepo true saved

    match run (ShipperRegistration.register repo (fixedId (Guid.NewGuid())) individualCommand) with
    | Error(BusinessRuleViolation(rule, _)) ->
        rule |> should equal "EmailAlreadyRegistered"
        saved.Value |> should be Empty
    | other -> failwithf "BusinessRuleViolation を期待したが: %A" other

[<Fact>]
let ``法人荷主を割引率付きで登録できる`` () =
    let saved = ref []
    let repo = stubRepo false saved

    let cmd =
        { individualCommand with
            Email = "corp@example.com"
            Corporate =
                Some
                    { ContractNumber = "CT-1"
                      DiscountRate = 0.25m } }

    match run (ShipperRegistration.register repo (fixedId (Guid.NewGuid())) cmd) with
    | Ok _ ->
        match saved.Value with
        | [ s ] -> Shipper.effectiveDiscountRate s |> should equal 0.25m
        | _ -> failwith "1 件の保存を期待"
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``不正なメールと範囲外割引率は検証エラーで登録できない`` () =
    let saved = ref []
    let repo = stubRepo false saved

    let cmd =
        { individualCommand with
            Email = "bad-email"
            Corporate =
                Some
                    { ContractNumber = "CT-1"
                      DiscountRate = 0.5m } }

    match run (ShipperRegistration.register repo (fixedId (Guid.NewGuid())) cmd) with
    | Error(ValidationError _) -> saved.Value |> should be Empty
    | other -> failwithf "ValidationError を期待したが: %A" other

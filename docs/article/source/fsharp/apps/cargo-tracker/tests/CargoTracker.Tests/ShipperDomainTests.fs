module CargoTracker.Tests.ShipperDomainTests

open System
open Xunit
open FsUnit.Xunit
open FsCheck
open FsToolkit.ErrorHandling
open CargoTracker.Shared.Domain
open CargoTracker.Shipper.Domain

// US02: 荷主を登録する / US03: 法人荷主を登録する

[<Fact>]
let ``割引率は 0〜30% の範囲なら Ok を返す`` () =
    match DiscountRate.create 0.15m with
    | Ok rate -> DiscountRate.value rate |> should equal 0.15m
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``割引率が 30% を超えると Error を返す`` () =
    match DiscountRate.create 0.31m with
    | Error(ValidationError(field, _)) -> field |> should equal "DiscountRate"
    | other -> failwithf "ValidationError を期待したが: %A" other

[<Fact>]
let ``割引率が負なら Error を返す`` () =
    match DiscountRate.create -0.01m with
    | Error(ValidationError _) -> ()
    | other -> failwithf "ValidationError を期待したが: %A" other

[<Fact>]
let ``不正な形式のメールは Error を返す`` () =
    match Email.create "not-an-email" with
    | Error(ValidationError(field, _)) -> field |> should equal "Email"
    | other -> failwithf "ValidationError を期待したが: %A" other

[<Fact>]
let ``個人荷主を登録できる`` () =
    let id = ShipperId.ofGuid (Guid.NewGuid())

    let result =
        result {
            let! name = ShipperName.create "山田太郎"
            and! email = Email.create "yamada@example.com"
            let shipper, event = Shipper.register id name email None None Individual
            return shipper, event
        }

    match result with
    | Ok(shipper, event) ->
        shipper.Kind |> should equal Individual
        Shipper.effectiveDiscountRate shipper |> should equal 0.0000m
        event.ShipperId |> should equal id
        (ShipperCode.value shipper.Code).StartsWith("SHP-") |> should equal true
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``法人荷主は契約番号と割引率を持って登録できる`` () =
    let id = ShipperId.ofGuid (Guid.NewGuid())

    let result =
        result {
            let! name = ShipperName.create "株式会社サンプル"
            and! email = Email.create "corp@example.com"
            and! contract = ContractNumber.create "CT-0001"
            and! rate = DiscountRate.create 0.2000m
            let kind = Corporate(contract, rate)
            let shipper, _ = Shipper.register id name email None None kind
            return shipper
        }

    match result with
    | Ok shipper -> Shipper.effectiveDiscountRate shipper |> should equal 0.2000m
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
let ``ShipperCode は同一 ShipperId から決定的に生成される`` () =
    let id = ShipperId.ofGuid (Guid.NewGuid())
    let c1 = ShipperCode.generate id
    let c2 = ShipperCode.generate id
    ShipperCode.value c1 |> should equal (ShipperCode.value c2)

/// FsCheck プロパティ: 0〜0.3 の範囲の割引率は常に Ok を返す。
[<Fact>]
let ``範囲内の割引率は常に Ok を返す`` () =
    let property (raw: int) =
        // 0〜3000（= 0.0000〜0.3000）に正規化
        let normalized = decimal (((raw % 3001 + 3001) % 3001)) / 10000m

        match DiscountRate.create normalized with
        | Ok r -> DiscountRate.value r = normalized
        | Error _ -> false

    Check.QuickThrowOnFailure property

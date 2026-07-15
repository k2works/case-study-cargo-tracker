module CargoTracker.ArchTests.LayerDependencyTests

open System.Reflection
open Xunit
open ArchUnitNET.Loader
open ArchUnitNET.xUnit
open type ArchUnitNET.Fluent.ArchRuleDefinition

/// 各コンテキストのアセンブリをロードしてアーキテクチャを構築する（ADR-0001 の垂直スライス）。
let architecture =
    ArchLoader()
        .LoadAssemblies(
            Assembly.Load("CargoTracker.Shared"),
            Assembly.Load("CargoTracker.Booking"),
            Assembly.Load("CargoTracker.Shipper"),
            Assembly.Load("CargoTracker.Estimation"),
            Assembly.Load("CargoTracker.Routing")
        )
        .Build()

/// 「<Context>.Domain は <Context>.Infrastructure に依存しない」ルール。
/// プレースホルダー（型なし）の BC でも空マッチを許容し、型追加時に自動有効化する。
let private domainNotDependOnInfrastructure (context: string) =
    Types()
        .That()
        .ResideInNamespace(sprintf "CargoTracker.%s.Domain" context)
        .Should()
        .NotDependOnAny(Types().That().ResideInNamespace(sprintf "CargoTracker.%s.Infrastructure" context))
        .WithoutRequiringPositiveResults()

/// 「<Context>.Domain はデータアクセス（Donald）に依存しない」ルール。
let private domainNotDependOnDonald (context: string) =
    Types()
        .That()
        .ResideInNamespace(sprintf "CargoTracker.%s.Domain" context)
        .Should()
        .NotDependOnAny(Types().That().ResideInNamespace("Donald"))
        .WithoutRequiringPositiveResults()

[<Theory>]
[<InlineData("Booking")>]
[<InlineData("Shipper")>]
[<InlineData("Estimation")>]
[<InlineData("Routing")>]
let ``Domain は Infrastructure に依存しない`` (context: string) =
    (domainNotDependOnInfrastructure context).Check(architecture)

[<Theory>]
[<InlineData("Shipper")>]
[<InlineData("Estimation")>]
[<InlineData("Routing")>]
let ``Domain はデータアクセス（Donald）に依存しない`` (context: string) =
    (domainNotDependOnDonald context).Check(architecture)

/// 「Shipper と Estimation は互いの Domain 型を直接参照しない」ルール（BC 独立性・ADR-0001）。
[<Fact>]
let ``Shipper と Estimation は互いに直接依存しない`` () =
    Types()
        .That()
        .ResideInNamespace("CargoTracker.Shipper")
        .Should()
        .NotDependOnAny(Types().That().ResideInNamespace("CargoTracker.Estimation"))
        .WithoutRequiringPositiveResults()
        .Check(architecture)

/// 「Routing は他 BC の Domain 型を直接参照しない」ルール（BC 独立性・ADR-0001/ADR-0009）。
[<Theory>]
[<InlineData("Booking")>]
[<InlineData("Shipper")>]
[<InlineData("Estimation")>]
let ``Routing は他 BC に直接依存しない`` (other: string) =
    Types()
        .That()
        .ResideInNamespace("CargoTracker.Routing")
        .Should()
        .NotDependOnAny(Types().That().ResideInNamespace(sprintf "CargoTracker.%s" other))
        .WithoutRequiringPositiveResults()
        .Check(architecture)

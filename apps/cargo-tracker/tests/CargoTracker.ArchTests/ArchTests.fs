module CargoTracker.ArchTests.LayerDependencyTests

open System.Reflection
open Xunit
open ArchUnitNET.Loader
open ArchUnitNET.xUnit
open type ArchUnitNET.Fluent.ArchRuleDefinition

/// Booking コンテキストのアセンブリをロードしてアーキテクチャを構築する。
/// ArchTests は Booking を ProjectReference しているため出力に .dll が配置される。
let architecture =
    ArchLoader().LoadAssembly(Assembly.Load("CargoTracker.Booking")).Build()

// NOTE: 現時点では Domain / Infrastructure 名前空間はプレースホルダー（型なし）のため、
// ルールにマッチする型が存在しない。WithoutRequiringPositiveResults() で空マッチを許容し、
// Domain 層に型を追加した時点で「Domain は Infrastructure に依存しない」制約が自動で有効化される。
[<Fact>]
let ``Booking Domain は Infrastructure に依存しない`` () =
    let rule =
        Types()
            .That()
            .ResideInNamespace("CargoTracker.Booking.Domain")
            .Should()
            .NotDependOnAny(Types().That().ResideInNamespace("CargoTracker.Booking.Infrastructure"))
            .WithoutRequiringPositiveResults()

    rule.Check(architecture)

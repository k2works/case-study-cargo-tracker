using ArchUnitNET.Domain;
using ArchUnitNET.Loader;
using ArchUnitNET.xUnit;
using static ArchUnitNET.Fluent.ArchRuleDefinition;

namespace CargoTracker.Architecture.Tests;

/// <summary>
/// ヘキサゴナルアーキテクチャの依存関係ルールを自動検証する（tech_stack.md の 4 ルール・ADR-0005 コンプライアンス）。
/// 依存方向の腐敗・Bounded Context 間の直接参照を CI で継続検出し、IT1 の参照実装を基準線として固定する。
/// </summary>
public class HexagonalArchitectureTest
{
    private static readonly ArchUnitNET.Domain.Architecture _architecture = new ArchLoader()
        .LoadAssemblies(typeof(CargoTracker.Shared.Domain.Model.AggregateRoot).Assembly)
        .Build();

    private static readonly IObjectProvider<IType> _domainLayer =
        Types().That().ResideInNamespaceMatching(@"CargoTracker\.\w+\.Domain").As("ドメイン層");

    private static readonly IObjectProvider<IType> _applicationLayer =
        Types().That().ResideInNamespaceMatching(@"CargoTracker\.\w+\.Application").As("アプリケーション層");

    private static readonly IObjectProvider<IType> _infrastructureLayer =
        Types().That().ResideInNamespaceMatching(@"CargoTracker\.\w+\.Infrastructure").As("インフラ層");

    // ルール 1: Domain 名前空間が Infrastructure 名前空間に依存しない
    [Fact]
    public void ドメイン層はインフラ層に依存しない()
    {
        Types().That().Are(_domainLayer)
            .Should().NotDependOnAny(_infrastructureLayer)
            .Because("依存方向は Infrastructure → Domain。ドメイン層はインフラ層を直接参照してはならない")
            .Check(_architecture);
    }

    // ルール 2: Domain 名前空間で ASP.NET Core / Dapper / Npgsql / Sqlite を使用しない
    [Fact]
    public void ドメイン層はフレームワークに依存しない()
    {
        Types().That().Are(_domainLayer)
            .Should().NotDependOnAny(Types().That()
                .ResideInNamespaceMatching("Microsoft.AspNetCore")
                .Or().ResideInNamespaceMatching("Dapper")
                .Or().ResideInNamespaceMatching("Npgsql")
                .Or().ResideInNamespaceMatching("Microsoft.Data.Sqlite"))
            .Because("ドメインオブジェクトは POCO でなければならない。技術的関心事を持ち込まない")
            .Check(_architecture);
    }

    // ルール 3: Application 名前空間が Infrastructure 名前空間に直接依存しない（ポート経由のみ）
    [Fact]
    public void アプリケーション層はインフラ層に直接依存しない()
    {
        Types().That().Are(_applicationLayer)
            .Should().NotDependOnAny(_infrastructureLayer)
            .Because("アプリケーション層はポートインターフェース経由でのみインフラ層と通信しなければならない")
            .Check(_architecture);
    }

    // ルール 4: 異なる Bounded Context 間でクラスを直接参照しない（Shared は共有カーネルとして許可）
    [Fact]
    public void 荷主コンテキストと見積コンテキストは相互に直接参照しない()
    {
        Types().That().ResideInNamespaceMatching(@"CargoTracker\.Shipper\.")
            .Should().NotDependOnAny(Types().That().ResideInNamespaceMatching(@"CargoTracker\.Estimation\."))
            .Because("Bounded Context 間の通信はドメインイベントまたは ACL 経由でなければならない")
            .Check(_architecture);

        Types().That().ResideInNamespaceMatching(@"CargoTracker\.Estimation\.")
            .Should().NotDependOnAny(Types().That().ResideInNamespaceMatching(@"CargoTracker\.Shipper\."))
            .Because("Bounded Context 間の通信はドメインイベントまたは ACL 経由でなければならない")
            .Check(_architecture);
    }

    [Fact]
    public void 予約コンテキストは荷主コンテキストと見積コンテキストに直接参照しない()
    {
        var bookingContext = Types().That().ResideInNamespaceMatching(@"CargoTracker\.Booking\.");
        var shipperContext = Types().That().ResideInNamespaceMatching(@"CargoTracker\.Shipper\.");
        var estimationContext = Types().That().ResideInNamespaceMatching(@"CargoTracker\.Estimation\.");

        Types().That().Are(bookingContext)
            .Should().NotDependOnAny(shipperContext)
            .Because("Booking から Shipper への参照は ACL 経由に限定し、Shared 以外の BC 内部型へ依存してはならない")
            .Check(_architecture);

        Types().That().Are(bookingContext)
            .Should().NotDependOnAny(estimationContext)
            .Because("Booking と Estimation は独立した Bounded Context として直接参照してはならない")
            .Check(_architecture);
    }

    [Fact]
    public void 経路コンテキストは他BCの内部型に直接参照しない()
    {
        var routingContext = Types().That().ResideInNamespaceMatching(@"CargoTracker\.Routing\.");
        var bookingContext = Types().That().ResideInNamespaceMatching(@"CargoTracker\.Booking\.");
        var estimationContext = Types().That().ResideInNamespaceMatching(@"CargoTracker\.Estimation\.");
        var shipperContext = Types().That().ResideInNamespaceMatching(@"CargoTracker\.Shipper\.");

        Types().That().Are(routingContext)
            .Should().NotDependOnAny(bookingContext)
            .Because("Routing は Booking の CargoType 等に依存せず、Shared Kernel だけを共有する")
            .Check(_architecture);

        Types().That().Are(routingContext)
            .Should().NotDependOnAny(estimationContext)
            .Because("Routing と Estimation は独立した Bounded Context として直接参照してはならない")
            .Check(_architecture);

        Types().That().Are(routingContext)
            .Should().NotDependOnAny(shipperContext)
            .Because("Routing と Shipper は独立した Bounded Context として直接参照してはならない")
            .Check(_architecture);
    }
}

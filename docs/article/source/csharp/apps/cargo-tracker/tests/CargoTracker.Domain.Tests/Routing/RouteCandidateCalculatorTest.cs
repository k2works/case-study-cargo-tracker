using System.Globalization;
using CargoTracker.Routing.Domain.Model;
using CargoTracker.Shared.Domain.Model;
using FluentAssertions;

namespace CargoTracker.Domain.Tests.Routing;

public class RouteCandidateCalculatorTest
{
    private static readonly Location _tokyo = new("JPTYO");
    private static readonly Location _shanghai = new("CNSHA");
    private static readonly Location _singapore = new("SGSIN");
    private static readonly Location _hamburg = new("DEHAM");

    private readonly RouteCandidateCalculator _calculator = new();

    private static DateTimeOffset At(string value) => DateTimeOffset.Parse(value, CultureInfo.InvariantCulture);

    private static Voyage MakeVoyage(string number, params (Location from, Location to, string dep, string arr)[] legs)
    {
        var movements = legs.Select((leg, index) => new CarrierMovement(
            leg.from, leg.to, At(leg.dep), At(leg.arr), index + 1));
        return Voyage.Create(
            new VoyageNumber(number), "Vessel", "Carrier",
            new[] { SupportedCargoType.General }, new Schedule(movements));
    }

    [Fact]
    public void 直行便がある場合は経路候補として算出される()
    {
        var voyages = new[]
        {
            MakeVoyage("V-DIRECT", (_tokyo, _hamburg, "2026-09-01T10:00:00Z", "2026-09-20T10:00:00Z")),
        };

        var routes = _calculator.Calculate(_tokyo, _hamburg, new DateOnly(2026, 9, 30), voyages);

        routes.Should().HaveCount(1);
        routes[0].IsDirect.Should().BeTrue();
        routes[0].VoyageNumbers.Should().ContainSingle().Which.Value.Should().Be("V-DIRECT");
        routes[0].TransitDays.Should().Be(19);
        routes[0].Ports.Should().HaveCount(2);
    }

    [Fact]
    public void 航海内の寄港地を経由して出発地から目的地へ到達できる()
    {
        // 単一航海 JPTYO→CNSHA→DEHAM。JPTYO で乗り DEHAM で降りれば直行扱い。
        var voyages = new[]
        {
            MakeVoyage("V-MULTI",
                (_tokyo, _shanghai, "2026-09-01T10:00:00Z", "2026-09-04T10:00:00Z"),
                (_shanghai, _hamburg, "2026-09-05T10:00:00Z", "2026-09-18T10:00:00Z")),
        };

        var routes = _calculator.Calculate(_tokyo, _hamburg, new DateOnly(2026, 9, 30), voyages);

        routes.Should().Contain(route => route.IsDirect);
        var direct = routes.First(route => route.IsDirect);
        direct.Ports[0].SameAs(_tokyo).Should().BeTrue();
        direct.Ports[^1].SameAs(_hamburg).Should().BeTrue();
    }

    [Fact]
    public void 乗継は前航海の到着以降に出発する航海のみ接続できる()
    {
        var voyages = new[]
        {
            MakeVoyage("V-1", (_tokyo, _shanghai, "2026-09-01T10:00:00Z", "2026-09-04T10:00:00Z")),
            // V-2 は V-1 到着（9/4）以降に出発するので接続可能。
            MakeVoyage("V-2", (_shanghai, _hamburg, "2026-09-06T10:00:00Z", "2026-09-20T10:00:00Z")),
        };

        var routes = _calculator.Calculate(_tokyo, _hamburg, new DateOnly(2026, 9, 30), voyages);

        routes.Should().ContainSingle();
        routes[0].TransferCount.Should().Be(1);
        routes[0].VoyageNumbers.Select(v => v.Value).Should().Equal("V-1", "V-2");
        routes[0].Ports.Select(p => p.UnLocode).Should().Equal("JPTYO", "CNSHA", "DEHAM");
    }

    [Fact]
    public void 到着より前に出発する乗継は接続できない()
    {
        var voyages = new[]
        {
            MakeVoyage("V-1", (_tokyo, _shanghai, "2026-09-05T10:00:00Z", "2026-09-10T10:00:00Z")),
            // V-2 は V-1 到着（9/10）より前（9/06）に出発するので接続不可。
            MakeVoyage("V-2", (_shanghai, _hamburg, "2026-09-06T10:00:00Z", "2026-09-20T10:00:00Z")),
        };

        var routes = _calculator.Calculate(_tokyo, _hamburg, new DateOnly(2026, 9, 30), voyages);

        routes.Should().BeEmpty();
    }

    [Fact]
    public void 期限を超過する経路は除外される()
    {
        var voyages = new[]
        {
            MakeVoyage("V-LATE", (_tokyo, _hamburg, "2026-09-01T10:00:00Z", "2026-10-05T10:00:00Z")),
        };

        var routes = _calculator.Calculate(_tokyo, _hamburg, new DateOnly(2026, 9, 30), voyages);

        routes.Should().BeEmpty();
    }

    [Fact]
    public void 直行便は乗継便より優先して並べられる()
    {
        var voyages = new[]
        {
            // 乗継（合計で短くても直行が優先）
            MakeVoyage("V-1", (_tokyo, _shanghai, "2026-09-01T10:00:00Z", "2026-09-03T10:00:00Z")),
            MakeVoyage("V-2", (_shanghai, _hamburg, "2026-09-04T10:00:00Z", "2026-09-08T10:00:00Z")),
            // 直行（所要は長い）
            MakeVoyage("V-DIRECT", (_tokyo, _hamburg, "2026-09-01T10:00:00Z", "2026-09-20T10:00:00Z")),
        };

        var routes = _calculator.Calculate(_tokyo, _hamburg, new DateOnly(2026, 9, 30), voyages);

        routes.Should().HaveCountGreaterThanOrEqualTo(2);
        routes[0].IsDirect.Should().BeTrue("直行便は所要日数が長くても最優先で提示される");
        routes[0].VoyageNumbers.Should().ContainSingle().Which.Value.Should().Be("V-DIRECT");
    }

    [Fact]
    public void 直行が複数あれば所要日数の短い順に並べられる()
    {
        var voyages = new[]
        {
            MakeVoyage("V-SLOW", (_tokyo, _hamburg, "2026-09-01T10:00:00Z", "2026-09-25T10:00:00Z")),
            MakeVoyage("V-FAST", (_tokyo, _hamburg, "2026-09-01T10:00:00Z", "2026-09-15T10:00:00Z")),
        };

        var routes = _calculator.Calculate(_tokyo, _hamburg, new DateOnly(2026, 9, 30), voyages);

        routes.Should().HaveCount(2);
        routes[0].VoyageNumbers[0].Value.Should().Be("V-FAST");
        routes[1].VoyageNumbers[0].Value.Should().Be("V-SLOW");
    }

    [Fact]
    public void 条件を満たす経路がなければ空を返す()
    {
        var voyages = new[]
        {
            MakeVoyage("V-OTHER", (_singapore, _hamburg, "2026-09-01T10:00:00Z", "2026-09-10T10:00:00Z")),
        };

        var routes = _calculator.Calculate(_tokyo, _hamburg, new DateOnly(2026, 9, 30), voyages);

        routes.Should().BeEmpty();
    }

    private static readonly Location _osaka = new("JPOSA");
    private static readonly Location _rotterdam = new("NLRTM");

    [Fact]
    public void 乗継3航海までは経路候補として算出される()
    {
        // JPTYO→CNSHA→SGSIN→DEHAM を 3 航海の乗継で到達（_maxLegs=3 の境界・成功側）。
        var voyages = new[]
        {
            MakeVoyage("V-1", (_tokyo, _shanghai, "2026-09-01T10:00:00Z", "2026-09-03T10:00:00Z")),
            MakeVoyage("V-2", (_shanghai, _singapore, "2026-09-04T10:00:00Z", "2026-09-06T10:00:00Z")),
            MakeVoyage("V-3", (_singapore, _hamburg, "2026-09-07T10:00:00Z", "2026-09-20T10:00:00Z")),
        };

        var routes = _calculator.Calculate(_tokyo, _hamburg, new DateOnly(2026, 9, 30), voyages);

        routes.Should().ContainSingle();
        routes[0].TransferCount.Should().Be(2, "3 航海 = 乗継 2 回");
        routes[0].VoyageNumbers.Select(v => v.Value).Should().Equal("V-1", "V-2", "V-3");
    }

    [Fact]
    public void 乗継4航海が必要な経路は上限を超えるため算出されない()
    {
        // JPTYO→CNSHA→SGSIN→NLRTM→DEHAM は 4 航海必要 → _maxLegs=3 を超え枝刈りされる。
        var voyages = new[]
        {
            MakeVoyage("V-1", (_tokyo, _shanghai, "2026-09-01T10:00:00Z", "2026-09-03T10:00:00Z")),
            MakeVoyage("V-2", (_shanghai, _singapore, "2026-09-04T10:00:00Z", "2026-09-06T10:00:00Z")),
            MakeVoyage("V-3", (_singapore, _rotterdam, "2026-09-07T10:00:00Z", "2026-09-09T10:00:00Z")),
            MakeVoyage("V-4", (_rotterdam, _hamburg, "2026-09-10T10:00:00Z", "2026-09-20T10:00:00Z")),
        };

        var routes = _calculator.Calculate(_tokyo, _hamburg, new DateOnly(2026, 9, 30), voyages);

        routes.Should().BeEmpty();
    }

    [Fact]
    public void 前航海の到着時刻と次航海の出発時刻が同一なら接続できる()
    {
        // 接続判定は BoardTime < earliestBoard を除外＝等号は接続可（境界）。
        var voyages = new[]
        {
            MakeVoyage("V-1", (_tokyo, _shanghai, "2026-09-01T10:00:00Z", "2026-09-04T10:00:00Z")),
            MakeVoyage("V-2", (_shanghai, _hamburg, "2026-09-04T10:00:00Z", "2026-09-20T10:00:00Z")),
        };

        var routes = _calculator.Calculate(_tokyo, _hamburg, new DateOnly(2026, 9, 30), voyages);

        routes.Should().ContainSingle();
        routes[0].TransferCount.Should().Be(1);
    }

    [Fact]
    public void 到着日が期限当日ちょうどなら経路候補に含まれる()
    {
        // 期限は当日終端（TimeOnly.MaxValue）まで許容＝AlightTime > deadline を除外する境界。
        var voyages = new[]
        {
            MakeVoyage("V-DEADLINE", (_tokyo, _hamburg, "2026-09-01T10:00:00Z", "2026-09-30T23:00:00Z")),
        };

        var routes = _calculator.Calculate(_tokyo, _hamburg, new DateOnly(2026, 9, 30), voyages);

        routes.Should().ContainSingle();
        routes[0].IsDirect.Should().BeTrue();
    }

    [Fact]
    public void 同一航海を再び使う循環経路は生成されない()
    {
        // V-LOOP は JPTYO→CNSHA と CNSHA→JPTYO の両区間を持つが、同一航海の再乗船は不可。
        // JPTYO 起点で目的地 JPOSA へ到達できないため空になる（循環しない）。
        var voyages = new[]
        {
            MakeVoyage("V-LOOP",
                (_tokyo, _shanghai, "2026-09-01T10:00:00Z", "2026-09-03T10:00:00Z"),
                (_shanghai, _tokyo, "2026-09-04T10:00:00Z", "2026-09-06T10:00:00Z")),
        };

        var routes = _calculator.Calculate(_tokyo, _osaka, new DateOnly(2026, 9, 30), voyages);

        routes.Should().BeEmpty();
    }
}

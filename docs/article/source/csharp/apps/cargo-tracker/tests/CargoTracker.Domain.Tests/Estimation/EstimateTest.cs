using CargoTracker.Estimation.Domain.Model;
using CargoTracker.Shared.Domain.Model;
using FluentAssertions;

namespace CargoTracker.Domain.Tests.Estimation;

public class EstimateTest
{
    private static Estimate CreateEstimate() => Estimate.Create(
        new Location("JPTYO"), new Location("DEHAM"), new DateOnly(2026, 9, 30), CargoType.General, 1200.5m);

    [Fact]
    public void 見積を作成すると初期状態はCreatedで見積IDが採番される()
    {
        var estimate = CreateEstimate();

        estimate.Status.Should().Be(EstimateStatus.Created);
        estimate.EstimateId.Value.Should().NotBe(Guid.Empty);
        estimate.WeightKg.Should().Be(1200.5m);
        estimate.Origin.UnLocode.Should().Be("JPTYO");
    }

    [Fact]
    public void 出発地と仕向地が同一なら例外()
    {
        var act = () => Estimate.Create(
            new Location("JPTYO"), new Location("JPTYO"), new DateOnly(2026, 9, 30), CargoType.General, 100m);
        act.Should().Throw<ArgumentException>();
    }

    [Theory]
    [InlineData(0)]
    [InlineData(-1)]
    public void 重量が正でないなら例外(decimal invalidWeight)
    {
        var act = () => Estimate.Create(
            new Location("JPTYO"), new Location("DEHAM"), new DateOnly(2026, 9, 30), CargoType.General, invalidWeight);
        act.Should().Throw<ArgumentException>();
    }

    [Fact]
    public void 到着期限が過去日なら例外()
    {
        var act = () => Estimate.Create(
            new Location("JPTYO"), new Location("DEHAM"), new DateOnly(2026, 7, 8), CargoType.General, 100m,
            today: new DateOnly(2026, 7, 9));

        act.Should().Throw<ArgumentException>().WithMessage("*到着期限*");
    }

    [Theory]
    [InlineData(2026, 7, 9)]
    [InlineData(2026, 7, 10)]
    public void 到着期限が当日または未来日なら作成できる(int year, int month, int day)
    {
        var estimate = Estimate.Create(
            new Location("JPTYO"), new Location("DEHAM"), new DateOnly(year, month, day), CargoType.General, 100m,
            today: new DateOnly(2026, 7, 9));

        estimate.ArrivalDeadline.Should().Be(new DateOnly(year, month, day));
    }

    [Fact]
    public void ルート候補を差し替えられる()
    {
        var estimate = CreateEstimate();
        var candidates = new List<RouteCandidate>
        {
            new("V001", "SGSIN", 14, 150000m),
            new("V002", "HKHKG", 18, 120000m),
        };

        estimate.ReplaceCandidates(candidates);

        estimate.Candidates.Should().HaveCount(2);
        estimate.Candidates[0].VoyageNumber.Should().Be("V001");
    }

    [Theory]
    [InlineData("", 14, 1000)]
    [InlineData("V001", 0, 1000)]
    [InlineData("V001", 14, 0)]
    public void ルート候補の不正値は例外(string voyage, int transitDays, decimal cost)
    {
        var act = () => new RouteCandidate(voyage, "SGSIN", transitDays, cost);
        act.Should().Throw<ArgumentException>();
    }

    [Theory]
    [InlineData("JPTY")]
    [InlineData("JPTYOO")]
    [InlineData("")]
    public void UNLOCODEが5文字でないなら例外(string invalid)
    {
        var act = () => new Location(invalid);
        act.Should().Throw<ArgumentException>();
    }
}

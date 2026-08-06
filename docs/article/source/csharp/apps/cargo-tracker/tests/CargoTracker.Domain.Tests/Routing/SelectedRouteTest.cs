using CargoTracker.Routing.Domain.Model;
using CargoTracker.Shared.Domain.Model;
using FluentAssertions;

namespace CargoTracker.Domain.Tests.Routing;

public class SelectedRouteTest
{
    private static CandidateRoute MakeRoute() => new(
        new[]
        {
            new RouteLeg(new VoyageNumber("V001"), new Location("JPTYO"), new Location("DEHAM"),
                new DateTimeOffset(2026, 9, 1, 0, 0, 0, TimeSpan.Zero), new DateTimeOffset(2026, 9, 20, 0, 0, 0, TimeSpan.Zero)),
        },
        19, 1000m);

    [Fact]
    public void 経路候補を選択すると確定経路が生成される()
    {
        var selected = SelectedRoute.Confirm("BKG-0001", MakeRoute());

        selected.BookingId.Should().Be("BKG-0001");
        selected.Status.Should().Be(RouteStatus.Confirmed);
        selected.Route.VoyageNumbers.Should().ContainSingle().Which.Value.Should().Be("V001");
        selected.Version.Should().Be(0);
    }

    [Fact]
    public void 予約番号が空なら確定経路を生成できない()
    {
        var act = () => SelectedRoute.Confirm(" ", MakeRoute());

        act.Should().Throw<ArgumentException>();
    }
}

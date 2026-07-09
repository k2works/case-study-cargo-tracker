using CargoTracker.Booking.Domain.Events;
using CargoTracker.Booking.Domain.Model;
using CargoTracker.Shared.Domain.Model;
using FluentAssertions;

namespace CargoTracker.Domain.Tests.Booking;

public class CargoTest
{
    private static readonly ShipperId _shipperId = new(Guid.Parse("00000000-0000-0000-0000-000000000001"));
    private static readonly RouteSpecification _route = new(
        new Location("JPTYO"), new Location("DEHAM"), new DateOnly(2026, 9, 30));

    private static Cargo CreateCargo() => Cargo.Create(
        _shipperId, _route, CargoType.General, 1200.5m,
        new Dimensions(120m, 80m, 90m), new Quantity(2), new Description("機械部品"));

    [Fact]
    public void 貨物予約を作成すると予約番号が発行され初期状態はPreliminaryになる()
    {
        var cargo = CreateCargo();

        cargo.BookingId.Value.Should().StartWith("BKG-");
        cargo.ShipperId.Should().Be(_shipperId);
        cargo.BookingStatus.Should().Be(BookingStatus.Preliminary);
        cargo.Weight.Should().Be(1200.5m);
        cargo.Version.Should().Be(0);
        cargo.PullDomainEvents().Should().ContainSingle(e => e is CargoBookedEvent);
    }

    [Fact]
    public void 経路設計へ割り当てるとRouteProposedになる()
    {
        var cargo = CreateCargo();
        cargo.PullDomainEvents();

        cargo.AssignToRouting();

        cargo.BookingStatus.Should().Be(BookingStatus.RouteProposed);
    }

    [Theory]
    [InlineData(0)]
    [InlineData(-1)]
    public void 重量が正でないなら例外(decimal invalidWeight)
    {
        var act = () => Cargo.Create(_shipperId, _route, CargoType.General, invalidWeight);

        act.Should().Throw<ArgumentException>();
    }

    [Fact]
    public void 出発地と仕向地が同一なら例外()
    {
        var act = () => new RouteSpecification(
            new Location("JPTYO"), new Location("JPTYO"), new DateOnly(2026, 9, 30));

        act.Should().Throw<ArgumentException>();
    }

    [Theory]
    [InlineData(0, 10, 10)]
    [InlineData(10, -1, 10)]
    [InlineData(10, 10, 0)]
    public void 寸法が指定される場合はすべて正の値でなければならない(decimal length, decimal width, decimal height)
    {
        var act = () => new Dimensions(length, width, height);

        act.Should().Throw<ArgumentException>();
    }

    [Theory]
    [InlineData(0)]
    [InlineData(-1)]
    public void 個数が1未満なら例外(int value)
    {
        var act = () => new Quantity(value);

        act.Should().Throw<ArgumentException>();
    }

    [Fact]
    public void 品名が500文字を超えるなら例外()
    {
        var act = () => new Description(new string('a', 501));

        act.Should().Throw<ArgumentException>();
    }
}

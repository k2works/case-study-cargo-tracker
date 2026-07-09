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
    public void 経路設計へ割り当てるとRouteProposedになりVersionが上がりイベントが発生する()
    {
        var cargo = CreateCargo();
        cargo.PullDomainEvents();

        cargo.AssignToRouting();

        cargo.BookingStatus.Should().Be(BookingStatus.RouteProposed);
        cargo.Version.Should().Be(1);
        cargo.PullDomainEvents().Should().ContainSingle(e => e is AssignedToRoutingEvent);
    }

    [Fact]
    public void Preliminary以外から経路設計へ割り当てると例外()
    {
        var cargo = Cargo.Reconstruct(
            new BookingId("BKG-TEST-0000000001"),
            _shipperId,
            _route,
            CargoType.General,
            1200m,
            null,
            null,
            null,
            BookingStatus.RouteProposed,
            1);

        var act = () => cargo.AssignToRouting();

        act.Should().Throw<InvalidOperationException>();
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

    [Theory]
    [InlineData("", "UN1203", "Gasoline")]
    [InlineData("3", "", "Gasoline")]
    [InlineData("3", "UN1203", " ")]
    public void 危険物申告は危険物クラスとUN番号と正式輸送品名が必須(string hazardousClass, string unNumber, string properShippingName)
    {
        var act = () => new HazardousDeclaration(hazardousClass, unNumber, properShippingName);

        act.Should().Throw<ArgumentException>();
    }

    [Fact]
    public void 温度管理条件は最低温度が最高温度以下でなければならない()
    {
        var act = () => new TemperatureRequirement(5m, -10m, TemperatureUnit.Celsius);

        act.Should().Throw<ArgumentException>();
    }

    [Fact]
    public void 危険物は危険物申告が必須()
    {
        var act = () => Cargo.Create(_shipperId, _route, CargoType.Hazardous, 1200m);

        act.Should().Throw<ArgumentException>().WithMessage("*危険物申告*");
    }

    [Fact]
    public void 危険物に温度管理条件は指定できない()
    {
        var act = () => Cargo.Create(
            _shipperId, _route, CargoType.Hazardous, 1200m,
            hazardousDeclaration: new HazardousDeclaration("3", "UN1203", "Gasoline"),
            temperatureRequirement: new TemperatureRequirement(-20m, -10m, TemperatureUnit.Celsius));

        act.Should().Throw<ArgumentException>().WithMessage("*温度管理条件*");
    }

    [Fact]
    public void 冷凍冷蔵貨物は温度管理条件が必須()
    {
        var act = () => Cargo.Create(_shipperId, _route, CargoType.Refrigerated, 1200m);

        act.Should().Throw<ArgumentException>().WithMessage("*温度管理条件*");
    }

    [Fact]
    public void 冷凍冷蔵貨物に危険物申告は指定できない()
    {
        var act = () => Cargo.Create(
            _shipperId, _route, CargoType.Refrigerated, 1200m,
            hazardousDeclaration: new HazardousDeclaration("3", "UN1203", "Gasoline"),
            temperatureRequirement: new TemperatureRequirement(-20m, -10m, TemperatureUnit.Celsius));

        act.Should().Throw<ArgumentException>().WithMessage("*危険物申告*");
    }

    [Fact]
    public void 一般貨物に特別情報は指定できない()
    {
        var act = () => Cargo.Create(
            _shipperId, _route, CargoType.General, 1200m,
            hazardousDeclaration: new HazardousDeclaration("3", "UN1203", "Gasoline"));

        act.Should().Throw<ArgumentException>().WithMessage("*特別情報*");
    }

    [Fact]
    public void 危険物は危険物申告を保持できる()
    {
        var declaration = new HazardousDeclaration("3", "UN1203", "Gasoline");

        var cargo = Cargo.Create(_shipperId, _route, CargoType.Hazardous, 1200m, hazardousDeclaration: declaration);

        cargo.HazardousDeclaration.Should().Be(declaration);
        cargo.TemperatureRequirement.Should().BeNull();
    }

    [Fact]
    public void 冷凍冷蔵貨物は温度管理条件を保持できる()
    {
        var requirement = new TemperatureRequirement(-20m, -10m, TemperatureUnit.Celsius);

        var cargo = Cargo.Create(_shipperId, _route, CargoType.Refrigerated, 1200m, temperatureRequirement: requirement);

        cargo.TemperatureRequirement.Should().Be(requirement);
        cargo.HazardousDeclaration.Should().BeNull();
    }
}

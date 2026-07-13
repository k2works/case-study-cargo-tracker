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
    public void 到着期限が過去日なら予約を作成できない()
    {
        var pastRoute = new RouteSpecification(
            new Location("JPTYO"), new Location("DEHAM"), new DateOnly(2026, 9, 29));

        var act = () => Cargo.Create(
            _shipperId, pastRoute, CargoType.General, 1200m, today: new DateOnly(2026, 9, 30));

        act.Should().Throw<ArgumentException>().WithMessage("*到着期限は当日以降*");
    }

    [Fact]
    public void 到着期限が当日なら予約を作成できる()
    {
        var todayRoute = new RouteSpecification(
            new Location("JPTYO"), new Location("DEHAM"), new DateOnly(2026, 9, 30));

        var act = () => Cargo.Create(
            _shipperId, todayRoute, CargoType.General, 1200m, today: new DateOnly(2026, 9, 30));

        act.Should().NotThrow();
    }

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

    // --- US11: 経路情報を予約に紐付ける ---

    private static CargoItinerary CreateItinerary() => new(new[]
    {
        new Leg(new VoyageNumber("V001"), new Location("JPTYO"), new Location("SGSIN"),
            new DateTimeOffset(2026, 9, 1, 0, 0, 0, TimeSpan.Zero), new DateTimeOffset(2026, 9, 10, 0, 0, 0, TimeSpan.Zero)),
        new Leg(new VoyageNumber("V002"), new Location("SGSIN"), new Location("DEHAM"),
            new DateTimeOffset(2026, 9, 12, 0, 0, 0, TimeSpan.Zero), new DateTimeOffset(2026, 9, 25, 0, 0, 0, TimeSpan.Zero)),
    });

    private static Cargo RouteProposedCargo() => Cargo.Reconstruct(
        new BookingId("BKG-TEST-0000000001"), _shipperId, _route, CargoType.General, 1200m,
        null, null, null, BookingStatus.RouteProposed, 1);

    [Fact]
    public void 旅程のLeg連結制約を満たさない場合は例外()
    {
        var act = () => new CargoItinerary(new[]
        {
            new Leg(new VoyageNumber("V001"), new Location("JPTYO"), new Location("SGSIN"),
                new DateTimeOffset(2026, 9, 1, 0, 0, 0, TimeSpan.Zero), new DateTimeOffset(2026, 9, 10, 0, 0, 0, TimeSpan.Zero)),
            new Leg(new VoyageNumber("V002"), new Location("USNYC"), new Location("DEHAM"),
                new DateTimeOffset(2026, 9, 12, 0, 0, 0, TimeSpan.Zero), new DateTimeOffset(2026, 9, 25, 0, 0, 0, TimeSpan.Zero)),
        });

        act.Should().Throw<ArgumentException>().WithMessage("*連結*");
    }

    [Fact]
    public void 経路をRouteProposedの予約に割り当てると旅程が保持されVersionが上がる()
    {
        var cargo = RouteProposedCargo();
        var itinerary = CreateItinerary();

        cargo.AssignItinerary(itinerary);

        cargo.CargoItinerary.Should().Be(itinerary);
        cargo.BookingStatus.Should().Be(BookingStatus.RouteProposed);
        cargo.Version.Should().Be(2);
    }

    [Fact]
    public void RouteProposed以外へ経路を割り当てると例外()
    {
        var cargo = CreateCargo();

        var act = () => cargo.AssignItinerary(CreateItinerary());

        act.Should().Throw<InvalidOperationException>();
    }

    // --- US13: 予約を確定する ---

    [Fact]
    public void 旅程割当済みのRouteProposedを確定するとConfirmedになる()
    {
        var cargo = RouteProposedCargo();
        cargo.AssignItinerary(CreateItinerary());

        cargo.Confirm();

        cargo.BookingStatus.Should().Be(BookingStatus.Confirmed);
        cargo.Version.Should().Be(3);
    }

    [Fact]
    public void 旅程未割当のまま確定すると例外()
    {
        var cargo = RouteProposedCargo();

        var act = () => cargo.Confirm();

        act.Should().Throw<InvalidOperationException>().WithMessage("*経路*");
    }

    [Fact]
    public void RouteProposedを経路再設計に差し戻すとPreliminaryになる()
    {
        var cargo = RouteProposedCargo();

        cargo.ReturnToRouting();

        cargo.BookingStatus.Should().Be(BookingStatus.Preliminary);
        cargo.Version.Should().Be(2);
    }

    [Fact]
    public void 予約をキャンセルするとCancelledになる()
    {
        var cargo = RouteProposedCargo();

        cargo.Cancel();

        cargo.BookingStatus.Should().Be(BookingStatus.Cancelled);
    }

    [Fact]
    public void 確定済みの予約はキャンセルできない()
    {
        var cargo = RouteProposedCargo();
        cargo.AssignItinerary(CreateItinerary());
        cargo.Confirm();

        var act = () => cargo.Cancel();

        act.Should().Throw<InvalidOperationException>();
    }

    // --- US12: 確定経路を荷主に通知する ---

    [Fact]
    public void 旅程割当済みの予約から通知記録を生成できる()
    {
        var cargo = RouteProposedCargo();
        cargo.AssignItinerary(CreateItinerary());
        var notifiedAt = new DateTimeOffset(2026, 8, 20, 9, 0, 0, TimeSpan.Zero);

        var notification = RouteNotification.Create(cargo, notifiedAt);

        notification.BookingId.Should().Be(cargo.BookingId);
        notification.NotifiedAt.Should().Be(notifiedAt);
        notification.ExpectedArrivalTime.Should().Be(new DateTimeOffset(2026, 9, 25, 0, 0, 0, TimeSpan.Zero));
    }

    [Fact]
    public void 旅程未割当の予約は通知できない()
    {
        var cargo = RouteProposedCargo();

        var act = () => RouteNotification.Create(cargo, DateTimeOffset.UtcNow);

        act.Should().Throw<InvalidOperationException>().WithMessage("*経路*");
    }

    [Fact]
    public void 経路提案中でない予約は通知できない()
    {
        var cargo = CreateCargo();

        var act = () => RouteNotification.Create(cargo, DateTimeOffset.UtcNow);

        act.Should().Throw<InvalidOperationException>();
    }

    // --- US14: 追跡番号発行に連動した状態遷移 ---

    [Fact]
    public void 確定済みの予約は追跡発行でTrackingIssuedになる()
    {
        var cargo = RouteProposedCargo();
        cargo.AssignItinerary(CreateItinerary());
        cargo.Confirm();

        cargo.IssueTracking();

        cargo.BookingStatus.Should().Be(BookingStatus.TrackingIssued);
    }

    [Fact]
    public void 確定前の予約は追跡発行できない()
    {
        var cargo = RouteProposedCargo();

        var act = () => cargo.IssueTracking();

        act.Should().Throw<InvalidOperationException>();
    }
}

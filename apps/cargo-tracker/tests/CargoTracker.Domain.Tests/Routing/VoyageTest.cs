using CargoTracker.Routing.Domain.Model;
using CargoTracker.Routing.Domain.Model.Events;
using CargoTracker.Shared.Domain.Model;
using FluentAssertions;

namespace CargoTracker.Domain.Tests.Routing;

public sealed class VoyageTest
{
    private static readonly DateTimeOffset _baseTime = new(2026, 9, 1, 10, 0, 0, TimeSpan.Zero);

    [Fact]
    public void 航海を作成すると初期Versionは0で登録イベントが発生する()
    {
        var voyage = CreateVoyage();

        voyage.VoyageNumber.Value.Should().Be("VYG-001");
        voyage.VesselName.Should().Be("Kiso Maru");
        voyage.Carrier.Should().Be("Ocean Network");
        voyage.SupportedCargoTypes.Should().Contain(SupportedCargoType.General);
        voyage.SupportedCargoTypes.Should().Contain(SupportedCargoType.Refrigerated);
        voyage.Version.Should().Be(0);
        voyage.PullDomainEvents().Should().ContainSingle(e => e is VoyageRegisteredEvent);
    }

    [Fact]
    public void 航海番号は必須()
    {
        var act = () => new VoyageNumber(" ");

        act.Should().Throw<ArgumentException>().WithMessage("*航海番号*");
    }

    [Fact]
    public void 出発地と到着地が同一の区間は作成できない()
    {
        var act = () => new CarrierMovement(
            new Location("JPTYO"), new Location("JPTYO"), _baseTime, _baseTime.AddDays(1), 1);

        act.Should().Throw<ArgumentException>().WithMessage("*出発地*");
    }

    [Fact]
    public void 出発日時が到着日時より後の区間は作成できない()
    {
        var act = () => new CarrierMovement(
            new Location("JPTYO"), new Location("DEHAM"), _baseTime.AddDays(2), _baseTime, 1);

        act.Should().Throw<ArgumentException>().WithMessage("*出発日時*");
    }

    [Fact]
    public void Scheduleは区間を時系列順で保持する()
    {
        var second = Movement("SGSIN", "DEHAM", 2, 3, 2);
        var first = Movement("JPTYO", "SGSIN", 0, 1, 1);

        var schedule = new Schedule([second, first]);

        schedule.CarrierMovements[0].DepartureLocation.UnLocode.Should().Be("JPTYO");
        schedule.CarrierMovements[1].DepartureLocation.UnLocode.Should().Be("SGSIN");
    }

    [Fact]
    public void Scheduleの区間が時系列順でないなら例外()
    {
        var first = Movement("JPTYO", "SGSIN", 0, 3, 1);
        var second = Movement("SGSIN", "DEHAM", 2, 4, 2);

        var act = () => new Schedule([first, second]);

        act.Should().Throw<ArgumentException>().WithMessage("*時系列*");
    }

    [Fact]
    public void VoyageのScheduleが空なら例外()
    {
        var act = () => Voyage.Create(
            new VoyageNumber("VYG-001"), "Kiso Maru", "Ocean Network",
            [SupportedCargoType.General], new Schedule([]));

        act.Should().Throw<ArgumentException>().WithMessage("*運送区間*");
    }

    [Fact]
    public void 対応貨物種別が空なら例外()
    {
        var act = () => Voyage.Create(
            new VoyageNumber("VYG-001"), "Kiso Maru", "Ocean Network",
            [], new Schedule([Movement("JPTYO", "DEHAM", 0, 1, 1)]));

        act.Should().Throw<ArgumentException>().WithMessage("*対応貨物種別*");
    }

    [Fact]
    public void 前区間の到着港と次区間の出発港が一致しないなら例外()
    {
        var schedule = new Schedule([
            Movement("JPTYO", "SGSIN", 0, 1, 1),
            Movement("CNSHA", "DEHAM", 2, 3, 2),
        ]);

        var act = () => Voyage.Create(
            new VoyageNumber("VYG-001"), "Kiso Maru", "Ocean Network",
            [SupportedCargoType.General], schedule);

        act.Should().Throw<ArgumentException>().WithMessage("*到着港*出発港*");
    }

    [Fact]
    public void Reconstructではイベントが発生しない()
    {
        var voyage = Voyage.Reconstruct(
            new VoyageNumber("VYG-001"), "Kiso Maru", "Ocean Network",
            [SupportedCargoType.General], new Schedule([Movement("JPTYO", "DEHAM", 0, 1, 1)]), 3);

        voyage.Version.Should().Be(3);
        voyage.PullDomainEvents().Should().BeEmpty();
    }

    private static Voyage CreateVoyage()
        => Voyage.Create(
            new VoyageNumber("VYG-001"),
            "Kiso Maru",
            "Ocean Network",
            [SupportedCargoType.General, SupportedCargoType.Refrigerated],
            new Schedule([
                Movement("JPTYO", "SGSIN", 0, 1, 1),
                Movement("SGSIN", "DEHAM", 2, 3, 2),
            ]));

    private static CarrierMovement Movement(string departure, string arrival, int departureOffsetDays, int arrivalOffsetDays, int sequence)
        => new(
            new Location(departure),
            new Location(arrival),
            _baseTime.AddDays(departureOffsetDays),
            _baseTime.AddDays(arrivalOffsetDays),
            sequence);
}

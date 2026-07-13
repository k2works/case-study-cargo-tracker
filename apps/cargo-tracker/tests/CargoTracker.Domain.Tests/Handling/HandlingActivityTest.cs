using CargoTracker.Handling.Domain.Model;
using FluentAssertions;

namespace CargoTracker.Domain.Tests.Handling;

public class HandlingActivityTest
{
    private static CargoSnapshot Snapshot() => new(
        "BKG-0001", "JPTYO", "DEHAM",
        new[]
        {
            new LegSnapshot("JPTYO", "SGSIN", "V001"),
            new LegSnapshot("SGSIN", "DEHAM", "V002"),
        });

    [Fact]
    public void 積込荷降しには航海番号が必須()
    {
        var act = () => HandlingActivity.Register(
            "BKG-0001", HandlingEventType.Load, "JPTYO", DateTimeOffset.UtcNow);

        act.Should().Throw<ArgumentException>().WithMessage("*航海番号*");
    }

    [Fact]
    public void 受領は出発港と一致すれば妥当()
    {
        var receive = HandlingActivity.Register(
            "BKG-0001", HandlingEventType.Receive, "JPTYO", DateTimeOffset.UtcNow);

        receive.IsValidFor(Snapshot()).Should().BeTrue();
        receive.IsMisrouteWhenInvalid().Should().BeFalse();
    }

    [Fact]
    public void 受領が出発港と異なると妥当でない_ただし警告扱い()
    {
        var receive = HandlingActivity.Register(
            "BKG-0001", HandlingEventType.Receive, "CNSHA", DateTimeOffset.UtcNow);

        receive.IsValidFor(Snapshot()).Should().BeFalse();
        receive.IsMisrouteWhenInvalid().Should().BeFalse();
    }

    [Fact]
    public void 積込は旅程の積込港と航海番号が一致すれば妥当()
    {
        var load = HandlingActivity.Register(
            "BKG-0001", HandlingEventType.Load, "JPTYO", DateTimeOffset.UtcNow, "V001");

        load.IsValidFor(Snapshot()).Should().BeTrue();
    }

    [Fact]
    public void 積込が旅程外の港ならMISROUTED扱い()
    {
        var load = HandlingActivity.Register(
            "BKG-0001", HandlingEventType.Load, "USNYC", DateTimeOffset.UtcNow, "V001");

        load.IsValidFor(Snapshot()).Should().BeFalse();
        load.IsMisrouteWhenInvalid().Should().BeTrue();
    }

    [Fact]
    public void 荷降しは旅程の荷降港と一致すれば妥当()
    {
        var unload = HandlingActivity.Register(
            "BKG-0001", HandlingEventType.Unload, "DEHAM", DateTimeOffset.UtcNow, "V002");

        unload.IsValidFor(Snapshot()).Should().BeTrue();
    }
}

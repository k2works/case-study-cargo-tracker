using CargoTracker.Tracking.Domain.Model;
using FluentAssertions;

namespace CargoTracker.Domain.Tests.Tracking;

public class TrackingActivityTest
{
    [Fact]
    public void 予約に追跡番号を発行すると受領待ちで開始する()
    {
        var tracking = TrackingActivity.Issue("BKG-TEST-0000000001");

        tracking.TrackingNumber.Value.Should().Be("TRK-TEST-0000000001");
        tracking.BookingId.Value.Should().Be("BKG-TEST-0000000001");
        tracking.CurrentStatus().Should().Be(TrackingStatus.NotReceived);
        tracking.Version.Should().Be(0);
    }

    [Fact]
    public void 予約番号が空なら追跡番号を発行できない()
    {
        var act = () => TrackingActivity.Issue(" ");

        act.Should().Throw<ArgumentException>();
    }

    [Fact]
    public void 受領イベントを追加すると受領済になる()
    {
        var tracking = TrackingActivity.Issue("BKG-0001");

        tracking.AddEvent(new TrackingActivityEvent(
            TrackingEventType.Receive, new TrackingLocation("JPTYO"),
            new DateTimeOffset(2026, 9, 1, 0, 0, 0, TimeSpan.Zero)));

        tracking.CurrentStatus().Should().Be(TrackingStatus.Received);
        tracking.Version.Should().Be(1);
    }

    [Fact]
    public void 最新イベントの種別が現在状態を決める()
    {
        var tracking = TrackingActivity.Issue("BKG-0002");
        tracking.AddEvent(new TrackingActivityEvent(
            TrackingEventType.Receive, new TrackingLocation("JPTYO"), new DateTimeOffset(2026, 9, 1, 0, 0, 0, TimeSpan.Zero)));
        tracking.AddEvent(new TrackingActivityEvent(
            TrackingEventType.Load, new TrackingLocation("JPTYO"), new DateTimeOffset(2026, 9, 2, 0, 0, 0, TimeSpan.Zero)));

        tracking.CurrentStatus().Should().Be(TrackingStatus.Loaded);
        tracking.Events.Should().HaveCount(2);
    }
}

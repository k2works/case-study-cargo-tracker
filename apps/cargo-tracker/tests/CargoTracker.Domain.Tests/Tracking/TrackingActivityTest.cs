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

    private static TrackingExceptionEvent ExceptionEvent(
        ExceptionType type, string description = "状況説明") =>
        new(type, new TrackingLocation("USLAX"),
            new DateTimeOffset(2026, 10, 8, 14, 0, 0, TimeSpan.Zero), description);

    [Fact]
    public void 遅延例外を登録すると例外発生状態になる()
    {
        var tracking = TrackingActivity.Issue("BKG-EX-0001");
        tracking.AddEvent(new TrackingActivityEvent(
            TrackingEventType.Load, new TrackingLocation("JPTYO"), new DateTimeOffset(2026, 10, 1, 0, 0, 0, TimeSpan.Zero)));

        tracking.AddException(ExceptionEvent(ExceptionType.Delay));

        tracking.CurrentStatus().Should().Be(TrackingStatus.Exception);
        tracking.HasActiveException().Should().BeTrue();
        tracking.Exceptions.Should().ContainSingle();
    }

    [Fact]
    public void 紛失例外はエスカレーションフラグが立つ()
    {
        var tracking = TrackingActivity.Issue("BKG-EX-0002");

        tracking.AddException(ExceptionEvent(ExceptionType.Lost));

        tracking.Exceptions[^1].EscalationFlag.Should().BeTrue();
    }

    [Fact]
    public void 遅延や破損例外はエスカレーションしない()
    {
        var tracking = TrackingActivity.Issue("BKG-EX-0003");

        tracking.AddException(ExceptionEvent(ExceptionType.Delay));
        tracking.AddException(ExceptionEvent(ExceptionType.Damage));

        tracking.Exceptions.Should().OnlyContain(e => e.EscalationFlag == false);
    }

    [Fact]
    public void 例外を解決すると例外発生前の状態に復帰する()
    {
        var tracking = TrackingActivity.Issue("BKG-EX-0004");
        tracking.AddEvent(new TrackingActivityEvent(
            TrackingEventType.Load, new TrackingLocation("JPTYO"), new DateTimeOffset(2026, 10, 1, 0, 0, 0, TimeSpan.Zero)));
        tracking.AddException(ExceptionEvent(ExceptionType.Delay));
        tracking.CurrentStatus().Should().Be(TrackingStatus.Exception);

        tracking.ResolveException(
            new DateTimeOffset(2026, 10, 9, 0, 0, 0, TimeSpan.Zero), "新到着予定日を提示");

        tracking.HasActiveException().Should().BeFalse();
        tracking.CurrentStatus().Should().Be(TrackingStatus.Loaded);
        tracking.Exceptions[^1].ResolvedAt.Should().NotBeNull();
    }

    [Fact]
    public void 未解決の例外がなければ解決できない()
    {
        var tracking = TrackingActivity.Issue("BKG-EX-0005");

        var act = () => tracking.ResolveException(
            new DateTimeOffset(2026, 10, 9, 0, 0, 0, TimeSpan.Zero), "対応");

        act.Should().Throw<InvalidOperationException>();
    }
}

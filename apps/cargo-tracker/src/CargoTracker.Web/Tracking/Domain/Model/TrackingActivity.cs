using CargoTracker.Shared.Domain.Model;

namespace CargoTracker.Tracking.Domain.Model;

/// <summary>
/// 追跡レコード（US14）。予約確定後に追跡番号を発行して作成される集約ルート。
/// 荷役・手動更新の追跡イベントを時系列で保持し、現在の輸送状態を導出する。
/// </summary>
public sealed class TrackingActivity : AggregateRoot
{
    private readonly List<TrackingActivityEvent> _events;

    public TrackingNumber TrackingNumber { get; }
    public TrackingBookingId BookingId { get; }
    public IReadOnlyList<TrackingActivityEvent> Events => _events;
    public long Version { get; private set; }

    private TrackingActivity(
        TrackingNumber trackingNumber, TrackingBookingId bookingId,
        IEnumerable<TrackingActivityEvent> events, long version)
    {
        TrackingNumber = trackingNumber;
        BookingId = bookingId;
        _events = events.ToList();
        Version = version;
    }

    /// <summary>予約に対して追跡番号を発行し追跡を開始する（US14）。初期状態は受領待ち（NotReceived）。</summary>
    public static TrackingActivity Issue(string bookingId)
    {
        var trackingBookingId = new TrackingBookingId(bookingId);
        var trackingNumber = TrackingNumber.Generate(bookingId);
        return new TrackingActivity(trackingNumber, trackingBookingId, [], 0);
    }

    /// <summary>追跡イベントを時系列で追加する（US15/US17）。</summary>
    public void AddEvent(TrackingActivityEvent activityEvent)
    {
        ArgumentNullException.ThrowIfNull(activityEvent);
        _events.Add(activityEvent);
        Version++;
    }

    /// <summary>現在の輸送状態を導出する。イベントがなければ受領待ち（NotReceived）。</summary>
    public TrackingStatus CurrentStatus()
        => _events.Count == 0
            ? TrackingStatus.NotReceived
            : _events[^1].ToStatus();

    /// <summary>永続化データから再構築する。</summary>
    public static TrackingActivity Reconstruct(
        TrackingNumber trackingNumber, TrackingBookingId bookingId,
        IEnumerable<TrackingActivityEvent> events, long version)
        => new(trackingNumber, bookingId, events, version);
}

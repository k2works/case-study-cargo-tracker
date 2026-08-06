namespace CargoTracker.Tracking.Domain.Model;

/// <summary>追跡イベント（集約内エンティティ）。荷役・手動更新で時系列に追記される出来事。</summary>
public sealed class TrackingActivityEvent
{
    public TrackingEventType EventType { get; }
    public TrackingLocation Location { get; }
    public DateTimeOffset CompletionTime { get; }
    public TrackingVoyageNumber? VoyageNumber { get; }

    public TrackingActivityEvent(
        TrackingEventType eventType, TrackingLocation location, DateTimeOffset completionTime,
        TrackingVoyageNumber? voyageNumber = null)
    {
        ArgumentNullException.ThrowIfNull(location);
        EventType = eventType;
        Location = location;
        CompletionTime = completionTime;
        VoyageNumber = voyageNumber;
    }

    /// <summary>イベント種別に対応する輸送状態を返す。</summary>
    public TrackingStatus ToStatus() => EventType switch
    {
        TrackingEventType.Receive => TrackingStatus.Received,
        TrackingEventType.Load => TrackingStatus.Loaded,
        TrackingEventType.Unload => TrackingStatus.Unloaded,
        TrackingEventType.Claim => TrackingStatus.Claimed,
        _ => TrackingStatus.Unknown,
    };
}

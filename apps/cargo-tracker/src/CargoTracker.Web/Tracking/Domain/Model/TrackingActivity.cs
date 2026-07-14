using CargoTracker.Shared.Domain.Model;
using CargoTracker.Tracking.Domain.Events;

namespace CargoTracker.Tracking.Domain.Model;

/// <summary>
/// 追跡レコード（US14）。予約確定後に追跡番号を発行して作成される集約ルート。
/// 荷役・手動更新の追跡イベントを時系列で保持し、現在の輸送状態を導出する。
/// </summary>
public sealed class TrackingActivity : AggregateRoot
{
    private readonly List<TrackingActivityEvent> _events;
    private readonly List<TrackingExceptionEvent> _exceptions;

    public TrackingNumber TrackingNumber { get; }
    public TrackingBookingId BookingId { get; }
    public IReadOnlyList<TrackingActivityEvent> Events => _events;
    public IReadOnlyList<TrackingExceptionEvent> Exceptions => _exceptions;
    public long Version { get; private set; }

    private TrackingActivity(
        TrackingNumber trackingNumber, TrackingBookingId bookingId,
        IEnumerable<TrackingActivityEvent> events, long version,
        IEnumerable<TrackingExceptionEvent>? exceptions = null)
    {
        TrackingNumber = trackingNumber;
        BookingId = bookingId;
        _events = events.ToList();
        _exceptions = exceptions?.ToList() ?? [];
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

    /// <summary>
    /// 例外事象を登録する（US19/US20）。登録中は現在状態が例外発生（Exception）になり、
    /// TrackingExceptionDetectedEvent を post-commit で発行して Booking へ伝える（ADR-0009）。
    /// 不変条件: 同時に未解決の例外は 1 件まで（UI の単一対応報告フォームと整合し、二重登録・通知重複を防ぐ）。
    /// 追加の例外を登録する前に、既存の未解決例外を ResolveException で解決する必要がある。
    /// </summary>
    public void AddException(TrackingExceptionEvent exceptionEvent)
    {
        ArgumentNullException.ThrowIfNull(exceptionEvent);
        if (HasActiveException())
        {
            throw new InvalidOperationException("未解決の例外があります。先に対応報告で解決してください。");
        }
        _exceptions.Add(exceptionEvent);
        Version++;
        AddDomainEvent(new TrackingExceptionDetectedEvent(
            BookingId.Value,
            TrackingNumber.Value,
            exceptionEvent.ExceptionType.ToString().ToUpperInvariant(),
            exceptionEvent.Location.UnLocode,
            exceptionEvent.EscalationFlag,
            exceptionEvent.OccurredAt));
    }

    /// <summary>未解決の例外があるか（domain-model の HasActiveException）。</summary>
    public bool HasActiveException() => _exceptions.Any(e => e.IsActive);

    /// <summary>
    /// 未解決の例外を解決し、輸送状態を例外発生前（イベント由来）の状態へ復帰させる
    /// （US19/US20 の対応報告・domain-model ビジネスルール 5）。
    /// </summary>
    public void ResolveException(DateTimeOffset resolvedAt, string? resolutionNotes)
    {
        var active = _exceptions.LastOrDefault(e => e.IsActive)
            ?? throw new InvalidOperationException("未解決の例外がありません。");
        active.Resolve(resolvedAt, resolutionNotes);
        Version++;
        AddDomainEvent(new TrackingExceptionResolvedEvent(
            BookingId.Value,
            TrackingNumber.Value,
            active.ExceptionType.ToString().ToUpperInvariant(),
            resolvedAt,
            resolutionNotes));
    }

    /// <summary>
    /// 現在の輸送状態を導出する。未解決の例外があれば例外発生（Exception）、
    /// なければ最新イベントから導出する。イベントがなければ受領待ち（NotReceived）。
    /// </summary>
    public TrackingStatus CurrentStatus()
    {
        if (HasActiveException())
        {
            return TrackingStatus.Exception;
        }
        return _events.Count == 0
            ? TrackingStatus.NotReceived
            : _events[^1].ToStatus();
    }

    /// <summary>永続化データから再構築する。</summary>
    public static TrackingActivity Reconstruct(
        TrackingNumber trackingNumber, TrackingBookingId bookingId,
        IEnumerable<TrackingActivityEvent> events, long version,
        IEnumerable<TrackingExceptionEvent>? exceptions = null)
        => new(trackingNumber, bookingId, events, version, exceptions);
}

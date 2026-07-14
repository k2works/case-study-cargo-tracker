namespace CargoTracker.Tracking.Domain.Model;

/// <summary>
/// 追跡例外イベント（集約内エンティティ・US19/US20）。遅延・破損・紛失などの例外事象を時系列で記録する。
/// 紛失（Lost）は登録時にエスカレーションフラグが立ち、上位管理者へのエスカレーション対象となる（domain-model ビジネスルール 3）。
/// </summary>
public sealed class TrackingExceptionEvent
{
    public ExceptionType ExceptionType { get; }
    public TrackingLocation Location { get; }
    public DateTimeOffset OccurredAt { get; }
    public string? Description { get; }
    public bool EscalationFlag { get; }
    public DateTimeOffset? ResolvedAt { get; private set; }
    public string? ResolutionNotes { get; private set; }

    public TrackingExceptionEvent(
        ExceptionType exceptionType, TrackingLocation location,
        DateTimeOffset occurredAt, string? description,
        DateTimeOffset? resolvedAt = null, string? resolutionNotes = null)
    {
        ArgumentNullException.ThrowIfNull(location);
        ExceptionType = exceptionType;
        Location = location;
        OccurredAt = occurredAt;
        Description = description;
        // 紛失は重大例外のためエスカレーション必須（domain-model BR3）。
        EscalationFlag = exceptionType == ExceptionType.Lost;
        ResolvedAt = resolvedAt;
        ResolutionNotes = resolutionNotes;
    }

    /// <summary>未解決（対応中）かどうか。</summary>
    public bool IsActive => ResolvedAt is null;

    /// <summary>例外を解決済みにする（US19/US20 の対応報告）。</summary>
    public void Resolve(DateTimeOffset resolvedAt, string? resolutionNotes)
    {
        ResolvedAt = resolvedAt;
        ResolutionNotes = resolutionNotes;
    }
}

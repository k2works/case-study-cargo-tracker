using MediatR;

namespace CargoTracker.Tracking.Domain.Events;

/// <summary>
/// 追跡例外が検知されたことを表すドメインイベント（US19/US20）。
/// Booking Context が消費し、荷主通知・予約側の例外把握に用いる（ADR-0009 の結果整合性方針に従う）。
/// BC 独立のためプリミティブのみを運ぶ。
/// </summary>
public sealed record TrackingExceptionDetectedEvent(
    string BookingId,
    string TrackingNumber,
    string ExceptionType,
    string LocationUnLocode,
    bool EscalationFlag,
    DateTimeOffset OccurredAt) : INotification;

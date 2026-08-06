using MediatR;

namespace CargoTracker.Tracking.Domain.Events;

/// <summary>
/// 追跡例外が解決されたことを表すドメインイベント（US19/US20 の対応報告）。
/// 荷主への対応報告通知（記録で代替）に用いる（ADR-0009 の結果整合性方針に従う）。
/// BC 独立のためプリミティブのみを運ぶ。
/// </summary>
public sealed record TrackingExceptionResolvedEvent(
    string BookingId,
    string TrackingNumber,
    string ExceptionType,
    DateTimeOffset ResolvedAt,
    string? ResolutionNotes) : INotification;

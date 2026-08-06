using MediatR;

namespace CargoTracker.Handling.Domain.Events;

/// <summary>
/// 荷役作業が登録されたことを表すドメインイベント（US15）。
/// Tracking Context（追跡イベント追記）・Booking Context（状態同期）が消費する。
/// BC 独立のためプリミティブのみを運ぶ。
/// </summary>
public sealed record HandlingActivityRegisteredEvent(
    string BookingId,
    string EventType,
    string LocationUnLocode,
    string? VoyageNumber,
    DateTimeOffset CompletionTime,
    bool IsMisrouted) : INotification;

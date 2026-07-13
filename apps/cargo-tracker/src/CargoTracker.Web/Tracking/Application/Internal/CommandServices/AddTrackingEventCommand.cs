namespace CargoTracker.Tracking.Application.Internal.CommandServices;

/// <summary>追跡イベントを手動追記するコマンド（US17）。追跡管理者が状態・位置・日時を更新する。</summary>
public sealed record AddTrackingEventCommand(
    string TrackingNumber,
    string EventType,
    string LocationUnLocode,
    DateTimeOffset EventTime,
    string? VoyageNumber = null);

namespace CargoTracker.Tracking.Application.Internal.CommandServices;

/// <summary>追跡例外を登録するコマンド（US19/US20）。追跡管理者が遅延・破損・紛失を記録する。</summary>
public sealed record RegisterExceptionCommand(
    string TrackingNumber,
    string ExceptionType,
    string LocationUnLocode,
    DateTimeOffset OccurredAt,
    string? Description = null);

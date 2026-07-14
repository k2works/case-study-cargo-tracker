namespace CargoTracker.Tracking.Application.Internal.CommandServices;

/// <summary>追跡例外を解決するコマンド（US19/US20 の対応報告）。追跡管理者が対応内容を記録し状態を復帰させる。</summary>
public sealed record ResolveExceptionCommand(
    string TrackingNumber,
    DateTimeOffset ResolvedAt,
    string? ResolutionNotes = null);

namespace CargoTracker.Handling.Application.Internal.CommandServices;

/// <summary>荷役作業を登録するコマンド（US15）。追跡番号ではなく予約番号で貨物を特定する。</summary>
public sealed record RegisterHandlingActivityCommand(
    string BookingId,
    string EventType,
    string LocationUnLocode,
    DateTimeOffset CompletionTime,
    string? VoyageNumber = null);

/// <summary>荷役登録の結果（予定外場所の警告有無）。</summary>
public sealed record RegisterHandlingActivityResult(bool IsMisrouted, bool IsOffRoute);

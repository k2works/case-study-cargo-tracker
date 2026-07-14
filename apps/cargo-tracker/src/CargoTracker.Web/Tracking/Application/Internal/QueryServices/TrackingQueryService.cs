using CargoTracker.Shared.Application.Persistence;
using Dapper;

namespace CargoTracker.Tracking.Application.Internal.QueryServices;

/// <summary>追跡イベント 1 件（読取・時系列タイムライン用）。</summary>
public sealed class TrackingEventView
{
    public string EventType { get; set; } = string.Empty;
    public DateTime EventTime { get; set; }
    public string LocationUnlocode { get; set; } = string.Empty;
    public string? VoyageNumber { get; set; }
}

/// <summary>追跡例外 1 件（読取・US19/US20）。</summary>
public sealed class TrackingExceptionView
{
    public string ExceptionType { get; set; } = string.Empty;
    public string LocationUnlocode { get; set; } = string.Empty;
    public DateTime OccurredAt { get; set; }
    public bool EscalationFlag { get; set; }
    public string? Description { get; set; }
    public DateTime? ResolvedAt { get; set; }
    public string? ResolutionNotes { get; set; }

    public bool IsActive => ResolvedAt is null;
}

/// <summary>追跡照会の詳細（US18）。</summary>
public sealed class TrackingDetailView
{
    public string TrackingNumber { get; set; } = string.Empty;
    public string BookingId { get; set; } = string.Empty;
    public string TransportStatus { get; set; } = string.Empty;
    public string? CurrentLocation { get; set; }
    public DateTime? EstimatedArrival { get; set; }
    public IReadOnlyList<TrackingEventView> Events { get; set; } = [];
    public IReadOnlyList<TrackingExceptionView> Exceptions { get; set; } = [];

    /// <summary>未解決の例外があるか（EXCEPTION バッジ表示用）。</summary>
    public bool HasActiveException => Exceptions.Any(e => e.IsActive);
}

/// <summary>追跡照会の読取サービス（US18）。追跡番号から状態・現在地・イベント履歴・推定到着日を取得する。</summary>
public sealed class TrackingQueryService(IDbConnectionFactory connectionFactory)
{
    public async Task<TrackingDetailView?> FindByTrackingNumberAsync(string trackingNumber, CancellationToken ct = default)
    {
        using var connection = connectionFactory.Create();
        var normalized = trackingNumber.Trim().ToUpperInvariant();
        var header = await connection.QuerySingleOrDefaultAsync<TrackingDetailView>(new CommandDefinition(
            "SELECT tracking_number AS TrackingNumber, booking_id AS BookingId, transport_status AS TransportStatus FROM tracking_activity WHERE tracking_number = @TrackingNumber",
            new { TrackingNumber = normalized }, cancellationToken: ct));
        if (header is null)
        {
            return null;
        }

        var events = (await connection.QueryAsync<TrackingEventView>(new CommandDefinition(
            """
            SELECT event_type AS EventType, event_time AS EventTime,
                   location_unlocode AS LocationUnlocode, voyage_number AS VoyageNumber
            FROM tracking_handling_event
            WHERE tracking_id = (SELECT id FROM tracking_activity WHERE tracking_number = @TrackingNumber)
            ORDER BY seq_number
            """,
            new { TrackingNumber = normalized }, cancellationToken: ct))).ToList();

        header.Events = events;

        header.Exceptions = (await connection.QueryAsync<TrackingExceptionView>(new CommandDefinition(
            """
            SELECT exception_type AS ExceptionType, location_unlocode AS LocationUnlocode,
                   occurred_at AS OccurredAt, escalation_flag AS EscalationFlag,
                   description AS Description, resolved_at AS ResolvedAt, resolution_notes AS ResolutionNotes
            FROM tracking_exception_event
            WHERE tracking_id = (SELECT id FROM tracking_activity WHERE tracking_number = @TrackingNumber)
            ORDER BY id
            """,
            new { TrackingNumber = normalized }, cancellationToken: ct))).ToList();

        header.CurrentLocation = events.Count > 0 ? events[^1].LocationUnlocode : null;
        header.EstimatedArrival = await connection.ExecuteScalarAsync<DateTime?>(new CommandDefinition(
            """
            SELECT MAX(unload_time) FROM leg
            WHERE cargo_id = (SELECT id FROM cargo WHERE booking_id = @BookingId)
            """,
            new { header.BookingId }, cancellationToken: ct));
        return header;
    }
}

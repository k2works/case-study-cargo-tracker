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

/// <summary>追跡照会の詳細（US18）。</summary>
public sealed class TrackingDetailView
{
    public string TrackingNumber { get; set; } = string.Empty;
    public string BookingId { get; set; } = string.Empty;
    public string TransportStatus { get; set; } = string.Empty;
    public string? CurrentLocation { get; set; }
    public DateTime? EstimatedArrival { get; set; }
    public IReadOnlyList<TrackingEventView> Events { get; set; } = [];
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

using CargoTracker.Shared.Application.Persistence;
using Dapper;

namespace CargoTracker.Handling.Application.Internal.QueryServices;

/// <summary>荷役作業履歴の 1 行（読取専用）。</summary>
public sealed class HandlingActivitySummary
{
    public string BookingId { get; set; } = string.Empty;
    public string EventType { get; set; } = string.Empty;
    public DateTime EventCompletionTime { get; set; }
    public string LocationUnlocode { get; set; } = string.Empty;
    public string? VoyageNumber { get; set; }
}

/// <summary>荷役作業履歴の読取サービス（US15/US17）。</summary>
public sealed class HandlingActivityQueryService(IDbConnectionFactory connectionFactory)
{
    public async Task<IReadOnlyList<HandlingActivitySummary>> FindRecentAsync(CancellationToken ct = default)
    {
        using var connection = connectionFactory.Create();
        var items = await connection.QueryAsync<HandlingActivitySummary>(new CommandDefinition(
            """
            SELECT booking_id AS BookingId, event_type AS EventType,
                   event_completion_time AS EventCompletionTime,
                   location_unlocode AS LocationUnlocode, voyage_number AS VoyageNumber
            FROM handling_activity
            ORDER BY event_completion_time DESC, id DESC
            """,
            cancellationToken: ct));
        return items.ToList();
    }
}

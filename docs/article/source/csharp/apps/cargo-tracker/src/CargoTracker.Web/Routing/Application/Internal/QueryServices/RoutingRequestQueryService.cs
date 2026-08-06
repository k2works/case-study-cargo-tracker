using CargoTracker.Shared.Application.Persistence;
using Dapper;

namespace CargoTracker.Routing.Application.Internal.QueryServices;

public sealed class RoutingRequestSummary
{
    public string BookingId { get; set; } = string.Empty;
    public string OriginUnlocode { get; set; } = string.Empty;
    public string DestinationUnlocode { get; set; } = string.Empty;
    public DateOnly ArrivalDeadline { get; set; }
    public string CargoType { get; set; } = string.Empty;
    public decimal Weight { get; set; }
}

/// <summary>経路設計依頼一覧向けの読み取りサービス。</summary>
public sealed class RoutingRequestQueryService(IDbConnectionFactory connectionFactory)
{
    public async Task<IReadOnlyList<RoutingRequestSummary>> FindRouteProposedAsync(CancellationToken ct = default)
    {
        using var connection = connectionFactory.Create();
        var items = await connection.QueryAsync<RoutingRequestSummary>(new CommandDefinition(
            """
            SELECT booking_id AS BookingId,
                   origin_unlocode AS OriginUnlocode,
                   destination_unlocode AS DestinationUnlocode,
                   arrival_deadline AS ArrivalDeadline,
                   cargo_type AS CargoType,
                   weight AS Weight
            FROM cargo
            WHERE booking_status = 'ROUTE_PROPOSED'
            ORDER BY arrival_deadline, booking_id
            """,
            cancellationToken: ct));
        return items.ToArray();
    }
}

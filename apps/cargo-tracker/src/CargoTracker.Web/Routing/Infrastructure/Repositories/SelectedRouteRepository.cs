using System.Data;
using CargoTracker.Routing.Domain.Model;
using CargoTracker.Routing.Domain.Repositories;
using CargoTracker.Shared.Application.Persistence;
using CargoTracker.Shared.Domain.Model;
using CargoTracker.Shared.Infrastructure.Persistence;
using Dapper;

namespace CargoTracker.Routing.Infrastructure.Repositories;

/// <summary>Dapper による確定経路リポジトリ（US09）。</summary>
public sealed class SelectedRouteRepository(IDbConnectionFactory connectionFactory, AmbientTransaction ambient)
    : ISelectedRouteRepository
{
    public async Task SaveAsync(SelectedRoute selectedRoute, CancellationToken ct = default)
    {
        var now = ToDatabaseTimestamp(DateTimeOffset.UtcNow);
        var tx = ambient.Require();
        var connection = tx.Connection!;

        // 同一予約の既存確定経路を置き換える（区間 → ヘッダの順に削除）。
        await connection.ExecuteAsync(new CommandDefinition(
            "DELETE FROM selected_route_leg WHERE selected_route_id = (SELECT id FROM selected_route WHERE booking_id = @BookingId)",
            new { selectedRoute.BookingId }, tx, cancellationToken: ct));
        await connection.ExecuteAsync(new CommandDefinition(
            "DELETE FROM selected_route WHERE booking_id = @BookingId",
            new { selectedRoute.BookingId }, tx, cancellationToken: ct));

        await connection.ExecuteAsync(new CommandDefinition(
            """
            INSERT INTO selected_route
                (booking_id, transit_days, cost, route_status, created_at, updated_at, version)
            VALUES
                (@BookingId, @TransitDays, @Cost, @RouteStatus, @Now, @Now, 0)
            """,
            new
            {
                selectedRoute.BookingId,
                selectedRoute.Route.TransitDays,
                selectedRoute.Route.Cost,
                RouteStatus = selectedRoute.Status.ToString().ToUpperInvariant(),
                Now = now,
            },
            tx, cancellationToken: ct));

        var routeId = await connection.ExecuteScalarAsync<long>(new CommandDefinition(
            "SELECT id FROM selected_route WHERE booking_id = @BookingId",
            new { selectedRoute.BookingId }, tx, cancellationToken: ct));

        var seq = 1;
        foreach (var leg in selectedRoute.Route.Legs)
        {
            await connection.ExecuteAsync(new CommandDefinition(
                """
                INSERT INTO selected_route_leg
                    (selected_route_id, seq_number, voyage_number, board_unlocode, alight_unlocode,
                     board_time, alight_time, created_at, updated_at)
                VALUES
                    (@RouteId, @Seq, @VoyageNumber, @BoardUnlocode, @AlightUnlocode,
                     @BoardTime, @AlightTime, @Now, @Now)
                """,
                new
                {
                    RouteId = routeId,
                    Seq = seq,
                    VoyageNumber = leg.VoyageNumber.Value,
                    BoardUnlocode = leg.BoardLocation.UnLocode,
                    AlightUnlocode = leg.AlightLocation.UnLocode,
                    BoardTime = ToDatabaseTimestamp(leg.BoardTime),
                    AlightTime = ToDatabaseTimestamp(leg.AlightTime),
                    Now = now,
                },
                tx, cancellationToken: ct));
            seq++;
        }
    }

    public async Task<SelectedRoute?> FindByBookingIdAsync(string bookingId, CancellationToken ct = default)
    {
        if (ambient.Current is not null)
        {
            return await QueryAsync(bookingId, ambient.Current.Connection!, ambient.Current, ct);
        }

        using var connection = connectionFactory.Create();
        return await QueryAsync(bookingId, connection, null, ct);
    }

    private static async Task<SelectedRoute?> QueryAsync(
        string bookingId, IDbConnection connection, IDbTransaction? tx, CancellationToken ct)
    {
        var header = await connection.QuerySingleOrDefaultAsync<HeaderRow>(new CommandDefinition(
            """
            SELECT booking_id AS BookingId, transit_days AS TransitDays, cost AS Cost,
                   route_status AS RouteStatus, version AS Version
            FROM selected_route WHERE booking_id = @BookingId
            """,
            new { BookingId = bookingId }, tx, cancellationToken: ct));
        if (header is null)
        {
            return null;
        }

        var legRows = await connection.QueryAsync<LegRow>(new CommandDefinition(
            """
            SELECT voyage_number AS VoyageNumber, board_unlocode AS BoardUnlocode,
                   alight_unlocode AS AlightUnlocode, board_time AS BoardTime, alight_time AS AlightTime
            FROM selected_route_leg
            WHERE selected_route_id = (SELECT id FROM selected_route WHERE booking_id = @BookingId)
            ORDER BY seq_number
            """,
            new { BookingId = bookingId }, tx, cancellationToken: ct));

        var legs = legRows.Select(r => r.ToRouteLeg()).ToList();
        var route = new CandidateRoute(legs, header.TransitDays, header.Cost);
        return SelectedRoute.Reconstruct(
            header.BookingId, route,
            Enum.Parse<RouteStatus>(header.RouteStatus, ignoreCase: true), header.Version);
    }

    private static DateTime ToDatabaseTimestamp(DateTimeOffset value)
        => DatabaseTimestamp.ToDatabase(value);

    private sealed class HeaderRow
    {
        public string BookingId { get; set; } = string.Empty;
        public int TransitDays { get; set; }
        public decimal Cost { get; set; }
        public string RouteStatus { get; set; } = string.Empty;
        public long Version { get; set; }
    }

    private sealed class LegRow
    {
        public string VoyageNumber { get; set; } = string.Empty;
        public string BoardUnlocode { get; set; } = string.Empty;
        public string AlightUnlocode { get; set; } = string.Empty;
        public DateTime BoardTime { get; set; }
        public DateTime AlightTime { get; set; }

        public RouteLeg ToRouteLeg() => new(
            new VoyageNumber(VoyageNumber),
            new Location(BoardUnlocode),
            new Location(AlightUnlocode),
            new DateTimeOffset(DateTime.SpecifyKind(BoardTime, DateTimeKind.Utc)),
            new DateTimeOffset(DateTime.SpecifyKind(AlightTime, DateTimeKind.Utc)));
    }
}

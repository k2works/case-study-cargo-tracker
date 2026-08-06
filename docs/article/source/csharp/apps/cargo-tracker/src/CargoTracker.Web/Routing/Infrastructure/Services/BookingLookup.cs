using CargoTracker.Routing.Application.Internal.OutboundServices;
using CargoTracker.Shared.Application.Persistence;
using Dapper;

namespace CargoTracker.Routing.Infrastructure.Services;

/// <summary>cargo テーブルだけを参照する Routing→Booking ACL 実装。</summary>
public sealed class BookingLookup(IDbConnectionFactory connectionFactory) : IBookingLookup
{
    public async Task<RoutingBookingInfo?> FindByBookingIdAsync(string bookingId, CancellationToken ct = default)
    {
        using var connection = connectionFactory.Create();
        return await connection.QuerySingleOrDefaultAsync<RoutingBookingInfo>(new CommandDefinition(
            """
            SELECT booking_id AS BookingId,
                   origin_unlocode AS OriginUnlocode,
                   destination_unlocode AS DestinationUnlocode,
                   arrival_deadline AS ArrivalDeadline,
                   cargo_type AS CargoType,
                   weight AS Weight
            FROM cargo
            WHERE booking_id = @BookingId
            """,
            new { BookingId = bookingId }, cancellationToken: ct));
    }
}

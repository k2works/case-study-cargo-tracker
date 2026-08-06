using CargoTracker.Handling.Application.Internal.OutboundServices;
using CargoTracker.Handling.Domain.Model;
using CargoTracker.Shared.Application.Persistence;
using Dapper;

namespace CargoTracker.Handling.Infrastructure.Services;

/// <summary>貨物スナップショット取得 ACL の実装（US15）。cargo・leg を SQL 直接参照する（Booking 内部型に非依存）。</summary>
public sealed class CargoSnapshotProvider(IDbConnectionFactory connectionFactory) : ICargoSnapshotProvider
{
    public async Task<CargoSnapshot?> FindByBookingIdAsync(string bookingId, CancellationToken ct = default)
    {
        using var connection = connectionFactory.Create();
        var header = await connection.QuerySingleOrDefaultAsync<HeaderRow>(new CommandDefinition(
            "SELECT booking_id AS BookingId, origin_unlocode AS Origin, destination_unlocode AS Destination FROM cargo WHERE booking_id = @BookingId",
            new { BookingId = bookingId }, cancellationToken: ct));
        if (header is null)
        {
            return null;
        }

        var legs = await connection.QueryAsync<LegRow>(new CommandDefinition(
            """
            SELECT load_location_unlocode AS LoadLocation, unload_location_unlocode AS UnloadLocation, voyage_number AS VoyageNumber
            FROM leg
            WHERE cargo_id = (SELECT id FROM cargo WHERE booking_id = @BookingId)
            ORDER BY seq_number
            """,
            new { BookingId = bookingId }, cancellationToken: ct));

        return new CargoSnapshot(
            header.BookingId, header.Origin, header.Destination,
            legs.Select(l => new LegSnapshot(l.LoadLocation, l.UnloadLocation, l.VoyageNumber)).ToList());
    }

    private sealed class HeaderRow
    {
        public string BookingId { get; set; } = string.Empty;
        public string Origin { get; set; } = string.Empty;
        public string Destination { get; set; } = string.Empty;
    }

    private sealed class LegRow
    {
        public string LoadLocation { get; set; } = string.Empty;
        public string UnloadLocation { get; set; } = string.Empty;
        public string VoyageNumber { get; set; } = string.Empty;
    }
}

using CargoTracker.Booking.Application.Internal.OutboundServices;
using CargoTracker.Shared.Application.Persistence;
using Dapper;

namespace CargoTracker.Booking.Infrastructure.Services;

/// <summary>確定経路読取 ACL の実装（US11）。selected_route を SQL で直接参照する（Routing 内部型に非依存）。</summary>
public sealed class SelectedRouteLookup(IDbConnectionFactory connectionFactory) : ISelectedRouteLookup
{
    public async Task<IReadOnlyList<SelectedRouteLegDto>> FindLegsByBookingIdAsync(string bookingId, CancellationToken ct = default)
    {
        using var connection = connectionFactory.Create();
        var rows = await connection.QueryAsync<Row>(new CommandDefinition(
            """
            SELECT l.voyage_number AS VoyageNumber, l.board_unlocode AS BoardUnlocode,
                   l.alight_unlocode AS AlightUnlocode, l.board_time AS BoardTime, l.alight_time AS AlightTime
            FROM selected_route_leg l
            JOIN selected_route r ON r.id = l.selected_route_id
            WHERE r.booking_id = @BookingId
            ORDER BY l.seq_number
            """,
            new { BookingId = bookingId }, cancellationToken: ct));

        return rows.Select(r => new SelectedRouteLegDto
        {
            VoyageNumber = r.VoyageNumber,
            LoadUnLocode = r.BoardUnlocode,
            UnloadUnLocode = r.AlightUnlocode,
            LoadTime = new DateTimeOffset(DateTime.SpecifyKind(r.BoardTime, DateTimeKind.Utc)),
            UnloadTime = new DateTimeOffset(DateTime.SpecifyKind(r.AlightTime, DateTimeKind.Utc)),
        }).ToList();
    }

    private sealed class Row
    {
        public string VoyageNumber { get; set; } = string.Empty;
        public string BoardUnlocode { get; set; } = string.Empty;
        public string AlightUnlocode { get; set; } = string.Empty;
        public DateTime BoardTime { get; set; }
        public DateTime AlightTime { get; set; }
    }
}

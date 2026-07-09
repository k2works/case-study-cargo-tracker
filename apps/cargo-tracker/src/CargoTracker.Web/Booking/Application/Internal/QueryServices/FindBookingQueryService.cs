using CargoTracker.Shared.Application.Persistence;
using Dapper;

namespace CargoTracker.Booking.Application.Internal.QueryServices;

public sealed class BookingDetail
{
    public string BookingId { get; set; } = string.Empty;
    public long ShipperId { get; set; }
    public string ShipperCode { get; set; } = string.Empty;
    public string ShipperName { get; set; } = string.Empty;
    public string CargoType { get; set; } = string.Empty;
    public decimal Weight { get; set; }
    public string OriginUnlocode { get; set; } = string.Empty;
    public string DestinationUnlocode { get; set; } = string.Empty;
    public DateOnly ArrivalDeadline { get; set; }
    public string BookingStatus { get; set; } = string.Empty;
    public decimal? DimensionLength { get; set; }
    public decimal? DimensionWidth { get; set; }
    public decimal? DimensionHeight { get; set; }
    public int? Quantity { get; set; }
    public string? Description { get; set; }
}

public sealed class ShipperOption
{
    public long ShipperId { get; set; }
    public string ShipperCode { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
}

/// <summary>予約画面向けの読み取りサービス。</summary>
public sealed class FindBookingQueryService(IDbConnectionFactory connectionFactory)
{
    public async Task<IReadOnlyList<ShipperOption>> FindShipperOptionsAsync(CancellationToken ct = default)
    {
        using var connection = connectionFactory.Create();
        var items = await connection.QueryAsync<ShipperOption>(new CommandDefinition(
            """
            SELECT id AS ShipperId, shipper_code AS ShipperCode, name AS Name
            FROM shipper
            ORDER BY id
            """,
            cancellationToken: ct));
        return items.ToList();
    }

    public async Task<BookingDetail?> FindByBookingIdAsync(string bookingId, CancellationToken ct = default)
    {
        using var connection = connectionFactory.Create();
        return await connection.QuerySingleOrDefaultAsync<BookingDetail>(new CommandDefinition(
            """
            SELECT c.booking_id AS BookingId, c.shipper_id AS ShipperId,
                   s.shipper_code AS ShipperCode, s.name AS ShipperName,
                   c.cargo_type AS CargoType, c.weight AS Weight,
                   c.origin_unlocode AS OriginUnlocode, c.destination_unlocode AS DestinationUnlocode,
                   c.arrival_deadline AS ArrivalDeadline, c.booking_status AS BookingStatus,
                   c.dimension_length AS DimensionLength, c.dimension_width AS DimensionWidth,
                   c.dimension_height AS DimensionHeight, c.quantity AS Quantity, c.description AS Description
            FROM cargo c
            JOIN shipper s ON s.id = c.shipper_id
            WHERE c.booking_id = @BookingId
            """,
            new { BookingId = bookingId }, cancellationToken: ct));
    }
}

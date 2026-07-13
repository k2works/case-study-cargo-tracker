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
    public string? HazardousClass { get; set; }
    public string? UnNumber { get; set; }
    public string? ProperShippingName { get; set; }
    public decimal? MinTemperature { get; set; }
    public decimal? MaxTemperature { get; set; }
    public string? TemperatureUnit { get; set; }

    /// <summary>発行済みの追跡番号（US14）。未発行なら null。Tracking Context を読取参照する。</summary>
    public string? TrackingNumber { get; set; }

    /// <summary>紐付け済みの確定経路（旅程）の区間。US11 で割り当てられた CargoItinerary のスナップショット（IT4 レビュー H7）。</summary>
    public IReadOnlyList<BookingLeg> Itinerary { get; set; } = [];
}

/// <summary>予約詳細に表示する旅程区間（読取専用）。</summary>
public sealed class BookingLeg
{
    public string VoyageNumber { get; set; } = string.Empty;
    public string LoadUnLocode { get; set; } = string.Empty;
    public string UnloadUnLocode { get; set; } = string.Empty;
    public DateTime LoadTime { get; set; }
    public DateTime UnloadTime { get; set; }
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
        var detail = await connection.QuerySingleOrDefaultAsync<BookingDetail>(new CommandDefinition(
            """
            SELECT c.booking_id AS BookingId, c.shipper_id AS ShipperId,
                   s.shipper_code AS ShipperCode, s.name AS ShipperName,
                   c.cargo_type AS CargoType, c.weight AS Weight,
                   c.origin_unlocode AS OriginUnlocode, c.destination_unlocode AS DestinationUnlocode,
                   c.arrival_deadline AS ArrivalDeadline, c.booking_status AS BookingStatus,
                   c.dimension_length AS DimensionLength, c.dimension_width AS DimensionWidth,
                   c.dimension_height AS DimensionHeight, c.quantity AS Quantity, c.description AS Description,
                   c.hazardous_class AS HazardousClass, c.un_number AS UnNumber,
                   c.proper_shipping_name AS ProperShippingName, c.min_temperature AS MinTemperature,
                   c.max_temperature AS MaxTemperature, c.temperature_unit AS TemperatureUnit,
                   t.tracking_number AS TrackingNumber
            FROM cargo c
            JOIN shipper s ON s.id = c.shipper_id
            LEFT JOIN tracking_activity t ON t.booking_id = c.booking_id
            WHERE c.booking_id = @BookingId
            """,
            new { BookingId = bookingId }, cancellationToken: ct));

        if (detail is null)
        {
            return null;
        }

        // 紐付け済みの確定経路（旅程）を表示用に取得する（IT4 レビュー H7・予約詳細に確定経路を提示）。
        var legs = await connection.QueryAsync<BookingLeg>(new CommandDefinition(
            """
            SELECT voyage_number AS VoyageNumber, load_location_unlocode AS LoadUnLocode,
                   unload_location_unlocode AS UnloadUnLocode, load_time AS LoadTime, unload_time AS UnloadTime
            FROM leg
            WHERE cargo_id = (SELECT id FROM cargo WHERE booking_id = @BookingId)
            ORDER BY seq_number
            """,
            new { BookingId = bookingId }, cancellationToken: ct));
        detail.Itinerary = legs.ToList();
        return detail;
    }
}

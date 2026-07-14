using CargoTracker.Billing.Application.Internal.OutboundServices;
using CargoTracker.Shared.Application.Persistence;
using Dapper;

namespace CargoTracker.Billing.Infrastructure.Services;

/// <summary>
/// 精算スナップショット取得 ACL の実装（US21/US22）。cargo・shipper を SQL 直接参照する
/// （Booking/Shipper の内部型に非依存）。荷主種別・契約割引率を含めて 1 回で取得する。
/// </summary>
public sealed class BillingSnapshotProvider(IDbConnectionFactory connectionFactory) : IBillingSnapshotProvider
{
    public async Task<BillingSnapshot?> FindByBookingIdAsync(string bookingId, CancellationToken ct = default)
    {
        using var connection = connectionFactory.Create();
        var row = await connection.QuerySingleOrDefaultAsync<Row>(new CommandDefinition(
            """
            SELECT c.booking_id AS BookingId, c.booking_status AS BookingStatus,
                   c.cargo_type AS CargoType, c.weight AS Weight,
                   s.shipper_code AS ShipperId, s.shipper_type AS ShipperType,
                   s.discount_rate AS DiscountRate
            FROM cargo c
            JOIN shipper s ON s.id = c.shipper_id
            WHERE c.booking_id = @BookingId
            """,
            new { BookingId = bookingId }, cancellationToken: ct));
        return row?.ToSnapshot();
    }

    // Dapper の record 位置引数マッチングを避けるため可変 DTO に読み込む（既存 ACL と同方針）。
    private sealed class Row
    {
        public string BookingId { get; set; } = string.Empty;
        public string BookingStatus { get; set; } = string.Empty;
        public string CargoType { get; set; } = string.Empty;
        public decimal Weight { get; set; }
        public string ShipperId { get; set; } = string.Empty;
        public string ShipperType { get; set; } = string.Empty;
        public decimal DiscountRate { get; set; }

        public BillingSnapshot ToSnapshot() => new(
            BookingId, BookingStatus, CargoType, Weight, ShipperId, ShipperType, DiscountRate);
    }
}

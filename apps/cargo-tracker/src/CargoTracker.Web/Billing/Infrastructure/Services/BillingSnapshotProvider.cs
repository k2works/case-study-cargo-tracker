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
        return await connection.QuerySingleOrDefaultAsync<BillingSnapshot>(new CommandDefinition(
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
    }
}

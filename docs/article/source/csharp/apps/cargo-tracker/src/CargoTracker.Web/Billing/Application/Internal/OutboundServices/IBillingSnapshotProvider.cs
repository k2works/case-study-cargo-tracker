namespace CargoTracker.Billing.Application.Internal.OutboundServices;

/// <summary>
/// 精算に必要な予約・荷主のスナップショット（US21/US22・ACL・プリミティブ DTO）。
/// Booking/Shipper の内部型に依存せず、料金算出・割引適用に必要な素の値だけを運ぶ。
/// </summary>
public sealed record BillingSnapshot(
    string BookingId,
    string BookingStatus,
    string CargoType,
    decimal Weight,
    string ShipperId,
    string ShipperType,
    decimal DiscountRate);

/// <summary>予約・荷主スナップショット取得 ACL（US21）。cargo・shipper を SQL 直接参照する。</summary>
public interface IBillingSnapshotProvider
{
    Task<BillingSnapshot?> FindByBookingIdAsync(string bookingId, CancellationToken ct = default);
}

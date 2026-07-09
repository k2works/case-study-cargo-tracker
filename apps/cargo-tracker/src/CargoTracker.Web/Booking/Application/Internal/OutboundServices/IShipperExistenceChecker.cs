using CargoTracker.Shared.Domain.Model;

namespace CargoTracker.Booking.Application.Internal.OutboundServices;

/// <summary>Booking から Shipper への ACL。Shipper の内部ドメインモデルは参照しない。</summary>
public interface IShipperExistenceChecker
{
    Task<bool> ExistsAsync(ShipperId id, CancellationToken ct = default);
}

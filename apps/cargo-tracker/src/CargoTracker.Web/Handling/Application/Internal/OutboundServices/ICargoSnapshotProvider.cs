using CargoTracker.Handling.Domain.Model;

namespace CargoTracker.Handling.Application.Internal.OutboundServices;

/// <summary>
/// 荷役の妥当性検証に用いる貨物スナップショットを Booking Context から取得する ACL（US15）。
/// Booking の内部モデルに依存せず Handling 側プリミティブ（CargoSnapshot）で受け取る（BC 独立）。
/// </summary>
public interface ICargoSnapshotProvider
{
    Task<CargoSnapshot?> FindByBookingIdAsync(string bookingId, CancellationToken ct = default);
}

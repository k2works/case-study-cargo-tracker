using CargoTracker.Routing.Domain.Model;

namespace CargoTracker.Routing.Domain.Repositories;

/// <summary>確定経路リポジトリ（US09）。</summary>
public interface ISelectedRouteRepository
{
    /// <summary>確定経路を保存する。同一予約に既存があれば置き換える（upsert）。</summary>
    Task SaveAsync(SelectedRoute selectedRoute, CancellationToken ct = default);

    Task<SelectedRoute?> FindByBookingIdAsync(string bookingId, CancellationToken ct = default);
}

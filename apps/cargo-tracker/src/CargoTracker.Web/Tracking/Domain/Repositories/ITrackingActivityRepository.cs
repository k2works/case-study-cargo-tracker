using CargoTracker.Tracking.Domain.Model;

namespace CargoTracker.Tracking.Domain.Repositories;

/// <summary>追跡レコードリポジトリ（US14）。</summary>
public interface ITrackingActivityRepository
{
    Task SaveAsync(TrackingActivity trackingActivity, CancellationToken ct = default);

    Task<TrackingActivity?> FindByBookingIdAsync(string bookingId, CancellationToken ct = default);

    Task<TrackingActivity?> FindByTrackingNumberAsync(string trackingNumber, CancellationToken ct = default);

    Task<bool> ExistsForBookingAsync(string bookingId, CancellationToken ct = default);
}

using CargoTracker.Booking.Domain.Model;

namespace CargoTracker.Booking.Domain.Repositories;

/// <summary>経路通知記録リポジトリ（US12）。</summary>
public interface IRouteNotificationRepository
{
    Task SaveAsync(RouteNotification notification, CancellationToken ct = default);

    Task<RouteNotification?> FindLatestByBookingIdAsync(BookingId bookingId, CancellationToken ct = default);
}

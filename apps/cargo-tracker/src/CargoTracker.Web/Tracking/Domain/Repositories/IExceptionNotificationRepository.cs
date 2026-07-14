using CargoTracker.Tracking.Domain.Model;

namespace CargoTracker.Tracking.Domain.Repositories;

/// <summary>例外通知記録リポジトリ（US19/US20・append-only）。</summary>
public interface IExceptionNotificationRepository
{
    Task SaveAsync(ExceptionNotification notification, CancellationToken ct = default);

    Task<IReadOnlyList<ExceptionNotification>> FindByTrackingNumberAsync(
        string trackingNumber, CancellationToken ct = default);
}

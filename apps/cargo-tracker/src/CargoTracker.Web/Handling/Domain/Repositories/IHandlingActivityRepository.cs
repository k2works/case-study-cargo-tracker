using CargoTracker.Handling.Domain.Model;

namespace CargoTracker.Handling.Domain.Repositories;

/// <summary>荷役作業記録リポジトリ（US15/US16）。</summary>
public interface IHandlingActivityRepository
{
    Task SaveAsync(HandlingActivity handlingActivity, CancellationToken ct = default);
}

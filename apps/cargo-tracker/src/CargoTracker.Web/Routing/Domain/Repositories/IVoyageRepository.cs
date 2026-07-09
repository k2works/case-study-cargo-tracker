using CargoTracker.Routing.Domain.Model;

namespace CargoTracker.Routing.Domain.Repositories;

public interface IVoyageRepository
{
    Task SaveAsync(Voyage voyage, CancellationToken ct = default);

    Task<Voyage?> FindByVoyageNumberAsync(VoyageNumber voyageNumber, CancellationToken ct = default);

    Task<bool> ExistsAsync(VoyageNumber voyageNumber, CancellationToken ct = default);
}

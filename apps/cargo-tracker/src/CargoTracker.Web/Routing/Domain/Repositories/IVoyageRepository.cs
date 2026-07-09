using CargoTracker.Routing.Domain.Model;

namespace CargoTracker.Routing.Domain.Repositories;

public interface IVoyageRepository
{
    Task SaveAsync(Voyage voyage, CancellationToken ct = default);

    Task UpdateAsync(Voyage voyage, CancellationToken ct = default);

    Task<Voyage?> FindByVoyageNumberAsync(VoyageNumber voyageNumber, CancellationToken ct = default);

    /// <summary>登録済みの全航海を集約として取得する（経路候補算出の入力に使用）。</summary>
    Task<IReadOnlyList<Voyage>> FindAllAsync(CancellationToken ct = default);

    Task<bool> ExistsAsync(VoyageNumber voyageNumber, CancellationToken ct = default);
}

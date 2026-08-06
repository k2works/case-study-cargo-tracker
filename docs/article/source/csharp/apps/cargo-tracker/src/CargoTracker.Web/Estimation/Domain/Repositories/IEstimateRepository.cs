using CargoTracker.Estimation.Domain.Model;

namespace CargoTracker.Estimation.Domain.Repositories;

/// <summary>見積の永続化ポート。見積とルート候補を単一トランザクションで保存する（ADR-0002）。</summary>
public interface IEstimateRepository
{
    Task SaveAsync(Estimate estimate, CancellationToken ct = default);
}

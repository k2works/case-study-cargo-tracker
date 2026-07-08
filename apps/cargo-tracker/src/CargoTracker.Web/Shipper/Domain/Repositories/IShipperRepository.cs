using System.Data;
using ShipperAggregate = CargoTracker.Shipper.Domain.Model.Shipper;

namespace CargoTracker.Shipper.Domain.Repositories;

/// <summary>荷主の永続化ポート（出力ポート）。書き込みは UoW のトランザクション内で行う（ADR-0002）。</summary>
public interface IShipperRepository
{
    Task<bool> ExistsByEmailAsync(string email, IDbTransaction transaction, CancellationToken ct = default);

    Task SaveAsync(ShipperAggregate shipper, IDbTransaction transaction, CancellationToken ct = default);
}

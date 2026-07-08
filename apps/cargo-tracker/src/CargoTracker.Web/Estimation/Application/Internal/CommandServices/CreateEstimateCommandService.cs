using CargoTracker.Estimation.Application.Internal.OutboundServices;
using CargoTracker.Estimation.Domain.Model;
using CargoTracker.Estimation.Domain.Repositories;
using CargoTracker.Shared.Application.Persistence;
using CargoTracker.Shared.Domain.Model;

namespace CargoTracker.Estimation.Application.Internal.CommandServices;

/// <summary>
/// 見積作成ユースケース（US01）。見積集約を生成し、外部経路サービス（IT1 はスタブ）から
/// ルート候補を取得して紐付け、見積とルート候補を単一トランザクションで永続化する（ADR-0001/0002）。
/// </summary>
public sealed class CreateEstimateCommandService(
    IUnitOfWorkFactory unitOfWorkFactory,
    IEstimateRepository repository,
    IExternalRoutingServicePort routingService)
{
    public async Task<EstimateId> HandleAsync(CreateEstimateCommand command, CancellationToken ct = default)
    {
        var origin = new Location(command.OriginUnLocode);
        var destination = new Location(command.DestinationUnLocode);

        var estimate = Estimate.Create(origin, destination, command.ArrivalDeadline, command.CargoType, command.WeightKg);

        var candidates = await routingService.FindRouteCandidatesAsync(
            origin, destination, command.CargoType, command.WeightKg, ct);
        estimate.ReplaceCandidates(candidates);

        await using var unitOfWork = unitOfWorkFactory.Begin();
        unitOfWork.Track(estimate);
        await repository.SaveAsync(estimate, unitOfWork.Transaction, ct);
        await unitOfWork.CommitAsync(ct);

        return estimate.EstimateId;
    }
}

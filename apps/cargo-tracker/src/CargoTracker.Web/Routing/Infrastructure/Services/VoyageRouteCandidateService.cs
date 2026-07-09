using CargoTracker.Routing.Application.Internal.OutboundServices;
using CargoTracker.Routing.Domain.Model;
using CargoTracker.Routing.Domain.Repositories;
using CargoTracker.Shared.Domain.Model;

namespace CargoTracker.Routing.Infrastructure.Services;

/// <summary>
/// <see cref="IRouteCandidateService"/> の実装。登録済みの航海スケジュールを読み込み、
/// 貨物種別に対応する航海に絞ったうえで <see cref="RouteCandidateCalculator"/> により経路候補を算出する。
/// 現時点では外部経路サービスをローカルの航海データで代替するスタブ実装（IT1 の StubExternalRoutingService と同方針）。
/// 将来、実外部サービス連携時は本実装を差し替え、契約を WireMock.Net で固定する。
/// </summary>
public sealed class VoyageRouteCandidateService(IVoyageRepository voyageRepository) : IRouteCandidateService
{
    private readonly RouteCandidateCalculator _calculator = new();

    public async Task<IReadOnlyList<CandidateRoute>> FindCandidatesAsync(
        Location origin,
        Location destination,
        DateOnly arrivalDeadline,
        SupportedCargoType cargoType,
        CancellationToken ct = default)
    {
        var voyages = await voyageRepository.FindAllAsync(ct);

        // 貨物種別に対応する航海のみを算出対象とする（危険物・冷凍は対応航海のみ。US07-6 / US08）。
        var applicable = voyages
            .Where(voyage => voyage.SupportedCargoTypes.Contains(cargoType))
            .ToList();

        return _calculator.Calculate(origin, destination, arrivalDeadline, applicable);
    }
}

using CargoTracker.Estimation.Application.Internal.OutboundServices;
using CargoTracker.Estimation.Domain.Model;
using CargoTracker.Shared.Domain.Model;

namespace CargoTracker.Estimation.Infrastructure.Services;

/// <summary>
/// 外部経路サービスのスタブ（IT1）。重量と貨物種別係数から固定的なルート候補を生成する。
/// IT3 で WireMock.Net による契約テストと実サービス連携に差し替える（domain-model 規則 6）。
/// </summary>
public sealed class StubExternalRoutingService : IExternalRoutingServicePort
{
    // 貨物種別ごとの料金係数（domain-model の料金計算ロジック）。
    private static decimal CargoFactor(CargoType type) => type switch
    {
        CargoType.Hazardous => 1.8m,
        CargoType.Refrigerated => 1.5m,
        _ => 1.0m,
    };

    public Task<IReadOnlyList<RouteCandidate>> FindRouteCandidatesAsync(
        Location origin, Location destination, CargoType cargoType, decimal weightKg, CancellationToken ct = default)
    {
        var factor = CargoFactor(cargoType);

        decimal Cost(decimal baseRate) => Math.Round(weightKg * factor * baseRate, 2, MidpointRounding.ToEven);

        IReadOnlyList<RouteCandidate> candidates =
        [
            new("V-STUB-01", "SGSIN", 14, Cost(120m)),
            new("V-STUB-02", "HKHKG", 18, Cost(100m)),
            new("V-STUB-03", "AEJEA", 24, Cost(85m)),
        ];
        return Task.FromResult(candidates);
    }
}

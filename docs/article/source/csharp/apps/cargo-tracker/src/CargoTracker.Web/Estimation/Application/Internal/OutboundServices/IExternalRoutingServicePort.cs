using CargoTracker.Estimation.Domain.Model;
using CargoTracker.Shared.Domain.Model;

namespace CargoTracker.Estimation.Application.Internal.OutboundServices;

/// <summary>
/// 外部経路サービスへの出力ポート（ACL）。出発地・仕向地・貨物種別・重量から
/// ルート候補を取得する。IT1 ではスタブ実装、IT3 で WireMock.Net 契約・実サービスに差し替える。
/// </summary>
public interface IExternalRoutingServicePort
{
    Task<IReadOnlyList<RouteCandidate>> FindRouteCandidatesAsync(
        Location origin, Location destination, CargoType cargoType, decimal weightKg, CancellationToken ct = default);
}

using CargoTracker.Routing.Domain.Model;
using CargoTracker.Shared.Domain.Model;

namespace CargoTracker.Routing.Application.Internal.OutboundServices;

/// <summary>
/// 経路候補算出の出力ポート（US08・外部経路サービス ACL）。出発地・目的地・到着期限・貨物種別から
/// 制約条件を考慮した経路候補を推奨順で取得する。Estimation の IExternalRoutingServicePort（見積用）とは
/// 分離した Routing BC 固有のポート（BC 独立の設計判断）。
/// </summary>
public interface IRouteCandidateService
{
    Task<IReadOnlyList<CandidateRoute>> FindCandidatesAsync(
        Location origin,
        Location destination,
        DateOnly arrivalDeadline,
        SupportedCargoType cargoType,
        CancellationToken ct = default);
}

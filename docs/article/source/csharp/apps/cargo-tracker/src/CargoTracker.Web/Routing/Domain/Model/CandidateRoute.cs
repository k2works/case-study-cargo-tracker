using CargoTracker.Shared.Domain.Model;

namespace CargoTracker.Routing.Domain.Model;

/// <summary>
/// 経路候補（US08）。Routing BC 固有の値オブジェクト。Estimation Context の RouteCandidate（見積用）とは
/// ライフサイクル・責務が異なるため共有しない（domain-model・BC 独立の設計判断）。
/// 1 つ以上の航海（乗継可）で出発地から目的地へ到達する経路を表す。
/// </summary>
public sealed record CandidateRoute
{
    /// <summary>経路を構成する各区間（乗船する航海と乗降港・時刻）。</summary>
    public IReadOnlyList<RouteLeg> Legs { get; }

    /// <summary>経由港（出発地から目的地まで、乗降・乗継の港を順に並べたもの）。</summary>
    public IReadOnlyList<Location> Ports { get; }

    /// <summary>所要日数（最初の乗船から最後の下船まで、切り上げ）。</summary>
    public int TransitDays { get; }

    /// <summary>費用（簡易算出）。</summary>
    public decimal Cost { get; }

    /// <summary>乗継回数（0 なら直行便）。</summary>
    public int TransferCount => Legs.Count - 1;

    /// <summary>直行便かどうか（単一航海で到達）。</summary>
    public bool IsDirect => Legs.Count == 1;

    public CandidateRoute(IReadOnlyList<RouteLeg> legs, int transitDays, decimal cost)
    {
        if (legs.Count == 0)
        {
            throw new ArgumentException("経路候補は 1 区間以上で構成されます。", nameof(legs));
        }

        Legs = legs;
        TransitDays = transitDays;
        Cost = cost;

        var ports = new List<Location> { legs[0].BoardLocation };
        ports.AddRange(legs.Select(leg => leg.AlightLocation));
        Ports = ports;

        VoyageNumbers = legs.Select(leg => leg.VoyageNumber).ToArray();
    }

    /// <summary>経路が使用する航海番号の列（生成時に確定）。</summary>
    public IReadOnlyList<VoyageNumber> VoyageNumbers { get; }
}

/// <summary>経路候補の 1 区間。特定の航海に乗船港から下船港まで乗る単位。</summary>
public sealed record RouteLeg(
    VoyageNumber VoyageNumber,
    Location BoardLocation,
    Location AlightLocation,
    DateTimeOffset BoardTime,
    DateTimeOffset AlightTime);

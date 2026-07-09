using CargoTracker.Shared.Domain.Model;

namespace CargoTracker.Estimation.Domain.Model;

/// <summary>
/// 見積集約ルート（US01）。出発地・仕向地・期限・貨物種別・重量と、複数のルート候補を保持する。
/// ルート候補は 1 対多で見積の文脈でのみ意味を持つため単一集約に含める（domain-model 集約設計）。
/// </summary>
public sealed class Estimate : AggregateRoot
{
    private readonly List<RouteCandidate> _candidates = [];

    public EstimateId EstimateId { get; }
    public Location Origin { get; }
    public Location Destination { get; }
    public DateOnly ArrivalDeadline { get; }
    public CargoType CargoType { get; }
    public decimal WeightKg { get; }
    public EstimateStatus Status { get; }
    public long Version { get; }
    public IReadOnlyList<RouteCandidate> Candidates => _candidates;

    private Estimate(
        EstimateId estimateId, Location origin, Location destination, DateOnly arrivalDeadline,
        CargoType cargoType, decimal weightKg, EstimateStatus status, long version)
    {
        EstimateId = estimateId;
        Origin = origin;
        Destination = destination;
        ArrivalDeadline = arrivalDeadline;
        CargoType = cargoType;
        WeightKg = weightKg;
        Status = status;
        Version = version;
    }

    public static Estimate Create(
        Location origin, Location destination, DateOnly arrivalDeadline, CargoType cargoType, decimal weightKg,
        DateOnly? today = null)
    {
        if (origin.SameAs(destination))
        {
            throw new ArgumentException("出発地と仕向地は異なる必要があります。", nameof(destination));
        }
        var currentDate = today ?? DateOnly.FromDateTime(DateTime.UtcNow);
        if (arrivalDeadline < currentDate)
        {
            throw new ArgumentException("到着期限は当日以降でなければなりません。", nameof(arrivalDeadline));
        }
        if (weightKg <= 0)
        {
            throw new ArgumentException("重量は正の値でなければなりません。", nameof(weightKg));
        }

        return new Estimate(
            EstimateId.Generate(), origin, destination, arrivalDeadline, cargoType, weightKg, EstimateStatus.Created, 0);
    }

    /// <summary>ルート候補を一括で差し替える（domain-model の ReplaceCandidates）。</summary>
    public void ReplaceCandidates(IEnumerable<RouteCandidate> candidates)
    {
        _candidates.Clear();
        _candidates.AddRange(candidates);
    }

    /// <summary>永続化データから集約を再構築する。</summary>
    public static Estimate Reconstruct(
        EstimateId estimateId, Location origin, Location destination, DateOnly arrivalDeadline,
        CargoType cargoType, decimal weightKg, EstimateStatus status, long version, IEnumerable<RouteCandidate> candidates)
    {
        var estimate = new Estimate(estimateId, origin, destination, arrivalDeadline, cargoType, weightKg, status, version);
        estimate._candidates.AddRange(candidates);
        return estimate;
    }
}

using CargoTracker.Shared.Domain.Model;

namespace CargoTracker.Routing.Domain.Model;

/// <summary>
/// 経路候補算出ドメインサービス（US08）。利用可能な航海群から、出発地→目的地の経路候補を
/// 制約条件（寄港地の接続可能性・時刻接続・到着期限）を考慮して算出し、推奨順（直行優先・所要日数昇順）で返す。
/// 純粋なドメインロジックであり、永続化・外部サービスには依存しない。
/// </summary>
public sealed class RouteCandidateCalculator
{
    private const int _maxLegs = 3;
    private const decimal _costPerDay = 1000m;
    private const decimal _transferPenalty = 500m;

    /// <summary>
    /// 経路候補を算出する。到着期限（<paramref name="arrivalDeadline"/> の終日）までに目的地へ到達できる
    /// 候補のみを返す。該当なしの場合は空リスト。直行を最優先に、次いで所要日数の短い順に並べる。
    /// </summary>
    public IReadOnlyList<CandidateRoute> Calculate(
        Location origin,
        Location destination,
        DateOnly arrivalDeadline,
        IReadOnlyList<Voyage> availableVoyages)
    {
        var deadline = new DateTimeOffset(arrivalDeadline.ToDateTime(TimeOnly.MaxValue), TimeSpan.Zero);
        var segments = BuildSegments(availableVoyages);
        var results = new List<CandidateRoute>();

        // 出発地から到達可能な経路を深さ優先で探索する（乗継は最大 MaxLegs 区間まで）。
        Search(origin, destination, deadline, segments, [], DateTimeOffset.MinValue, results);

        return results
            .OrderBy(route => route.IsDirect ? 0 : 1)
            .ThenBy(route => route.TransitDays)
            .ThenBy(route => route.Cost)
            .ToList();
    }

    private static void Search(
        Location currentPort,
        Location destination,
        DateTimeOffset deadline,
        List<VoyageSegment> segments,
        List<RouteLeg> path,
        DateTimeOffset earliestBoard,
        List<CandidateRoute> results)
    {
        if (path.Count >= _maxLegs)
        {
            return;
        }

        foreach (var segment in segments)
        {
            // 同一航海の再乗船は不可（循環防止）。
            if (path.Any(leg => leg.VoyageNumber == segment.VoyageNumber))
            {
                continue;
            }
            // 現在地から乗船でき、前区間の到着以降に出発する区間のみ接続可能。
            if (!segment.BoardLocation.SameAs(currentPort) || segment.BoardTime < earliestBoard)
            {
                continue;
            }
            // 期限内に到達できない区間は枝刈り。
            if (segment.AlightTime > deadline)
            {
                continue;
            }

            var leg = new RouteLeg(
                segment.VoyageNumber, segment.BoardLocation, segment.AlightLocation, segment.BoardTime, segment.AlightTime);
            path.Add(leg);

            if (segment.AlightLocation.SameAs(destination))
            {
                results.Add(BuildCandidate(path));
            }
            else
            {
                Search(segment.AlightLocation, destination, deadline, segments, path, segment.AlightTime, results);
            }

            path.RemoveAt(path.Count - 1);
        }
    }

    private static CandidateRoute BuildCandidate(IReadOnlyList<RouteLeg> path)
    {
        var legs = path.ToArray();
        var boardTime = legs[0].BoardTime;
        var alightTime = legs[^1].AlightTime;
        var transitDays = (int)Math.Ceiling((alightTime - boardTime).TotalDays);
        var cost = (transitDays * _costPerDay) + ((legs.Length - 1) * _transferPenalty);
        return new CandidateRoute(legs, transitDays, cost);
    }

    /// <summary>
    /// 各航海のスケジュールから「乗船港→下船港」の到達可能な区間を展開する。
    /// 同一航海内では任意の寄港地で乗船し、以降の任意の寄港地で下船できる。
    /// </summary>
    private static List<VoyageSegment> BuildSegments(IReadOnlyList<Voyage> voyages)
    {
        var segments = new List<VoyageSegment>();
        foreach (var voyage in voyages)
        {
            var movements = voyage.Schedule.CarrierMovements;
            for (var i = 0; i < movements.Count; i++)
            {
                // 区間 i の出発港で乗船し、区間 j（j >= i）の到着港で下船する経路を全て展開。
                // 航海内の区間は時系列順・港接続済み（Schedule の不変条件）。
                for (var j = i; j < movements.Count; j++)
                {
                    segments.Add(new VoyageSegment(
                        voyage.VoyageNumber,
                        movements[i].DepartureLocation,
                        movements[j].ArrivalLocation,
                        movements[i].DepartureDate,
                        movements[j].ArrivalDate));
                }
            }
        }

        return segments;
    }

    private sealed record VoyageSegment(
        VoyageNumber VoyageNumber,
        Location BoardLocation,
        Location AlightLocation,
        DateTimeOffset BoardTime,
        DateTimeOffset AlightTime);
}

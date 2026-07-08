using CargoTracker.Shared.Infrastructure.Persistence;
using Dapper;

namespace CargoTracker.Estimation.Application.Internal.QueryServices;

/// <summary>見積一覧の行 DTO。decimal は可変プロパティで Dapper の型変換に委ねる（ADR-0003 二方言差異）。</summary>
public sealed class EstimateListItem
{
    public Guid EstimateId { get; set; }
    public string OriginUnlocode { get; set; } = string.Empty;
    public string DestinationUnlocode { get; set; } = string.Empty;
    public DateOnly ArrivalDeadline { get; set; }
    public string CargoType { get; set; } = string.Empty;
    public decimal WeightKg { get; set; }
    public string Status { get; set; } = string.Empty;
    public int CandidateCount { get; set; }
}

/// <summary>見積ルート候補の行 DTO。</summary>
public sealed class RouteCandidateItem
{
    public string VoyageNumber { get; set; } = string.Empty;
    public string? TransitPort { get; set; }
    public int TransitDays { get; set; }
    public decimal EstimatedCost { get; set; }
}

/// <summary>見積詳細 DTO。</summary>
public sealed class EstimateDetail
{
    public Guid EstimateId { get; set; }
    public string OriginUnlocode { get; set; } = string.Empty;
    public string DestinationUnlocode { get; set; } = string.Empty;
    public DateOnly ArrivalDeadline { get; set; }
    public string CargoType { get; set; } = string.Empty;
    public decimal WeightKg { get; set; }
    public string Status { get; set; } = string.Empty;
    public IReadOnlyList<RouteCandidateItem> Candidates { get; set; } = [];
}

/// <summary>見積のクエリサービス（CQRS 読み取り・DTO 直接射影）。</summary>
public sealed class FindEstimateQueryService(IDbConnectionFactory connectionFactory)
{
    public async Task<IReadOnlyList<EstimateListItem>> FindAllAsync(CancellationToken ct = default)
    {
        using var connection = connectionFactory.Create();
        var items = await connection.QueryAsync<EstimateListItem>(new CommandDefinition(
            """
            SELECT e.estimate_id AS EstimateId, e.origin_unlocode AS OriginUnlocode,
                   e.destination_unlocode AS DestinationUnlocode, e.arrival_deadline AS ArrivalDeadline,
                   e.cargo_type AS CargoType, e.weight_kg AS WeightKg, e.status AS Status,
                   (SELECT COUNT(1) FROM route_candidate rc WHERE rc.estimate_id = e.id) AS CandidateCount
            FROM estimate e
            ORDER BY e.id DESC
            """,
            cancellationToken: ct));
        return items.ToList();
    }

    public async Task<EstimateDetail?> FindByEstimateIdAsync(Guid estimateId, CancellationToken ct = default)
    {
        using var connection = connectionFactory.Create();

        var detail = await connection.QuerySingleOrDefaultAsync<EstimateDetail>(new CommandDefinition(
            """
            SELECT estimate_id AS EstimateId, origin_unlocode AS OriginUnlocode,
                   destination_unlocode AS DestinationUnlocode, arrival_deadline AS ArrivalDeadline,
                   cargo_type AS CargoType, weight_kg AS WeightKg, status AS Status
            FROM estimate
            WHERE estimate_id = @EstimateId
            """,
            new { EstimateId = estimateId }, cancellationToken: ct));

        if (detail is null)
        {
            return null;
        }

        var candidates = await connection.QueryAsync<RouteCandidateItem>(new CommandDefinition(
            """
            SELECT rc.voyage_number AS VoyageNumber, rc.transit_port AS TransitPort,
                   rc.transit_days AS TransitDays, rc.estimated_cost AS EstimatedCost
            FROM route_candidate rc
            JOIN estimate e ON e.id = rc.estimate_id
            WHERE e.estimate_id = @EstimateId
            ORDER BY rc.rank
            """,
            new { EstimateId = estimateId }, cancellationToken: ct));
        detail.Candidates = candidates.ToList();

        return detail;
    }
}

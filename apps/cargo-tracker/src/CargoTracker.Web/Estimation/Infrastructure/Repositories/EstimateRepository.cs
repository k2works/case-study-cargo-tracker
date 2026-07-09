using CargoTracker.Estimation.Domain.Model;
using CargoTracker.Estimation.Domain.Repositories;
using CargoTracker.Shared.Infrastructure.Persistence;
using Dapper;

namespace CargoTracker.Estimation.Infrastructure.Repositories;

/// <summary>
/// Dapper による見積リポジトリ（ADR-0001）。見積を INSERT 後、採番されたサロゲートキーを
/// 業務キー（estimate_id）で取り直し（RETURNING 不使用・ADR-0003）、ルート候補を子として保存する。
/// </summary>
public sealed class EstimateRepository(AmbientTransaction ambient) : IEstimateRepository
{
    public async Task SaveAsync(Estimate estimate, CancellationToken ct = default)
    {
        var transaction = ambient.Require();
        var connection = transaction.Connection!;
        var now = DateTimeOffset.UtcNow;

        await connection.ExecuteAsync(new CommandDefinition(
            """
            INSERT INTO estimate
                (estimate_id, origin_unlocode, destination_unlocode, arrival_deadline,
                 cargo_type, weight_kg, status, created_at, updated_at, version)
            VALUES
                (@EstimateId, @Origin, @Destination, @ArrivalDeadline,
                 @CargoType, @WeightKg, @Status, @CreatedAt, @UpdatedAt, 0)
            """,
            new
            {
                EstimateId = estimate.EstimateId.Value,
                Origin = estimate.Origin.UnLocode,
                Destination = estimate.Destination.UnLocode,
                ArrivalDeadline = estimate.ArrivalDeadline,
                CargoType = estimate.CargoType.ToString().ToUpperInvariant(),
                WeightKg = estimate.WeightKg,
                Status = estimate.Status.ToString().ToUpperInvariant(),
                CreatedAt = now,
                UpdatedAt = now,
            },
            transaction, cancellationToken: ct));

        var surrogateId = await connection.ExecuteScalarAsync<long>(new CommandDefinition(
            "SELECT id FROM estimate WHERE estimate_id = @EstimateId",
            new { EstimateId = estimate.EstimateId.Value }, transaction, cancellationToken: ct));

        var rank = 0;
        foreach (var candidate in estimate.Candidates)
        {
            await connection.ExecuteAsync(new CommandDefinition(
                """
                INSERT INTO route_candidate
                    (estimate_id, voyage_number, transit_port, transit_days, estimated_cost, rank)
                VALUES
                    (@EstimateId, @VoyageNumber, @TransitPort, @TransitDays, @EstimatedCost, @Rank)
                """,
                new
                {
                    EstimateId = surrogateId,
                    candidate.VoyageNumber,
                    candidate.TransitPort,
                    candidate.TransitDays,
                    candidate.EstimatedCost,
                    Rank = rank++,
                },
                transaction, cancellationToken: ct));
        }
    }
}

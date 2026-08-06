using CargoTracker.Shared.Application.Persistence;
using Dapper;

namespace CargoTracker.Routing.Application.Internal.QueryServices;

public sealed class VoyageSummary
{
    public string VoyageNumber { get; set; } = string.Empty;
    public string VesselName { get; set; } = string.Empty;
    public string Carrier { get; set; } = string.Empty;
    public string SupportedCargoTypes { get; set; } = string.Empty;
    public string DepartureLocationUnlocode { get; set; } = string.Empty;
    public string ArrivalLocationUnlocode { get; set; } = string.Empty;
    public DateTime FirstDepartureDate { get; set; }
    public DateTime LastArrivalDate { get; set; }
}

public sealed class VoyageDetail
{
    public string VoyageNumber { get; set; } = string.Empty;
    public string VesselName { get; set; } = string.Empty;
    public string Carrier { get; set; } = string.Empty;
    public string SupportedCargoTypes { get; set; } = string.Empty;
    public long Version { get; set; }
    public IReadOnlyList<VoyageMovementDetail> CarrierMovements { get; set; } = [];
}

public sealed class VoyageMovementDetail
{
    public string DepartureLocationUnlocode { get; set; } = string.Empty;
    public string ArrivalLocationUnlocode { get; set; } = string.Empty;
    public DateTime DepartureDate { get; set; }
    public DateTime ArrivalDate { get; set; }
    public int SequenceNumber { get; set; }
}

/// <summary>航海画面向けの読み取りサービス。</summary>
public sealed class FindVoyageQueryService(IDbConnectionFactory connectionFactory)
{
    public async Task<IReadOnlyList<VoyageSummary>> FindAllAsync(CancellationToken ct = default)
    {
        using var connection = connectionFactory.Create();
        var items = await connection.QueryAsync<VoyageSummary>(new CommandDefinition(
            """
            SELECT v.voyage_number AS VoyageNumber,
                   v.vessel_name AS VesselName,
                   v.carrier AS Carrier,
                   v.supported_cargo_types AS SupportedCargoTypes,
                   first_movement.departure_location_unlocode AS DepartureLocationUnlocode,
                   last_movement.arrival_location_unlocode AS ArrivalLocationUnlocode,
                   first_movement.departure_date AS FirstDepartureDate,
                   last_movement.arrival_date AS LastArrivalDate
            FROM voyage v
            JOIN carrier_movement first_movement
              ON first_movement.voyage_id = v.id
             AND first_movement.seq_number = (
                 SELECT MIN(seq_number) FROM carrier_movement WHERE voyage_id = v.id
             )
            JOIN carrier_movement last_movement
              ON last_movement.voyage_id = v.id
             AND last_movement.seq_number = (
                 SELECT MAX(seq_number) FROM carrier_movement WHERE voyage_id = v.id
             )
            ORDER BY first_movement.departure_date, v.voyage_number
            """,
            cancellationToken: ct));
        return items.ToList();
    }

    public async Task<VoyageDetail?> FindByVoyageNumberAsync(string voyageNumber, CancellationToken ct = default)
    {
        using var connection = connectionFactory.Create();
        var detail = await connection.QuerySingleOrDefaultAsync<VoyageDetail>(new CommandDefinition(
            """
            SELECT voyage_number AS VoyageNumber,
                   vessel_name AS VesselName,
                   carrier AS Carrier,
                   supported_cargo_types AS SupportedCargoTypes,
                   version AS Version
            FROM voyage
            WHERE voyage_number = @VoyageNumber
            """,
            new { VoyageNumber = voyageNumber }, cancellationToken: ct));
        if (detail is null)
        {
            return null;
        }

        var movements = await connection.QueryAsync<VoyageMovementDetail>(new CommandDefinition(
            """
            SELECT departure_location_unlocode AS DepartureLocationUnlocode,
                   arrival_location_unlocode AS ArrivalLocationUnlocode,
                   departure_date AS DepartureDate,
                   arrival_date AS ArrivalDate,
                   seq_number AS SequenceNumber
            FROM carrier_movement cm
            JOIN voyage v ON v.id = cm.voyage_id
            WHERE v.voyage_number = @VoyageNumber
            ORDER BY cm.seq_number
            """,
            new { VoyageNumber = voyageNumber }, cancellationToken: ct));
        detail.CarrierMovements = movements.ToArray();
        return detail;
    }
}

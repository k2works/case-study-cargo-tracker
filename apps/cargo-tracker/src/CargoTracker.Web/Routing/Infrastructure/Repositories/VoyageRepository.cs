using System.Data;
using CargoTracker.Routing.Domain.Model;
using CargoTracker.Routing.Domain.Repositories;
using CargoTracker.Shared.Application.Persistence;
using CargoTracker.Shared.Domain.Model;
using CargoTracker.Shared.Infrastructure.Persistence;
using Dapper;

namespace CargoTracker.Routing.Infrastructure.Repositories;

/// <summary>Dapper による航海リポジトリ。</summary>
public sealed class VoyageRepository(IDbConnectionFactory connectionFactory, AmbientTransaction ambient) : IVoyageRepository
{
    public async Task SaveAsync(Voyage voyage, CancellationToken ct = default)
    {
        var now = ToDatabaseTimestamp(DateTimeOffset.UtcNow);
        var tx = ambient.Require();
        var connection = tx.Connection!;
        await connection.ExecuteAsync(new CommandDefinition(
            """
            INSERT INTO voyage
                (voyage_number, vessel_name, carrier, supported_cargo_types, created_at, updated_at, version)
            VALUES
                (@VoyageNumber, @VesselName, @Carrier, @SupportedCargoTypes, @CreatedAt, @UpdatedAt, 0)
            """,
            new
            {
                VoyageNumber = voyage.VoyageNumber.Value,
                voyage.VesselName,
                voyage.Carrier,
                SupportedCargoTypes = SerializeCargoTypes(voyage.SupportedCargoTypes),
                CreatedAt = now,
                UpdatedAt = now,
            },
            tx, cancellationToken: ct));

        var voyageId = await connection.ExecuteScalarAsync<long>(new CommandDefinition(
            "SELECT id FROM voyage WHERE voyage_number = @VoyageNumber",
            new { VoyageNumber = voyage.VoyageNumber.Value }, tx, cancellationToken: ct));

        foreach (var movement in voyage.Schedule.CarrierMovements)
        {
            await connection.ExecuteAsync(new CommandDefinition(
                """
                INSERT INTO carrier_movement
                    (voyage_id, departure_location_unlocode, arrival_location_unlocode,
                     departure_date, arrival_date, seq_number, created_at, updated_at)
                VALUES
                    (@VoyageId, @DepartureLocation, @ArrivalLocation,
                     @DepartureDate, @ArrivalDate, @SequenceNumber, @CreatedAt, @UpdatedAt)
                """,
                new
                {
                    VoyageId = voyageId,
                    DepartureLocation = movement.DepartureLocation.UnLocode,
                    ArrivalLocation = movement.ArrivalLocation.UnLocode,
                    DepartureDate = ToDatabaseTimestamp(movement.DepartureDate),
                    ArrivalDate = ToDatabaseTimestamp(movement.ArrivalDate),
                    movement.SequenceNumber,
                    CreatedAt = now,
                    UpdatedAt = now,
                },
                tx, cancellationToken: ct));
        }
    }

    public async Task<Voyage?> FindByVoyageNumberAsync(VoyageNumber voyageNumber, CancellationToken ct = default)
    {
        if (ambient.Current is not null)
        {
            var tx = ambient.Current;
            return await QueryByVoyageNumberAsync(voyageNumber, tx.Connection!, tx, ct);
        }

        using var connection = connectionFactory.Create();
        return await QueryByVoyageNumberAsync(voyageNumber, connection, null, ct);
    }

    public async Task<bool> ExistsAsync(VoyageNumber voyageNumber, CancellationToken ct = default)
    {
        if (ambient.Current is not null)
        {
            var tx = ambient.Current;
            return await ExistsAsync(voyageNumber, tx.Connection!, tx, ct);
        }

        using var connection = connectionFactory.Create();
        return await ExistsAsync(voyageNumber, connection, null, ct);
    }

    private static async Task<bool> ExistsAsync(
        VoyageNumber voyageNumber, IDbConnection connection, IDbTransaction? transaction, CancellationToken ct)
    {
        var count = await connection.ExecuteScalarAsync<int>(new CommandDefinition(
            """
            SELECT COUNT(1)
            FROM voyage
            WHERE voyage_number = @VoyageNumber
            """,
            new { VoyageNumber = voyageNumber.Value }, transaction, cancellationToken: ct));
        return count > 0;
    }

    private static async Task<Voyage?> QueryByVoyageNumberAsync(
        VoyageNumber voyageNumber, IDbConnection connection, IDbTransaction? transaction, CancellationToken ct)
    {
        var row = await connection.QuerySingleOrDefaultAsync<VoyageRow>(new CommandDefinition(
            """
            SELECT id AS Id, voyage_number AS VoyageNumber, vessel_name AS VesselName,
                   carrier AS Carrier, supported_cargo_types AS SupportedCargoTypes, version AS Version
            FROM voyage
            WHERE voyage_number = @VoyageNumber
            """,
            new { VoyageNumber = voyageNumber.Value }, transaction, cancellationToken: ct));
        if (row is null)
        {
            return null;
        }

        var movements = await connection.QueryAsync<CarrierMovementRow>(new CommandDefinition(
            """
            SELECT departure_location_unlocode AS DepartureLocationUnlocode,
                   arrival_location_unlocode AS ArrivalLocationUnlocode,
                   departure_date AS DepartureDate,
                   arrival_date AS ArrivalDate,
                   seq_number AS SequenceNumber
            FROM carrier_movement
            WHERE voyage_id = @VoyageId
            ORDER BY seq_number
            """,
            new { VoyageId = row.Id }, transaction, cancellationToken: ct));

        return row.ToVoyage(movements);
    }

    private static string SerializeCargoTypes(IEnumerable<SupportedCargoType> cargoTypes)
        => string.Join(',', cargoTypes.OrderBy(cargoType => cargoType).Select(cargoType => cargoType.ToString().ToUpperInvariant()));

    private static DateTime ToDatabaseTimestamp(DateTimeOffset value)
        => DateTime.SpecifyKind(value.UtcDateTime, DateTimeKind.Unspecified);

    private static HashSet<SupportedCargoType> DeserializeCargoTypes(string value)
        => value.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
            .Select(item => Enum.Parse<SupportedCargoType>(item, ignoreCase: true))
            .ToHashSet();

    private sealed class VoyageRow
    {
        public long Id { get; set; }
        public string VoyageNumber { get; set; } = string.Empty;
        public string VesselName { get; set; } = string.Empty;
        public string Carrier { get; set; } = string.Empty;
        public string SupportedCargoTypes { get; set; } = string.Empty;
        public long Version { get; set; }

        public Voyage ToVoyage(IEnumerable<CarrierMovementRow> movements)
            => Voyage.Reconstruct(
                new VoyageNumber(VoyageNumber),
                VesselName,
                Carrier,
                DeserializeCargoTypes(SupportedCargoTypes),
                new Schedule(movements.Select(row => row.ToCarrierMovement())),
                Version);
    }

    private sealed class CarrierMovementRow
    {
        public string DepartureLocationUnlocode { get; set; } = string.Empty;
        public string ArrivalLocationUnlocode { get; set; } = string.Empty;
        public DateTime DepartureDate { get; set; }
        public DateTime ArrivalDate { get; set; }
        public int SequenceNumber { get; set; }

        public CarrierMovement ToCarrierMovement()
            => new(
                new Location(DepartureLocationUnlocode),
                new Location(ArrivalLocationUnlocode),
                new DateTimeOffset(DateTime.SpecifyKind(DepartureDate, DateTimeKind.Utc)),
                new DateTimeOffset(DateTime.SpecifyKind(ArrivalDate, DateTimeKind.Utc)),
                SequenceNumber);
    }
}

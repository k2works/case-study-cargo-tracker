using CargoTracker.Routing.Domain.Model;
using CargoTracker.Shared.Application.Persistence;
using Dapper;

namespace CargoTracker.Routing.Application.Internal.QueryServices;

public sealed record SearchVoyagesQuery(
    string OriginUnlocode,
    string DestinationUnlocode,
    DateTimeOffset DepartureFrom,
    DateTimeOffset DepartureTo,
    SupportedCargoType CargoType);

public sealed class VoyageSearchResult
{
    public string VoyageNumber { get; set; } = string.Empty;
    public string Carrier { get; set; } = string.Empty;
    public DateTime DepartureDate { get; set; }
    public DateTime ArrivalDate { get; set; }
    public string PortChain { get; set; } = string.Empty;
}

public sealed class SearchVoyagesQueryService(IDbConnectionFactory connectionFactory)
{
    public async Task<IReadOnlyList<VoyageSearchResult>> SearchAsync(SearchVoyagesQuery query, CancellationToken ct = default)
    {
        using var connection = connectionFactory.Create();
        var rows = await connection.QueryAsync<VoyageSearchRow>(new CommandDefinition(
            """
            SELECT v.voyage_number AS VoyageNumber,
                   v.carrier AS Carrier,
                   v.supported_cargo_types AS SupportedCargoTypes,
                   cm.departure_location_unlocode AS DepartureLocationUnlocode,
                   cm.arrival_location_unlocode AS ArrivalLocationUnlocode,
                   cm.departure_date AS DepartureDate,
                   cm.arrival_date AS ArrivalDate,
                   cm.seq_number AS SequenceNumber
            FROM voyage v
            JOIN carrier_movement cm ON cm.voyage_id = v.id
            WHERE v.id IN (
                SELECT voyage_id
                FROM carrier_movement
                WHERE seq_number = 1
                  AND departure_date >= @DepartureFrom
                  AND departure_date <= @DepartureTo
            )
            ORDER BY v.voyage_number, cm.seq_number
            """,
            new
            {
                DepartureFrom = ToDatabaseTimestamp(query.DepartureFrom),
                DepartureTo = ToDatabaseTimestamp(query.DepartureTo),
            },
            cancellationToken: ct));

        return rows
            .GroupBy(row => row.VoyageNumber)
            .Select(group => BuildResult(group.OrderBy(row => row.SequenceNumber).ToArray(), query))
            .Where(result => result is not null)
            .Select(result => result!)
            .OrderBy(result => result.DepartureDate)
            .ThenBy(result => result.VoyageNumber)
            .ToArray();
    }

    private static VoyageSearchResult? BuildResult(IReadOnlyList<VoyageSearchRow> rows, SearchVoyagesQuery query)
    {
        if (rows.Count == 0 || !SupportsCargoType(rows[0].SupportedCargoTypes, query.CargoType))
        {
            return null;
        }

        var originIndex = FindDepartureIndex(rows, query.OriginUnlocode);
        if (originIndex < 0)
        {
            return null;
        }

        var destinationIndex = FindArrivalIndex(rows, query.DestinationUnlocode, originIndex);
        if (destinationIndex < originIndex)
        {
            return null;
        }

        var selectedRows = rows.Skip(originIndex).Take(destinationIndex - originIndex + 1).ToArray();
        var ports = selectedRows
            .Select(row => row.DepartureLocationUnlocode)
            .Append(selectedRows[^1].ArrivalLocationUnlocode);

        return new VoyageSearchResult
        {
            VoyageNumber = rows[0].VoyageNumber,
            Carrier = rows[0].Carrier,
            DepartureDate = selectedRows[0].DepartureDate,
            ArrivalDate = selectedRows[^1].ArrivalDate,
            PortChain = string.Join(" → ", ports),
        };
    }

    private static int FindDepartureIndex(IReadOnlyList<VoyageSearchRow> rows, string unlocode)
    {
        for (var i = 0; i < rows.Count; i++)
        {
            if (string.Equals(rows[i].DepartureLocationUnlocode, unlocode, StringComparison.OrdinalIgnoreCase))
            {
                return i;
            }
        }
        return -1;
    }

    private static int FindArrivalIndex(IReadOnlyList<VoyageSearchRow> rows, string unlocode, int startIndex)
    {
        for (var i = startIndex; i < rows.Count; i++)
        {
            if (string.Equals(rows[i].ArrivalLocationUnlocode, unlocode, StringComparison.OrdinalIgnoreCase))
            {
                return i;
            }
        }
        return -1;
    }

    private static bool SupportsCargoType(string csv, SupportedCargoType cargoType)
        => csv.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
            .Any(value => string.Equals(value, cargoType.ToString(), StringComparison.OrdinalIgnoreCase));

    private static DateTime ToDatabaseTimestamp(DateTimeOffset value)
        => DateTime.SpecifyKind(value.UtcDateTime, DateTimeKind.Unspecified);

    private sealed class VoyageSearchRow
    {
        public string VoyageNumber { get; set; } = string.Empty;
        public string Carrier { get; set; } = string.Empty;
        public string SupportedCargoTypes { get; set; } = string.Empty;
        public string DepartureLocationUnlocode { get; set; } = string.Empty;
        public string ArrivalLocationUnlocode { get; set; } = string.Empty;
        public DateTime DepartureDate { get; set; }
        public DateTime ArrivalDate { get; set; }
        public int SequenceNumber { get; set; }
    }
}

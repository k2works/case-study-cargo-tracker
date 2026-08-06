using CargoTracker.Shared.Domain.Model;

namespace CargoTracker.Routing.Domain.Model;

/// <summary>航海番号。Routing BC 内の航海を一意に識別する。</summary>
public sealed record VoyageNumber
{
    public string Value { get; }

    public VoyageNumber(string value)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            throw new ArgumentException("航海番号は必須です。", nameof(value));
        }
        if (value.Length > 20)
        {
            throw new ArgumentException("航海番号は 20 文字以内で指定してください。", nameof(value));
        }
        Value = value.Trim().ToUpperInvariant();
    }

    public override string ToString() => Value;
}

/// <summary>Routing BC 独自の対応貨物種別。Booking BC の CargoType とは共有しない。</summary>
public enum SupportedCargoType
{
    General,
    Hazardous,
    Refrigerated,
}

/// <summary>航海を構成する運送区間。</summary>
public sealed record CarrierMovement
{
    public Location DepartureLocation { get; }
    public Location ArrivalLocation { get; }
    public DateTimeOffset DepartureDate { get; }
    public DateTimeOffset ArrivalDate { get; }
    public int SequenceNumber { get; }

    public CarrierMovement(
        Location departureLocation,
        Location arrivalLocation,
        DateTimeOffset departureDate,
        DateTimeOffset arrivalDate,
        int sequenceNumber)
    {
        if (departureLocation.SameAs(arrivalLocation))
        {
            throw new ArgumentException("出発地と到着地は異なる必要があります。", nameof(arrivalLocation));
        }
        if (departureDate > arrivalDate)
        {
            throw new ArgumentException("出発日時は到着日時以前でなければなりません。", nameof(arrivalDate));
        }
        if (sequenceNumber < 1)
        {
            throw new ArgumentException("区間順序は 1 以上で指定してください。", nameof(sequenceNumber));
        }

        DepartureLocation = departureLocation;
        ArrivalLocation = arrivalLocation;
        DepartureDate = departureDate;
        ArrivalDate = arrivalDate;
        SequenceNumber = sequenceNumber;
    }
}

/// <summary>航海スケジュール。区間は seq_number 順で保持される。</summary>
public sealed record Schedule
{
    private readonly IReadOnlyList<CarrierMovement> _carrierMovements;

    public IReadOnlyList<CarrierMovement> CarrierMovements => _carrierMovements;

    public Schedule(IEnumerable<CarrierMovement> carrierMovements)
    {
        var ordered = carrierMovements.OrderBy(movement => movement.SequenceNumber).ToArray();
        ValidateTimeline(ordered);
        _carrierMovements = ordered;
    }

    public IReadOnlyList<Location> Departures() => _carrierMovements.Select(movement => movement.DepartureLocation).ToArray();

    public IReadOnlyList<Location> Arrivals() => _carrierMovements.Select(movement => movement.ArrivalLocation).ToArray();

    private static void ValidateTimeline(CarrierMovement[] carrierMovements)
    {
        for (var i = 1; i < carrierMovements.Length; i++)
        {
            if (carrierMovements[i - 1].ArrivalDate > carrierMovements[i].DepartureDate)
            {
                throw new ArgumentException("運送区間は時系列順で指定してください。", nameof(carrierMovements));
            }
        }
    }
}

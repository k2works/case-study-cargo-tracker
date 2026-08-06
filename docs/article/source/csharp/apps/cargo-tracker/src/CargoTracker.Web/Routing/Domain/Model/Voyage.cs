using CargoTracker.Routing.Domain.Model.Events;
using CargoTracker.Shared.Domain.Model;

namespace CargoTracker.Routing.Domain.Model;

/// <summary>航海集約ルート（US24）。</summary>
public sealed class Voyage : AggregateRoot
{
    public VoyageNumber VoyageNumber { get; }
    public string VesselName { get; private set; }
    public string Carrier { get; private set; }
    public IReadOnlySet<SupportedCargoType> SupportedCargoTypes { get; private set; }
    public Schedule Schedule { get; private set; }
    public long Version { get; private set; }

    private Voyage(
        VoyageNumber voyageNumber,
        string vesselName,
        string carrier,
        IReadOnlySet<SupportedCargoType> supportedCargoTypes,
        Schedule schedule,
        long version)
    {
        VoyageNumber = voyageNumber;
        VesselName = vesselName;
        Carrier = carrier;
        SupportedCargoTypes = supportedCargoTypes;
        Schedule = schedule;
        Version = version;
    }

    public static Voyage Create(
        VoyageNumber voyageNumber,
        string vesselName,
        string carrier,
        IEnumerable<SupportedCargoType> supportedCargoTypes,
        Schedule schedule)
    {
        var voyage = Reconstruct(voyageNumber, vesselName, carrier, supportedCargoTypes, schedule, 0);
        voyage.AddDomainEvent(new VoyageRegisteredEvent(voyage.VoyageNumber));
        return voyage;
    }

    public void UpdateSchedule(
        string vesselName,
        string carrier,
        IEnumerable<SupportedCargoType> supportedCargoTypes,
        Schedule schedule)
    {
        var validated = Reconstruct(VoyageNumber, vesselName, carrier, supportedCargoTypes, schedule, Version);

        VesselName = validated.VesselName;
        Carrier = validated.Carrier;
        SupportedCargoTypes = validated.SupportedCargoTypes;
        Schedule = validated.Schedule;
        Version++;
        AddDomainEvent(new ScheduleUpdatedEvent(VoyageNumber));
    }

    /// <summary>永続化データから集約を再構築する（イベントは発生させない）。</summary>
    public static Voyage Reconstruct(
        VoyageNumber voyageNumber,
        string vesselName,
        string carrier,
        IEnumerable<SupportedCargoType> supportedCargoTypes,
        Schedule schedule,
        long version)
    {
        if (string.IsNullOrWhiteSpace(vesselName))
        {
            throw new ArgumentException("船名は必須です。", nameof(vesselName));
        }
        if (vesselName.Length > 200)
        {
            throw new ArgumentException("船名は 200 文字以内で指定してください。", nameof(vesselName));
        }
        if (string.IsNullOrWhiteSpace(carrier))
        {
            throw new ArgumentException("運送会社は必須です。", nameof(carrier));
        }
        if (carrier.Length > 200)
        {
            throw new ArgumentException("運送会社は 200 文字以内で指定してください。", nameof(carrier));
        }

        var cargoTypes = supportedCargoTypes.Distinct().ToHashSet();
        if (cargoTypes.Count == 0)
        {
            throw new ArgumentException("対応貨物種別を 1 つ以上指定してください。", nameof(supportedCargoTypes));
        }
        if (schedule.CarrierMovements.Count == 0)
        {
            throw new ArgumentException("運送区間を 1 件以上指定してください。", nameof(schedule));
        }
        ValidateContinuity(schedule);

        return new Voyage(voyageNumber, vesselName.Trim(), carrier.Trim(), cargoTypes, schedule, version);
    }

    private static void ValidateContinuity(Schedule schedule)
    {
        var movements = schedule.CarrierMovements;
        for (var i = 1; i < movements.Count; i++)
        {
            if (!movements[i - 1].ArrivalLocation.SameAs(movements[i].DepartureLocation))
            {
                throw new ArgumentException("前区間の到着港と次区間の出発港が一致している必要があります。", nameof(schedule));
            }
        }
    }
}

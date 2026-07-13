using CargoTracker.Shared.Domain.Model;

namespace CargoTracker.Handling.Domain.Model;

/// <summary>
/// 荷役作業（US15/US16）。荷役作業員が記録する受領・積込・荷降し・引取の作業単位。
/// CargoSnapshot に対して作業場所・航海番号の妥当性を検証する集約ルート。
/// </summary>
public sealed class HandlingActivity : AggregateRoot
{
    public CargoBookingId BookingId { get; }
    public HandlingType Type { get; }
    public Location Location { get; }
    public DateTimeOffset CompletionTime { get; }
    public VoyageNumber? VoyageNumber { get; }

    private HandlingActivity(
        CargoBookingId bookingId, HandlingType type, Location location,
        DateTimeOffset completionTime, VoyageNumber? voyageNumber)
    {
        BookingId = bookingId;
        Type = type;
        Location = location;
        CompletionTime = completionTime;
        VoyageNumber = voyageNumber;
    }

    /// <summary>荷役作業を登録する（US15）。種別が航海番号を要する場合は必須検証を行う。</summary>
    public static HandlingActivity Register(
        string bookingId, HandlingEventType eventType, string locationUnLocode,
        DateTimeOffset completionTime, string? voyageNumber = null)
    {
        var type = new HandlingType(eventType);
        if (type.RequiresVoyageNumber() && string.IsNullOrWhiteSpace(voyageNumber))
        {
            throw new ArgumentException("積込・荷降しには航海番号が必要です。", nameof(voyageNumber));
        }
        return new HandlingActivity(
            new CargoBookingId(bookingId), type, new Location(locationUnLocode), completionTime,
            string.IsNullOrWhiteSpace(voyageNumber) ? null : new VoyageNumber(voyageNumber));
    }

    /// <summary>
    /// 貨物スナップショットに対して作業場所が予定ルートと一致するか検証する（デシジョンテーブル）。
    /// RECEIVE=出発港、LOAD=旅程の積込港、UNLOAD=旅程の荷降港、CLAIM=目的港と一致すれば妥当。
    /// </summary>
    public bool IsValidFor(CargoSnapshot snapshot)
    {
        ArgumentNullException.ThrowIfNull(snapshot);
        var loc = Location.UnLocode;
        return Type.Type switch
        {
            HandlingEventType.Receive => loc == snapshot.Origin,
            HandlingEventType.Claim => loc == snapshot.Destination,
            HandlingEventType.Load => snapshot.ItineraryLegs.Any(
                l => l.LoadLocationUnLocode == loc && l.VoyageNumber == VoyageNumber?.Number),
            HandlingEventType.Unload => snapshot.ItineraryLegs.Any(
                l => l.UnloadLocationUnLocode == loc && l.VoyageNumber == VoyageNumber?.Number),
            _ => false,
        };
    }

    /// <summary>妥当でない場合に MISROUTED（経路誤り）として扱うか。積込・荷降しの不一致は MISROUTED、受領・引取は警告。</summary>
    public bool IsMisrouteWhenInvalid() => Type.RequiresVoyageNumber();

    /// <summary>この荷役に対応する追跡イベント種別（受領→Receive など）。追跡 ACL への変換に使う。</summary>
    public string EventTypeName => Type.Type.ToString();
}

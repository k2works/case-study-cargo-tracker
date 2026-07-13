using CargoTracker.Booking.Domain.Events;
using CargoTracker.Shared.Domain.Model;

namespace CargoTracker.Booking.Domain.Model;

/// <summary>貨物予約集約ルート（US04）。</summary>
public sealed class Cargo : AggregateRoot
{
    public BookingId BookingId { get; }
    public ShipperId ShipperId { get; }
    public RouteSpecification RouteSpecification { get; }
    public CargoType CargoType { get; }
    public decimal Weight { get; }
    public Dimensions? Dimensions { get; }
    public Quantity? Quantity { get; }
    public Description? Description { get; }
    public HazardousDeclaration? HazardousDeclaration { get; }
    public TemperatureRequirement? TemperatureRequirement { get; }
    public BookingStatus BookingStatus { get; private set; }
    public CargoItinerary? CargoItinerary { get; private set; }
    public long Version { get; private set; }

    private Cargo(
        BookingId bookingId, ShipperId shipperId, RouteSpecification routeSpecification, CargoType cargoType,
        decimal weight, Dimensions? dimensions, Quantity? quantity, Description? description,
        HazardousDeclaration? hazardousDeclaration, TemperatureRequirement? temperatureRequirement,
        BookingStatus bookingStatus, long version, CargoItinerary? cargoItinerary = null)
    {
        BookingId = bookingId;
        ShipperId = shipperId;
        RouteSpecification = routeSpecification;
        CargoType = cargoType;
        Weight = weight;
        Dimensions = dimensions;
        Quantity = quantity;
        Description = description;
        HazardousDeclaration = hazardousDeclaration;
        TemperatureRequirement = temperatureRequirement;
        BookingStatus = bookingStatus;
        CargoItinerary = cargoItinerary;
        Version = version;
    }

    public static Cargo Create(
        ShipperId shipperId, RouteSpecification routeSpecification, CargoType cargoType, decimal weight,
        Dimensions? dimensions = null, Quantity? quantity = null, Description? description = null,
        HazardousDeclaration? hazardousDeclaration = null, TemperatureRequirement? temperatureRequirement = null,
        DateOnly? today = null)
    {
        if (weight <= 0)
        {
            throw new ArgumentException("重量は正の値でなければなりません。", nameof(weight));
        }
        // 到着期限は当日以降でなければならない（IT2 レビュー H3。Estimate.Create と同じ不変条件）。
        var currentDate = today ?? DateOnly.FromDateTime(DateTime.UtcNow);
        if (routeSpecification.ArrivalDeadline < currentDate)
        {
            throw new ArgumentException("到着期限は当日以降でなければなりません。", nameof(routeSpecification));
        }
        ValidateSpecialRequirements(cargoType, hazardousDeclaration, temperatureRequirement);

        var cargo = new Cargo(
            BookingId.Generate(), shipperId, routeSpecification, cargoType, weight,
            dimensions, quantity, description, hazardousDeclaration, temperatureRequirement, BookingStatus.Preliminary, 0);
        cargo.AddDomainEvent(new CargoBookedEvent(cargo.BookingId));
        return cargo;
    }

    public void AssignToRouting()
    {
        if (BookingStatus != BookingStatus.Preliminary)
        {
            throw new InvalidOperationException("仮受付の予約のみ経路設計へ割り当てられます。");
        }
        BookingStatus = BookingStatus.RouteProposed;
        Version++;
        AddDomainEvent(new AssignedToRoutingEvent(BookingId));
    }

    /// <summary>確定経路（旅程）を予約に紐付ける（US11）。状態は RouteProposed のまま維持する。</summary>
    public void AssignItinerary(CargoItinerary itinerary)
    {
        ArgumentNullException.ThrowIfNull(itinerary);
        if (BookingStatus != BookingStatus.RouteProposed)
        {
            throw new InvalidOperationException("経路提案中の予約のみ経路を紐付けられます。");
        }
        CargoItinerary = itinerary;
        Version++;
        AddDomainEvent(new CargoRoutedEvent(BookingId));
    }

    /// <summary>予約を確定する（US13）。経路提案中かつ旅程割当済みでなければならない。</summary>
    public void Confirm()
    {
        if (BookingStatus != BookingStatus.RouteProposed)
        {
            throw new InvalidOperationException("経路提案中の予約のみ確定できます。");
        }
        if (CargoItinerary is null)
        {
            throw new InvalidOperationException("経路が紐付けられていない予約は確定できません。");
        }
        BookingStatus = BookingStatus.Confirmed;
        Version++;
        AddDomainEvent(new BookingConfirmedEvent(BookingId));
    }

    /// <summary>荷主のルート変更希望で経路再設計に差し戻す（US13）。RouteProposed → Preliminary。</summary>
    public void ReturnToRouting()
    {
        if (BookingStatus != BookingStatus.RouteProposed)
        {
            throw new InvalidOperationException("経路提案中の予約のみ経路再設計に差し戻せます。");
        }
        BookingStatus = BookingStatus.Preliminary;
        CargoItinerary = null;
        Version++;
    }

    /// <summary>予約をキャンセルする（US13）。確定後・終端状態からはキャンセルできない。</summary>
    public void Cancel()
    {
        if (BookingStatus is not (BookingStatus.Preliminary or BookingStatus.RouteProposed))
        {
            throw new InvalidOperationException("仮受付または経路提案中の予約のみキャンセルできます。");
        }
        BookingStatus = BookingStatus.Cancelled;
        Version++;
    }

    /// <summary>永続化データから集約を再構築する（イベントは発生させない）。</summary>
    public static Cargo Reconstruct(
        BookingId bookingId, ShipperId shipperId, RouteSpecification routeSpecification, CargoType cargoType,
        decimal weight, Dimensions? dimensions, Quantity? quantity, Description? description,
        BookingStatus bookingStatus, long version,
        HazardousDeclaration? hazardousDeclaration = null, TemperatureRequirement? temperatureRequirement = null,
        CargoItinerary? cargoItinerary = null)
        => new(bookingId, shipperId, routeSpecification, cargoType, weight, dimensions, quantity, description,
            hazardousDeclaration, temperatureRequirement,
            bookingStatus, version, cargoItinerary);

    private static void ValidateSpecialRequirements(
        CargoType cargoType, HazardousDeclaration? hazardousDeclaration, TemperatureRequirement? temperatureRequirement)
    {
        switch (cargoType)
        {
            case CargoType.Hazardous:
                if (hazardousDeclaration is null)
                {
                    throw new ArgumentException("危険物貨物には危険物申告が必要です。", nameof(hazardousDeclaration));
                }
                if (temperatureRequirement is not null)
                {
                    throw new ArgumentException("危険物貨物に温度管理条件は指定できません。", nameof(temperatureRequirement));
                }
                break;
            case CargoType.Refrigerated:
                if (temperatureRequirement is null)
                {
                    throw new ArgumentException("冷凍・冷蔵貨物には温度管理条件が必要です。", nameof(temperatureRequirement));
                }
                if (hazardousDeclaration is not null)
                {
                    throw new ArgumentException("冷凍・冷蔵貨物に危険物申告は指定できません。", nameof(hazardousDeclaration));
                }
                break;
            case CargoType.General:
                if (hazardousDeclaration is not null || temperatureRequirement is not null)
                {
                    throw new ArgumentException("一般貨物に特別情報は指定できません。");
                }
                break;
            default:
                throw new ArgumentOutOfRangeException(nameof(cargoType), cargoType, "未対応の貨物種別です。");
        }
    }
}

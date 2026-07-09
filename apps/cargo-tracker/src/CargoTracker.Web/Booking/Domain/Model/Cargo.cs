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
    public long Version { get; private set; }

    private Cargo(
        BookingId bookingId, ShipperId shipperId, RouteSpecification routeSpecification, CargoType cargoType,
        decimal weight, Dimensions? dimensions, Quantity? quantity, Description? description,
        HazardousDeclaration? hazardousDeclaration, TemperatureRequirement? temperatureRequirement,
        BookingStatus bookingStatus, long version)
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
        Version = version;
    }

    public static Cargo Create(
        ShipperId shipperId, RouteSpecification routeSpecification, CargoType cargoType, decimal weight,
        Dimensions? dimensions = null, Quantity? quantity = null, Description? description = null,
        HazardousDeclaration? hazardousDeclaration = null, TemperatureRequirement? temperatureRequirement = null)
    {
        if (weight <= 0)
        {
            throw new ArgumentException("重量は正の値でなければなりません。", nameof(weight));
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

    /// <summary>永続化データから集約を再構築する（イベントは発生させない）。</summary>
    public static Cargo Reconstruct(
        BookingId bookingId, ShipperId shipperId, RouteSpecification routeSpecification, CargoType cargoType,
        decimal weight, Dimensions? dimensions, Quantity? quantity, Description? description,
        BookingStatus bookingStatus, long version,
        HazardousDeclaration? hazardousDeclaration = null, TemperatureRequirement? temperatureRequirement = null)
        => new(bookingId, shipperId, routeSpecification, cargoType, weight, dimensions, quantity, description,
            hazardousDeclaration, temperatureRequirement,
            bookingStatus, version);

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

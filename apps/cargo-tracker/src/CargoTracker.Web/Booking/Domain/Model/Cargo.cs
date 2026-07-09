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
    public BookingStatus BookingStatus { get; private set; }
    public long Version { get; }

    private Cargo(
        BookingId bookingId, ShipperId shipperId, RouteSpecification routeSpecification, CargoType cargoType,
        decimal weight, Dimensions? dimensions, Quantity? quantity, Description? description,
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
        BookingStatus = bookingStatus;
        Version = version;
    }

    public static Cargo Create(
        ShipperId shipperId, RouteSpecification routeSpecification, CargoType cargoType, decimal weight,
        Dimensions? dimensions = null, Quantity? quantity = null, Description? description = null)
    {
        if (weight <= 0)
        {
            throw new ArgumentException("重量は正の値でなければなりません。", nameof(weight));
        }

        var cargo = new Cargo(
            BookingId.Generate(), shipperId, routeSpecification, cargoType, weight,
            dimensions, quantity, description, BookingStatus.Preliminary, 0);
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
    }

    /// <summary>永続化データから集約を再構築する（イベントは発生させない）。</summary>
    public static Cargo Reconstruct(
        BookingId bookingId, ShipperId shipperId, RouteSpecification routeSpecification, CargoType cargoType,
        decimal weight, Dimensions? dimensions, Quantity? quantity, Description? description,
        BookingStatus bookingStatus, long version)
        => new(bookingId, shipperId, routeSpecification, cargoType, weight, dimensions, quantity, description,
            bookingStatus, version);
}

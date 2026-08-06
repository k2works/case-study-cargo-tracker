namespace CargoTracker.Routing.Application.Internal.OutboundServices;

public sealed class RoutingBookingInfo
{
    public string BookingId { get; set; } = string.Empty;
    public string OriginUnlocode { get; set; } = string.Empty;
    public string DestinationUnlocode { get; set; } = string.Empty;
    public DateOnly ArrivalDeadline { get; set; }
    public string CargoType { get; set; } = string.Empty;
    public decimal Weight { get; set; }
}

public interface IBookingLookup
{
    Task<RoutingBookingInfo?> FindByBookingIdAsync(string bookingId, CancellationToken ct = default);
}

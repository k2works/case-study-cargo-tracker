namespace CargoTracker.Booking.Domain.Model;

/// <summary>予約状態。本 IT では Preliminary 起点と RouteProposed への遷移のみ実装する。</summary>
public enum BookingStatus
{
    Preliminary,
    RouteProposed,
    Confirmed,
    TrackingIssued,
    InTransit,
    Delivered,
    Settled,
    Cancelled,
}

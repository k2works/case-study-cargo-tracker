using CargoTracker.Booking.Domain.Model;

namespace CargoTracker.Booking.Application.Internal.CommandServices;

/// <summary>確定経路を荷主に通知するコマンド（US12）。</summary>
public sealed record NotifyRouteToShipperCommand(BookingId BookingId);

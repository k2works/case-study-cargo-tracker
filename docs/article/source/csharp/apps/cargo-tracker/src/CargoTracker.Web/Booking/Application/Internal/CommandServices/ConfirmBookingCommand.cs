using CargoTracker.Booking.Domain.Model;

namespace CargoTracker.Booking.Application.Internal.CommandServices;

/// <summary>予約を確定するコマンド（US13）。</summary>
public sealed record ConfirmBookingCommand(BookingId BookingId);

using CargoTracker.Booking.Domain.Model;

namespace CargoTracker.Booking.Application.Internal.CommandServices;

/// <summary>予約をキャンセルするコマンド（US13）。</summary>
public sealed record CancelBookingCommand(BookingId BookingId);

using CargoTracker.Booking.Domain.Model;

namespace CargoTracker.Booking.Application.Internal.CommandServices;

/// <summary>荷主のルート変更希望で経路再設計に差し戻すコマンド（US13）。</summary>
public sealed record ReturnToRoutingCommand(BookingId BookingId);

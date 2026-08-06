using CargoTracker.Booking.Domain.Model;

namespace CargoTracker.Booking.Application.Internal.CommandServices;

/// <summary>貨物予約を経路設計へ引き渡すコマンド（US06）。</summary>
public sealed record AssignToRoutingCommand(BookingId BookingId);

using CargoTracker.Booking.Domain.Model;
using MediatR;

namespace CargoTracker.Booking.Domain.Events;

/// <summary>貨物予約が経路設計へ引き渡されたことを表すドメインイベント。</summary>
public sealed record AssignedToRoutingEvent(BookingId BookingId) : INotification;

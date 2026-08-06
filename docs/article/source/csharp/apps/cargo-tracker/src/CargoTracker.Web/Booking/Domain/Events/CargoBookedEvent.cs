using CargoTracker.Booking.Domain.Model;
using MediatR;

namespace CargoTracker.Booking.Domain.Events;

/// <summary>貨物予約が登録されたことを表すドメインイベント。</summary>
public sealed record CargoBookedEvent(BookingId BookingId) : INotification;

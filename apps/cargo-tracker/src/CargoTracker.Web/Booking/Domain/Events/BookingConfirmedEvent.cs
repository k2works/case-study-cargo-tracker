using CargoTracker.Booking.Domain.Model;
using MediatR;

namespace CargoTracker.Booking.Domain.Events;

/// <summary>予約が確定されたことを表すドメインイベント（US13）。追跡番号発行フェーズを後続で起動する。</summary>
public sealed record BookingConfirmedEvent(BookingId BookingId) : INotification;

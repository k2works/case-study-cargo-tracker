using CargoTracker.Booking.Domain.Model;
using MediatR;

namespace CargoTracker.Booking.Domain.Events;

/// <summary>確定経路（旅程）が予約に紐付けられたことを表すドメインイベント（US11）。</summary>
public sealed record CargoRoutedEvent(BookingId BookingId) : INotification;

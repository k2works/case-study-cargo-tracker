using CargoTracker.Shared.Domain.Model;
using MediatR;

namespace CargoTracker.Shipper.Domain.Model.Events;

/// <summary>荷主が新規登録されたことを表すドメインイベント。</summary>
public sealed record ShipperRegisteredEvent(ShipperId ShipperId, ShipperType Type) : INotification;

using CargoTracker.Routing.Domain.Model;
using MediatR;

namespace CargoTracker.Routing.Domain.Model.Events;

/// <summary>航海スケジュールが更新されたことを表すドメインイベント。</summary>
public sealed record ScheduleUpdatedEvent(VoyageNumber VoyageNumber) : INotification;

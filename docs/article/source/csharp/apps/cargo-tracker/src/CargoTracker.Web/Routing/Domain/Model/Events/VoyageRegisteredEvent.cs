using CargoTracker.Routing.Domain.Model;
using MediatR;

namespace CargoTracker.Routing.Domain.Model.Events;

/// <summary>航海スケジュールが登録されたことを表すドメインイベント。</summary>
public sealed record VoyageRegisteredEvent(VoyageNumber VoyageNumber) : INotification;

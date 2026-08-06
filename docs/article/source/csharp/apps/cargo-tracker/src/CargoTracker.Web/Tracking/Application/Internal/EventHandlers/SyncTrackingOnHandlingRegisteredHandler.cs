using CargoTracker.Handling.Domain.Events;
using CargoTracker.Shared.Application.Persistence;
using CargoTracker.Tracking.Domain.Model;
using CargoTracker.Tracking.Domain.Repositories;
using MediatR;

namespace CargoTracker.Tracking.Application.Internal.EventHandlers;

/// <summary>
/// 荷役登録（US15）を起点に追跡イベントを追記する post-commit ハンドラ。
/// 荷役種別を追跡イベント種別に変換し TrackingActivity に追加、TransportStatus を更新する。
/// Tracking Context が HandlingActivityRegisteredEvent を消費して状態同期する（domain-model の設計）。
/// </summary>
public sealed class SyncTrackingOnHandlingRegisteredHandler(
    IUnitOfWorkFactory unitOfWorkFactory,
    ITrackingActivityRepository repository) : INotificationHandler<HandlingActivityRegisteredEvent>
{
    public async Task Handle(HandlingActivityRegisteredEvent notification, CancellationToken cancellationToken)
    {
        var tracking = await repository.FindByBookingIdAsync(notification.BookingId, cancellationToken);
        if (tracking is null)
        {
            // 追跡番号未発行の予約には同期しない（US14 で発行済みが前提）。
            return;
        }

        if (!Enum.TryParse<TrackingEventType>(notification.EventType, ignoreCase: true, out var eventType))
        {
            return;
        }

        tracking.AddEvent(new TrackingActivityEvent(
            eventType,
            new TrackingLocation(notification.LocationUnLocode),
            notification.CompletionTime,
            string.IsNullOrWhiteSpace(notification.VoyageNumber) ? null : new TrackingVoyageNumber(notification.VoyageNumber)));

        await using var unitOfWork = unitOfWorkFactory.Begin();
        unitOfWork.Track(tracking);
        await repository.SaveAsync(tracking, cancellationToken);
        await unitOfWork.CommitAsync(cancellationToken);
    }
}

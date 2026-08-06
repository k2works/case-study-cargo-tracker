using CargoTracker.Booking.Domain.Events;
using CargoTracker.Booking.Domain.Model;
using CargoTracker.Booking.Domain.Repositories;
using CargoTracker.Shared.Application.Persistence;
using CargoTracker.Tracking.Application.Internal.CommandServices;
using MediatR;

namespace CargoTracker.Booking.Application.Internal.EventHandlers;

/// <summary>
/// 予約確定（US13）を起点に追跡番号を発行する post-commit ハンドラ（US14・IT4 レビュー H3 解消）。
/// Tracking BC に追跡レコードを作成し、予約状態を Confirmed → TrackingIssued に遷移させる。
/// Booking Context がイベント駆動で追跡発行をオーケストレーションする（domain-model の設計）。
/// </summary>
public sealed class IssueTrackingOnBookingConfirmedHandler(
    AssignTrackingNumberCommandService assignTrackingNumberCommandService,
    IUnitOfWorkFactory unitOfWorkFactory,
    ICargoRepository cargoRepository) : INotificationHandler<BookingConfirmedEvent>
{
    public async Task Handle(BookingConfirmedEvent notification, CancellationToken cancellationToken)
    {
        // 追跡レコードを発行する（発行済みなら冪等スキップ）。
        await assignTrackingNumberCommandService.HandleAsync(
            new AssignTrackingNumberCommand(notification.BookingId.Value), cancellationToken);

        // 予約状態を追跡ライフサイクルへ移行する。
        await using var unitOfWork = unitOfWorkFactory.Begin();
        var cargo = await cargoRepository.FindByBookingIdAsync(notification.BookingId, cancellationToken);
        if (cargo is null || cargo.BookingStatus != BookingStatus.Confirmed)
        {
            return;
        }
        cargo.IssueTracking();
        unitOfWork.Track(cargo);
        await cargoRepository.UpdateAsync(cargo, cancellationToken);
        await unitOfWork.CommitAsync(cancellationToken);
    }
}

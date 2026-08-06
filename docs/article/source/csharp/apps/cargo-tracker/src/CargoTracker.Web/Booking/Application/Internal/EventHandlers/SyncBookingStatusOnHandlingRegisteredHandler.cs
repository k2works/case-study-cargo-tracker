using CargoTracker.Booking.Domain.Model;
using CargoTracker.Booking.Domain.Repositories;
using CargoTracker.Handling.Domain.Events;
using CargoTracker.Shared.Application.Persistence;
using MediatR;

namespace CargoTracker.Booking.Application.Internal.EventHandlers;

/// <summary>
/// 荷役登録（US15/US16）を起点に予約状態を同期する post-commit ハンドラ。
/// 積込・荷降し（LOAD/UNLOAD）→ 輸送中（InTransit）、引取（CLAIM）→ 配送完了（Delivered）。
/// </summary>
public sealed class SyncBookingStatusOnHandlingRegisteredHandler(
    IUnitOfWorkFactory unitOfWorkFactory,
    ICargoRepository cargoRepository) : INotificationHandler<HandlingActivityRegisteredEvent>
{
    public async Task Handle(HandlingActivityRegisteredEvent notification, CancellationToken cancellationToken)
    {
        var bookingId = new BookingId(notification.BookingId);
        var cargo = await cargoRepository.FindByBookingIdAsync(bookingId, cancellationToken);
        if (cargo is null)
        {
            return;
        }

        var eventType = notification.EventType.ToUpperInvariant();
        var before = cargo.BookingStatus;
        switch (eventType)
        {
            case "LOAD":
            case "UNLOAD":
                cargo.MarkInTransit();
                break;
            case "CLAIM":
                cargo.MarkDelivered();
                break;
            default:
                return; // RECEIVE は予約状態を変えない。
        }

        if (cargo.BookingStatus == before)
        {
            return; // 変化なし。
        }

        await using var unitOfWork = unitOfWorkFactory.Begin();
        unitOfWork.Track(cargo);
        await cargoRepository.UpdateAsync(cargo, cancellationToken);
        await unitOfWork.CommitAsync(cancellationToken);
    }
}

using CargoTracker.Booking.Domain.Model;
using CargoTracker.Booking.Domain.Repositories;
using CargoTracker.Shared.Application.Persistence;

namespace CargoTracker.Booking.Application.Internal.CommandServices;

/// <summary>確定経路を荷主に通知するユースケース（US12）。通知記録を保存する。</summary>
public sealed class NotifyRouteToShipperCommandService(
    IUnitOfWorkFactory unitOfWorkFactory,
    ICargoRepository cargoRepository,
    IRouteNotificationRepository notificationRepository)
{
    public async Task HandleAsync(NotifyRouteToShipperCommand command, CancellationToken ct = default)
    {
        await using var unitOfWork = unitOfWorkFactory.Begin();

        var cargo = await cargoRepository.FindByBookingIdAsync(command.BookingId, ct)
            ?? throw new InvalidOperationException("指定された貨物予約が見つかりません。");

        var notification = RouteNotification.Create(cargo, DateTimeOffset.UtcNow);
        await notificationRepository.SaveAsync(notification, ct);
        await unitOfWork.CommitAsync(ct);
    }
}

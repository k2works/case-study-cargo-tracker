using CargoTracker.Booking.Domain.Model;
using CargoTracker.Booking.Domain.Repositories;
using CargoTracker.Shared.Application.Persistence;

namespace CargoTracker.Booking.Application.Internal.CommandServices;

/// <summary>
/// 確定経路を荷主に通知するユースケース（US12）。通知記録を保存する。
/// 通知は追記型（append-only）の監査ログとして扱う：経路変更後の再通知は正当な業務操作のため、
/// 同一予約への複数回通知を許容し記録を蓄積する（IT4 レビュー H5 の方針確定）。
/// 表示・最新参照は notified_at 降順で最新 1 件を用いる。
/// </summary>
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

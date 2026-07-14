using CargoTracker.Billing.Domain.Events;
using CargoTracker.Booking.Domain.Model;
using CargoTracker.Booking.Domain.Repositories;
using CargoTracker.Shared.Application.Persistence;
using MediatR;
using Microsoft.Extensions.Logging;

namespace CargoTracker.Booking.Application.Internal.EventHandlers;

/// <summary>
/// 入金確認（US23）を起点に予約状態を精算済（Settled）へ同期する post-commit ハンドラ。
/// ADR-0009 の結果整合性方針に従い、冪等（既に Settled ならスキップ）・失敗は WARN ログに残す。
/// </summary>
public sealed class SyncBookingStatusOnPaymentConfirmedHandler(
    IUnitOfWorkFactory unitOfWorkFactory,
    ICargoRepository cargoRepository,
    ILogger<SyncBookingStatusOnPaymentConfirmedHandler> logger)
    : INotificationHandler<PaymentConfirmedEvent>
{
    public async Task Handle(PaymentConfirmedEvent notification, CancellationToken cancellationToken)
    {
        try
        {
            var cargo = await cargoRepository.FindByBookingIdAsync(new BookingId(notification.BookingId), cancellationToken);
            if (cargo is null || cargo.BookingStatus == BookingStatus.Settled)
            {
                return; // 未発見、または既に精算済（冪等スキップ）。
            }

            cargo.MarkSettled();

            await using var unitOfWork = unitOfWorkFactory.Begin();
            unitOfWork.Track(cargo);
            await cargoRepository.UpdateAsync(cargo, cancellationToken);
            await unitOfWork.CommitAsync(cancellationToken);
        }
        catch (Exception ex)
        {
            // ADR-0009: 部分適用の一次検知手段として同期失敗を記録する。元コミットには影響させない。
#pragma warning disable CA1848
            logger.LogWarning(ex,
                "入金確認後の予約状態同期に失敗しました。BookingId={BookingId} InvoiceNumber={InvoiceNumber}",
                notification.BookingId, notification.InvoiceNumber);
#pragma warning restore CA1848
        }
    }
}

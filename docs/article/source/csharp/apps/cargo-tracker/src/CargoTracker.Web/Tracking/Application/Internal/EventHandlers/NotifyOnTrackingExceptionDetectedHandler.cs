using CargoTracker.Shared.Application.Persistence;
using CargoTracker.Tracking.Domain.Events;
using CargoTracker.Tracking.Domain.Model;
using CargoTracker.Tracking.Domain.Repositories;
using MediatR;
using Microsoft.Extensions.Logging;

namespace CargoTracker.Tracking.Application.Internal.EventHandlers;

/// <summary>
/// 例外検知（US19/US20）を起点に荷主・管理職への通知を append-only 記録する post-commit ハンドラ。
/// 荷主通知は常に、エスカレーションフラグ（紛失＝Lost 等）が立つ例外では管理職通知も記録する。
/// ADR-0009 の結果整合性方針に従い、失敗は WARN ログに残し元コミットへ影響させない。
/// </summary>
public sealed class NotifyOnTrackingExceptionDetectedHandler(
    IUnitOfWorkFactory unitOfWorkFactory,
    IExceptionNotificationRepository repository,
    ILogger<NotifyOnTrackingExceptionDetectedHandler> logger)
    : INotificationHandler<TrackingExceptionDetectedEvent>
{
    public async Task Handle(TrackingExceptionDetectedEvent notification, CancellationToken cancellationToken)
    {
        try
        {
            await using var unitOfWork = unitOfWorkFactory.Begin();

            await repository.SaveAsync(ExceptionNotification.ForShipper(
                notification.TrackingNumber, notification.BookingId,
                notification.ExceptionType, notification.OccurredAt), cancellationToken);

            if (notification.EscalationFlag)
            {
                await repository.SaveAsync(ExceptionNotification.ForManagement(
                    notification.TrackingNumber, notification.BookingId,
                    notification.ExceptionType, notification.OccurredAt), cancellationToken);
            }

            await unitOfWork.CommitAsync(cancellationToken);
        }
        catch (Exception ex)
        {
            // ADR-0009: 部分適用の一次検知手段として同期失敗を記録する。元コミットには影響させない。
            // CA1848（LoggerMessage デリゲート）は稀なエラー経路のため最適化不要として抑制する。
#pragma warning disable CA1848
            logger.LogWarning(ex,
                "例外通知の記録に失敗しました。TrackingNumber={TrackingNumber} ExceptionType={ExceptionType}",
                notification.TrackingNumber, notification.ExceptionType);
#pragma warning restore CA1848
        }
    }
}

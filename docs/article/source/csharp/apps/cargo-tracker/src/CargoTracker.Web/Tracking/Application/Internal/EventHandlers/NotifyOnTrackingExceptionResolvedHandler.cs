using CargoTracker.Shared.Application.Persistence;
using CargoTracker.Tracking.Domain.Events;
using CargoTracker.Tracking.Domain.Model;
using CargoTracker.Tracking.Domain.Repositories;
using MediatR;
using Microsoft.Extensions.Logging;

namespace CargoTracker.Tracking.Application.Internal.EventHandlers;

/// <summary>
/// 例外解決（US19 AC4/US20 AC5 の対応報告）を起点に荷主への対応報告通知を append-only 記録する post-commit ハンドラ。
/// ADR-0009 の結果整合性方針に従い、失敗は WARN ログに残し元コミットへ影響させない。
/// </summary>
public sealed class NotifyOnTrackingExceptionResolvedHandler(
    IUnitOfWorkFactory unitOfWorkFactory,
    IExceptionNotificationRepository repository,
    ILogger<NotifyOnTrackingExceptionResolvedHandler> logger)
    : INotificationHandler<TrackingExceptionResolvedEvent>
{
    public async Task Handle(TrackingExceptionResolvedEvent notification, CancellationToken cancellationToken)
    {
        try
        {
            await using var unitOfWork = unitOfWorkFactory.Begin();

            await repository.SaveAsync(ExceptionNotification.ForResolution(
                notification.TrackingNumber, notification.BookingId,
                notification.ExceptionType, notification.ResolutionNotes, notification.ResolvedAt), cancellationToken);

            await unitOfWork.CommitAsync(cancellationToken);
        }
        catch (Exception ex)
        {
            // ADR-0009: 部分適用の一次検知手段として同期失敗を記録する。元コミットには影響させない。
#pragma warning disable CA1848
            logger.LogWarning(ex,
                "例外対応報告通知の記録に失敗しました。TrackingNumber={TrackingNumber} ExceptionType={ExceptionType}",
                notification.TrackingNumber, notification.ExceptionType);
#pragma warning restore CA1848
        }
    }
}

using CargoTracker.Shared.Application.Persistence;
using CargoTracker.Tracking.Domain.Model;
using CargoTracker.Tracking.Domain.Repositories;

namespace CargoTracker.Tracking.Application.Internal.CommandServices;

/// <summary>
/// 追跡番号を発行するユースケース（US14）。予約に対して追跡レコードを作成し受領待ちで開始する。
/// 既に発行済みの場合は何もしない（冪等）。
/// </summary>
public sealed class AssignTrackingNumberCommandService(
    IUnitOfWorkFactory unitOfWorkFactory,
    ITrackingActivityRepository repository)
{
    public async Task<TrackingNumber?> HandleAsync(AssignTrackingNumberCommand command, CancellationToken ct = default)
    {
        if (await repository.ExistsForBookingAsync(command.BookingId, ct))
        {
            return null;
        }

        var tracking = TrackingActivity.Issue(command.BookingId);

        await using var unitOfWork = unitOfWorkFactory.Begin();
        unitOfWork.Track(tracking);
        await repository.SaveAsync(tracking, ct);
        await unitOfWork.CommitAsync(ct);
        return tracking.TrackingNumber;
    }
}

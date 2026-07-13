using CargoTracker.Shared.Application.Persistence;
using CargoTracker.Tracking.Domain.Model;
using CargoTracker.Tracking.Domain.Repositories;

namespace CargoTracker.Tracking.Application.Internal.CommandServices;

/// <summary>追跡イベントを手動追記するユースケース（US17）。追跡管理者が荷役記録に現れない状態変化を反映する。</summary>
public sealed class AddTrackingEventCommandService(
    IUnitOfWorkFactory unitOfWorkFactory,
    ITrackingActivityRepository repository)
{
    public async Task HandleAsync(AddTrackingEventCommand command, CancellationToken ct = default)
    {
        if (!Enum.TryParse<TrackingEventType>(command.EventType, ignoreCase: true, out var eventType))
        {
            throw new ArgumentException("追跡イベント種別が不正です。", nameof(command));
        }

        var tracking = await repository.FindByTrackingNumberAsync(command.TrackingNumber, ct)
            ?? throw new InvalidOperationException("指定された追跡番号が見つかりません。");

        tracking.AddEvent(new TrackingActivityEvent(
            eventType,
            new TrackingLocation(command.LocationUnLocode),
            command.EventTime,
            string.IsNullOrWhiteSpace(command.VoyageNumber) ? null : new TrackingVoyageNumber(command.VoyageNumber)));

        await using var unitOfWork = unitOfWorkFactory.Begin();
        unitOfWork.Track(tracking);
        await repository.SaveAsync(tracking, ct);
        await unitOfWork.CommitAsync(ct);
    }
}

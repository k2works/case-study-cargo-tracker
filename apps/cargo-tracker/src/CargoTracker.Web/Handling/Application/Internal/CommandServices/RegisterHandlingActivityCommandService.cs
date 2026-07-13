using CargoTracker.Handling.Application.Internal.OutboundServices;
using CargoTracker.Handling.Domain.Model;
using CargoTracker.Handling.Domain.Repositories;
using CargoTracker.Shared.Application.Persistence;

namespace CargoTracker.Handling.Application.Internal.CommandServices;

/// <summary>
/// 荷役作業を登録するユースケース（US15）。CargoSnapshot で作業場所の妥当性を検証し、
/// 記録を保存して HandlingActivityRegisteredEvent を発行する（Tracking/Booking が同期）。
/// </summary>
public sealed class RegisterHandlingActivityCommandService(
    IUnitOfWorkFactory unitOfWorkFactory,
    IHandlingActivityRepository repository,
    ICargoSnapshotProvider cargoSnapshotProvider)
{
    public async Task<RegisterHandlingActivityResult> HandleAsync(
        RegisterHandlingActivityCommand command, CancellationToken ct = default)
    {
        if (!Enum.TryParse<HandlingEventType>(command.EventType, ignoreCase: true, out var eventType))
        {
            throw new ArgumentException("荷役種別が不正です。", nameof(command));
        }

        var snapshot = await cargoSnapshotProvider.FindByBookingIdAsync(command.BookingId, ct)
            ?? throw new InvalidOperationException("指定された貨物予約が見つかりません。追跡番号・予約番号を確認してください。");

        var activity = HandlingActivity.Register(
            command.BookingId, eventType, command.LocationUnLocode, command.CompletionTime, command.VoyageNumber);

        var isValid = activity.IsValidFor(snapshot);
        var isMisrouted = !isValid && activity.IsMisrouteWhenInvalid();
        var isOffRoute = !isValid;
        activity.ConfirmRegistration(isMisrouted);

        await using var unitOfWork = unitOfWorkFactory.Begin();
        unitOfWork.Track(activity);
        await repository.SaveAsync(activity, ct);
        await unitOfWork.CommitAsync(ct);

        return new RegisterHandlingActivityResult(isMisrouted, isOffRoute);
    }
}

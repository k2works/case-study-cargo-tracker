using CargoTracker.Shared.Application.Persistence;
using CargoTracker.Tracking.Domain.Repositories;

namespace CargoTracker.Tracking.Application.Internal.CommandServices;

/// <summary>
/// 追跡例外を解決するユースケース（US19/US20 の対応報告）。
/// 対応内容（新到着予定日・補償方針等）を記録し、輸送状態を例外発生前へ復帰させる（domain-model BR5）。
/// </summary>
public sealed class ResolveExceptionCommandService(
    IUnitOfWorkFactory unitOfWorkFactory,
    ITrackingActivityRepository repository)
{
    public async Task HandleAsync(ResolveExceptionCommand command, CancellationToken ct = default)
    {
        var tracking = await repository.FindByTrackingNumberAsync(command.TrackingNumber, ct)
            ?? throw new InvalidOperationException("指定された追跡番号が見つかりません。");

        tracking.ResolveException(command.ResolvedAt, command.ResolutionNotes);

        await using var unitOfWork = unitOfWorkFactory.Begin();
        unitOfWork.Track(tracking);
        await repository.SaveAsync(tracking, ct);
        await unitOfWork.CommitAsync(ct);
    }
}

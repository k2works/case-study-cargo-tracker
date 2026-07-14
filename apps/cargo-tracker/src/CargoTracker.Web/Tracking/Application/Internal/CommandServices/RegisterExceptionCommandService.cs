using CargoTracker.Shared.Application.Persistence;
using CargoTracker.Tracking.Domain.Model;
using CargoTracker.Tracking.Domain.Repositories;

namespace CargoTracker.Tracking.Application.Internal.CommandServices;

/// <summary>
/// 追跡例外を登録するユースケース（US19/US20）。遅延・破損・紛失を記録し、貨物状態を例外発生へ更新する。
/// 紛失（Lost）はエスカレーションフラグが自動で立つ（domain-model BR3）。
/// 登録により TrackingExceptionDetectedEvent が post-commit で発行される。
/// </summary>
public sealed class RegisterExceptionCommandService(
    IUnitOfWorkFactory unitOfWorkFactory,
    ITrackingActivityRepository repository)
{
    public async Task HandleAsync(RegisterExceptionCommand command, CancellationToken ct = default)
    {
        if (!Enum.TryParse<ExceptionType>(command.ExceptionType, ignoreCase: true, out var exceptionType))
        {
            throw new ArgumentException("例外種別が不正です。", nameof(command));
        }

        // 通関保留（CustomsHold）は税関システム連携で自動登録するため、本ユースケース（手動登録）では受け付けない。
        if (exceptionType == ExceptionType.CustomsHold)
        {
            throw new ArgumentException("税関保留は税関システム連携で登録されます。手動登録の対象外です。", nameof(command));
        }

        if (string.IsNullOrWhiteSpace(command.LocationUnLocode))
        {
            throw new ArgumentException("発生場所（港コード）は必須です。", nameof(command));
        }

        var tracking = await repository.FindByTrackingNumberAsync(command.TrackingNumber, ct)
            ?? throw new InvalidOperationException("指定された追跡番号が見つかりません。");

        tracking.AddException(new TrackingExceptionEvent(
            exceptionType,
            new TrackingLocation(command.LocationUnLocode),
            command.OccurredAt,
            command.Description));

        await using var unitOfWork = unitOfWorkFactory.Begin();
        unitOfWork.Track(tracking);
        await repository.SaveAsync(tracking, ct);
        await unitOfWork.CommitAsync(ct);
    }
}

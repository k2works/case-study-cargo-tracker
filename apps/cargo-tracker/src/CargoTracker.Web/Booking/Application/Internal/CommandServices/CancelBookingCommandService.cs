using CargoTracker.Booking.Domain.Repositories;
using CargoTracker.Shared.Application.Persistence;

namespace CargoTracker.Booking.Application.Internal.CommandServices;

/// <summary>予約をキャンセルするユースケース（US13）。Cancelled に遷移する。</summary>
public sealed class CancelBookingCommandService(
    IUnitOfWorkFactory unitOfWorkFactory,
    ICargoRepository repository)
{
    public async Task HandleAsync(CancelBookingCommand command, CancellationToken ct = default)
    {
        await using var unitOfWork = unitOfWorkFactory.Begin();

        var cargo = await repository.FindByBookingIdAsync(command.BookingId, ct)
            ?? throw new InvalidOperationException("指定された貨物予約が見つかりません。");

        cargo.Cancel();
        unitOfWork.Track(cargo);
        await repository.UpdateAsync(cargo, ct);
        await unitOfWork.CommitAsync(ct);
    }
}

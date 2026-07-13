using CargoTracker.Booking.Domain.Repositories;
using CargoTracker.Shared.Application.Persistence;

namespace CargoTracker.Booking.Application.Internal.CommandServices;

/// <summary>予約を確定するユースケース（US13）。RouteProposed → Confirmed に遷移する。</summary>
public sealed class ConfirmBookingCommandService(
    IUnitOfWorkFactory unitOfWorkFactory,
    ICargoRepository repository)
{
    public async Task HandleAsync(ConfirmBookingCommand command, CancellationToken ct = default)
    {
        await using var unitOfWork = unitOfWorkFactory.Begin();

        var cargo = await repository.FindByBookingIdAsync(command.BookingId, ct)
            ?? throw new InvalidOperationException("指定された貨物予約が見つかりません。");

        cargo.Confirm();
        unitOfWork.Track(cargo);
        await repository.UpdateAsync(cargo, ct);
        await unitOfWork.CommitAsync(ct);
    }
}

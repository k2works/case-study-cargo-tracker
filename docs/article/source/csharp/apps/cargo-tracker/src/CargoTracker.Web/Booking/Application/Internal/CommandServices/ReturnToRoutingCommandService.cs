using CargoTracker.Booking.Domain.Repositories;
using CargoTracker.Shared.Application.Persistence;

namespace CargoTracker.Booking.Application.Internal.CommandServices;

/// <summary>経路再設計に差し戻すユースケース（US13）。RouteProposed → Preliminary に遷移する。</summary>
public sealed class ReturnToRoutingCommandService(
    IUnitOfWorkFactory unitOfWorkFactory,
    ICargoRepository repository)
{
    public async Task HandleAsync(ReturnToRoutingCommand command, CancellationToken ct = default)
    {
        await using var unitOfWork = unitOfWorkFactory.Begin();

        var cargo = await repository.FindByBookingIdAsync(command.BookingId, ct)
            ?? throw new InvalidOperationException("指定された貨物予約が見つかりません。");

        cargo.ReturnToRouting();
        unitOfWork.Track(cargo);
        await repository.UpdateAsync(cargo, ct);
        await unitOfWork.CommitAsync(ct);
    }
}

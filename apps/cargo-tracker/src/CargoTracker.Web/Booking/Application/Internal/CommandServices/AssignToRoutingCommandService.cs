using CargoTracker.Booking.Domain.Model;
using CargoTracker.Booking.Domain.Repositories;
using CargoTracker.Shared.Application.Persistence;

namespace CargoTracker.Booking.Application.Internal.CommandServices;

/// <summary>貨物予約を経路設計へ引き渡すユースケース（US06）。</summary>
public sealed class AssignToRoutingCommandService(
    IUnitOfWorkFactory unitOfWorkFactory,
    ICargoRepository repository)
{
    public async Task HandleAsync(AssignToRoutingCommand command, CancellationToken ct = default)
    {
        await using var unitOfWork = unitOfWorkFactory.Begin();

        var cargo = await repository.FindByBookingIdAsync(command.BookingId, unitOfWork.Transaction, ct)
            ?? throw new InvalidOperationException("指定された貨物予約が見つかりません。");

        cargo.AssignToRouting();
        unitOfWork.Track(cargo);
        await repository.UpdateAsync(cargo, unitOfWork.Transaction, ct);
        await unitOfWork.CommitAsync(ct);
    }
}

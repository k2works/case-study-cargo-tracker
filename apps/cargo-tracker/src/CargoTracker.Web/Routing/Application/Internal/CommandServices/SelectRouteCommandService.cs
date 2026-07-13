using CargoTracker.Routing.Domain.Model;
using CargoTracker.Routing.Domain.Repositories;
using CargoTracker.Shared.Application.Persistence;

namespace CargoTracker.Routing.Application.Internal.CommandServices;

/// <summary>経路候補を選択・確定するユースケース（US09）。確定経路を予約単位で保存する。</summary>
public sealed class SelectRouteCommandService(
    IUnitOfWorkFactory unitOfWorkFactory,
    ISelectedRouteRepository repository)
{
    public async Task HandleAsync(SelectRouteCommand command, CancellationToken ct = default)
    {
        var selected = SelectedRoute.Confirm(command.BookingId, command.Route);

        await using var unitOfWork = unitOfWorkFactory.Begin();
        unitOfWork.Track(selected);
        await repository.SaveAsync(selected, ct);
        await unitOfWork.CommitAsync(ct);
    }
}

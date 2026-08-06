using CargoTracker.Routing.Domain.Model;
using CargoTracker.Routing.Domain.Repositories;
using CargoTracker.Shared.Application.Persistence;
using CargoTracker.Shared.Domain.Model;

namespace CargoTracker.Routing.Application.Internal.CommandServices;

/// <summary>航海スケジュール登録ユースケース（US24）。</summary>
public sealed class RegisterVoyageCommandService(
    IUnitOfWorkFactory unitOfWorkFactory,
    IVoyageRepository repository)
{
    public async Task<VoyageNumber> HandleAsync(RegisterVoyageCommand command, CancellationToken ct = default)
    {
        var voyageNumber = new VoyageNumber(command.VoyageNumber);
        if (await repository.ExistsAsync(voyageNumber, ct))
        {
            throw new InvalidOperationException("同一航海番号の航海スケジュールは既に登録されています。");
        }

        var voyage = Voyage.Create(
            voyageNumber,
            command.VesselName,
            command.Carrier,
            command.SupportedCargoTypes,
            new Schedule(command.CarrierMovements.Select(movement => new CarrierMovement(
                new Location(movement.DepartureLocationUnLocode),
                new Location(movement.ArrivalLocationUnLocode),
                movement.DepartureDate,
                movement.ArrivalDate,
                movement.SequenceNumber))));

        await using var unitOfWork = unitOfWorkFactory.Begin();
        unitOfWork.Track(voyage);
        await repository.SaveAsync(voyage, ct);
        await unitOfWork.CommitAsync(ct);

        return voyage.VoyageNumber;
    }
}

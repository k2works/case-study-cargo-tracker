using CargoTracker.Routing.Domain.Model;
using CargoTracker.Routing.Domain.Repositories;
using CargoTracker.Shared.Application.Persistence;
using CargoTracker.Shared.Domain.Model;

namespace CargoTracker.Routing.Application.Internal.CommandServices;

/// <summary>航海スケジュール更新ユースケース（US25）。</summary>
public sealed class UpdateScheduleCommandService(
    IUnitOfWorkFactory unitOfWorkFactory,
    IVoyageRepository repository)
{
    public async Task HandleAsync(UpdateScheduleCommand command, CancellationToken ct = default)
    {
        await using var unitOfWork = unitOfWorkFactory.Begin();

        var voyageNumber = new VoyageNumber(command.VoyageNumber);
        var voyage = await repository.FindByVoyageNumberAsync(voyageNumber, ct)
            ?? throw new InvalidOperationException("指定された航海スケジュールが存在しません。");
        if (voyage.Version != command.Version)
        {
            throw new InvalidOperationException("航海スケジュールが並行更新されたため、更新内容を保存できませんでした。");
        }

        voyage.UpdateSchedule(
            command.VesselName,
            command.Carrier,
            command.SupportedCargoTypes,
            new Schedule(command.CarrierMovements.Select(movement => new CarrierMovement(
                new Location(movement.DepartureLocationUnLocode),
                new Location(movement.ArrivalLocationUnLocode),
                movement.DepartureDate,
                movement.ArrivalDate,
                movement.SequenceNumber))));

        unitOfWork.Track(voyage);
        await repository.UpdateAsync(voyage, ct);
        await unitOfWork.CommitAsync(ct);
    }
}

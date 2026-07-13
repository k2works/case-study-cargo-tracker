using CargoTracker.Booking.Domain.Model;
using CargoTracker.Booking.Domain.Repositories;
using CargoTracker.Shared.Application.Persistence;
using CargoTracker.Shared.Domain.Model;

namespace CargoTracker.Booking.Application.Internal.CommandServices;

/// <summary>確定経路を予約に紐付けるユースケース（US11）。状態は RouteProposed のまま旅程を割り当てる。</summary>
public sealed class RouteCargoCommandService(
    IUnitOfWorkFactory unitOfWorkFactory,
    ICargoRepository repository)
{
    public async Task HandleAsync(RouteCargoCommand command, CancellationToken ct = default)
    {
        if (command.Legs is null || command.Legs.Count == 0)
        {
            throw new ArgumentException("旅程は 1 区間以上で構成されます。", nameof(command));
        }

        var itinerary = new CargoItinerary(command.Legs.Select(leg => new Leg(
            new VoyageNumber(leg.VoyageNumber),
            new Location(leg.LoadUnLocode),
            new Location(leg.UnloadUnLocode),
            leg.LoadTime,
            leg.UnloadTime)).ToList());

        await using var unitOfWork = unitOfWorkFactory.Begin();

        var cargo = await repository.FindByBookingIdAsync(new BookingId(command.BookingId), ct)
            ?? throw new InvalidOperationException("指定された貨物予約が見つかりません。");

        cargo.AssignItinerary(itinerary);
        unitOfWork.Track(cargo);
        await repository.UpdateAsync(cargo, ct);
        await unitOfWork.CommitAsync(ct);
    }
}

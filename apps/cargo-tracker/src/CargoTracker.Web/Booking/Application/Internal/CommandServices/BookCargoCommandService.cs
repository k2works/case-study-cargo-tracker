using CargoTracker.Booking.Application.Internal.OutboundServices;
using CargoTracker.Booking.Application.Internal.Services;
using CargoTracker.Booking.Domain.Model;
using CargoTracker.Booking.Domain.Repositories;
using CargoTracker.Shared.Application.Persistence;
using CargoTracker.Shared.Domain.Model;

namespace CargoTracker.Booking.Application.Internal.CommandServices;

/// <summary>貨物予約登録ユースケース（US04）。</summary>
public sealed class BookCargoCommandService(
    IUnitOfWorkFactory unitOfWorkFactory,
    ICargoRepository repository,
    IShipperExistenceChecker shipperExistenceChecker)
{
    public async Task<BookingId> HandleAsync(BookCargoCommand command, CancellationToken ct = default)
    {
        if (!long.TryParse(command.ShipperId, out var shipperSurrogateId))
        {
            throw new ArgumentException("荷主 ID が不正です。", nameof(command));
        }

        var shipperId = ShipperIdCodec.FromSurrogateId(shipperSurrogateId);
        if (!await shipperExistenceChecker.ExistsAsync(shipperId, ct))
        {
            throw new InvalidOperationException("指定された荷主が存在しません。");
        }

        var cargo = Cargo.Create(
            shipperId,
            new RouteSpecification(new Location(command.OriginUnLocode), new Location(command.DestinationUnLocode), command.ArrivalDeadline),
            command.CargoType,
            command.Weight,
            BuildDimensions(command),
            command.Quantity is null ? null : new Quantity(command.Quantity.Value),
            string.IsNullOrWhiteSpace(command.Description) ? null : new Description(command.Description));

        await using var unitOfWork = unitOfWorkFactory.Begin();
        unitOfWork.Track(cargo);
        await repository.SaveAsync(cargo, unitOfWork.Transaction, ct);
        await unitOfWork.CommitAsync(ct);

        return cargo.BookingId;
    }

    private static Dimensions? BuildDimensions(BookCargoCommand command)
    {
        if (command.DimensionLength is null && command.DimensionWidth is null && command.DimensionHeight is null)
        {
            return null;
        }
        if (command.DimensionLength is null || command.DimensionWidth is null || command.DimensionHeight is null)
        {
            throw new ArgumentException("寸法は長さ・幅・高さをすべて入力してください。", nameof(command));
        }
        return new Dimensions(command.DimensionLength.Value, command.DimensionWidth.Value, command.DimensionHeight.Value);
    }
}

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
            string.IsNullOrWhiteSpace(command.Description) ? null : new Description(command.Description),
            BuildHazardousDeclaration(command),
            BuildTemperatureRequirement(command));

        await using var unitOfWork = unitOfWorkFactory.Begin();
        unitOfWork.Track(cargo);
        await repository.SaveAsync(cargo, ct);
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

    private static HazardousDeclaration? BuildHazardousDeclaration(BookCargoCommand command)
    {
        if (string.IsNullOrWhiteSpace(command.HazardousClass)
            && string.IsNullOrWhiteSpace(command.UnNumber)
            && string.IsNullOrWhiteSpace(command.ProperShippingName))
        {
            return null;
        }
        return new HazardousDeclaration(
            command.HazardousClass ?? string.Empty,
            command.UnNumber ?? string.Empty,
            command.ProperShippingName ?? string.Empty);
    }

    private static TemperatureRequirement? BuildTemperatureRequirement(BookCargoCommand command)
    {
        if (command.MinTemperature is null && command.MaxTemperature is null && command.TemperatureUnit is null)
        {
            return null;
        }
        if (command.MinTemperature is null || command.MaxTemperature is null || command.TemperatureUnit is null)
        {
            throw new ArgumentException("温度管理条件は最低温度・最高温度・温度単位をすべて入力してください。", nameof(command));
        }
        return new TemperatureRequirement(command.MinTemperature.Value, command.MaxTemperature.Value, command.TemperatureUnit.Value);
    }
}

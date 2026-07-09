using System.Data;
using CargoTracker.Booking.Application.Internal.Services;
using CargoTracker.Booking.Domain.Model;
using CargoTracker.Booking.Domain.Repositories;
using CargoTracker.Shared.Application.Persistence;
using CargoTracker.Shared.Domain.Model;
using CargoTracker.Shared.Infrastructure.Persistence;
using Dapper;

namespace CargoTracker.Booking.Infrastructure.Repositories;

/// <summary>Dapper による貨物予約リポジトリ。</summary>
public sealed class CargoRepository(IDbConnectionFactory connectionFactory, AmbientTransaction ambient) : ICargoRepository
{
    public async Task SaveAsync(Cargo cargo, CancellationToken ct = default)
    {
        var now = DateTimeOffset.UtcNow;
        var tx = ambient.Require();
        await tx.Connection!.ExecuteAsync(new CommandDefinition(
            """
            INSERT INTO cargo
                (booking_id, shipper_id, cargo_type, weight, origin_unlocode, destination_unlocode,
                 arrival_deadline, booking_status, dimension_length, dimension_width, dimension_height,
                 quantity, description, hazardous_class, un_number, proper_shipping_name,
                 min_temperature, max_temperature, temperature_unit, created_at, updated_at, version)
            VALUES
                (@BookingId, @ShipperId, @CargoType, @Weight, @Origin, @Destination,
                 @ArrivalDeadline, @BookingStatus, @DimensionLength, @DimensionWidth, @DimensionHeight,
                 @Quantity, @Description, @HazardousClass, @UnNumber, @ProperShippingName,
                 @MinTemperature, @MaxTemperature, @TemperatureUnit, @CreatedAt, @UpdatedAt, 0)
            """,
            new
            {
                BookingId = cargo.BookingId.Value,
                ShipperId = ShipperIdCodec.ToSurrogateId(cargo.ShipperId),
                CargoType = cargo.CargoType.ToString().ToUpperInvariant(),
                cargo.Weight,
                Origin = cargo.RouteSpecification.Origin.UnLocode,
                Destination = cargo.RouteSpecification.Destination.UnLocode,
                cargo.RouteSpecification.ArrivalDeadline,
                BookingStatus = cargo.BookingStatus.ToString().ToUpperInvariant(),
                DimensionLength = cargo.Dimensions?.Length,
                DimensionWidth = cargo.Dimensions?.Width,
                DimensionHeight = cargo.Dimensions?.Height,
                Quantity = cargo.Quantity?.Value,
                Description = cargo.Description?.Value,
                HazardousClass = cargo.HazardousDeclaration?.HazardousClass,
                UnNumber = cargo.HazardousDeclaration?.UnNumber,
                ProperShippingName = cargo.HazardousDeclaration?.ProperShippingName,
                MinTemperature = cargo.TemperatureRequirement?.MinTemperature,
                MaxTemperature = cargo.TemperatureRequirement?.MaxTemperature,
                TemperatureUnit = cargo.TemperatureRequirement?.TemperatureUnit.ToString().ToUpperInvariant(),
                CreatedAt = now,
                UpdatedAt = now,
            },
            tx, cancellationToken: ct));
    }

    public async Task UpdateAsync(Cargo cargo, CancellationToken ct = default)
    {
        var now = DateTimeOffset.UtcNow;
        var tx = ambient.Require();
        // Cargo.AssignToRouting() が Version をインクリメント済みのため、WHERE は更新前 version、
        // SET は集約が保持する新 version を使う。影響行数 0 は並行更新競合として扱う。
        var expectedVersion = cargo.Version - 1;
        var affectedRows = await tx.Connection!.ExecuteAsync(new CommandDefinition(
            """
            UPDATE cargo
            SET booking_status = @BookingStatus,
                version = @NewVersion,
                updated_at = @UpdatedAt
            WHERE booking_id = @BookingId
              AND version = @ExpectedVersion
            """,
            new
            {
                BookingId = cargo.BookingId.Value,
                BookingStatus = cargo.BookingStatus.ToString().ToUpperInvariant(),
                NewVersion = cargo.Version,
                ExpectedVersion = expectedVersion,
                UpdatedAt = now,
            },
            tx, cancellationToken: ct));

        if (affectedRows == 0)
        {
            throw new InvalidOperationException("貨物予約が並行更新されたため、経路設計依頼を保存できませんでした。");
        }
    }

    public async Task<Cargo?> FindByBookingIdAsync(BookingId id, CancellationToken ct = default)
    {
        if (ambient.Current is not null)
        {
            var tx = ambient.Current;
            var transactionalRow = await QueryByBookingIdAsync(id, tx.Connection!, tx, ct);
            return transactionalRow?.ToCargo();
        }

        using var connection = connectionFactory.Create();
        var row = await QueryByBookingIdAsync(id, connection, null, ct);

        return row?.ToCargo();
    }

    private static Task<CargoRow?> QueryByBookingIdAsync(
        BookingId id, IDbConnection connection, IDbTransaction? transaction, CancellationToken ct)
        => connection.QuerySingleOrDefaultAsync<CargoRow>(new CommandDefinition(
            """
            SELECT booking_id AS BookingId, shipper_id AS ShipperId, cargo_type AS CargoType,
                   weight AS Weight, origin_unlocode AS OriginUnlocode, destination_unlocode AS DestinationUnlocode,
                   arrival_deadline AS ArrivalDeadline, booking_status AS BookingStatus,
                   dimension_length AS DimensionLength, dimension_width AS DimensionWidth,
                   dimension_height AS DimensionHeight, quantity AS Quantity, description AS Description,
                   hazardous_class AS HazardousClass, un_number AS UnNumber,
                   proper_shipping_name AS ProperShippingName, min_temperature AS MinTemperature,
                   max_temperature AS MaxTemperature, temperature_unit AS TemperatureUnit,
                   version AS Version
            FROM cargo
            WHERE booking_id = @BookingId
            """,
            new { BookingId = id.Value }, transaction, cancellationToken: ct));

    private sealed class CargoRow
    {
        public string BookingId { get; set; } = string.Empty;
        public long ShipperId { get; set; }
        public string CargoType { get; set; } = string.Empty;
        public decimal Weight { get; set; }
        public string OriginUnlocode { get; set; } = string.Empty;
        public string DestinationUnlocode { get; set; } = string.Empty;
        public DateOnly ArrivalDeadline { get; set; }
        public string BookingStatus { get; set; } = string.Empty;
        public decimal? DimensionLength { get; set; }
        public decimal? DimensionWidth { get; set; }
        public decimal? DimensionHeight { get; set; }
        public int? Quantity { get; set; }
        public string? Description { get; set; }
        public string? HazardousClass { get; set; }
        public string? UnNumber { get; set; }
        public string? ProperShippingName { get; set; }
        public decimal? MinTemperature { get; set; }
        public decimal? MaxTemperature { get; set; }
        public string? TemperatureUnit { get; set; }
        public long Version { get; set; }

        public Cargo ToCargo()
        {
            var dimensions = DimensionLength is null || DimensionWidth is null || DimensionHeight is null
                ? null
                : new Dimensions(DimensionLength.Value, DimensionWidth.Value, DimensionHeight.Value);
            var hazardousDeclaration = string.IsNullOrWhiteSpace(HazardousClass)
                ? null
                : new HazardousDeclaration(HazardousClass, UnNumber ?? string.Empty, ProperShippingName ?? string.Empty);
            var temperatureRequirement = MinTemperature is null || MaxTemperature is null || string.IsNullOrWhiteSpace(TemperatureUnit)
                ? null
                : new TemperatureRequirement(
                    MinTemperature.Value,
                    MaxTemperature.Value,
                    Enum.Parse<TemperatureUnit>(TemperatureUnit, ignoreCase: true));

            return Cargo.Reconstruct(
                new BookingId(BookingId),
                ShipperIdCodec.FromSurrogateId(ShipperId),
                new RouteSpecification(new Location(OriginUnlocode), new Location(DestinationUnlocode), ArrivalDeadline),
                Enum.Parse<CargoType>(CargoType, ignoreCase: true),
                Weight,
                dimensions,
                Quantity is null ? null : new Quantity(Quantity.Value),
                Description is null ? null : new Description(Description),
                Enum.Parse<BookingStatus>(BookingStatus, ignoreCase: true),
                Version,
                hazardousDeclaration,
                temperatureRequirement);
        }
    }
}

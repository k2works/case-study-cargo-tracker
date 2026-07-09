using System.Data;
using CargoTracker.Booking.Application.Internal.Services;
using CargoTracker.Booking.Domain.Model;
using CargoTracker.Booking.Domain.Repositories;
using CargoTracker.Shared.Application.Persistence;
using CargoTracker.Shared.Domain.Model;
using Dapper;

namespace CargoTracker.Booking.Infrastructure.Repositories;

/// <summary>Dapper による貨物予約リポジトリ。</summary>
public sealed class CargoRepository(IDbConnectionFactory connectionFactory) : ICargoRepository
{
    public async Task SaveAsync(Cargo cargo, IDbTransaction transaction, CancellationToken ct = default)
    {
        var now = DateTimeOffset.UtcNow;
        await transaction.Connection!.ExecuteAsync(new CommandDefinition(
            """
            INSERT INTO cargo
                (booking_id, shipper_id, cargo_type, weight, origin_unlocode, destination_unlocode,
                 arrival_deadline, booking_status, dimension_length, dimension_width, dimension_height,
                 quantity, description, created_at, updated_at, version)
            VALUES
                (@BookingId, @ShipperId, @CargoType, @Weight, @Origin, @Destination,
                 @ArrivalDeadline, @BookingStatus, @DimensionLength, @DimensionWidth, @DimensionHeight,
                 @Quantity, @Description, @CreatedAt, @UpdatedAt, 0)
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
                CreatedAt = now,
                UpdatedAt = now,
            },
            transaction, cancellationToken: ct));
    }

    public async Task<Cargo?> FindByBookingIdAsync(BookingId id, CancellationToken ct = default)
    {
        using var connection = connectionFactory.Create();
        var row = await connection.QuerySingleOrDefaultAsync<CargoRow>(new CommandDefinition(
            """
            SELECT booking_id AS BookingId, shipper_id AS ShipperId, cargo_type AS CargoType,
                   weight AS Weight, origin_unlocode AS OriginUnlocode, destination_unlocode AS DestinationUnlocode,
                   arrival_deadline AS ArrivalDeadline, booking_status AS BookingStatus,
                   dimension_length AS DimensionLength, dimension_width AS DimensionWidth,
                   dimension_height AS DimensionHeight, quantity AS Quantity, description AS Description,
                   version AS Version
            FROM cargo
            WHERE booking_id = @BookingId
            """,
            new { BookingId = id.Value }, cancellationToken: ct));

        return row?.ToCargo();
    }

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
        public long Version { get; set; }

        public Cargo ToCargo()
        {
            var dimensions = DimensionLength is null || DimensionWidth is null || DimensionHeight is null
                ? null
                : new Dimensions(DimensionLength.Value, DimensionWidth.Value, DimensionHeight.Value);

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
                Version);
        }
    }
}

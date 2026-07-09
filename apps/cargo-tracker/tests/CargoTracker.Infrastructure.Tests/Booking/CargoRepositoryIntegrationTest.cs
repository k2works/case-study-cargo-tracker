using System.Globalization;
using CargoTracker.Booking.Application.Internal.CommandServices;
using CargoTracker.Booking.Application.Internal.OutboundServices;
using CargoTracker.Booking.Domain.Model;
using CargoTracker.Booking.Infrastructure.Repositories;
using CargoTracker.Booking.Infrastructure.Services;
using CargoTracker.Shared.Infrastructure.Persistence;
using Dapper;
using FluentAssertions;
using MediatR;
using Moq;
using Testcontainers.PostgreSql;

namespace CargoTracker.Infrastructure.Tests.Booking;

public sealed class CargoRepositoryIntegrationTest : IAsyncLifetime
{
    private readonly PostgreSqlContainer _postgres = new PostgreSqlBuilder("postgres:16-alpine").Build();
    private readonly Mock<IPublisher> _publisher = new();
    private DbConnectionFactory _connectionFactory = null!;
    private BookCargoCommandService _commandService = null!;
    private CargoRepository _repository = null!;
    private long _shipperId;

    public async Task InitializeAsync()
    {
        await _postgres.StartAsync();
        DatabaseMigrator.Migrate(DatabaseProvider.Postgres, _postgres.GetConnectionString()).Successful.Should().BeTrue();

        _connectionFactory = new DbConnectionFactory(new DatabaseOptions
        {
            Provider = DatabaseProvider.Postgres,
            ConnectionString = _postgres.GetConnectionString(),
        });
        using (var connection = _connectionFactory.Create())
        {
            connection.Open();
            _shipperId = await connection.ExecuteScalarAsync<long>(
                """
                INSERT INTO shipper
                    (shipper_code, shipper_type, name, email, discount_rate, created_at, updated_at, version)
                VALUES
                    ('SHP-IT2', 'INDIVIDUAL', '山田太郎', 'booking@example.com', 0, @Now, @Now, 0);
                SELECT id FROM shipper WHERE shipper_code = 'SHP-IT2'
                """,
                new { Now = DateTimeOffset.UtcNow });
        }

        _repository = new CargoRepository(_connectionFactory);
        var checker = new ShipperExistenceChecker(_connectionFactory);
        _commandService = new BookCargoCommandService(
            new UnitOfWorkFactory(_connectionFactory, _publisher.Object), _repository, checker);
    }

    public Task DisposeAsync() => _postgres.DisposeAsync().AsTask();

    [Fact]
    public async Task 貨物予約を登録するとCargoテーブルに保存されイベントが発行される()
    {
        var bookingId = await _commandService.HandleAsync(new BookCargoCommand(
            _shipperId.ToString(CultureInfo.InvariantCulture), "JPTYO", "DEHAM", new DateOnly(2026, 9, 30),
            CargoType.General, 1200m, 120m, 80m, 90m, 2, "機械部品"));

        bookingId.Value.Should().StartWith("BKG-");

        var cargo = await _repository.FindByBookingIdAsync(bookingId);
        cargo.Should().NotBeNull();
        cargo!.BookingStatus.Should().Be(BookingStatus.Preliminary);
        cargo.RouteSpecification.Origin.UnLocode.Should().Be("JPTYO");
        cargo.RouteSpecification.Destination.UnLocode.Should().Be("DEHAM");
        cargo.Dimensions!.Length.Should().Be(120m);
        cargo.Quantity!.Value.Should().Be(2);
        cargo.Description!.Value.Should().Be("機械部品");
        _publisher.Verify(p => p.Publish(It.IsAny<INotification>(), It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task 存在しない荷主なら予約登録できない()
    {
        var act = () => _commandService.HandleAsync(new BookCargoCommand(
            "999999", "JPTYO", "DEHAM", new DateOnly(2026, 9, 30), CargoType.General, 1200m));

        await act.Should().ThrowAsync<InvalidOperationException>()
            .WithMessage("*荷主*");
    }

    [Fact]
    public async Task 危険物予約を登録すると危険物申告が保存され再構築される()
    {
        var bookingId = await _commandService.HandleAsync(new BookCargoCommand(
            _shipperId.ToString(CultureInfo.InvariantCulture), "JPTYO", "DEHAM", new DateOnly(2026, 9, 30),
            CargoType.Hazardous, 1200m, HazardousClass: "3", UnNumber: "UN1203", ProperShippingName: "Gasoline"));

        var cargo = await _repository.FindByBookingIdAsync(bookingId);

        cargo.Should().NotBeNull();
        cargo!.CargoType.Should().Be(CargoType.Hazardous);
        cargo.HazardousDeclaration.Should().NotBeNull();
        cargo.HazardousDeclaration!.HazardousClass.Should().Be("3");
        cargo.HazardousDeclaration.UnNumber.Should().Be("UN1203");
        cargo.HazardousDeclaration.ProperShippingName.Should().Be("Gasoline");
        cargo.TemperatureRequirement.Should().BeNull();
    }

    [Fact]
    public async Task 冷凍冷蔵予約を登録すると温度管理条件が保存され再構築される()
    {
        var bookingId = await _commandService.HandleAsync(new BookCargoCommand(
            _shipperId.ToString(CultureInfo.InvariantCulture), "JPTYO", "DEHAM", new DateOnly(2026, 9, 30),
            CargoType.Refrigerated, 1200m,
            MinTemperature: -20m, MaxTemperature: -10m, TemperatureUnit: TemperatureUnit.Celsius));

        var cargo = await _repository.FindByBookingIdAsync(bookingId);

        cargo.Should().NotBeNull();
        cargo!.CargoType.Should().Be(CargoType.Refrigerated);
        cargo.TemperatureRequirement.Should().NotBeNull();
        cargo.TemperatureRequirement!.MinTemperature.Should().Be(-20m);
        cargo.TemperatureRequirement.MaxTemperature.Should().Be(-10m);
        cargo.TemperatureRequirement.TemperatureUnit.Should().Be(TemperatureUnit.Celsius);
        cargo.HazardousDeclaration.Should().BeNull();
    }
}

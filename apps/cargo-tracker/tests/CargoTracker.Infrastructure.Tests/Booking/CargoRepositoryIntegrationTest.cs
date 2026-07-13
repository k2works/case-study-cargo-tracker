using System.Globalization;
using CargoTracker.Booking.Application.Internal.CommandServices;
using CargoTracker.Booking.Application.Internal.OutboundServices;
using CargoTracker.Booking.Domain.Model;
using CargoTracker.Booking.Infrastructure.Repositories;
using CargoTracker.Booking.Infrastructure.Services;
using CargoTracker.Shared.Domain.Model;
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
    private AmbientTransaction _ambient = null!;
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

        _ambient = new AmbientTransaction();
        _repository = new CargoRepository(_connectionFactory, _ambient);
        var checker = new ShipperExistenceChecker(_connectionFactory);
        _commandService = new BookCargoCommandService(
            new UnitOfWorkFactory(_connectionFactory, _publisher.Object, _ambient), _repository, checker);
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

    [Fact]
    public async Task 経路設計へ引き渡すと状態が永続化されVersionが上がる()
    {
        var bookingId = await CreateGeneralCargoAsync();
        var cargo = await _repository.FindByBookingIdAsync(bookingId);
        cargo!.AssignToRouting();

        await using (var unitOfWork = new UnitOfWorkFactory(_connectionFactory, _publisher.Object, _ambient).Begin())
        {
            unitOfWork.Track(cargo);
            await _repository.UpdateAsync(cargo);
            await unitOfWork.CommitAsync();
        }

        var updated = await _repository.FindByBookingIdAsync(bookingId);
        updated!.BookingStatus.Should().Be(BookingStatus.RouteProposed);
        updated.Version.Should().Be(1);
        _publisher.Verify(p => p.Publish(It.IsAny<INotification>(), It.IsAny<CancellationToken>()), Times.AtLeastOnce);
    }

    [Fact]
    public async Task Version不一致なら並行更新例外になる()
    {
        var bookingId = await CreateGeneralCargoAsync();
        var first = await _repository.FindByBookingIdAsync(bookingId);
        var second = await _repository.FindByBookingIdAsync(bookingId);
        first!.AssignToRouting();

        await using (var unitOfWork = new UnitOfWorkFactory(_connectionFactory, _publisher.Object, _ambient).Begin())
        {
            await _repository.UpdateAsync(first);
            await unitOfWork.CommitAsync();
        }

        second!.AssignToRouting();
        var act = async () =>
        {
            await using var unitOfWork = new UnitOfWorkFactory(_connectionFactory, _publisher.Object, _ambient).Begin();
            await _repository.UpdateAsync(second);
        };

        await act.Should().ThrowAsync<InvalidOperationException>().WithMessage("*並行更新*");
    }

    [Fact]
    public async Task 経路を紐付けると旅程が永続化され再構築される()
    {
        var bookingId = await CreateGeneralCargoAsync();
        var cargo = await _repository.FindByBookingIdAsync(bookingId);
        cargo!.AssignToRouting();
        await using (var uow = new UnitOfWorkFactory(_connectionFactory, _publisher.Object, _ambient).Begin())
        {
            await _repository.UpdateAsync(cargo);
            await uow.CommitAsync();
        }

        var routeProposed = await _repository.FindByBookingIdAsync(bookingId);
        var itinerary = new CargoItinerary(new[]
        {
            new Leg(new VoyageNumber("V001"), new Location("JPTYO"), new Location("SGSIN"),
                new DateTimeOffset(2026, 9, 1, 0, 0, 0, TimeSpan.Zero), new DateTimeOffset(2026, 9, 10, 0, 0, 0, TimeSpan.Zero)),
            new Leg(new VoyageNumber("V002"), new Location("SGSIN"), new Location("DEHAM"),
                new DateTimeOffset(2026, 9, 12, 0, 0, 0, TimeSpan.Zero), new DateTimeOffset(2026, 9, 25, 0, 0, 0, TimeSpan.Zero)),
        });
        routeProposed!.AssignItinerary(itinerary);
        await using (var uow = new UnitOfWorkFactory(_connectionFactory, _publisher.Object, _ambient).Begin())
        {
            await _repository.UpdateAsync(routeProposed);
            await uow.CommitAsync();
        }

        var reloaded = await _repository.FindByBookingIdAsync(bookingId);
        reloaded!.CargoItinerary.Should().NotBeNull();
        reloaded.CargoItinerary!.Legs.Should().HaveCount(2);
        reloaded.CargoItinerary.Legs[0].Voyage.Value.Should().Be("V001");
        reloaded.CargoItinerary.Legs[0].LoadLocation.UnLocode.Should().Be("JPTYO");
        reloaded.CargoItinerary.Legs[1].UnloadLocation.UnLocode.Should().Be("DEHAM");
        reloaded.BookingStatus.Should().Be(BookingStatus.RouteProposed);
        reloaded.Version.Should().Be(2);
    }

    [Fact]
    public async Task 旅程割当済みの予約を確定するとConfirmedになる()
    {
        var bookingId = await CreateRouteProposedWithItineraryAsync();
        var factory = new UnitOfWorkFactory(_connectionFactory, _publisher.Object, _ambient);

        await new ConfirmBookingCommandService(factory, _repository)
            .HandleAsync(new ConfirmBookingCommand(bookingId));

        var confirmed = await _repository.FindByBookingIdAsync(bookingId);
        confirmed!.BookingStatus.Should().Be(BookingStatus.Confirmed);
    }

    [Fact]
    public async Task 経路提案中の予約を差し戻すとPreliminaryに戻り旅程が消える()
    {
        var bookingId = await CreateRouteProposedWithItineraryAsync();
        var factory = new UnitOfWorkFactory(_connectionFactory, _publisher.Object, _ambient);

        await new ReturnToRoutingCommandService(factory, _repository)
            .HandleAsync(new ReturnToRoutingCommand(bookingId));

        var returned = await _repository.FindByBookingIdAsync(bookingId);
        returned!.BookingStatus.Should().Be(BookingStatus.Preliminary);
        returned.CargoItinerary.Should().BeNull();
    }

    [Fact]
    public async Task 経路提案中の予約をキャンセルするとCancelledになる()
    {
        var bookingId = await CreateRouteProposedWithItineraryAsync();
        var factory = new UnitOfWorkFactory(_connectionFactory, _publisher.Object, _ambient);

        await new CancelBookingCommandService(factory, _repository)
            .HandleAsync(new CancelBookingCommand(bookingId));

        var cancelled = await _repository.FindByBookingIdAsync(bookingId);
        cancelled!.BookingStatus.Should().Be(BookingStatus.Cancelled);
    }

    [Fact]
    public async Task RouteCargoコマンドで確定経路を予約に紐付けられる()
    {
        var bookingId = await CreateGeneralCargoAsync();
        var factory = new UnitOfWorkFactory(_connectionFactory, _publisher.Object, _ambient);

        var cargo = await _repository.FindByBookingIdAsync(bookingId);
        cargo!.AssignToRouting();
        await using (var uow = factory.Begin())
        {
            await _repository.UpdateAsync(cargo);
            await uow.CommitAsync();
        }

        await new RouteCargoCommandService(factory, _repository).HandleAsync(new RouteCargoCommand(
            bookingId.Value,
            new[]
            {
                new RouteLegInput("V001", "JPTYO", "SGSIN",
                    new DateTimeOffset(2026, 9, 1, 0, 0, 0, TimeSpan.Zero), new DateTimeOffset(2026, 9, 10, 0, 0, 0, TimeSpan.Zero)),
                new RouteLegInput("V002", "SGSIN", "DEHAM",
                    new DateTimeOffset(2026, 9, 12, 0, 0, 0, TimeSpan.Zero), new DateTimeOffset(2026, 9, 25, 0, 0, 0, TimeSpan.Zero)),
            }));

        var routed = await _repository.FindByBookingIdAsync(bookingId);
        routed!.CargoItinerary.Should().NotBeNull();
        routed.CargoItinerary!.Legs.Should().HaveCount(2);
        routed.CargoItinerary.Legs[0].UnloadLocation.UnLocode.Should().Be("SGSIN");
        routed.BookingStatus.Should().Be(BookingStatus.RouteProposed);
    }

    [Fact]
    public async Task 確定経路を荷主に通知すると通知記録が保存される()
    {
        var bookingId = await CreateRouteProposedWithItineraryAsync();
        var factory = new UnitOfWorkFactory(_connectionFactory, _publisher.Object, _ambient);
        var notificationRepository = new RouteNotificationRepository(_connectionFactory, _ambient);

        await new NotifyRouteToShipperCommandService(factory, _repository, notificationRepository)
            .HandleAsync(new NotifyRouteToShipperCommand(bookingId));

        var notification = await notificationRepository.FindLatestByBookingIdAsync(bookingId);
        notification.Should().NotBeNull();
        notification!.BookingId.Should().Be(bookingId);
        notification.ExpectedArrivalTime.Should().Be(new DateTimeOffset(2026, 9, 20, 0, 0, 0, TimeSpan.Zero));
    }

    private async Task<BookingId> CreateRouteProposedWithItineraryAsync()
    {
        var bookingId = await CreateGeneralCargoAsync();
        var factory = new UnitOfWorkFactory(_connectionFactory, _publisher.Object, _ambient);

        var cargo = await _repository.FindByBookingIdAsync(bookingId);
        cargo!.AssignToRouting();
        await using (var uow = factory.Begin())
        {
            await _repository.UpdateAsync(cargo);
            await uow.CommitAsync();
        }

        var routeProposed = await _repository.FindByBookingIdAsync(bookingId);
        routeProposed!.AssignItinerary(new CargoItinerary(new[]
        {
            new Leg(new VoyageNumber("V001"), new Location("JPTYO"), new Location("DEHAM"),
                new DateTimeOffset(2026, 9, 1, 0, 0, 0, TimeSpan.Zero), new DateTimeOffset(2026, 9, 20, 0, 0, 0, TimeSpan.Zero)),
        }));
        await using (var uow = factory.Begin())
        {
            await _repository.UpdateAsync(routeProposed);
            await uow.CommitAsync();
        }

        return bookingId;
    }

    private Task<BookingId> CreateGeneralCargoAsync() =>
        _commandService.HandleAsync(new BookCargoCommand(
            _shipperId.ToString(CultureInfo.InvariantCulture), "JPTYO", "DEHAM", new DateOnly(2026, 9, 30),
            CargoType.General, 1200m));
}

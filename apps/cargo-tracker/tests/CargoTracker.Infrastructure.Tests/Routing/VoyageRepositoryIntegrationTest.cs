using CargoTracker.Routing.Application.Internal.CommandServices;
using CargoTracker.Routing.Domain.Model;
using CargoTracker.Routing.Infrastructure.Repositories;
using CargoTracker.Shared.Infrastructure.Persistence;
using FluentAssertions;
using MediatR;
using Moq;
using Testcontainers.PostgreSql;

namespace CargoTracker.Infrastructure.Tests.Routing;

public sealed class VoyageRepositoryIntegrationTest : IAsyncLifetime
{
    private readonly PostgreSqlContainer _postgres = new PostgreSqlBuilder("postgres:16-alpine").Build();
    private readonly Mock<IPublisher> _publisher = new();
    private DbConnectionFactory _connectionFactory = null!;
    private VoyageRepository _repository = null!;
    private RegisterVoyageCommandService _commandService = null!;
    private AmbientTransaction _ambient = null!;

    public async Task InitializeAsync()
    {
        await _postgres.StartAsync();
        DatabaseMigrator.Migrate(DatabaseProvider.Postgres, _postgres.GetConnectionString()).Successful.Should().BeTrue();

        _connectionFactory = new DbConnectionFactory(new DatabaseOptions
        {
            Provider = DatabaseProvider.Postgres,
            ConnectionString = _postgres.GetConnectionString(),
        });
        _ambient = new AmbientTransaction();
        _repository = new VoyageRepository(_connectionFactory, _ambient);
        _commandService = new RegisterVoyageCommandService(
            new UnitOfWorkFactory(_connectionFactory, _publisher.Object, _ambient), _repository);
    }

    public Task DisposeAsync() => _postgres.DisposeAsync().AsTask();

    [Fact]
    public async Task 航海スケジュールを登録するとVoyageとCarrierMovementが保存され再構築される()
    {
        var voyageNumber = await RegisterVoyageAsync("VYG-RT-001");

        var voyage = await _repository.FindByVoyageNumberAsync(voyageNumber);

        voyage.Should().NotBeNull();
        voyage!.VoyageNumber.Should().Be(voyageNumber);
        voyage.VesselName.Should().Be("Kiso Maru");
        voyage.Carrier.Should().Be("Ocean Network");
        voyage.SupportedCargoTypes.Should().Contain(SupportedCargoType.General);
        voyage.SupportedCargoTypes.Should().Contain(SupportedCargoType.Hazardous);
        voyage.Schedule.CarrierMovements.Should().HaveCount(2);
        voyage.Schedule.CarrierMovements[0].DepartureLocation.UnLocode.Should().Be("JPTYO");
        voyage.Schedule.CarrierMovements[1].ArrivalLocation.UnLocode.Should().Be("DEHAM");
        voyage.Version.Should().Be(0);
        _publisher.Verify(p => p.Publish(It.IsAny<INotification>(), It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task 同一航海番号の存在を判定できる()
    {
        var voyageNumber = await RegisterVoyageAsync("VYG-RT-002");

        var exists = await _repository.ExistsAsync(voyageNumber);
        var missing = await _repository.ExistsAsync(new VoyageNumber("VYG-MISSING"));

        exists.Should().BeTrue();
        missing.Should().BeFalse();
    }

    [Fact]
    public async Task 同一航海番号は登録できない()
    {
        await RegisterVoyageAsync("VYG-RT-003");

        var act = () => RegisterVoyageAsync("VYG-RT-003");

        await act.Should().ThrowAsync<InvalidOperationException>().WithMessage("*同一航海番号*");
    }

    private Task<VoyageNumber> RegisterVoyageAsync(string voyageNumber)
        => _commandService.HandleAsync(new RegisterVoyageCommand(
            voyageNumber,
            "Kiso Maru",
            "Ocean Network",
            [SupportedCargoType.General, SupportedCargoType.Hazardous],
            [
                new RegisterCarrierMovementCommand(
                    "JPTYO", "SGSIN",
                    new DateTimeOffset(2026, 9, 1, 10, 0, 0, TimeSpan.Zero),
                    new DateTimeOffset(2026, 9, 5, 10, 0, 0, TimeSpan.Zero),
                    1),
                new RegisterCarrierMovementCommand(
                    "SGSIN", "DEHAM",
                    new DateTimeOffset(2026, 9, 6, 10, 0, 0, TimeSpan.Zero),
                    new DateTimeOffset(2026, 9, 20, 10, 0, 0, TimeSpan.Zero),
                    2),
            ]));
}

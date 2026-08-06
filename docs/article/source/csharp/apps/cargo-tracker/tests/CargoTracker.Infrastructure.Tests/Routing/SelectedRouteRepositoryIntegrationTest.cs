using CargoTracker.Routing.Application.Internal.CommandServices;
using CargoTracker.Routing.Domain.Model;
using CargoTracker.Routing.Infrastructure.Repositories;
using CargoTracker.Shared.Domain.Model;
using CargoTracker.Shared.Infrastructure.Persistence;
using FluentAssertions;
using MediatR;
using Moq;
using Testcontainers.PostgreSql;

namespace CargoTracker.Infrastructure.Tests.Routing;

public sealed class SelectedRouteRepositoryIntegrationTest : IAsyncLifetime
{
    private readonly PostgreSqlContainer _postgres = new PostgreSqlBuilder("postgres:16-alpine").Build();
    private readonly Mock<IPublisher> _publisher = new();
    private DbConnectionFactory _connectionFactory = null!;
    private SelectedRouteRepository _repository = null!;
    private SelectRouteCommandService _commandService = null!;
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
        _repository = new SelectedRouteRepository(_connectionFactory, _ambient);
        _commandService = new SelectRouteCommandService(
            new UnitOfWorkFactory(_connectionFactory, _publisher.Object, _ambient), _repository);
    }

    public Task DisposeAsync() => _postgres.DisposeAsync().AsTask();

    private static CandidateRoute MakeRoute() => new(
        new[]
        {
            new RouteLeg(new VoyageNumber("V001"), new Location("JPTYO"), new Location("SGSIN"),
                new DateTimeOffset(2026, 9, 1, 0, 0, 0, TimeSpan.Zero), new DateTimeOffset(2026, 9, 10, 0, 0, 0, TimeSpan.Zero)),
            new RouteLeg(new VoyageNumber("V002"), new Location("SGSIN"), new Location("DEHAM"),
                new DateTimeOffset(2026, 9, 12, 0, 0, 0, TimeSpan.Zero), new DateTimeOffset(2026, 9, 25, 0, 0, 0, TimeSpan.Zero)),
        },
        24, 1500m);

    [Fact]
    public async Task 経路を選択確定すると保存され再構築される()
    {
        await _commandService.HandleAsync(new SelectRouteCommand("BKG-RT-0001", MakeRoute()));

        var selected = await _repository.FindByBookingIdAsync("BKG-RT-0001");
        selected.Should().NotBeNull();
        selected!.Status.Should().Be(RouteStatus.Confirmed);
        selected.Route.Legs.Should().HaveCount(2);
        selected.Route.TransitDays.Should().Be(24);
        selected.Route.Cost.Should().Be(1500m);
        selected.Route.Legs[0].AlightLocation.UnLocode.Should().Be("SGSIN");
        selected.Route.Legs[1].VoyageNumber.Value.Should().Be("V002");
    }

    [Fact]
    public async Task 同一予約で再選択すると確定経路が置き換わる()
    {
        await _commandService.HandleAsync(new SelectRouteCommand("BKG-RT-0002", MakeRoute()));

        var direct = new CandidateRoute(
            new[]
            {
                new RouteLeg(new VoyageNumber("V999"), new Location("JPTYO"), new Location("DEHAM"),
                    new DateTimeOffset(2026, 9, 2, 0, 0, 0, TimeSpan.Zero), new DateTimeOffset(2026, 9, 18, 0, 0, 0, TimeSpan.Zero)),
            },
            16, 1200m);
        await _commandService.HandleAsync(new SelectRouteCommand("BKG-RT-0002", direct));

        var selected = await _repository.FindByBookingIdAsync("BKG-RT-0002");
        selected!.Route.Legs.Should().ContainSingle();
        selected.Route.Legs[0].VoyageNumber.Value.Should().Be("V999");
        selected.Route.TransitDays.Should().Be(16);
    }
}

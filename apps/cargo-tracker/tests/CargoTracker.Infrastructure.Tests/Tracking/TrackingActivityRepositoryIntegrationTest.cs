using CargoTracker.Shared.Infrastructure.Persistence;
using CargoTracker.Tracking.Application.Internal.CommandServices;
using CargoTracker.Tracking.Domain.Model;
using CargoTracker.Tracking.Infrastructure.Repositories;
using FluentAssertions;
using MediatR;
using Moq;
using Testcontainers.PostgreSql;

namespace CargoTracker.Infrastructure.Tests.Tracking;

public sealed class TrackingActivityRepositoryIntegrationTest : IAsyncLifetime
{
    private readonly PostgreSqlContainer _postgres = new PostgreSqlBuilder("postgres:16-alpine").Build();
    private readonly Mock<IPublisher> _publisher = new();
    private DbConnectionFactory _connectionFactory = null!;
    private TrackingActivityRepository _repository = null!;
    private AssignTrackingNumberCommandService _commandService = null!;
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
        _repository = new TrackingActivityRepository(_connectionFactory, _ambient);
        _commandService = new AssignTrackingNumberCommandService(
            new UnitOfWorkFactory(_connectionFactory, _publisher.Object, _ambient), _repository);
    }

    public Task DisposeAsync() => _postgres.DisposeAsync().AsTask();

    [Fact]
    public async Task 追跡番号を発行すると受領待ちで保存され再構築される()
    {
        var trackingNumber = await _commandService.HandleAsync(new AssignTrackingNumberCommand("BKG-TRK-0001"));

        trackingNumber.Should().NotBeNull();
        trackingNumber!.Value.Should().Be("TRK-TRK-0001");

        var tracking = await _repository.FindByBookingIdAsync("BKG-TRK-0001");
        tracking.Should().NotBeNull();
        tracking!.TrackingNumber.Value.Should().Be("TRK-TRK-0001");
        tracking.CurrentStatus().Should().Be(TrackingStatus.NotReceived);
    }

    [Fact]
    public async Task 既に発行済みの予約には再発行しない()
    {
        await _commandService.HandleAsync(new AssignTrackingNumberCommand("BKG-TRK-0002"));

        var second = await _commandService.HandleAsync(new AssignTrackingNumberCommand("BKG-TRK-0002"));

        second.Should().BeNull();
        (await _repository.ExistsForBookingAsync("BKG-TRK-0002")).Should().BeTrue();
    }
}

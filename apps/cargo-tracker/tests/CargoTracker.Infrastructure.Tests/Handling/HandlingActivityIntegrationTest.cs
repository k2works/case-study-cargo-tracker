using CargoTracker.Handling.Application.Internal.CommandServices;
using CargoTracker.Handling.Infrastructure.Repositories;
using CargoTracker.Handling.Infrastructure.Services;
using CargoTracker.Shared.Infrastructure.Persistence;
using Dapper;
using FluentAssertions;
using MediatR;
using Moq;
using Testcontainers.PostgreSql;

namespace CargoTracker.Infrastructure.Tests.Handling;

public sealed class HandlingActivityIntegrationTest : IAsyncLifetime
{
    private readonly PostgreSqlContainer _postgres = new PostgreSqlBuilder("postgres:16-alpine").Build();
    private readonly Mock<IPublisher> _publisher = new();
    private DbConnectionFactory _connectionFactory = null!;
    private AmbientTransaction _ambient = null!;
    private RegisterHandlingActivityCommandService _service = null!;

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
        _service = new RegisterHandlingActivityCommandService(
            new UnitOfWorkFactory(_connectionFactory, _publisher.Object, _ambient),
            new HandlingActivityRepository(_ambient),
            new CargoSnapshotProvider(_connectionFactory));

        // 荷役の妥当性検証に必要な貨物・旅程を投入する（Booking の cargo/leg を直接 seed）。
        using var connection = _connectionFactory.Create();
        connection.Open();
        var now = DateTime.UtcNow;
        var shipperId = await connection.ExecuteScalarAsync<long>(
            """
            INSERT INTO shipper (shipper_code, shipper_type, name, email, discount_rate, created_at, updated_at, version)
            VALUES ('SHP-HD', 'INDIVIDUAL', '荷役太郎', 'handling@example.com', 0, @Now, @Now, 0);
            SELECT id FROM shipper WHERE shipper_code = 'SHP-HD'
            """, new { Now = now });
        var cargoId = await connection.ExecuteScalarAsync<long>(
            """
            INSERT INTO cargo (booking_id, shipper_id, cargo_type, weight, origin_unlocode, destination_unlocode,
                               arrival_deadline, booking_status, created_at, updated_at, version)
            VALUES ('BKG-HD-0001', @ShipperId, 'GENERAL', 1000, 'JPTYO', 'DEHAM', @Deadline, 'TRACKING_ISSUED', @Now, @Now, 0);
            SELECT id FROM cargo WHERE booking_id = 'BKG-HD-0001'
            """, new { ShipperId = shipperId, Deadline = new DateTime(2026, 10, 31), Now = now });
        await connection.ExecuteAsync(
            """
            INSERT INTO leg (cargo_id, seq_number, voyage_number, load_location_unlocode, unload_location_unlocode, load_time, unload_time, created_at, updated_at)
            VALUES (@CargoId, 1, 'V001', 'JPTYO', 'SGSIN', @T1, @T2, @Now, @Now),
                   (@CargoId, 2, 'V002', 'SGSIN', 'DEHAM', @T3, @T4, @Now, @Now)
            """,
            new { CargoId = cargoId, T1 = new DateTime(2026, 9, 1), T2 = new DateTime(2026, 9, 10), T3 = new DateTime(2026, 9, 12), T4 = new DateTime(2026, 9, 25), Now = now });
    }

    public Task DisposeAsync() => _postgres.DisposeAsync().AsTask();

    [Fact]
    public async Task 出発港での受領を登録すると妥当で記録される()
    {
        var result = await _service.HandleAsync(new RegisterHandlingActivityCommand(
            "BKG-HD-0001", "Receive", "JPTYO", new DateTimeOffset(2026, 9, 1, 0, 0, 0, TimeSpan.Zero)));

        result.IsMisrouted.Should().BeFalse();
        result.IsOffRoute.Should().BeFalse();

        using var connection = _connectionFactory.Create();
        var count = await connection.ExecuteScalarAsync<int>(
            "SELECT COUNT(*) FROM handling_activity WHERE booking_id = 'BKG-HD-0001'");
        count.Should().Be(1);
    }

    [Fact]
    public async Task 旅程の積込港での積込は妥当()
    {
        var result = await _service.HandleAsync(new RegisterHandlingActivityCommand(
            "BKG-HD-0001", "Load", "JPTYO", new DateTimeOffset(2026, 9, 1, 0, 0, 0, TimeSpan.Zero), "V001"));

        result.IsOffRoute.Should().BeFalse();
    }

    [Fact]
    public async Task 旅程外の港での積込はMISROUTED警告になるが記録される()
    {
        var result = await _service.HandleAsync(new RegisterHandlingActivityCommand(
            "BKG-HD-0001", "Load", "USNYC", new DateTimeOffset(2026, 9, 1, 0, 0, 0, TimeSpan.Zero), "V001"));

        result.IsMisrouted.Should().BeTrue();
        result.IsOffRoute.Should().BeTrue();
    }

    [Fact]
    public async Task 存在しない予約への荷役は拒否される()
    {
        var act = () => _service.HandleAsync(new RegisterHandlingActivityCommand(
            "BKG-NOT-EXIST", "Receive", "JPTYO", DateTimeOffset.UtcNow));

        await act.Should().ThrowAsync<InvalidOperationException>();
    }

    [Fact]
    public async Task 引取は荷受人確認がないと登録できない()
    {
        var act = () => _service.HandleAsync(new RegisterHandlingActivityCommand(
            "BKG-HD-0001", "Claim", "DEHAM", new DateTimeOffset(2026, 9, 25, 0, 0, 0, TimeSpan.Zero)));

        await act.Should().ThrowAsync<ArgumentException>().WithMessage("*荷受人*");
    }

    [Fact]
    public async Task 目的港での引取は荷受人確認があれば妥当に登録される()
    {
        var result = await _service.HandleAsync(new RegisterHandlingActivityCommand(
            "BKG-HD-0001", "Claim", "DEHAM", new DateTimeOffset(2026, 9, 25, 0, 0, 0, TimeSpan.Zero),
            ConsigneeConfirmation: "SIGN-12345"));

        result.IsOffRoute.Should().BeFalse();
    }
}

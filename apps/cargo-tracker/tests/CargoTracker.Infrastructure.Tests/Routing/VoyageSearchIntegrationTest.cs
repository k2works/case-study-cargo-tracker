using CargoTracker.Routing.Application.Internal.QueryServices;
using CargoTracker.Routing.Domain.Model;
using CargoTracker.Routing.Infrastructure.Services;
using CargoTracker.Shared.Infrastructure.Persistence;
using Dapper;
using FluentAssertions;
using Testcontainers.PostgreSql;

namespace CargoTracker.Infrastructure.Tests.Routing;

public sealed class VoyageSearchIntegrationTest : IAsyncLifetime
{
    private readonly PostgreSqlContainer _postgres = new PostgreSqlBuilder("postgres:16-alpine").Build();
    private DbConnectionFactory _connectionFactory = null!;

    public async Task InitializeAsync()
    {
        await _postgres.StartAsync();
        DatabaseMigrator.Migrate(DatabaseProvider.Postgres, _postgres.GetConnectionString()).Successful.Should().BeTrue();

        _connectionFactory = new DbConnectionFactory(new DatabaseOptions
        {
            Provider = DatabaseProvider.Postgres,
            ConnectionString = _postgres.GetConnectionString(),
        });
        await SeedAsync();
    }

    public Task DisposeAsync() => _postgres.DisposeAsync().AsTask();

    [Fact]
    public async Task BookingLookupはcargoテーブルから予約情報を取得する()
    {
        var lookup = new BookingLookup(_connectionFactory);

        var booking = await lookup.FindByBookingIdAsync("BKG-US07-001");

        booking.Should().NotBeNull();
        booking!.OriginUnlocode.Should().Be("JPTYO");
        booking.DestinationUnlocode.Should().Be("DEHAM");
        booking.ArrivalDeadline.Should().Be(new DateOnly(2026, 10, 31));
        booking.CargoType.Should().Be("HAZARDOUS");
        booking.Weight.Should().Be(1200m);
    }

    [Fact]
    public async Task 経路設計依頼一覧はRouteProposedの予約だけを返す()
    {
        var queryService = new RoutingRequestQueryService(_connectionFactory);

        var requests = await queryService.FindRouteProposedAsync();

        requests.Should().ContainSingle(request => request.BookingId == "BKG-US07-001");
        requests.Should().NotContain(request => request.BookingId == "BKG-US07-002");
    }

    [Fact]
    public async Task 航海検索は港連鎖と出発期間と貨物種別で利用可能な航海を絞り込む()
    {
        var queryService = new SearchVoyagesQueryService(_connectionFactory);

        var results = await queryService.SearchAsync(new SearchVoyagesQuery(
            "JPTYO",
            "DEHAM",
            new DateTimeOffset(2026, 10, 1, 0, 0, 0, TimeSpan.Zero),
            new DateTimeOffset(2026, 10, 31, 23, 59, 0, TimeSpan.Zero),
            SupportedCargoType.Hazardous));

        results.Should().ContainSingle();
        results[0].VoyageNumber.Should().Be("VYG-US07-HZ");
        results[0].Carrier.Should().Be("Hazard Carrier");
        results[0].PortChain.Should().Be("JPTYO → SGSIN → DEHAM");
    }

    [Fact]
    public async Task 条件を満たす航海がない場合は空を返す()
    {
        var queryService = new SearchVoyagesQueryService(_connectionFactory);

        var results = await queryService.SearchAsync(new SearchVoyagesQuery(
            "JPTYO",
            "USNYC",
            new DateTimeOffset(2026, 10, 1, 0, 0, 0, TimeSpan.Zero),
            new DateTimeOffset(2026, 10, 31, 23, 59, 0, TimeSpan.Zero),
            SupportedCargoType.General));

        results.Should().BeEmpty();
    }

    private async Task SeedAsync()
    {
        using var connection = _connectionFactory.Create();
        connection.Open();
        var now = DateTimeOffset.UtcNow;
        var shipperId = await connection.ExecuteScalarAsync<long>(
            """
            INSERT INTO shipper
                (shipper_code, shipper_type, name, email, discount_rate, created_at, updated_at, version)
            VALUES
                ('SHP-US07', 'INDIVIDUAL', '検索太郎', 'routing-search@example.com', 0, @Now, @Now, 0);
            SELECT id FROM shipper WHERE shipper_code = 'SHP-US07'
            """,
            new { Now = now });

        await connection.ExecuteAsync(
            """
            INSERT INTO cargo
                (booking_id, shipper_id, cargo_type, weight, origin_unlocode, destination_unlocode,
                 arrival_deadline, booking_status, created_at, updated_at, version)
            VALUES
                ('BKG-US07-001', @ShipperId, 'HAZARDOUS', 1200, 'JPTYO', 'DEHAM',
                 '2026-10-31', 'ROUTEPROPOSED', @Now, @Now, 0),
                ('BKG-US07-002', @ShipperId, 'GENERAL', 800, 'JPTYO', 'DEHAM',
                 '2026-10-31', 'PRELIMINARY', @Now, @Now, 0)
            """,
            new { ShipperId = shipperId, Now = now });

        await InsertVoyageAsync("VYG-US07-GN", "General Carrier", "GENERAL", "JPTYO", "SGSIN", "DEHAM");
        await InsertVoyageAsync("VYG-US07-HZ", "Hazard Carrier", "GENERAL,HAZARDOUS", "JPTYO", "SGSIN", "DEHAM");
        await InsertVoyageAsync("VYG-US07-RF", "Reefer Carrier", "REFRIGERATED", "JPTYO", "CNSHA", "DEHAM");
    }

    private async Task InsertVoyageAsync(
        string voyageNumber,
        string carrier,
        string supportedCargoTypes,
        string origin,
        string transit,
        string destination)
    {
        using var connection = _connectionFactory.Create();
        connection.Open();
        var now = DateTime.SpecifyKind(DateTime.UtcNow, DateTimeKind.Unspecified);
        await connection.ExecuteAsync(
            """
            INSERT INTO voyage
                (voyage_number, vessel_name, carrier, supported_cargo_types, created_at, updated_at, version)
            VALUES
                (@VoyageNumber, @VesselName, @Carrier, @SupportedCargoTypes, @Now, @Now, 0)
            """,
            new
            {
                VoyageNumber = voyageNumber,
                VesselName = $"{voyageNumber} Vessel",
                Carrier = carrier,
                SupportedCargoTypes = supportedCargoTypes,
                Now = now,
            });
        var voyageId = await connection.ExecuteScalarAsync<long>(
            "SELECT id FROM voyage WHERE voyage_number = @VoyageNumber",
            new { VoyageNumber = voyageNumber });

        await connection.ExecuteAsync(
            """
            INSERT INTO carrier_movement
                (voyage_id, departure_location_unlocode, arrival_location_unlocode,
                 departure_date, arrival_date, seq_number, created_at, updated_at)
            VALUES
                (@VoyageId, @Origin, @Transit, '2026-10-01 10:00:00', '2026-10-04 10:00:00', 1, @Now, @Now),
                (@VoyageId, @Transit, @Destination, '2026-10-05 10:00:00', '2026-10-18 10:00:00', 2, @Now, @Now)
            """,
            new
            {
                VoyageId = voyageId,
                Origin = origin,
                Transit = transit,
                Destination = destination,
                Now = now,
            });
    }
}

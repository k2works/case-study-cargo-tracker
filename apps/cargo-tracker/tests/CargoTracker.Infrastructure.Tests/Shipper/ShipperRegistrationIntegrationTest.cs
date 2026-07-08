using CargoTracker.Shared.Infrastructure.Persistence;
using CargoTracker.Shipper.Application.Internal.CommandServices;
using CargoTracker.Shipper.Application.Internal.QueryServices;
using CargoTracker.Shipper.Domain;
using CargoTracker.Shipper.Infrastructure.Repositories;
using FluentAssertions;
using MediatR;
using Moq;
using Testcontainers.PostgreSql;

namespace CargoTracker.Infrastructure.Tests.Shipper;

/// <summary>
/// 荷主登録の統合テスト（実 PostgreSQL・ADR-0003）。コマンドサービス → リポジトリ →
/// UoW → post-commit イベントの縦断フローを検証する。
/// </summary>
public sealed class ShipperRegistrationIntegrationTest : IAsyncLifetime
{
    private readonly PostgreSqlContainer _postgres = new PostgreSqlBuilder("postgres:16-alpine").Build();
    private readonly Mock<IPublisher> _publisher = new();
    private DbConnectionFactory _connectionFactory = null!;
    private RegisterShipperCommandService _commandService = null!;
    private FindShipperQueryService _queryService = null!;

    public async Task InitializeAsync()
    {
        await _postgres.StartAsync();
        DatabaseMigrator.Migrate(DatabaseProvider.Postgres, _postgres.GetConnectionString()).Successful.Should().BeTrue();

        _connectionFactory = new DbConnectionFactory(new DatabaseOptions
        {
            Provider = DatabaseProvider.Postgres,
            ConnectionString = _postgres.GetConnectionString(),
        });
        var repository = new ShipperRepository();
        var unitOfWorkFactory = new UnitOfWorkFactory(_connectionFactory, _publisher.Object);
        _commandService = new RegisterShipperCommandService(unitOfWorkFactory, repository);
        _queryService = new FindShipperQueryService(_connectionFactory);
    }

    public Task DisposeAsync() => _postgres.DisposeAsync().AsTask();

    [Fact]
    public async Task 個人荷主を登録すると一覧に表示されイベントが発行される()
    {
        // When: 個人荷主を登録する
        await _commandService.HandleAsync(new RegisterShipperCommand(
            IsCorporate: false, Name: "山田太郎", Email: "yamada@example.com", Phone: "03-1234-5678",
            Address: "東京都港区1-2-3", ContractNumber: null, DiscountRate: null));

        // Then: 一覧に表示される
        var list = await _queryService.FindAllAsync();
        list.Should().ContainSingle();
        list[0].Name.Should().Be("山田太郎");
        list[0].ShipperType.Should().Be("INDIVIDUAL");
        list[0].DiscountRate.Should().Be(0m);
        list[0].ShipperCode.Should().StartWith("SHP-");
        list[0].Address.Should().Be("東京都港区1-2-3");

        // コミット後にドメインイベントが発行される（MediatR は実行時型 ShipperRegisteredEvent で解決）
        _publisher.Verify(p => p.Publish(It.IsAny<INotification>(), It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task 法人荷主を登録すると契約番号と割引率が保持される()
    {
        await _commandService.HandleAsync(new RegisterShipperCommand(
            IsCorporate: true, Name: "株式会社サンプル", Email: "corp@example.com", Phone: null,
            Address: null, ContractNumber: "C-0001", DiscountRate: 0.15m));

        var list = await _queryService.FindAllAsync();
        list.Should().ContainSingle();
        list[0].ShipperType.Should().Be("CORPORATE");
        list[0].DiscountRate.Should().Be(0.15m);
    }

    [Fact]
    public async Task 重複メールアドレスの登録は例外になる()
    {
        await _commandService.HandleAsync(new RegisterShipperCommand(
            false, "山田太郎", "dup@example.com", null, null, null, null));

        var act = () => _commandService.HandleAsync(new RegisterShipperCommand(
            false, "田中花子", "dup@example.com", null, null, null, null));

        await act.Should().ThrowAsync<EmailAlreadyRegisteredException>();
    }
}

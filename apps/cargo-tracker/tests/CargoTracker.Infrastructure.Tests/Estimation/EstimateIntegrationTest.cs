using CargoTracker.Estimation.Application.Internal.CommandServices;
using CargoTracker.Estimation.Application.Internal.QueryServices;
using CargoTracker.Estimation.Domain.Model;
using CargoTracker.Estimation.Infrastructure.Repositories;
using CargoTracker.Estimation.Infrastructure.Services;
using CargoTracker.Shared.Infrastructure.Persistence;
using FluentAssertions;
using MediatR;
using Moq;
using Testcontainers.PostgreSql;

namespace CargoTracker.Infrastructure.Tests.Estimation;

/// <summary>見積作成の統合テスト（実 PostgreSQL）。見積とルート候補の集約永続化を検証する。</summary>
public sealed class EstimateIntegrationTest : IAsyncLifetime
{
    private readonly PostgreSqlContainer _postgres = new PostgreSqlBuilder("postgres:16-alpine").Build();
    private CreateEstimateCommandService _commandService = null!;
    private FindEstimateQueryService _queryService = null!;

    public async Task InitializeAsync()
    {
        await _postgres.StartAsync();
        DatabaseMigrator.Migrate(DatabaseProvider.Postgres, _postgres.GetConnectionString()).Successful.Should().BeTrue();

        var factory = new DbConnectionFactory(new DatabaseOptions
        {
            Provider = DatabaseProvider.Postgres,
            ConnectionString = _postgres.GetConnectionString(),
        });
        _commandService = new CreateEstimateCommandService(
            factory, new EstimateRepository(), new StubExternalRoutingService(), new Mock<IPublisher>().Object);
        _queryService = new FindEstimateQueryService(factory);
    }

    public Task DisposeAsync() => _postgres.DisposeAsync().AsTask();

    [Fact]
    public async Task 見積を作成すると見積番号が発行されルート候補が保存される()
    {
        // When: 見積を作成する
        var estimateId = await _commandService.HandleAsync(new CreateEstimateCommand(
            "JPTYO", "DEHAM", new DateOnly(2026, 9, 30), CargoType.General, 1200m));

        // Then: 見積番号が発行される
        estimateId.Value.Should().NotBe(Guid.Empty);

        // 詳細にルート候補（スタブ 3 件）が保存される
        var detail = await _queryService.FindByEstimateIdAsync(estimateId.Value);
        detail.Should().NotBeNull();
        detail!.Status.Should().Be("CREATED");
        detail.WeightKg.Should().Be(1200m);
        detail.Candidates.Should().HaveCount(3);
        detail.Candidates[0].VoyageNumber.Should().Be("V-STUB-01");
        detail.Candidates[0].EstimatedCost.Should().BeGreaterThan(0);

        // 一覧に表示される
        var list = await _queryService.FindAllAsync();
        list.Should().ContainSingle(e => e.EstimateId == estimateId.Value);
        list[0].CandidateCount.Should().Be(3);
    }

    [Fact]
    public async Task 危険物は割増係数でコストが高くなる()
    {
        var generalId = await _commandService.HandleAsync(new CreateEstimateCommand(
            "JPTYO", "DEHAM", new DateOnly(2026, 9, 30), CargoType.General, 1000m));
        var hazardousId = await _commandService.HandleAsync(new CreateEstimateCommand(
            "JPTYO", "DEHAM", new DateOnly(2026, 9, 30), CargoType.Hazardous, 1000m));

        var general = await _queryService.FindByEstimateIdAsync(generalId.Value);
        var hazardous = await _queryService.FindByEstimateIdAsync(hazardousId.Value);

        hazardous!.Candidates[0].EstimatedCost.Should().BeGreaterThan(general!.Candidates[0].EstimatedCost);
    }
}

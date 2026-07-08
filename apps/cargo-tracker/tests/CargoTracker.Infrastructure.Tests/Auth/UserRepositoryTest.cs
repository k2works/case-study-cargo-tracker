using CargoTracker.Shared.Infrastructure.Auth;
using CargoTracker.Shared.Infrastructure.Persistence;
using FluentAssertions;
using Testcontainers.PostgreSql;

namespace CargoTracker.Infrastructure.Tests.Auth;

/// <summary>
/// UserRepository の統合テスト。データアクセスの検証は実 PostgreSQL（Testcontainers）を正とする（ADR-0003・test_strategy）。
/// </summary>
public sealed class UserRepositoryTest : IAsyncLifetime
{
    private readonly PostgreSqlContainer _postgres = new PostgreSqlBuilder("postgres:16-alpine")
        .Build();

    private UserRepository _repository = null!;
    private readonly IdentityPasswordHasher _hasher = new();

    public async Task InitializeAsync()
    {
        await _postgres.StartAsync();
        var result = DatabaseMigrator.Migrate(DatabaseProvider.Postgres, _postgres.GetConnectionString());
        result.Successful.Should().BeTrue();

        var factory = new DbConnectionFactory(new DatabaseOptions
        {
            Provider = DatabaseProvider.Postgres,
            ConnectionString = _postgres.GetConnectionString(),
        });
        _repository = new UserRepository(factory);
    }

    public Task DisposeAsync() => _postgres.DisposeAsync().AsTask();

    [Fact]
    public async Task ユーザーを登録してユーザー名で検索できる()
    {
        // Given: ユーザーを登録する
        await _repository.AddIfNotExistsAsync("yamada", _hasher.Hash("SecurePass123!"), Roles.Sales);

        // When: ユーザー名で検索する
        var found = await _repository.FindByUsernameAsync("yamada");

        // Then: 登録した内容が取得できる
        found.Should().NotBeNull();
        found!.Username.Should().Be("yamada");
        found.Role.Should().Be(Roles.Sales);
        found.Id.Should().BeGreaterThan(0);
    }

    [Fact]
    public async Task 存在しないユーザー名で検索するとnullを返す()
    {
        // When & Then
        var found = await _repository.FindByUsernameAsync("nobody");
        found.Should().BeNull();
    }

    [Fact]
    public async Task 同一ユーザー名の二重登録はべき等で重複しない()
    {
        // Given: 同じユーザー名で 2 回登録する
        await _repository.AddIfNotExistsAsync("tanaka", _hasher.Hash("pass1"), Roles.Admin);
        await _repository.AddIfNotExistsAsync("tanaka", _hasher.Hash("pass2"), Roles.Sales);

        // Then: 最初の登録が保持される（UK 違反にならない）
        var found = await _repository.FindByUsernameAsync("tanaka");
        found.Should().NotBeNull();
        found!.Role.Should().Be(Roles.Admin);
    }
}

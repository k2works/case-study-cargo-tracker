using CargoTracker.Shared.Infrastructure.Persistence;
using Dapper;

namespace CargoTracker.Shared.Infrastructure.Auth;

/// <summary>
/// Dapper による認証ユーザーの永続化（US26）。SQL は ADR-0003 の方言禁止規約に従い、
/// タイムスタンプは C# 側で生成し（NOW() 不使用）、採番は自動生成に任せる（RETURNING 不使用）。
/// </summary>
public sealed class UserRepository(IDbConnectionFactory connectionFactory) : IUserRepository
{
    public async Task<AppUser?> FindByUsernameAsync(string username, CancellationToken ct = default)
    {
        using var connection = connectionFactory.Create();
        return await connection.QuerySingleOrDefaultAsync<AppUser>(new CommandDefinition(
            "SELECT id AS Id, username AS Username, password_hash AS PasswordHash, role AS Role " +
            "FROM users WHERE username = @Username",
            new { Username = username },
            cancellationToken: ct));
    }

    public async Task AddIfNotExistsAsync(string username, string passwordHash, string role, CancellationToken ct = default)
    {
        using var connection = connectionFactory.Create();

        var exists = await connection.ExecuteScalarAsync<long>(new CommandDefinition(
            "SELECT COUNT(1) FROM users WHERE username = @Username",
            new { Username = username },
            cancellationToken: ct));
        if (exists > 0)
        {
            return;
        }

        var now = DateTimeOffset.UtcNow;
        await connection.ExecuteAsync(new CommandDefinition(
            "INSERT INTO users (username, password_hash, role, created_at, updated_at, version) " +
            "VALUES (@Username, @PasswordHash, @Role, @CreatedAt, @UpdatedAt, 0)",
            new { Username = username, PasswordHash = passwordHash, Role = role, CreatedAt = now, UpdatedAt = now },
            cancellationToken: ct));
    }
}

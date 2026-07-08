namespace CargoTracker.Shared.Infrastructure.Auth;

/// <summary>認証ユーザーの永続化ポート（US26）。</summary>
public interface IUserRepository
{
    Task<AppUser?> FindByUsernameAsync(string username, CancellationToken ct = default);

    /// <summary>ユーザーを登録する（シード投入で使用）。既に存在する場合は何もしない。</summary>
    Task AddIfNotExistsAsync(string username, string passwordHash, string role, CancellationToken ct = default);
}

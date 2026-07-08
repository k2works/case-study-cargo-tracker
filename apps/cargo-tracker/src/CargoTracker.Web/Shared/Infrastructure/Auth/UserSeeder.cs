namespace CargoTracker.Shared.Infrastructure.Auth;

/// <summary>
/// 開発・デモ用のシードユーザーを投入する（US26 タスク 2.4）。
/// 各ロールを代表するユーザーを 1 件ずつ登録する。本番環境では実行しない。
/// </summary>
public static class UserSeeder
{
    // 開発用の既定パスワード。全シードユーザー共通（デモ用途）。
    public const string DefaultPassword = "Password1!";

    private static readonly (string Username, string Role)[] _seedUsers =
    [
        ("admin", Roles.Admin),
        ("sales", Roles.Sales),
        ("router", Roles.RouteDesigner),
        ("tracker", Roles.Tracker),
        ("handler", Roles.Handler),
        ("billing", Roles.Billing),
    ];

    public static async Task SeedAsync(IUserRepository repository, IPasswordHasher passwordHasher, CancellationToken ct = default)
    {
        var passwordHash = passwordHasher.Hash(DefaultPassword);
        foreach (var (username, role) in _seedUsers)
        {
            await repository.AddIfNotExistsAsync(username, passwordHash, role, ct);
        }
    }
}

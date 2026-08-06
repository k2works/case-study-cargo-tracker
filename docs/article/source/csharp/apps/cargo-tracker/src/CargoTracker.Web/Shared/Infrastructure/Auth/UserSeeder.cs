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

    /// <summary>ログイン画面（開発環境）で提示するデモアカウント一覧。ロールと利用可能機能を添える。</summary>
    public static readonly IReadOnlyList<DemoAccount> DemoAccounts =
    [
        new("sales", "営業担当者", "荷主管理・見積・貨物予約"),
        new("router", "経路設計者", "航路管理・経路設計（経路候補算出）"),
        new("tracker", "追跡管理者", "貨物追跡・荷役管理"),
        new("handler", "荷役作業員", "荷役管理"),
        new("billing", "経理担当者", "請求管理"),
        new("admin", "管理者", "管理設定"),
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

/// <summary>ログイン画面に提示するデモアカウント情報（開発環境のみ）。</summary>
public sealed record DemoAccount(string Username, string RoleLabel, string Functions);

namespace CargoTracker.Shared.Infrastructure.Auth;

/// <summary>
/// 認証ユーザー（US26）。認証は横断的関心事のためドメイン集約ではなくインフラの型として扱う。
/// <paramref name="PasswordHash"/> はハッシュ化済みの値（平文は保持しない）。
/// </summary>
public sealed record AppUser(long Id, string Username, string PasswordHash, string Role);

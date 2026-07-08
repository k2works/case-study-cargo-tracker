using Microsoft.AspNetCore.Identity;

namespace CargoTracker.Shared.Infrastructure.Auth;

/// <summary>パスワードのハッシュ化・検証を担うポート（US26 受入条件 7）。</summary>
public interface IPasswordHasher
{
    string Hash(string password);

    bool Verify(string passwordHash, string providedPassword);
}

/// <summary>
/// ASP.NET Core Identity の <see cref="PasswordHasher{TUser}"/>（PBKDF2・BCrypt 相当の適応的ハッシュ）を用いた実装。
/// full Identity（EF）は導入せず、ハッシュ機能のみを利用する。
/// </summary>
public sealed class IdentityPasswordHasher : IPasswordHasher
{
    private readonly PasswordHasher<object> _inner = new();
    private static readonly object _user = new();

    public string Hash(string password) => _inner.HashPassword(_user, password);

    public bool Verify(string passwordHash, string providedPassword)
        => _inner.VerifyHashedPassword(_user, passwordHash, providedPassword) != PasswordVerificationResult.Failed;
}

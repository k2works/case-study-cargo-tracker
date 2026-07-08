namespace CargoTracker.Shared.Infrastructure.Auth;

/// <summary>
/// ユーザー名とパスワードによる資格情報の検証（US26 受入条件 1・2）。
/// 認証成功時はユーザーを返し、失敗時は null を返す（成否の理由は呼び出し側で一律メッセージ化）。
/// </summary>
public sealed class UserAuthenticator(IUserRepository repository, IPasswordHasher passwordHasher)
{
    public async Task<AppUser?> AuthenticateAsync(string username, string password, CancellationToken ct = default)
    {
        var user = await repository.FindByUsernameAsync(username, ct);
        if (user is null)
        {
            return null;
        }

        return passwordHasher.Verify(user.PasswordHash, password) ? user : null;
    }
}

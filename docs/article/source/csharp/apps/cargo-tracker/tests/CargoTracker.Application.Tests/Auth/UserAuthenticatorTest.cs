using CargoTracker.Shared.Infrastructure.Auth;
using FluentAssertions;
using Moq;

namespace CargoTracker.Application.Tests.Auth;

public class UserAuthenticatorTest
{
    private readonly IdentityPasswordHasher _hasher = new();

    private UserAuthenticator CreateAuthenticator(AppUser? storedUser)
    {
        var repository = new Mock<IUserRepository>();
        repository
            .Setup(r => r.FindByUsernameAsync(It.IsAny<string>(), It.IsAny<CancellationToken>()))
            .ReturnsAsync(storedUser);
        return new UserAuthenticator(repository.Object, _hasher);
    }

    [Fact]
    public async Task 正しい資格情報で認証に成功しユーザーを返す()
    {
        // Given: パスワードをハッシュ化して保存したユーザー
        var user = new AppUser(1, "yamada", _hasher.Hash("SecurePass123!"), Roles.Sales);
        var authenticator = CreateAuthenticator(user);

        // When: 正しいパスワードで認証する
        var result = await authenticator.AuthenticateAsync("yamada", "SecurePass123!");

        // Then: ユーザーが返る
        result.Should().NotBeNull();
        result!.Username.Should().Be("yamada");
        result.Role.Should().Be(Roles.Sales);
    }

    [Fact]
    public async Task パスワードが一致しない場合はnullを返す()
    {
        // Given
        var user = new AppUser(1, "yamada", _hasher.Hash("SecurePass123!"), Roles.Sales);
        var authenticator = CreateAuthenticator(user);

        // When: 誤ったパスワード
        var result = await authenticator.AuthenticateAsync("yamada", "wrong-password");

        // Then
        result.Should().BeNull();
    }

    [Fact]
    public async Task ユーザーが存在しない場合はnullを返す()
    {
        // Given: リポジトリがユーザーを返さない
        var authenticator = CreateAuthenticator(storedUser: null);

        // When
        var result = await authenticator.AuthenticateAsync("unknown", "any-password");

        // Then
        result.Should().BeNull();
    }
}

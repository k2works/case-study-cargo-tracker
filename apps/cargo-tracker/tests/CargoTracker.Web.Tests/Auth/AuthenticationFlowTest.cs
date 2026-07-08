using System.Net;
using System.Text.RegularExpressions;
using FluentAssertions;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Data.Sqlite;
using Microsoft.Extensions.Hosting;

namespace CargoTracker.Web.Tests.Auth;

public sealed class AuthenticationFlowTest : IClassFixture<AuthenticationFlowTest.AuthWebFactory>
{
    private readonly AuthWebFactory _factory;

    public AuthenticationFlowTest(AuthWebFactory factory) => _factory = factory;

    private HttpClient CreateClient() =>
        _factory.CreateClient(new WebApplicationFactoryClientOptions { AllowAutoRedirect = false });

    private static string ExtractAntiforgeryToken(string html)
    {
        var match = Regex.Match(html, "name=\"__RequestVerificationToken\"[^>]*value=\"([^\"]+)\"");
        return match.Groups[1].Value;
    }

    [Fact]
    public async Task 未認証で保護ページにアクセスするとログイン画面へリダイレクトされる()
    {
        var response = await CreateClient().GetAsync("/");

        response.StatusCode.Should().Be(HttpStatusCode.Redirect);
        response.Headers.Location!.OriginalString.Should().Contain("/login");
    }

    [Fact]
    public async Task ヘルスチェックは未認証でアクセスできる()
    {
        var response = await CreateClient().GetAsync("/health");
        response.StatusCode.Should().Be(HttpStatusCode.OK);
    }

    [Fact]
    public async Task 公開貨物追跡ページは未認証でアクセスできる()
    {
        var response = await CreateClient().GetAsync("/public/tracking/ABC123");
        response.StatusCode.Should().Be(HttpStatusCode.OK);
    }

    [Fact]
    public async Task ログイン画面は未認証で表示できる()
    {
        var response = await CreateClient().GetAsync("/login");
        response.StatusCode.Should().Be(HttpStatusCode.OK);
    }

    [Fact]
    public async Task 正しい資格情報でログインしダッシュボードへリダイレクトされる()
    {
        var client = CreateClient();
        var loginPage = await client.GetStringAsync("/login");
        var token = ExtractAntiforgeryToken(loginPage);

        var response = await client.PostAsync("/login", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["Username"] = "sales",
            ["Password"] = "Password1!",
            ["__RequestVerificationToken"] = token,
        }));

        response.StatusCode.Should().Be(HttpStatusCode.Redirect);
        response.Headers.Location!.OriginalString.Should().Be("/");
    }

    [Fact]
    public async Task 誤った資格情報ではエラーが表示されユーザー名が保持される()
    {
        var client = CreateClient();
        var loginPage = await client.GetStringAsync("/login");
        var token = ExtractAntiforgeryToken(loginPage);

        var response = await client.PostAsync("/login", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["Username"] = "sales",
            ["Password"] = "wrong-password",
            ["__RequestVerificationToken"] = token,
        }));

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var body = await response.Content.ReadAsStringAsync();
        body.Should().Contain("正しくありません");
        body.Should().Contain("value=\"sales\"");
    }

    /// <summary>SQLite 一時 DB で起動する統合テスト用ファクトリ。Development 環境でシードユーザーを投入する。</summary>
    public sealed class AuthWebFactory : WebApplicationFactory<Program>
    {
        private readonly string _dbPath = Path.Combine(Path.GetTempPath(), $"ct_web_{Guid.NewGuid():N}.db");

        protected override void ConfigureWebHost(IWebHostBuilder builder)
        {
            builder.UseEnvironment(Environments.Development);
            builder.UseSetting("Database:Provider", "Sqlite");
            builder.UseSetting("Database:ConnectionString", $"Data Source={_dbPath}");
        }

        protected override void Dispose(bool disposing)
        {
            base.Dispose(disposing);
            if (disposing)
            {
                SqliteConnection.ClearAllPools();
                if (File.Exists(_dbPath))
                {
                    File.Delete(_dbPath);
                }
            }
        }
    }
}

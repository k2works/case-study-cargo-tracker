using System.Net;
using System.Text.RegularExpressions;
using CargoTracker.Web.Tests.Auth;
using FluentAssertions;
using Microsoft.AspNetCore.Mvc.Testing;

namespace CargoTracker.Web.Tests.Shipper;

/// <summary>荷主登録の受入テスト（US02/US03）。営業ロールでログインして登録 → 一覧表示を検証する。</summary>
public sealed class ShipperRegistrationWebTest : IClassFixture<AuthenticationFlowTest.AuthWebFactory>
{
    private readonly AuthenticationFlowTest.AuthWebFactory _factory;

    public ShipperRegistrationWebTest(AuthenticationFlowTest.AuthWebFactory factory) => _factory = factory;

    private static string Token(string html) =>
        Regex.Match(html, "name=\"__RequestVerificationToken\"[^>]*value=\"([^\"]+)\"").Groups[1].Value;

    private async Task<HttpClient> LoginAsSalesAsync()
    {
        var client = _factory.CreateClient(new WebApplicationFactoryClientOptions { AllowAutoRedirect = false });
        var token = Token(await client.GetStringAsync("/login"));
        await client.PostAsync("/login", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["Username"] = "sales",
            ["Password"] = "Password1!",
            ["__RequestVerificationToken"] = token,
        }));
        return client;
    }

    [Fact]
    public async Task 個人荷主を登録すると一覧に表示される()
    {
        var client = await LoginAsSalesAsync();
        var email = $"ind_{Guid.NewGuid():N}@example.com";
        var token = Token(await client.GetStringAsync("/shippers/new"));

        var response = await client.PostAsync("/shippers/new", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["ShipperType"] = "Individual",
            ["Name"] = "山田太郎",
            ["Email"] = email,
            ["__RequestVerificationToken"] = token,
        }));

        // PRG: 登録成功で /shippers へリダイレクト
        response.StatusCode.Should().Be(HttpStatusCode.Redirect);
        response.Headers.Location!.OriginalString.Should().Be("/shippers");

        // 一覧に表示される
        var list = await client.GetStringAsync("/shippers");
        list.Should().Contain("山田太郎").And.Contain(email);
    }

    [Fact]
    public async Task 法人荷主を登録すると割引率が一覧に表示される()
    {
        var client = await LoginAsSalesAsync();
        var email = $"corp_{Guid.NewGuid():N}@example.com";
        var token = Token(await client.GetStringAsync("/shippers/new"));

        await client.PostAsync("/shippers/new", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["ShipperType"] = "Corporate",
            ["Name"] = "株式会社サンプル",
            ["Email"] = email,
            ["ContractNumber"] = "C-0001",
            ["DiscountPercent"] = "15",
            ["__RequestVerificationToken"] = token,
        }));

        var list = await client.GetStringAsync("/shippers");
        list.Should().Contain("株式会社サンプル").And.Contain("15%");
    }

    [Fact]
    public async Task 法人選択で契約番号が未入力ならエラーが表示され登録されない()
    {
        var client = await LoginAsSalesAsync();
        var token = Token(await client.GetStringAsync("/shippers/new"));

        var response = await client.PostAsync("/shippers/new", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["ShipperType"] = "Corporate",
            ["Name"] = "契約番号なし法人",
            ["Email"] = $"nc_{Guid.NewGuid():N}@example.com",
            ["__RequestVerificationToken"] = token,
        }));

        // バリデーションエラーで再表示（リダイレクトしない）
        response.StatusCode.Should().Be(HttpStatusCode.OK);
        (await response.Content.ReadAsStringAsync()).Should().Contain("契約番号が必要です");
    }
}

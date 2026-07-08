using System.Net;
using System.Text.RegularExpressions;
using CargoTracker.Web.Tests.Auth;
using FluentAssertions;
using Microsoft.AspNetCore.Mvc.Testing;

namespace CargoTracker.Web.Tests.Estimation;

/// <summary>見積作成の受入テスト（US01）。営業ロールで作成 → 見積番号発行 → 候補表示を検証する。</summary>
public sealed class EstimateWebTest : IClassFixture<AuthenticationFlowTest.AuthWebFactory>
{
    private readonly AuthenticationFlowTest.AuthWebFactory _factory;

    public EstimateWebTest(AuthenticationFlowTest.AuthWebFactory factory) => _factory = factory;

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
    public async Task 見積を作成すると詳細へ遷移しルート候補と見積番号が表示される()
    {
        var client = await LoginAsSalesAsync();
        var token = Token(await client.GetStringAsync("/estimates/new"));

        var response = await client.PostAsync("/estimates/new", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["OriginUnLocode"] = "JPTYO",
            ["DestinationUnLocode"] = "DEHAM",
            ["ArrivalDeadline"] = "2026-09-30",
            ["CargoType"] = "General",
            ["WeightKg"] = "1200",
            ["__RequestVerificationToken"] = token,
        }));

        // PRG: 作成成功で詳細（/estimates/{guid}）へリダイレクト
        response.StatusCode.Should().Be(HttpStatusCode.Redirect);
        response.Headers.Location!.OriginalString.Should().MatchRegex("^/estimates/[0-9a-fA-F-]{36}$");

        // 詳細にルート候補（スタブ）と見積番号が表示される
        var detail = await client.GetStringAsync(response.Headers.Location.OriginalString);
        detail.Should().Contain("V-STUB-01").And.Contain("ルート候補").And.Contain("見積番号");
    }

    [Fact]
    public async Task 出発地と仕向地が同一ならエラーで作成されない()
    {
        var client = await LoginAsSalesAsync();
        var token = Token(await client.GetStringAsync("/estimates/new"));

        var response = await client.PostAsync("/estimates/new", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["OriginUnLocode"] = "JPTYO",
            ["DestinationUnLocode"] = "JPTYO",
            ["ArrivalDeadline"] = "2026-09-30",
            ["CargoType"] = "General",
            ["WeightKg"] = "1200",
            ["__RequestVerificationToken"] = token,
        }));

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        (await response.Content.ReadAsStringAsync()).Should().Contain("出発地と仕向地は異なる");
    }
}

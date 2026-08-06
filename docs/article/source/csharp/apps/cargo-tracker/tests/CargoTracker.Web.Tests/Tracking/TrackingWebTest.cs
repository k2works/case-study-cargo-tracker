using System.Net;
using System.Text.RegularExpressions;
using CargoTracker.Web.Tests.Auth;
using FluentAssertions;
using Microsoft.AspNetCore.Mvc.Testing;

namespace CargoTracker.Web.Tests.Tracking;

public sealed class TrackingWebTest : IClassFixture<AuthenticationFlowTest.AuthWebFactory>
{
    private readonly AuthenticationFlowTest.AuthWebFactory _factory;

    public TrackingWebTest(AuthenticationFlowTest.AuthWebFactory factory) => _factory = factory;

    private static string Token(string html) =>
        Regex.Match(html, "name=\"__RequestVerificationToken\"[^>]*value=\"([^\"]+)\"").Groups[1].Value;

    private async Task<HttpClient> LoginAsync(string username)
    {
        var client = _factory.CreateClient(new WebApplicationFactoryClientOptions { AllowAutoRedirect = false });
        var token = Token(await client.GetStringAsync("/login"));
        await client.PostAsync("/login", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["Username"] = username,
            ["Password"] = "Password1!",
            ["__RequestVerificationToken"] = token,
        }));
        return client;
    }

    [Fact]
    public async Task 追跡管理者が追跡照会画面を開ける()
    {
        var tracker = await LoginAsync("tracker");
        var page = await tracker.GetStringAsync("/tracking");
        page.Should().Contain("貨物追跡").And.Contain("追跡番号");
    }

    [Fact]
    public async Task 存在しない追跡番号は見つかりませんと表示する()
    {
        var tracker = await LoginAsync("tracker");
        var page = await tracker.GetStringAsync("/tracking/TRK-NOT-EXIST");
        page.Should().Contain("追跡番号が見つかりません");
    }

    [Fact]
    public async Task 公開追跡ページは未認証で到達できる()
    {
        var anonymous = _factory.CreateClient(new WebApplicationFactoryClientOptions { AllowAutoRedirect = false });
        var response = await anonymous.GetAsync("/public/tracking/TRK-NOT-EXIST");
        response.StatusCode.Should().Be(HttpStatusCode.OK);
        (await response.Content.ReadAsStringAsync()).Should().Contain("貨物追跡");
    }
}

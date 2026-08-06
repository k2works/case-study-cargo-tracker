using System.Net;
using System.Text.RegularExpressions;
using CargoTracker.Web.Tests.Auth;
using FluentAssertions;
using Microsoft.AspNetCore.Mvc.Testing;

namespace CargoTracker.Web.Tests.Handling;

public sealed class HandlingWebTest : IClassFixture<AuthenticationFlowTest.AuthWebFactory>
{
    private readonly AuthenticationFlowTest.AuthWebFactory _factory;

    public HandlingWebTest(AuthenticationFlowTest.AuthWebFactory factory) => _factory = factory;

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
    public async Task 荷役作業員が荷役登録画面を開ける()
    {
        var handler = await LoginAsync("handler");

        var page = await handler.GetStringAsync("/handling/new");

        page.Should().Contain("荷役作業登録")
            .And.Contain("追跡番号")
            .And.Contain("受領").And.Contain("積込").And.Contain("荷降し");
    }

    [Fact]
    public async Task 荷役登録画面に引取と荷受人確認欄がある()
    {
        var handler = await LoginAsync("handler");

        var page = await handler.GetStringAsync("/handling/new");

        page.Should().Contain("引取").And.Contain("荷受人確認");
    }

    [Fact]
    public async Task 存在しない追跡番号では荷役登録できない()
    {
        var handler = await LoginAsync("handler");
        var token = Token(await handler.GetStringAsync("/handling/new"));

        var response = await handler.PostAsync("/handling", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["TrackingNumber"] = "TRK-NOT-EXIST",
            ["EventType"] = "Receive",
            ["LocationUnLocode"] = "JPTYO",
            ["CompletionTime"] = "2026-09-01T00:00",
            ["__RequestVerificationToken"] = token,
        }));

        var body = await response.Content.ReadAsStringAsync();
        body.Should().Contain("追跡番号が見つかりません");
    }
}

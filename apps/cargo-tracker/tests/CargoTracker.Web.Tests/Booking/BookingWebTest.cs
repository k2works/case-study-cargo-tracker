using System.Net;
using System.Text.RegularExpressions;
using CargoTracker.Web.Tests.Auth;
using FluentAssertions;
using Microsoft.AspNetCore.Mvc.Testing;

namespace CargoTracker.Web.Tests.Booking;

public sealed class BookingWebTest : IClassFixture<AuthenticationFlowTest.AuthWebFactory>
{
    private readonly AuthenticationFlowTest.AuthWebFactory _factory;

    public BookingWebTest(AuthenticationFlowTest.AuthWebFactory factory) => _factory = factory;

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
    public async Task 貨物予約を登録すると詳細へ遷移し予約番号とPreliminaryが表示される()
    {
        var client = await LoginAsSalesAsync();
        var newPage = await client.GetStringAsync("/bookings/new");
        var token = Token(newPage);
        var shipperId = Regex.Match(newPage, "<option value=\"([^\"]+)\"").Groups[1].Value;

        var response = await client.PostAsync("/bookings", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["ShipperId"] = shipperId,
            ["OriginUnLocode"] = "JPTYO",
            ["DestinationUnLocode"] = "DEHAM",
            ["ArrivalDeadline"] = "2026-09-30",
            ["CargoType"] = "General",
            ["Weight"] = "1200",
            ["DimensionLength"] = "120",
            ["DimensionWidth"] = "80",
            ["DimensionHeight"] = "90",
            ["Quantity"] = "2",
            ["Description"] = "機械部品",
            ["__RequestVerificationToken"] = token,
        }));

        response.StatusCode.Should().Be(HttpStatusCode.Redirect);
        response.Headers.Location!.OriginalString.Should().MatchRegex("^/bookings/BKG-[A-Z0-9-]+$");

        var detail = await client.GetStringAsync(response.Headers.Location.OriginalString);
        detail.Should().Contain("予約番号").And.Contain("PRELIMINARY").And.Contain("機械部品");
    }
}

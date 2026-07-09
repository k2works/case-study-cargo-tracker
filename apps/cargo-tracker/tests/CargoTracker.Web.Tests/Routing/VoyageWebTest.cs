using System.Net;
using System.Text.RegularExpressions;
using CargoTracker.Web.Tests.Auth;
using FluentAssertions;
using Microsoft.AspNetCore.Mvc.Testing;

namespace CargoTracker.Web.Tests.Routing;

public sealed class VoyageWebTest : IClassFixture<AuthenticationFlowTest.AuthWebFactory>
{
    private readonly AuthenticationFlowTest.AuthWebFactory _factory;

    public VoyageWebTest(AuthenticationFlowTest.AuthWebFactory factory) => _factory = factory;

    private static string Token(string html) =>
        Regex.Match(html, "name=\"__RequestVerificationToken\"[^>]*value=\"([^\"]+)\"").Groups[1].Value;

    private async Task<HttpClient> LoginAsRouteDesignerAsync()
    {
        var client = _factory.CreateClient(new WebApplicationFactoryClientOptions { AllowAutoRedirect = false });
        var token = Token(await client.GetStringAsync("/login"));
        await client.PostAsync("/login", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["Username"] = "router",
            ["Password"] = "Password1!",
            ["__RequestVerificationToken"] = token,
        }));
        return client;
    }

    [Fact]
    public async Task 航海スケジュールを登録すると一覧へ遷移し航海番号が表示される()
    {
        var client = await LoginAsRouteDesignerAsync();
        var response = await PostVoyageAsync(client, "VYG-WEB-001");

        response.StatusCode.Should().Be(HttpStatusCode.Redirect);
        response.Headers.Location!.OriginalString.Should().Be("/voyages");

        var index = await client.GetStringAsync("/voyages");
        index.Should().Contain("VYG-WEB-001")
            .And.Contain("Kiso Maru")
            .And.Contain("Ocean Network")
            .And.Contain("JPTYO")
            .And.Contain("DEHAM");
    }

    [Fact]
    public async Task 重複航海番号は登録されない()
    {
        var client = await LoginAsRouteDesignerAsync();
        await PostVoyageAsync(client, "VYG-WEB-002");

        var response = await PostVoyageAsync(client, "VYG-WEB-002");

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var body = await response.Content.ReadAsStringAsync();
        body.Should().Contain("同一航海番号").And.Contain("alert-danger");
    }

    [Fact]
    public async Task 区間が連続しない航海スケジュールは登録されない()
    {
        var client = await LoginAsRouteDesignerAsync();
        var newPage = await client.GetStringAsync("/voyages/new");
        var token = Token(newPage);

        var response = await client.PostAsync("/voyages", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["VoyageNumber"] = "VYG-WEB-003",
            ["VesselName"] = "Kiso Maru",
            ["Carrier"] = "Ocean Network",
            ["SupportedCargoTypes"] = "General",
            ["CarrierMovements[0].DepartureLocationUnLocode"] = "JPTYO",
            ["CarrierMovements[0].ArrivalLocationUnLocode"] = "SGSIN",
            ["CarrierMovements[0].DepartureDate"] = "2026-09-01T10:00:00+00:00",
            ["CarrierMovements[0].ArrivalDate"] = "2026-09-05T10:00:00+00:00",
            ["CarrierMovements[1].DepartureLocationUnLocode"] = "CNSHA",
            ["CarrierMovements[1].ArrivalLocationUnLocode"] = "DEHAM",
            ["CarrierMovements[1].DepartureDate"] = "2026-09-06T10:00:00+00:00",
            ["CarrierMovements[1].ArrivalDate"] = "2026-09-20T10:00:00+00:00",
            ["__RequestVerificationToken"] = token,
        }));

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var body = await response.Content.ReadAsStringAsync();
        body.Should().Contain("到着港").And.Contain("出発港").And.Contain("alert-danger");
    }

    private static async Task<HttpResponseMessage> PostVoyageAsync(HttpClient client, string voyageNumber)
    {
        var newPage = await client.GetStringAsync("/voyages/new");
        var token = Token(newPage);

        return await client.PostAsync("/voyages", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["VoyageNumber"] = voyageNumber,
            ["VesselName"] = "Kiso Maru",
            ["Carrier"] = "Ocean Network",
            ["SupportedCargoTypes"] = "General",
            ["CarrierMovements[0].DepartureLocationUnLocode"] = "JPTYO",
            ["CarrierMovements[0].ArrivalLocationUnLocode"] = "SGSIN",
            ["CarrierMovements[0].DepartureDate"] = "2026-09-01T10:00:00+00:00",
            ["CarrierMovements[0].ArrivalDate"] = "2026-09-05T10:00:00+00:00",
            ["CarrierMovements[1].DepartureLocationUnLocode"] = "SGSIN",
            ["CarrierMovements[1].ArrivalLocationUnLocode"] = "DEHAM",
            ["CarrierMovements[1].DepartureDate"] = "2026-09-06T10:00:00+00:00",
            ["CarrierMovements[1].ArrivalDate"] = "2026-09-20T10:00:00+00:00",
            ["__RequestVerificationToken"] = token,
        }));
    }
}

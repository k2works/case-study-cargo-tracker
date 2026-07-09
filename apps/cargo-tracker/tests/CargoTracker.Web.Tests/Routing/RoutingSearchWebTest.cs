using System.Net;
using System.Text.RegularExpressions;
using CargoTracker.Web.Tests.Auth;
using FluentAssertions;
using Microsoft.AspNetCore.Mvc.Testing;

namespace CargoTracker.Web.Tests.Routing;

public sealed class RoutingSearchWebTest : IClassFixture<AuthenticationFlowTest.AuthWebFactory>
{
    private readonly AuthenticationFlowTest.AuthWebFactory _factory;

    public RoutingSearchWebTest(AuthenticationFlowTest.AuthWebFactory factory) => _factory = factory;

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
    public async Task 経路設計依頼一覧から予約を選択し航海スケジュールを検索できる()
    {
        var sales = await LoginAsync("sales");
        var bookingId = await CreateAndAssignGeneralBookingAsync(sales);
        var router = await LoginAsync("router");
        await CreateVoyageAsync(router, "VYG-SRCH-001", "General");

        var requests = await router.GetStringAsync("/routing/requests");
        requests.Should().Contain(bookingId).And.Contain("JPTYO").And.Contain("DEHAM");

        var request = await router.GetStringAsync($"/routing/requests/{bookingId}");
        request.Should().Contain("予約情報")
            .And.Contain("貨物仕様")
            .And.Contain("JPTYO")
            .And.Contain("DEHAM");

        var results = await router.GetStringAsync(SearchUrl(bookingId, "General", "DEHAM"));
        results.Should().Contain("VYG-SRCH-001")
            .And.Contain("Ocean Network")
            .And.Contain("JPTYO → SGSIN → DEHAM");
    }

    [Fact]
    public async Task 危険物予約では危険物対応航海のみ表示される()
    {
        var sales = await LoginAsync("sales");
        var bookingId = await CreateAndAssignHazardousBookingAsync(sales);
        var router = await LoginAsync("router");
        await CreateVoyageAsync(router, "VYG-SRCH-002-GN", "General");
        await CreateVoyageAsync(router, "VYG-SRCH-002-HZ", "Hazardous");

        var results = await router.GetStringAsync(SearchUrl(bookingId, "Hazardous", "DEHAM"));

        results.Should().Contain("VYG-SRCH-002-HZ");
        results.Should().NotContain("VYG-SRCH-002-GN");
    }

    [Fact]
    public async Task 条件を満たす航海がない場合は再検索導線を表示する()
    {
        var sales = await LoginAsync("sales");
        var bookingId = await CreateAndAssignGeneralBookingAsync(sales);
        var router = await LoginAsync("router");

        var results = await router.GetStringAsync(SearchUrl(bookingId, "General", "USNYC"));

        results.Should().Contain("条件を満たす航海がありません")
            .And.Contain("条件を緩和して再検索");
    }

    private static async Task<string> CreateAndAssignGeneralBookingAsync(HttpClient client)
    {
        var location = await CreateBookingAsync(client, "General");
        await AssignRoutingAsync(client, location);
        return location.Split('/').Last();
    }

    private static async Task<string> CreateAndAssignHazardousBookingAsync(HttpClient client)
    {
        var location = await CreateBookingAsync(client, "Hazardous");
        await AssignRoutingAsync(client, location);
        return location.Split('/').Last();
    }

    private static async Task<string> CreateBookingAsync(HttpClient client, string cargoType)
    {
        var newPage = await client.GetStringAsync("/bookings/new");
        var token = Token(newPage);
        var shipperId = Regex.Match(newPage, "<option value=\"([^\"]+)\"").Groups[1].Value;
        var fields = new Dictionary<string, string>
        {
            ["ShipperId"] = shipperId,
            ["OriginUnLocode"] = "JPTYO",
            ["DestinationUnLocode"] = "DEHAM",
            ["ArrivalDeadline"] = "2026-10-31",
            ["CargoType"] = cargoType,
            ["Weight"] = "1200",
            ["Description"] = "検索対象貨物",
            ["__RequestVerificationToken"] = token,
        };
        if (cargoType == "Hazardous")
        {
            fields["HazardousClass"] = "3";
            fields["UnNumber"] = "UN1203";
            fields["ProperShippingName"] = "Gasoline";
        }

        var response = await client.PostAsync("/bookings", new FormUrlEncodedContent(fields));
        response.StatusCode.Should().Be(HttpStatusCode.Redirect);
        return response.Headers.Location!.OriginalString;
    }

    private static async Task AssignRoutingAsync(HttpClient client, string bookingLocation)
    {
        var detail = await client.GetStringAsync(bookingLocation);
        var token = Token(detail);
        var response = await client.PostAsync($"{bookingLocation}/assign-routing", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["__RequestVerificationToken"] = token,
        }));
        response.StatusCode.Should().Be(HttpStatusCode.Redirect);
    }

    private static async Task CreateVoyageAsync(HttpClient client, string voyageNumber, string cargoType)
    {
        var newPage = await client.GetStringAsync("/voyages/new");
        var token = Token(newPage);
        var response = await client.PostAsync("/voyages", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["VoyageNumber"] = voyageNumber,
            ["VesselName"] = $"{voyageNumber} Vessel",
            ["Carrier"] = "Ocean Network",
            ["SupportedCargoTypes"] = cargoType,
            ["CarrierMovements[0].DepartureLocationUnLocode"] = "JPTYO",
            ["CarrierMovements[0].ArrivalLocationUnLocode"] = "SGSIN",
            ["CarrierMovements[0].DepartureDate"] = "2026-10-01T10:00:00+00:00",
            ["CarrierMovements[0].ArrivalDate"] = "2026-10-05T10:00:00+00:00",
            ["CarrierMovements[1].DepartureLocationUnLocode"] = "SGSIN",
            ["CarrierMovements[1].ArrivalLocationUnLocode"] = "DEHAM",
            ["CarrierMovements[1].DepartureDate"] = "2026-10-06T10:00:00+00:00",
            ["CarrierMovements[1].ArrivalDate"] = "2026-10-20T10:00:00+00:00",
            ["__RequestVerificationToken"] = token,
        }));
        response.StatusCode.Should().Be(HttpStatusCode.Redirect);
    }

    private static string SearchUrl(string bookingId, string cargoType, string destination)
        => $"/routing/requests/{bookingId}/voyages?OriginUnlocode=JPTYO&DestinationUnlocode={destination}"
            + $"&DepartureFrom=2026-10-01T00%3A00%3A00%2B00%3A00&DepartureTo=2026-10-31T23%3A59%3A00%2B00%3A00"
            + $"&CargoType={cargoType}";
}

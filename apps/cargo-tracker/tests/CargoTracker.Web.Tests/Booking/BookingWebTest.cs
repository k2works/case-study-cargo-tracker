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

    [Fact]
    public async Task 危険物申告が欠落している危険物予約は登録されない()
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
            ["CargoType"] = "Hazardous",
            ["Weight"] = "1200",
            ["Description"] = "燃料",
            ["__RequestVerificationToken"] = token,
        }));

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var body = await response.Content.ReadAsStringAsync();
        body.Should().Contain("危険物申告").And.Contain("alert-danger");
    }

    [Fact]
    public async Task 危険物予約を登録すると詳細に危険物申告が表示される()
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
            ["CargoType"] = "Hazardous",
            ["Weight"] = "1200",
            ["Description"] = "燃料",
            ["HazardousClass"] = "3",
            ["UnNumber"] = "UN1203",
            ["ProperShippingName"] = "Gasoline",
            ["__RequestVerificationToken"] = token,
        }));

        response.StatusCode.Should().Be(HttpStatusCode.Redirect);

        var detail = await client.GetStringAsync(response.Headers.Location!.OriginalString);
        detail.Should().Contain("HAZARDOUS").And.Contain("UN1203").And.Contain("Gasoline");
    }

    [Fact]
    public async Task 貨物種別別の追加フィールドを部分更新できる()
    {
        var client = await LoginAsSalesAsync();

        var hazardous = await client.GetStringAsync("/bookings/new/cargo-fields?cargoType=Hazardous");
        var refrigerated = await client.GetStringAsync("/bookings/new/cargo-fields?cargoType=Refrigerated");
        var general = await client.GetStringAsync("/bookings/new/cargo-fields?cargoType=General");

        hazardous.Should().Contain("HazardousClass").And.Contain("UnNumber").And.NotContain("MinTemperature");
        refrigerated.Should().Contain("MinTemperature").And.Contain("MaxTemperature").And.NotContain("UnNumber");
        general.Should().NotContain("HazardousClass").And.NotContain("MinTemperature");
    }

    [Fact]
    public async Task Preliminaryの予約を経路設計へ引き渡すと詳細にRouteProposedが表示される()
    {
        var client = await LoginAsSalesAsync();
        var bookingLocation = await CreateGeneralBookingAsync(client);
        var detailPage = await client.GetStringAsync(bookingLocation);
        var token = Token(detailPage);

        var response = await client.PostAsync($"{bookingLocation}/assign-routing", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["__RequestVerificationToken"] = token,
        }));

        response.StatusCode.Should().Be(HttpStatusCode.Redirect);
        response.Headers.Location!.OriginalString.Should().Be(bookingLocation);

        var detail = await client.GetStringAsync(bookingLocation);
        detail.Should().Contain("経路提案中").And.Contain("ROUTE_PROPOSED").And.Contain("経路設計を依頼しました");
        detail.Should().NotContain("経路設計依頼</button>");
    }

    [Fact]
    public async Task RouteProposedの予約を再度引き渡すと拒否される()
    {
        var client = await LoginAsSalesAsync();
        var bookingLocation = await CreateGeneralBookingAsync(client);
        var token = Token(await client.GetStringAsync(bookingLocation));
        await client.PostAsync($"{bookingLocation}/assign-routing", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["__RequestVerificationToken"] = token,
        }));

        var response = await client.PostAsync($"{bookingLocation}/assign-routing", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["__RequestVerificationToken"] = token,
        }));

        response.StatusCode.Should().Be(HttpStatusCode.Redirect);
        var detail = await client.GetStringAsync(bookingLocation);
        detail.Should().Contain("仮受付の予約のみ").And.Contain("alert-warning");
    }

    [Fact]
    public async Task 貨物予約一覧に登録済み予約が表示されステータスで絞り込める()
    {
        var client = await LoginAsSalesAsync();
        var location = await CreateGeneralBookingAsync(client);
        var bookingId = location.Replace("/bookings/", string.Empty);

        // 一覧に登録済み予約と仮受付バッジが表示される。
        var list = await client.GetStringAsync("/bookings");
        list.Should().Contain("貨物予約一覧").And.Contain(bookingId).And.Contain("仮受付");

        // ステータス絞り込み（仮受付＝PRELIMINARY）でヒットする。
        var filtered = await client.GetStringAsync("/bookings?status=PRELIMINARY");
        filtered.Should().Contain(bookingId);

        // 該当しないステータス（精算済）では表示されない。
        var empty = await client.GetStringAsync("/bookings?status=SETTLED");
        empty.Should().NotContain(bookingId);
    }

    [Fact]
    public async Task 貨物予約一覧を出発地でフィルタできる()
    {
        var client = await LoginAsSalesAsync();
        var location = await CreateGeneralBookingAsync(client); // 出発地 JPTYO
        var bookingId = location.Replace("/bookings/", string.Empty);

        (await client.GetStringAsync("/bookings?origin=JPTYO")).Should().Contain(bookingId);
        (await client.GetStringAsync("/bookings?origin=USLAX")).Should().NotContain(bookingId);
    }

    private static async Task<string> CreateGeneralBookingAsync(HttpClient client)
    {
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
            ["Description"] = "機械部品",
            ["__RequestVerificationToken"] = token,
        }));

        return response.Headers.Location!.OriginalString;
    }
}

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

    private static string CandidatesUrl(string bookingId, string cargoType)
        => $"/routing/requests/{bookingId}/candidates?OriginUnlocode=JPTYO&DestinationUnlocode=DEHAM"
            + $"&DepartureFrom=2026-10-01T00%3A00%3A00%2B00%3A00&DepartureTo=2026-10-31T23%3A59%3A00%2B00%3A00"
            + $"&CargoType={cargoType}";

    [Fact]
    public async Task 経路候補を算出すると直行候補が推奨順で表示される()
    {
        var sales = await LoginAsync("sales");
        var bookingId = await CreateAndAssignGeneralBookingAsync(sales);
        var router = await LoginAsync("router");
        await CreateVoyageAsync(router, "VYG-CAND-001", "General");

        var candidates = await router.GetStringAsync(CandidatesUrl(bookingId, "General"));

        candidates.Should().Contain("VYG-CAND-001")
            .And.Contain("直行")
            .And.Contain("JPTYO")
            .And.Contain("DEHAM");
    }

    [Fact]
    public async Task 期限内に到達可能な経路がない場合は条件調整を促す()
    {
        var sales = await LoginAsync("sales");
        // 危険物予約に対し一般貨物対応の航海のみ登録 → 対応航海なしで候補は空になる。
        var bookingId = await CreateAndAssignHazardousBookingAsync(sales);
        var router = await LoginAsync("router");
        await CreateVoyageAsync(router, "VYG-CAND-002-GN", "General");

        var candidates = await router.GetStringAsync(CandidatesUrl(bookingId, "Hazardous"));

        candidates.Should().Contain("期限内に到達可能な経路がありません")
            .And.Contain("alert-warning");
    }

    [Fact]
    public async Task 予約フロー全体が算出から選択紐付け通知確定まで完了する()
    {
        var sales = await LoginAsync("sales");
        var bookingId = await CreateAndAssignGeneralBookingAsync(sales);
        var router = await LoginAsync("router");
        await CreateVoyageAsync(router, "VYG-FLOW-001", "General");

        // 経路設計者: 候補算出 → 選択・確定
        var candidates = await router.GetStringAsync(CandidatesUrl(bookingId, "General"));
        var selectToken = Token(candidates);
        (await router.PostAsync($"/routing/requests/{bookingId}/select", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["OriginUnlocode"] = "JPTYO",
            ["DestinationUnlocode"] = "DEHAM",
            ["DepartureFrom"] = "2026-10-01T00:00",
            ["DepartureTo"] = "2026-10-31T23:59",
            ["CargoType"] = "General",
            ["selectedIndex"] = "0",
            ["__RequestVerificationToken"] = selectToken,
        }))).StatusCode.Should().Be(HttpStatusCode.Redirect);

        // 営業担当者: 予約に紐付け → 荷主通知 → 予約確定
        await PostBookingActionAsync(sales, bookingId, "route");
        await PostBookingActionAsync(sales, bookingId, "notify");
        await PostBookingActionAsync(sales, bookingId, "confirm");

        var detail = await sales.GetStringAsync($"/bookings/{bookingId}");
        detail.Should().Contain("CONFIRMED").And.Contain("予約は確定済み");
    }

    private static async Task PostBookingActionAsync(HttpClient client, string bookingId, string action)
    {
        var detail = await client.GetStringAsync($"/bookings/{bookingId}");
        var token = Token(detail);
        var response = await client.PostAsync($"/bookings/{bookingId}/{action}", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["__RequestVerificationToken"] = token,
        }));
        response.StatusCode.Should().Be(HttpStatusCode.Redirect);
    }

    [Fact]
    public async Task 経路候補を選択確定すると依頼画面に確定経路が表示される()
    {
        var sales = await LoginAsync("sales");
        var bookingId = await CreateAndAssignGeneralBookingAsync(sales);
        var router = await LoginAsync("router");
        await CreateVoyageAsync(router, "VYG-SEL-001", "General");

        // 候補を算出して選択・確定フォームのトークンを取得する。
        var candidates = await router.GetStringAsync(CandidatesUrl(bookingId, "General"));
        candidates.Should().Contain("この経路を選択・確定");
        var token = Token(candidates);

        var response = await router.PostAsync($"/routing/requests/{bookingId}/select", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["OriginUnlocode"] = "JPTYO",
            ["DestinationUnlocode"] = "DEHAM",
            ["DepartureFrom"] = "2026-10-01T00:00",
            ["DepartureTo"] = "2026-10-31T23:59",
            ["CargoType"] = "General",
            ["selectedIndex"] = "0",
            ["__RequestVerificationToken"] = token,
        }));
        response.StatusCode.Should().Be(HttpStatusCode.Redirect);

        var request = await router.GetStringAsync($"/routing/requests/{bookingId}");
        request.Should().Contain("確定経路")
            .And.Contain("VYG-SEL-001");
    }

    [Theory]
    [InlineData("99")]
    [InlineData("-1")]
    public async Task 範囲外の経路候補インデックスを選択すると400になる(string selectedIndex)
    {
        var sales = await LoginAsync("sales");
        var bookingId = await CreateAndAssignGeneralBookingAsync(sales);
        var router = await LoginAsync("router");
        // Theory の各ケースで航海番号を一意にする（クラスフィクスチャで DB を共有するため）。
        await CreateVoyageAsync(router, $"VYG-SEL-OOR-{selectedIndex.Replace("-", "N")}", "General");

        var candidates = await router.GetStringAsync(CandidatesUrl(bookingId, "General"));
        var token = Token(candidates);

        var response = await router.PostAsync($"/routing/requests/{bookingId}/select", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["OriginUnlocode"] = "JPTYO",
            ["DestinationUnlocode"] = "DEHAM",
            ["DepartureFrom"] = "2026-10-01T00:00",
            ["DepartureTo"] = "2026-10-31T23:59",
            ["CargoType"] = "General",
            ["selectedIndex"] = selectedIndex,
            ["__RequestVerificationToken"] = token,
        }));

        response.StatusCode.Should().Be(HttpStatusCode.BadRequest);

        // 確定経路は保存されていない（依頼画面に確定経路バナーが出ない）。
        var request = await router.GetStringAsync($"/routing/requests/{bookingId}");
        request.Should().NotContain("確定経路");
    }
}

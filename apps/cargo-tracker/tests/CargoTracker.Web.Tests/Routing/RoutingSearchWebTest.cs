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
            ["routeKey"] = "VYG-FLOW-001",
            ["__RequestVerificationToken"] = selectToken,
        }))).StatusCode.Should().Be(HttpStatusCode.Redirect);

        // 営業担当者: 予約に紐付け → 荷主通知 → 予約確定
        await PostBookingActionAsync(sales, bookingId, "route");

        // 紐付け後、予約詳細に確定経路（旅程）と推奨手順が表示される（IT4 レビュー H6/H7）。
        var routed = await sales.GetStringAsync($"/bookings/{bookingId}");
        routed.Should().Contain("確定経路（旅程）")
            .And.Contain("VYG-FLOW-001")
            .And.Contain("推奨手順");

        await PostBookingActionAsync(sales, bookingId, "notify");
        await PostBookingActionAsync(sales, bookingId, "confirm");

        // 予約確定を起点に BookingConfirmedEvent → 追跡番号が自動発行され、状態が TrackingIssued に遷移する（US14・H3）。
        var detail = await sales.GetStringAsync($"/bookings/{bookingId}");
        detail.Should().Contain("TRACKING_ISSUED")
            .And.Contain("追跡番号")
            .And.Contain("TRK-");
    }

    private static string TrackingNumber(string bookingDetailHtml) =>
        Regex.Match(bookingDetailHtml, "badge text-bg-dark\">(TRK-[^<]+)</span>").Groups[1].Value;

    /// <summary>予約を確定まで進め、自動発行された追跡番号とともに返す（US14 の post-commit 発行を利用）。</summary>
    private async Task<(string BookingId, string TrackingNumber)> CreateConfirmedTrackedBookingAsync(string voyageNumber)
    {
        var sales = await LoginAsync("sales");
        var bookingId = await CreateAndAssignGeneralBookingAsync(sales);
        var router = await LoginAsync("router");
        await CreateVoyageAsync(router, voyageNumber, "General");

        var candidates = await router.GetStringAsync(CandidatesUrl(bookingId, "General"));
        var selectToken = Token(candidates);
        (await router.PostAsync($"/routing/requests/{bookingId}/select", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["OriginUnlocode"] = "JPTYO",
            ["DestinationUnlocode"] = "DEHAM",
            ["DepartureFrom"] = "2026-10-01T00:00",
            ["DepartureTo"] = "2026-10-31T23:59",
            ["CargoType"] = "General",
            ["routeKey"] = voyageNumber,
            ["__RequestVerificationToken"] = selectToken,
        }))).StatusCode.Should().Be(HttpStatusCode.Redirect);

        await PostBookingActionAsync(sales, bookingId, "route");
        await PostBookingActionAsync(sales, bookingId, "notify");
        await PostBookingActionAsync(sales, bookingId, "confirm");

        var detail = await sales.GetStringAsync($"/bookings/{bookingId}");
        var trackingNumber = TrackingNumber(detail);
        trackingNumber.Should().StartWith("TRK-");
        return (bookingId, trackingNumber);
    }

    private static async Task RegisterHandlingAsync(
        HttpClient handler, string trackingNumber, string eventType,
        string location, string completionTime, string? voyageNumber = null,
        string? consigneeConfirmation = null)
    {
        var token = Token(await handler.GetStringAsync("/handling/new"));
        var fields = new Dictionary<string, string>
        {
            ["TrackingNumber"] = trackingNumber,
            ["EventType"] = eventType,
            ["LocationUnLocode"] = location,
            ["CompletionTime"] = completionTime,
            ["__RequestVerificationToken"] = token,
        };
        if (voyageNumber is not null)
        {
            fields["VoyageNumber"] = voyageNumber;
        }
        if (consigneeConfirmation is not null)
        {
            fields["ConsigneeConfirmation"] = consigneeConfirmation;
        }

        var response = await handler.PostAsync("/handling", new FormUrlEncodedContent(fields));
        response.StatusCode.Should().Be(HttpStatusCode.Redirect);
    }

    [Fact]
    public async Task 荷役登録が実イベント経由で追跡状態と予約状態を同期する()
    {
        // 予約確定 → 追跡番号自動発行まで進める。
        var (bookingId, trackingNumber) = await CreateConfirmedTrackedBookingAsync("VYG-FLOW-SYNC-001");

        var handler = await LoginAsync("handler");
        var tracker = await LoginAsync("tracker");

        // 受領（Receive）を JPTYO で登録 → HandlingActivityRegisteredEvent が post-commit で発行され、
        // SyncTrackingOnHandlingRegisteredHandler が追跡イベントを追記し状態を受領済に更新する（実 MediatR 経由）。
        await RegisterHandlingAsync(handler, trackingNumber, "Receive", "JPTYO", "2026-10-01T09:00");

        var afterReceive = await tracker.GetStringAsync($"/tracking/{trackingNumber}");
        afterReceive.Should().Contain("受領済");

        // 積込（Load）を JPTYO・航海 VYG-FLOW-SYNC-001 で登録 → 予定ルート（JPTYO 発）と一致（MISROUTED でない）。
        // 追跡状態は積込済へ、SyncBookingStatusOnHandlingRegisteredHandler が予約を輸送中（IN_TRANSIT）へ同期する。
        await RegisterHandlingAsync(handler, trackingNumber, "Load", "JPTYO", "2026-10-01T10:00", "VYG-FLOW-SYNC-001");

        var afterLoad = await tracker.GetStringAsync($"/tracking/{trackingNumber}");
        afterLoad.Should().Contain("積込済");

        var sales = await LoginAsync("sales");
        var booking = await sales.GetStringAsync($"/bookings/{bookingId}");
        booking.Should().Contain("IN_TRANSIT").And.Contain("輸送中");
    }

    [Fact]
    public async Task 荷役登録の荷降しから引取まで進めると予約が配送完了へ同期する()
    {
        // IT5 レビュー H4：CLAIM→Delivered・UNLOAD→InTransit の状態同期を終端まで貫通検証する。
        // 予約確定 → 追跡番号自動発行 → 受領 → 積込（IN_TRANSIT）まで進める。
        var (bookingId, trackingNumber) = await CreateConfirmedTrackedBookingAsync("VYG-FLOW-CLAIM-001");

        var handler = await LoginAsync("handler");
        var tracker = await LoginAsync("tracker");
        var sales = await LoginAsync("sales");

        await RegisterHandlingAsync(handler, trackingNumber, "Receive", "JPTYO", "2026-10-01T09:00");
        await RegisterHandlingAsync(handler, trackingNumber, "Load", "JPTYO", "2026-10-01T10:00", "VYG-FLOW-CLAIM-001");

        // 荷降し（Unload）を目的港 DEHAM・航海 VYG-FLOW-CLAIM-001 で登録 → 追跡状態は荷降し済へ。
        // UNLOAD→InTransit のため予約状態は輸送中（IN_TRANSIT）を維持する（H4 の UNLOAD 分岐）。
        await RegisterHandlingAsync(handler, trackingNumber, "Unload", "DEHAM", "2026-10-20T08:00", "VYG-FLOW-CLAIM-001");

        var afterUnload = await tracker.GetStringAsync($"/tracking/{trackingNumber}");
        afterUnload.Should().Contain("荷降し済");
        var bookingAfterUnload = await sales.GetStringAsync($"/bookings/{bookingId}");
        bookingAfterUnload.Should().Contain("IN_TRANSIT").And.Contain("輸送中");

        // 引取（Claim）を目的港 DEHAM・荷受人確認付きで登録 → 追跡状態は引取済へ、
        // SyncBookingStatusOnHandlingRegisteredHandler が MarkDelivered() を通り予約を配送完了（DELIVERED）へ同期する（H4 の CLAIM 分岐終端）。
        await RegisterHandlingAsync(
            handler, trackingNumber, "Claim", "DEHAM", "2026-10-20T14:00",
            consigneeConfirmation: "署名: 荷受人 山田太郎");

        var afterClaim = await tracker.GetStringAsync($"/tracking/{trackingNumber}");
        afterClaim.Should().Contain("引取済");

        var bookingAfterClaim = await sales.GetStringAsync($"/bookings/{bookingId}");
        bookingAfterClaim.Should().Contain("DELIVERED").And.Contain("配送完了");
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
            ["routeKey"] = "VYG-SEL-001",
            ["__RequestVerificationToken"] = token,
        }));
        response.StatusCode.Should().Be(HttpStatusCode.Redirect);

        var request = await router.GetStringAsync($"/routing/requests/{bookingId}");
        request.Should().Contain("確定経路")
            .And.Contain("VYG-SEL-001");
    }

    [Fact]
    public async Task 候補キーで経路を選択確定できる()
    {
        var sales = await LoginAsync("sales");
        var bookingId = await CreateAndAssignGeneralBookingAsync(sales);
        var router = await LoginAsync("router");
        await CreateVoyageAsync(router, "VYG-KEY-001", "General");

        var candidates = await router.GetStringAsync(CandidatesUrl(bookingId, "General"));
        var token = Token(candidates);
        // 候補キー（直行便のため航海番号単体）で確定対象を照合する。
        var response = await router.PostAsync($"/routing/requests/{bookingId}/select", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["OriginUnlocode"] = "JPTYO",
            ["DestinationUnlocode"] = "DEHAM",
            ["DepartureFrom"] = "2026-10-01T00:00",
            ["DepartureTo"] = "2026-10-31T23:59",
            ["CargoType"] = "General",
            ["routeKey"] = "VYG-KEY-001",
            ["__RequestVerificationToken"] = token,
        }));
        response.StatusCode.Should().Be(HttpStatusCode.Redirect);

        var request = await router.GetStringAsync($"/routing/requests/{bookingId}");
        request.Should().Contain("確定経路").And.Contain("VYG-KEY-001");
    }

    [Fact]
    public async Task 存在しない候補キーを選択すると400になる()
    {
        var sales = await LoginAsync("sales");
        var bookingId = await CreateAndAssignGeneralBookingAsync(sales);
        var router = await LoginAsync("router");
        await CreateVoyageAsync(router, "VYG-KEY-002", "General");

        var candidates = await router.GetStringAsync(CandidatesUrl(bookingId, "General"));
        var token = Token(candidates);
        var response = await router.PostAsync($"/routing/requests/{bookingId}/select", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["OriginUnlocode"] = "JPTYO",
            ["DestinationUnlocode"] = "DEHAM",
            ["DepartureFrom"] = "2026-10-01T00:00",
            ["DepartureTo"] = "2026-10-31T23:59",
            ["CargoType"] = "General",
            ["routeKey"] = "VYG-NOT-EXIST",
            ["__RequestVerificationToken"] = token,
        }));

        response.StatusCode.Should().Be(HttpStatusCode.BadRequest);
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

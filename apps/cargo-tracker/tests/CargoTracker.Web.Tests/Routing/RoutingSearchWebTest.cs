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

    private static async Task RegisterExceptionAsync(
        HttpClient tracker, string trackingNumber, string exceptionType,
        string location, string occurredAt, string? description = null)
    {
        var token = Token(await tracker.GetStringAsync($"/tracking/{trackingNumber}/exceptions/new"));
        var fields = new Dictionary<string, string>
        {
            ["exceptionType"] = exceptionType,
            ["locationUnLocode"] = location,
            ["occurredAt"] = occurredAt,
            ["__RequestVerificationToken"] = token,
        };
        if (description is not null)
        {
            fields["description"] = description;
        }
        (await tracker.PostAsync($"/tracking/{trackingNumber}/exceptions",
            new FormUrlEncodedContent(fields))).StatusCode.Should().Be(HttpStatusCode.Redirect);
    }

    [Fact]
    public async Task 追跡管理者が遅延例外を登録し対応報告で解決できる()
    {
        // US19：予約確定→追跡番号発行まで進め、遅延例外を登録する。
        var (_, trackingNumber) = await CreateConfirmedTrackedBookingAsync("VYG-EX-DELAY-001");
        var tracker = await LoginAsync("tracker");

        await RegisterExceptionAsync(
            tracker, trackingNumber, "Delay", "USLAX", "2026-10-08T14:00", "荒天による寄港遅延");

        // 例外登録後は例外発生状態・例外履歴・荷主通知記録が表示される（AC3・post-commit で通知記録）。
        var afterRegister = await tracker.GetStringAsync($"/tracking/{trackingNumber}");
        afterRegister.Should().Contain("例外発生").And.Contain("DELAY").And.Contain("対応中")
            .And.Contain("通知記録").And.Contain("荷主");

        // 対応報告（解決）で例外発生前の状態へ復帰する。
        var token = Token(afterRegister);
        (await tracker.PostAsync($"/tracking/{trackingNumber}/exceptions/resolution",
            new FormUrlEncodedContent(new Dictionary<string, string>
            {
                ["resolvedAt"] = "2026-10-09T09:00",
                ["resolutionNotes"] = "新到着予定日を荷主に提示",
                ["__RequestVerificationToken"] = token,
            }))).StatusCode.Should().Be(HttpStatusCode.Redirect);

        var afterResolve = await tracker.GetStringAsync($"/tracking/{trackingNumber}");
        afterResolve.Should().Contain("解決済").And.NotContain("未解決の例外があります");
    }

    [Fact]
    public async Task 紛失例外を登録するとエスカレーションが表示される()
    {
        // US20：紛失例外は escalation_flag が立ち、エスカレーションバッジが表示される。
        var (_, trackingNumber) = await CreateConfirmedTrackedBookingAsync("VYG-EX-LOST-001");
        var tracker = await LoginAsync("tracker");

        await RegisterExceptionAsync(
            tracker, trackingNumber, "Lost", "USLAX", "2026-10-08T14:00", "紛失の疑い");

        var detail = await tracker.GetStringAsync($"/tracking/{trackingNumber}");
        detail.Should().Contain("LOST").And.Contain("エスカレーション").And.Contain("例外発生")
            .And.Contain("管理職"); // 紛失は管理職エスカレーション通知も記録される。
    }

    [Fact]
    public async Task 存在しない追跡番号への例外登録画面は追跡照会へリダイレクトされる()
    {
        var tracker = await LoginAsync("tracker");

        // 存在しない追跡番号では例外登録画面を出さず /tracking へリダイレクトする（LoginAsync は AutoRedirect 無効）。
        var response = await tracker.GetAsync("/tracking/TRK-NOT-EXIST/exceptions/new");

        response.StatusCode.Should().Be(HttpStatusCode.Redirect);
        response.Headers.Location!.OriginalString.Should().Be("/tracking");
    }

    /// <summary>予約を配送完了（Delivered）まで進めて予約番号を返す（US21 精算の前提）。</summary>
    private async Task<string> CreateDeliveredBookingAsync(string voyageNumber)
    {
        var (bookingId, trackingNumber) = await CreateConfirmedTrackedBookingAsync(voyageNumber);
        var handler = await LoginAsync("handler");

        await RegisterHandlingAsync(handler, trackingNumber, "Receive", "JPTYO", "2026-10-01T09:00");
        await RegisterHandlingAsync(handler, trackingNumber, "Load", "JPTYO", "2026-10-01T10:00", voyageNumber);
        await RegisterHandlingAsync(handler, trackingNumber, "Unload", "DEHAM", "2026-10-20T08:00", voyageNumber);
        await RegisterHandlingAsync(
            handler, trackingNumber, "Claim", "DEHAM", "2026-10-20T14:00",
            consigneeConfirmation: "署名: 荷受人 山田太郎");
        return bookingId;
    }

    private static async Task<string> GenerateInvoiceAsync(HttpClient billing, string bookingId)
    {
        var token = Token(await billing.GetStringAsync("/billing/invoices"));
        var response = await billing.PostAsync("/billing/invoices", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["bookingId"] = bookingId,
            ["__RequestVerificationToken"] = token,
        }));
        response.StatusCode.Should().Be(HttpStatusCode.Redirect);
        return response.Headers.Location!.OriginalString;
    }

    [Fact]
    public async Task 配送完了予約から精算書を発行し詳細を照会できる()
    {
        // US21/US22: 配送完了予約 → 料金算出 → 精算書発行 → 詳細照会（基本料金・支払状態）。
        var bookingId = await CreateDeliveredBookingAsync("VYG-BILL-001");
        var billing = await LoginAsync("billing");

        var location = await GenerateInvoiceAsync(billing, bookingId);
        location.Should().StartWith("/billing/invoices/INV-");

        var detail = await billing.GetStringAsync(location);
        detail.Should().Contain("精算書詳細").And.Contain("基本料金").And.Contain("支払待ち").And.Contain("精算明細");

        // 一覧にも精算書が表示される。
        var list = await billing.GetStringAsync("/billing/invoices");
        list.Should().Contain(bookingId.Replace("BKG-", "INV-"));
    }

    [Fact]
    public async Task 配送未完了の予約は精算書を発行できず警告される()
    {
        // US21 AC1（改善 #16）: Delivered 未満は発行不可。予約確定直後（未配送）で発行を試みる。
        var (bookingId, _) = await CreateConfirmedTrackedBookingAsync("VYG-BILL-002");
        var billing = await LoginAsync("billing");

        var location = await GenerateInvoiceAsync(billing, bookingId);

        location.Should().Be("/billing/invoices");
        var list = await billing.GetStringAsync("/billing/invoices");
        list.Should().Contain("配送完了（Delivered）の予約のみ");
    }

    [Fact]
    public async Task 入金確認で精算済になり予約状態も精算済へ同期される()
    {
        // US23: 精算書発行 → 入金確認 → 精算済（Confirmed）→ 予約状態 Settled 同期。
        var bookingId = await CreateDeliveredBookingAsync("VYG-BILL-003");
        var billing = await LoginAsync("billing");
        var detailUrl = await GenerateInvoiceAsync(billing, bookingId);

        // 入金確認（PRG）。
        var token = Token(await billing.GetStringAsync(detailUrl));
        var response = await billing.PostAsync($"{detailUrl}/payment", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["paymentMethod"] = "銀行振込",
            ["__RequestVerificationToken"] = token,
        }));
        response.StatusCode.Should().Be(HttpStatusCode.Redirect);

        // 精算書は精算済へ。
        var afterPayment = await billing.GetStringAsync(detailUrl);
        afterPayment.Should().Contain("精算済");

        // 予約状態も精算済（SETTLED）へ同期される（post-commit イベント経由）。
        var sales = await LoginAsync("sales");
        var booking = await sales.GetStringAsync($"/bookings/{bookingId}");
        booking.Should().Contain("SETTLED");
    }
}

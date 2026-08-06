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

    [Fact]
    public async Task 既存航海スケジュールを呼び出して更新すると一覧に更新内容が表示される()
    {
        var client = await LoginAsRouteDesignerAsync();
        await PostVoyageAsync(client, "VYG-WEB-004");

        var edit = await client.GetStringAsync("/voyages/VYG-WEB-004/edit");
        edit.Should().Contain("既存内容")
            .And.Contain("更新内容")
            .And.Contain("Kiso Maru")
            .And.Contain("JPTYO")
            // US25 差分ハイライト（M9）：変更セル強調の説明とベースライン埋め込みを確認。
            .And.Contain("変更したセル")
            .And.Contain("movement-baseline");

        var response = await PostUpdateAsync(client, "VYG-WEB-004", Token(edit), "0", "Shinano Maru", "Updated Carrier");

        response.StatusCode.Should().Be(HttpStatusCode.Redirect);
        response.Headers.Location!.OriginalString.Should().Be("/voyages");

        var index = await client.GetStringAsync("/voyages");
        // 一覧の区間列は起点→終点のサマリ表示（中間港は詳細/編集画面で確認）。
        // 更新の反映は船名・運送会社（一覧に表示される更新対象）と終点で検証する。
        index.Should().Contain("航海スケジュールを更新しました")
            .And.Contain("VYG-WEB-004")
            .And.Contain("Shinano Maru")
            .And.Contain("Updated Carrier")
            .And.Contain("DEHAM");
    }

    [Fact]
    public async Task キャンセルでは航海スケジュールは変更されない()
    {
        var client = await LoginAsRouteDesignerAsync();
        await PostVoyageAsync(client, "VYG-WEB-005");

        var edit = await client.GetStringAsync("/voyages/VYG-WEB-005/edit");
        edit.Should().Contain("キャンセル");

        var unchanged = await client.GetStringAsync("/voyages/VYG-WEB-005/edit");
        unchanged.Should().Contain("VYG-WEB-005")
            .And.Contain("Kiso Maru")
            .And.NotContain("Shinano Maru");
    }

    [Fact]
    public async Task 並行更新された航海スケジュールは更新できない()
    {
        var client = await LoginAsRouteDesignerAsync();
        await PostVoyageAsync(client, "VYG-WEB-006");
        var edit = await client.GetStringAsync("/voyages/VYG-WEB-006/edit");
        var token = Token(edit);

        await PostUpdateAsync(client, "VYG-WEB-006", token, "0", "Shinano Maru", "Updated Carrier");
        var response = await PostUpdateAsync(client, "VYG-WEB-006", token, "0", "Stale Maru", "Stale Carrier");

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var body = await response.Content.ReadAsStringAsync();
        body.Should().Contain("並行更新").And.Contain("alert-danger");
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

    private static Task<HttpResponseMessage> PostUpdateAsync(
        HttpClient client,
        string voyageNumber,
        string token,
        string version,
        string vesselName,
        string carrier)
        => client.PostAsync($"/voyages/{voyageNumber}", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["Version"] = version,
            ["VesselName"] = vesselName,
            ["Carrier"] = carrier,
            ["SupportedCargoTypes"] = "General",
            ["CarrierMovements[0].DepartureLocationUnLocode"] = "JPTYO",
            ["CarrierMovements[0].ArrivalLocationUnLocode"] = "CNSHA",
            ["CarrierMovements[0].DepartureDate"] = "2026-10-01T10:00:00+00:00",
            ["CarrierMovements[0].ArrivalDate"] = "2026-10-03T10:00:00+00:00",
            ["CarrierMovements[1].DepartureLocationUnLocode"] = "CNSHA",
            ["CarrierMovements[1].ArrivalLocationUnLocode"] = "DEHAM",
            ["CarrierMovements[1].DepartureDate"] = "2026-10-04T10:00:00+00:00",
            ["CarrierMovements[1].ArrivalDate"] = "2026-10-18T10:00:00+00:00",
            ["__RequestVerificationToken"] = token,
        }));
}

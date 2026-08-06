using System.Text.RegularExpressions;
using FluentAssertions;
using Microsoft.Playwright;

namespace CargoTracker.E2E.Tests;

/// <summary>見積作成の E2E（US01・タスク 4.5）。ログイン → 見積作成 → 詳細でルート候補表示を検証する。</summary>
[Collection("E2E")]
[Trait("Category", "E2E")]
public sealed class EstimateCreationE2ETest(E2EFixture fixture)
{
    [Fact]
    public async Task 営業担当者がログインして見積を作成し詳細でルート候補を確認できる()
    {
        var page = await fixture.NewLoggedInPageAsync("sales");

        // 見積作成画面へ遷移して入力・作成する
        await page.GotoAsync($"{fixture.BaseUrl}/estimates/new");
        await page.FillAsync("#OriginUnLocode", "JPTYO");
        await page.FillAsync("#DestinationUnLocode", "DEHAM");
        await page.FillAsync("#ArrivalDeadline", "2026-09-30");
        await page.SelectOptionAsync("#CargoType", "General");
        await page.FillAsync("#WeightKg", "1200");
        // navbar のログアウトボタンと区別するため、フォームの送信ボタンを文言で特定する。
        await page.ClickAsync("button:has-text('見積を作成')");

        // PRG で詳細（/estimates/{guid}）へ遷移し、見積番号とルート候補が表示される
        await page.WaitForURLAsync(new Regex($"{Regex.Escape(fixture.BaseUrl)}/estimates/[0-9a-fA-F-]{{36}}"));
        await Expect(page.GetByText("見積詳細")).ToBeVisibleAsync();
        await Expect(page.GetByText("V-STUB-01")).ToBeVisibleAsync();
        var body = await page.ContentAsync();
        body.Should().Contain("ルート候補").And.Contain("見積番号");
    }

    [Fact]
    public async Task 貨物種別を危険物に切り替えると危険物申告フォームがhtmxで表示される()
    {
        var page = await fixture.NewLoggedInPageAsync("sales");
        await page.GotoAsync($"{fixture.BaseUrl}/estimates/new");

        // 初期（一般貨物）は危険物申告フォーム非表示
        await Expect(page.Locator("#hazardous-fields")).ToHaveCountAsync(0);

        // 危険物を選択すると htmx で申告フォームが差し込まれる
        await page.SelectOptionAsync("#CargoType", "Hazardous");
        await Expect(page.Locator("#hazardous-fields")).ToBeVisibleAsync();
        await Expect(page.Locator("#UnNumber")).ToBeVisibleAsync();

        // 一般貨物に戻すと再び非表示になる
        await page.SelectOptionAsync("#CargoType", "General");
        await Expect(page.Locator("#hazardous-fields")).ToHaveCountAsync(0);
    }

    private static ILocatorAssertions Expect(ILocator locator) => Assertions.Expect(locator);
}

using FluentAssertions;
using Microsoft.Playwright;

namespace CargoTracker.E2E.Tests;

/// <summary>荷主登録の E2E（US02・タスク 3.4）。ログイン → 荷主登録 → 一覧表示をブラウザで検証する。</summary>
[Collection("E2E")]
[Trait("Category", "E2E")]
public sealed class ShipperRegistrationE2ETest(E2EFixture fixture)
{
    [Fact]
    public async Task 営業担当者がログインして個人荷主を登録し一覧で確認できる()
    {
        var page = await fixture.NewLoggedInPageAsync("sales");
        var email = $"e2e_{Guid.NewGuid():N}@example.com";

        // 荷主登録画面へ遷移して個人荷主を登録する
        await page.GotoAsync($"{fixture.BaseUrl}/shippers/new");
        await page.CheckAsync("#typeIndividual");
        await page.FillAsync("#Name", "E2E 太郎");
        await page.FillAsync("#Email", email);
        await page.FillAsync("#Phone", "03-0000-0000");
        await page.FillAsync("#Address", "東京都港区1-2-3");
        // navbar のログアウトボタンと区別するため、フォームの送信ボタンを文言で特定する。
        await page.ClickAsync("button:has-text('登録する')");

        // PRG で一覧へ遷移し、登録した荷主が表示される
        await page.WaitForURLAsync($"{fixture.BaseUrl}/shippers");
        var body = await page.ContentAsync();
        body.Should().Contain("E2E 太郎").And.Contain(email);
        await Expect(page.Locator(".alert-success")).ToContainTextAsync("荷主を登録しました");
    }

    [Fact]
    public async Task 荷主種別を法人に切り替えると契約番号フィールドがhtmxで表示される()
    {
        var page = await fixture.NewLoggedInPageAsync("sales");
        await page.GotoAsync($"{fixture.BaseUrl}/shippers/new");

        // 初期（個人）は法人フィールド非表示
        await Expect(page.Locator("#ContractNumber")).ToHaveCountAsync(0);

        // 法人を選択すると htmx で契約番号・割引率が差し込まれる
        await page.CheckAsync("#typeCorporate");
        await Expect(page.Locator("#ContractNumber")).ToBeVisibleAsync();
        await Expect(page.Locator("#DiscountPercent")).ToBeVisibleAsync();

        // 個人に戻すと再び非表示になる
        await page.CheckAsync("#typeIndividual");
        await Expect(page.Locator("#ContractNumber")).ToHaveCountAsync(0);
    }

    private static ILocatorAssertions Expect(ILocator locator) => Assertions.Expect(locator);
}

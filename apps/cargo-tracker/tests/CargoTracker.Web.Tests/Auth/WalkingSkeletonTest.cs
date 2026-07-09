using System.Net;
using System.Text.RegularExpressions;
using FluentAssertions;
using Microsoft.AspNetCore.Mvc.Testing;

namespace CargoTracker.Web.Tests.Auth;

/// <summary>
/// ウォーキングスケルトンの検証（US26 受入条件 6）。ロール別ナビの表示制御と
/// プレースホルダルートのロール制御・到達性を確認する。
/// </summary>
public sealed class WalkingSkeletonTest : IClassFixture<AuthenticationFlowTest.AuthWebFactory>
{
    private readonly AuthenticationFlowTest.AuthWebFactory _factory;

    public WalkingSkeletonTest(AuthenticationFlowTest.AuthWebFactory factory) => _factory = factory;

    private static string ExtractAntiforgeryToken(string html)
    {
        var match = Regex.Match(html, "name=\"__RequestVerificationToken\"[^>]*value=\"([^\"]+)\"");
        return match.Groups[1].Value;
    }

    /// <summary>指定シードユーザーでログイン済みのクライアントを返す（リダイレクト追従なし）。</summary>
    private async Task<HttpClient> LoginAsAsync(string username)
    {
        var client = _factory.CreateClient(new WebApplicationFactoryClientOptions { AllowAutoRedirect = false });
        var token = ExtractAntiforgeryToken(await client.GetStringAsync("/login"));
        var response = await client.PostAsync("/login", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["Username"] = username,
            ["Password"] = "Password1!",
            ["__RequestVerificationToken"] = token,
        }));
        response.StatusCode.Should().Be(HttpStatusCode.Redirect);
        return client;
    }

    [Fact]
    public async Task 営業ロールのダッシュボードには営業メニューのみ表示される()
    {
        var client = await LoginAsAsync("sales");

        var dashboard = await client.GetStringAsync("/");

        dashboard.Should().Contain("荷主管理").And.Contain("見積管理").And.Contain("貨物予約");
        dashboard.Should().NotContain("管理設定").And.NotContain("請求管理").And.NotContain("航路管理");
    }

    [Fact]
    public async Task 管理者ロールのナビには管理設定が表示される()
    {
        var client = await LoginAsAsync("admin");

        var dashboard = await client.GetStringAsync("/");

        dashboard.Should().Contain("管理設定");
    }

    [Fact]
    public async Task 経路設計者ロールのナビとダッシュボードに航路管理と経路設計が表示される()
    {
        // ナビゲーション整合性の必須検証: 新規画面（IT3 の航路管理・経路設計）が
        // ナビバー／ダッシュボードにロール条件付きで反映されていることを保証する。
        var client = await LoginAsAsync("router");

        var dashboard = await client.GetStringAsync("/");

        // ダッシュボード本体のカード固有文言（navbar には無い）で検証する
        dashboard.Should().Contain("航海スケジュール").And.Contain("経路候補の算出");
        dashboard.Should().Contain("/voyages").And.Contain("/routing/requests");
        dashboard.Should().NotContain("管理設定").And.NotContain("請求管理");
    }

    [Fact]
    public async Task 営業ロールは貨物予約登録画面にアクセスできる()
    {
        // /bookings は IT2（US04）で実画面化済み。/bookings/new の登録フォームに到達できることを検証する
        var client = await LoginAsAsync("sales");

        var response = await client.GetAsync("/bookings/new");

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        (await response.Content.ReadAsStringAsync()).Should().Contain("貨物予約登録");
    }

    [Fact]
    public async Task 権限のないロールは他フローのプレースホルダにアクセスできない()
    {
        // 営業ロールで管理設定（ROLE_ADMIN 専用）にアクセスする
        var client = await LoginAsAsync("sales");

        var response = await client.GetAsync("/admin/discount-policies");

        // 認可外は専用の 403 画面（AccessDeniedPath = /forbidden）へリダイレクトされる
        response.StatusCode.Should().Be(HttpStatusCode.Redirect);
        response.Headers.Location!.OriginalString.Should().Contain("/forbidden");
    }

    [Fact]
    public async Task 認可外リダイレクト先では権限エラー画面が表示される()
    {
        var client = await LoginAsAsync("sales");

        var response = await client.GetAsync("/forbidden");

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        (await response.Content.ReadAsStringAsync()).Should().Contain("権限がありません");
    }

    [Fact]
    public async Task ログアウトするとセッションが破棄されログイン画面に戻る()
    {
        var client = await LoginAsAsync("sales");

        // ダッシュボードのログアウトフォームからトークンを取得してログアウトする
        var token = ExtractAntiforgeryToken(await client.GetStringAsync("/"));
        var logout = await client.PostAsync("/logout", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["__RequestVerificationToken"] = token,
        }));
        logout.StatusCode.Should().Be(HttpStatusCode.Redirect);
        logout.Headers.Location!.OriginalString.Should().Contain("/login");

        // ログアウト後は保護ページがログインへリダイレクトされる（セッション破棄）
        var afterLogout = await client.GetAsync("/");
        afterLogout.StatusCode.Should().Be(HttpStatusCode.Redirect);
        afterLogout.Headers.Location!.OriginalString.Should().Contain("/login");
    }
}

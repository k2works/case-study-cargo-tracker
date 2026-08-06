using System.Net;
using System.Text.RegularExpressions;
using FluentAssertions;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.Data.Sqlite;
using Microsoft.Extensions.Hosting;

namespace CargoTracker.Web.Tests.Routing;

/// <summary>
/// Routing のデモシード（航海スケジュール・経路設計依頼）が起動時に投入され、
/// 経路設計者ロールで航路管理・経路設計依頼一覧に表示されることを検証する。
/// デモデータがテストの他フローを汚染しないよう、専用ファクトリ（Routing デモ有効）で分離する。
/// </summary>
public sealed class RoutingDemoSeedTest : IClassFixture<RoutingDemoSeedTest.RoutingDemoWebFactory>
{
    private readonly RoutingDemoWebFactory _factory;

    public RoutingDemoSeedTest(RoutingDemoWebFactory factory) => _factory = factory;

    private static string Token(string html) =>
        Regex.Match(html, "name=\"__RequestVerificationToken\"[^>]*value=\"([^\"]+)\"").Groups[1].Value;

    private async Task<HttpClient> LoginAsRouterAsync()
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
    public async Task 経路設計者は航路管理でデモ航海を確認できる()
    {
        var client = await LoginAsRouterAsync();

        var voyages = await client.GetStringAsync("/voyages");

        voyages.Should().Contain("VYG-DEMO-001")
            .And.Contain("VYG-DEMO-002")
            .And.Contain("VYG-DEMO-003")
            .And.Contain("SAKURA MARU");
    }

    [Fact]
    public async Task 経路設計者は経路設計依頼一覧でデモ依頼を確認できる()
    {
        var client = await LoginAsRouterAsync();

        var requests = await client.GetStringAsync("/routing/requests");

        // 経路設計に引き渡された（RouteProposed）デモ予約が一覧に表示される。
        requests.Should().Contain("JPTYO").And.Contain("DEHAM");
    }

    /// <summary>Routing デモを有効化した SQLite 一時 DB ファクトリ。</summary>
    public sealed class RoutingDemoWebFactory : WebApplicationFactory<Program>
    {
        private readonly string _dbPath = Path.Combine(Path.GetTempPath(), $"ct_routingdemo_{Guid.NewGuid():N}.db");

        protected override void ConfigureWebHost(IWebHostBuilder builder)
        {
            builder.UseEnvironment(Environments.Development);
            builder.UseSetting("Database:Provider", "Sqlite");
            builder.UseSetting("Database:ConnectionString", $"Data Source={_dbPath}");
            builder.UseSetting("Seed:IncludeRoutingDemo", "true");
        }

        protected override void Dispose(bool disposing)
        {
            base.Dispose(disposing);
            if (disposing)
            {
                SqliteConnection.ClearAllPools();
                if (File.Exists(_dbPath))
                {
                    File.Delete(_dbPath);
                }
            }
        }
    }
}

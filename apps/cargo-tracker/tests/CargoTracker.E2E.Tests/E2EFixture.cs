using System.Diagnostics;
using System.Net.Sockets;
using Microsoft.Playwright;

namespace CargoTracker.E2E.Tests;

/// <summary>
/// E2E 用フィクスチャ。アプリを Development / SQLite（一時 DB）で実プロセス起動し、
/// Playwright の Chromium（ヘッドレス）で駆動する。テストクラス間で共有する。
/// </summary>
public sealed class E2EFixture : IAsyncLifetime
{
    private readonly string _dbPath = Path.Combine(Path.GetTempPath(), $"ct_e2e_{Guid.NewGuid():N}.db");
    private Process? _app;
    private IPlaywright _playwright = null!;

    public IBrowser Browser { get; private set; } = null!;
    public string BaseUrl { get; private set; } = string.Empty;

    private static int FreePort()
    {
        var listener = new TcpListener(System.Net.IPAddress.Loopback, 0);
        listener.Start();
        var port = ((System.Net.IPEndPoint)listener.LocalEndpoint).Port;
        listener.Stop();
        return port;
    }

    private static string FindWebProjectPath()
    {
        var dir = new DirectoryInfo(AppContext.BaseDirectory);
        while (dir is not null)
        {
            var candidate = Path.Combine(dir.FullName, "src", "CargoTracker.Web", "CargoTracker.Web.csproj");
            if (File.Exists(candidate))
            {
                return candidate;
            }
            dir = dir.Parent;
        }
        throw new DirectoryNotFoundException("CargoTracker.Web.csproj を特定できませんでした。");
    }

    public async Task InitializeAsync()
    {
        var port = FreePort();
        BaseUrl = $"http://127.0.0.1:{port}";

        var startInfo = new ProcessStartInfo("dotnet")
        {
            ArgumentList =
            {
                "run", "--project", FindWebProjectPath(),
                "--no-build", "--no-launch-profile", "--urls", BaseUrl,
            },
            UseShellExecute = false,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
        };
        startInfo.Environment["ASPNETCORE_ENVIRONMENT"] = "Development";
        startInfo.Environment["Database__Provider"] = "Sqlite";
        startInfo.Environment["Database__ConnectionString"] = $"Data Source={_dbPath}";

        _app = Process.Start(startInfo)!;
        _ = _app.StandardOutput.ReadToEndAsync();
        _ = _app.StandardError.ReadToEndAsync();

        await WaitForReadyAsync();

        _playwright = await Playwright.CreateAsync();
        Browser = await _playwright.Chromium.LaunchAsync(new BrowserTypeLaunchOptions { Headless = true });
    }

    private async Task WaitForReadyAsync()
    {
        using var client = new HttpClient { Timeout = TimeSpan.FromSeconds(3) };
        for (var i = 0; i < 60; i++)
        {
            if (_app!.HasExited)
            {
                throw new InvalidOperationException("アプリの起動に失敗しました。");
            }
            try
            {
                var response = await client.GetAsync($"{BaseUrl}/health");
                if (response.IsSuccessStatusCode)
                {
                    return;
                }
            }
            catch (HttpRequestException)
            {
                // 起動待ち
            }
            catch (TaskCanceledException)
            {
                // タイムアウト時は再試行
            }
            await Task.Delay(1000);
        }
        throw new TimeoutException("アプリが起動しませんでした。");
    }

    public async Task<IPage> NewLoggedInPageAsync(string username = "sales")
    {
        var page = await Browser.NewPageAsync();
        await page.GotoAsync($"{BaseUrl}/login");
        // 開発環境ではユーザー名・パスワードがプリフィルされる。ユーザー名のみ上書きする。
        await page.FillAsync("#Username", username);
        await page.FillAsync("#Password", "Password1!");
        await page.ClickAsync("button[type=submit]");
        await page.WaitForURLAsync($"{BaseUrl}/");
        return page;
    }

    public async Task DisposeAsync()
    {
        if (Browser is not null)
        {
            await Browser.CloseAsync();
        }
        _playwright?.Dispose();

        if (_app is { HasExited: false })
        {
            _app.Kill(entireProcessTree: true);
            await _app.WaitForExitAsync();
        }
        _app?.Dispose();

        if (File.Exists(_dbPath))
        {
            File.Delete(_dbPath);
        }
    }
}

[CollectionDefinition("E2E")]
public sealed class E2ETestGroup : ICollectionFixture<E2EFixture>;

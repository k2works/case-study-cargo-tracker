using System.Security.Claims;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authentication.Cookies;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CargoTracker.Shared.Infrastructure.Auth;

/// <summary>ログイン・ログアウト（US26）。Cookie 認証で資格情報を検証しロールを Claim に載せる。</summary>
[AllowAnonymous]
public sealed class AuthController(UserAuthenticator authenticator, IWebHostEnvironment environment) : Controller
{
    [HttpGet("/login")]
    public IActionResult Login(bool timeout = false)
    {
        if (timeout)
        {
            ViewData["Error"] = "セッションの有効期限が切れました。再度ログインしてください。";
        }

        // 開発環境ではシードユーザーの既定資格情報を初期入力状態にする（デモ利便性）。
        // 本番ではプリフィルしない（セキュリティ）。
        var form = environment.IsDevelopment()
            ? new LoginForm { Username = "sales", Password = UserSeeder.DefaultPassword }
            : new LoginForm();
        return View(form);
    }

    [HttpPost("/login")]
    [ValidateAntiForgeryToken]
    public async Task<IActionResult> Login(LoginForm form, CancellationToken ct)
    {
        if (!ModelState.IsValid)
        {
            return View(form);
        }

        var user = await authenticator.AuthenticateAsync(form.Username, form.Password, ct);
        if (user is null)
        {
            // 認証失敗（受入条件 2）: ユーザー名を保持しパスワードはクリアしてエラー表示。
            ViewData["Error"] = "ユーザー名またはパスワードが正しくありません";
            return View(new LoginForm { Username = form.Username });
        }

        var claims = new List<Claim>
        {
            new(ClaimTypes.Name, user.Username),
            new(ClaimTypes.Role, user.Role),
        };
        var identity = new ClaimsIdentity(claims, CookieAuthenticationDefaults.AuthenticationScheme);
        await HttpContext.SignInAsync(CookieAuthenticationDefaults.AuthenticationScheme, new ClaimsPrincipal(identity));

        return LocalRedirect("/");
    }

    [HttpPost("/logout")]
    [ValidateAntiForgeryToken]
    public async Task<IActionResult> Logout()
    {
        await HttpContext.SignOutAsync(CookieAuthenticationDefaults.AuthenticationScheme);
        return LocalRedirect("/login");
    }
}

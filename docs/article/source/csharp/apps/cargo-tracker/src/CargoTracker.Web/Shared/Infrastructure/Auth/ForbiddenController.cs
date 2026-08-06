using Microsoft.AspNetCore.Mvc;

namespace CargoTracker.Shared.Infrastructure.Auth;

/// <summary>認証済みだが必要ロールを持たないユーザー向けの 403 画面。</summary>
public sealed class ForbiddenController : Controller
{
    [HttpGet("/forbidden")]
    public IActionResult Index() => View("~/Views/Auth/Forbidden.cshtml");
}

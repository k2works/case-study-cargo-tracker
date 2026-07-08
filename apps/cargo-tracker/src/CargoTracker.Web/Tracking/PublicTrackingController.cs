using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CargoTracker.Tracking;

/// <summary>
/// 公開貨物追跡（US26 受入条件 4・認証不要）。ウォーキングスケルトンの最小プレースホルダ。
/// 実画面は追跡フローの実装イテレーション（IT5）で拡張する。
/// </summary>
[AllowAnonymous]
public sealed class PublicTrackingController : Controller
{
    [HttpGet("/public/tracking/{trackingId}")]
    public IActionResult Show(string trackingId)
    {
        ViewData["TrackingId"] = trackingId;
        return View();
    }
}

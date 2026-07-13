using CargoTracker.Tracking.Application.Internal.QueryServices;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CargoTracker.Tracking;

/// <summary>
/// 公開貨物追跡（US18・認証不要）。荷主が URL 共有で貨物状態を照会できる。
/// </summary>
[AllowAnonymous]
public sealed class PublicTrackingController(TrackingQueryService queryService) : Controller
{
    [HttpGet("/public/tracking/{trackingId}")]
    public async Task<IActionResult> Show(string trackingId, CancellationToken ct)
    {
        var detail = await queryService.FindByTrackingNumberAsync(trackingId, ct);
        ViewData["TrackingId"] = trackingId;
        return View(detail);
    }
}

using CargoTracker.Shared.Infrastructure.Auth;
using CargoTracker.Tracking.Application.Internal.CommandServices;
using CargoTracker.Tracking.Application.Internal.QueryServices;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CargoTracker.Tracking.Interfaces;

/// <summary>追跡情報照会・手動更新（US17/US18）。認証照会（荷主・荷受人・追跡管理者）と公開ページ（認証不要）。</summary>
public sealed class TrackingController(
    TrackingQueryService queryService,
    AddTrackingEventCommandService addTrackingEventCommandService) : Controller
{
    [Authorize(Roles = Roles.Tracker)]
    [HttpGet("/tracking")]
    public IActionResult Index() => View(model: (string?)null);

    [Authorize(Roles = Roles.Tracker)]
    [HttpGet("/tracking/{trackingNumber}")]
    public async Task<IActionResult> Detail(string trackingNumber, CancellationToken ct)
    {
        var detail = await queryService.FindByTrackingNumberAsync(trackingNumber, ct);
        if (detail is null)
        {
            return View("Detail", model: null);
        }
        return View("Detail", detail);
    }

    [Authorize(Roles = Roles.Tracker)]
    [HttpPost("/tracking/{trackingNumber}/events")]
    [ValidateAntiForgeryToken]
    public async Task<IActionResult> AddEvent(
        string trackingNumber, string eventType, string locationUnLocode,
        DateTimeOffset eventTime, string? voyageNumber, CancellationToken ct)
    {
        try
        {
            await addTrackingEventCommandService.HandleAsync(new AddTrackingEventCommand(
                trackingNumber, eventType, locationUnLocode, eventTime, voyageNumber), ct);
            TempData["SuccessMessage"] = "追跡情報を更新しました。";
        }
        catch (Exception ex) when (ex is ArgumentException or InvalidOperationException)
        {
            TempData["WarningMessage"] = ex.Message;
        }

        return LocalRedirect($"/tracking/{trackingNumber}");
    }
}

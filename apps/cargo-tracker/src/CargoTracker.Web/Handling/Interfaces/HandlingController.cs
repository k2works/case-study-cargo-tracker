using CargoTracker.Handling.Application.Internal.CommandServices;
using CargoTracker.Handling.Application.Internal.OutboundServices;
using CargoTracker.Handling.Application.Internal.QueryServices;
using CargoTracker.Shared.Infrastructure.Auth;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CargoTracker.Handling.Interfaces;

/// <summary>荷役作業登録・一覧（US15/US16）。荷役作業員のみ利用可。</summary>
[Authorize(Roles = Roles.Handler)]
public sealed class HandlingController(
    HandlingActivityQueryService queryService,
    RegisterHandlingActivityCommandService commandService,
    ITrackingNumberResolver trackingNumberResolver) : Controller
{
    [HttpGet("/handling")]
    public async Task<IActionResult> Index(CancellationToken ct)
        => View(new HandlingListViewModel(await queryService.FindRecentAsync(ct)));

    [HttpGet("/handling/new")]
    public IActionResult New() => View(new HandlingForm());

    [HttpPost("/handling")]
    [ValidateAntiForgeryToken]
    public async Task<IActionResult> Create(HandlingForm form, CancellationToken ct)
    {
        if (!ModelState.IsValid)
        {
            return View("New", form);
        }

        var bookingId = await trackingNumberResolver.ResolveBookingIdAsync(form.TrackingNumber, ct);
        if (bookingId is null)
        {
            ModelState.AddModelError(nameof(form.TrackingNumber), "追跡番号が見つかりません。番号を確認してください。");
            return View("New", form);
        }

        try
        {
            var result = await commandService.HandleAsync(new RegisterHandlingActivityCommand(
                bookingId, form.EventType, form.LocationUnLocode, form.CompletionTime,
                form.VoyageNumber, form.ConsigneeConfirmation), ct);

            if (result.IsMisrouted)
            {
                TempData["WarningMessage"] = "予定外の作業場所です（経路誤りの可能性）。記録は登録しました。";
            }
            else if (result.IsOffRoute)
            {
                TempData["WarningMessage"] = "作業場所が予定ルートと異なります。記録は登録しました。";
            }
            else
            {
                TempData["SuccessMessage"] = "荷役作業を登録しました。";
            }
            return LocalRedirect("/handling");
        }
        catch (Exception ex) when (ex is ArgumentException or InvalidOperationException)
        {
            ModelState.AddModelError(string.Empty, ex.Message);
            return View("New", form);
        }
    }
}
